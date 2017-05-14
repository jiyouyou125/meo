(ns iwaswhere-web.graph.stats
  "Get stats from graph."
  (:require [ubergraph.core :as uber]
            [iwaswhere-web.graph.query :as gq]
            [clj-time.core :as t]
            [iwaswhere-web.graph.stats.awards :as aw]
            [iwaswhere-web.graph.stats.time :as t-s]
            [iwaswhere-web.graph.stats.location :as sl]
            [iwaswhere-web.graph.stats.custom-fields :as cf]
            [iwaswhere-web.utils.misc :as u]
            [clj-time.format :as ctf]
            [matthiasn.systems-toolbox.log :as l]
            [clojure.tools.logging :as log]
            [ubergraph.core :as uc]
            [clojure.set :as set]
            [iwaswhere-web.zipkin :as z]))

(defn tasks-mapper
  "Create mapper function for task stats"
  [current-state]
  (fn [d]
    (let [g (:graph current-state)
          date-string (:date-string d)
          day-nodes (gq/get-nodes-for-day g {:date-string date-string})
          day-nodes-attrs (map #(uber/attrs g %) day-nodes)
          task-nodes (filter #(contains? (:tags %) "#task") day-nodes-attrs)
          done-nodes (filter #(contains? (:tags %) "#done") day-nodes-attrs)
          closed-nodes (filter #(contains? (:tags %) "#closed") day-nodes-attrs)
          day-stats {:date-string date-string
                     :tasks-cnt   (count task-nodes)
                     :done-cnt    (count done-nodes)
                     :closed-cnt  (count closed-nodes)}]
      [date-string day-stats])))

(defn wordcount-mapper
  "Create mapper function for wordcount stats"
  [current-state]
  (fn [d]
    (let [g (:graph current-state)
          date-string (:date-string d)
          day-nodes (gq/get-nodes-for-day g {:date-string date-string})
          day-nodes-attrs (map #(uber/attrs g %) day-nodes)
          counts (map (fn [entry] (u/count-words entry)) day-nodes-attrs)
          day-stats {:date-string date-string
                     :word-count  (apply + counts)}]
      [date-string day-stats])))

(defn media-mapper
  "Create mapper function for media stats"
  [current-state]
  (fn [d]
    (let [g (:graph current-state)
          date-string (:date-string d)
          day-nodes (gq/get-nodes-for-day g {:date-string date-string})
          day-nodes-attrs (map #(uber/attrs g %) day-nodes)
          day-stats {:date-string date-string
                     :photo-cnt   (count (filter :img-file day-nodes-attrs))
                     :audio-cnt   (count (filter :audio-file day-nodes-attrs))
                     :video-cnt   (count (filter :video-file day-nodes-attrs))}]
      [date-string day-stats])))

(defn res-count
  "Count results for specified query."
  [current-state query]
  (let [res (gq/get-filtered current-state (merge {:n Integer/MAX_VALUE} query))]
    (count (set (:entries res)))))

(defn completed-count
  "Count completed tasks."
  [current-state]
  (let [q1 {:tags #{"#task" "#done"} :n Integer/MAX_VALUE}
        q2 {:tags #{"#task"} :opts #{":done"} :n Integer/MAX_VALUE}
        res1 (set (:entries (gq/get-filtered current-state q1)))
        res2 (set (:entries (gq/get-filtered current-state q2)))]
    (count (set/union res1 res2))))

(defn task-summary-stats
  "Generate some very basic stats about the graph for display in UI."
  [state]
  {:open-tasks-cnt     (res-count state {:tags     #{"#task"}
                                         :not-tags #{"#done" "#backlog" "#closed"}})
   :started-tasks-cnt  (res-count state {:tags     #{"#task"}
                                         :not-tags #{"#done" "#backlog" "#closed"}
                                         :opts     #{":started"}})
   :backlog-cnt        (res-count state {:tags     #{"#task" "#backlog"}
                                         :not-tags #{"#done" "#closed"}})
   :completed-cnt      (completed-count state)
   :closed-cnt         (res-count state {:tags #{"#task" "#closed"}})})

(defn daily-summaries-mapper
  "Create mapper function for daily summary stats"
  [current-state]
  (fn [d]
    (let [day (:date-string d)
          today? (= day (ctf/unparse (ctf/formatters :year-month-day) (t/now)))
          day-stats (if today?
                      (task-summary-stats current-state)
                      (get-in current-state [:stats :daily-summaries day]))]
      [day (merge day-stats {:date-string day})])))

(defn get-stats-fn
  "Retrieves stats of specified type. Picks the appropriate mapper function
   for the requested message type."
  [{:keys [current-state msg-payload msg-meta put-fn span]}]
  (future
    (let [stats-type (:type msg-payload)
          stats-mapper (case stats-type
                         :stats/pomodoro t-s/time-mapper
                         :stats/custom-fields cf/custom-fields-mapper
                         :stats/tasks tasks-mapper
                         :stats/wordcount wordcount-mapper
                         :stats/media media-mapper
                         :stats/daily-summaries daily-summaries-mapper
                         nil)
          days (:days msg-payload)
          stats (when stats-mapper
                  (let [child-span (z/child-span span (str stats-type))
                        res (mapv (stats-mapper current-state) days)]
                    (.finish child-span)
                    (into {} res)))
          extracted-trace (if-let [t (:trace msg-meta)]
                            (z/extract-trace t)
                            span)]
      (log/info stats-type (count (str stats)))
      (.tag span "meta" (str msg-meta))
      (.tag span "tag" (:tag msg-meta))
      (if stats
        (put-fn (with-meta [:stats/result {:stats stats
                                           :type  stats-type}] msg-meta))
        (l/warn "No mapper defined for" stats-type))))
  {})

(defn get-basic-stats
  "Generate some very basic stats about the graph size for display in UI."
  [state span]
  {:entry-count (count (:sorted-entries state))
   :node-count  (count (:node-map (:graph state)))
   :edge-count  (count (uber/find-edges (:graph state) {}))
   :import-cnt  (res-count state {:tags #{"#import"}})
   :new-cnt     (res-count state {:tags #{"#new"}})
   :locations   (sl/locations state)})

(def started-tasks
  {:tags     #{"#task"}
   :not-tags #{"#done" "#backlog" "#closed"}
   :opts     #{":started"}})

(def waiting-habits
  {:tags #{"#habit"}
   :opts #{":waiting"}})

(defn make-stats-tags
  "Generate stats and tags from current-state."
  [state span]
  {:hashtags       (gq/find-all-hashtags state)
   :pvt-hashtags   (gq/find-all-pvt-hashtags state)
   :started-tasks  (:entries (gq/get-filtered state started-tasks))
   :waiting-habits (:entries (gq/get-filtered state waiting-habits))
   :pvt-displayed  (:pvt-displayed (:cfg state))
   :mentions       (gq/find-all-mentions state)
   :stories        (gq/find-all-stories state)
   :locations      (let [child-span (z/child-span span "locations")
                         s (gq/find-all-locations state)]
                     (.finish child-span)
                     s)
   :briefings      (gq/find-all-briefings state)
   :sagas          (gq/find-all-sagas state)
   :cfg            (:cfg state)})

(defn stats-tags-fn
  "Generates stats and tags (they only change on insert anyway) and initiates
   publication thereof to all connected clients."
  [{:keys [current-state put-fn msg-meta span]}]
  (future
    (let [child-span (z/child-span span "stats-tags-fn")
          stats-tags (make-stats-tags current-state child-span)
          uid (:sente-uid msg-meta)]
      (.finish child-span)
      (put-fn (with-meta [:state/stats-tags stats-tags] {:sente-uid uid}))))
  {})

(defn task-summary-stats2
  "Generate some very basic stats about the graph for display in UI."
  [state k span msg-meta put-fn]
  (future
    (let [child-span (z/child-span span (str "task-summary-stats-" k))
          uid (:sente-uid msg-meta)
          res (case k
                :open-tasks-cnt    (res-count state {:tags     #{"#task"}
                                                     :not-tags #{"#done" "#backlog" "#closed"}})
                :started-tasks-cnt (res-count state {:tags     #{"#task"}
                                                     :not-tags #{"#done" "#backlog" "#closed"}
                                                     :opts     #{":started"}})
                :backlog-cnt       (res-count state {:tags     #{"#task" "#backlog"}
                                                     :not-tags #{"#done" "#closed"}})
                :completed-cnt     (completed-count state)
                :closed-cnt        (res-count state {:tags #{"#task" "#closed"}}))]
      (.finish child-span)
      (put-fn (with-meta [:stats/result2 {k res}] {:sente-uid uid})))))

(defn get-stats-fn2
  "Generates stats and tags (they only change on insert anyway) and initiates
   publication thereof to all connected clients."
  [{:keys [current-state put-fn msg-meta span]}]
  (future
    (let [child-span (z/child-span span "get-stats-fn2")
          stats (get-basic-stats current-state child-span)
          uid (:sente-uid msg-meta)]
      (.finish child-span)
      (put-fn (with-meta [:stats/result2 stats] {:sente-uid uid}))))
  (task-summary-stats2 current-state :open-tasks-cnt span msg-meta put-fn)
  (task-summary-stats2 current-state :started-tasks-cnt span msg-meta put-fn)
  (task-summary-stats2 current-state :backlog-cnt span msg-meta put-fn)
  (task-summary-stats2 current-state :completed-cnt span msg-meta put-fn)
  (task-summary-stats2 current-state :closed-cnt span msg-meta put-fn)
  (future
    (let [child-span (z/child-span span "award-points")
          stats {:award-points (aw/award-points current-state)}
          uid (:sente-uid msg-meta)]
      (.finish child-span)
      (put-fn (with-meta [:stats/result2 stats] {:sente-uid uid}))))
  {})

(def stats-handler-map
  {:stats/get            (z/traced get-stats-fn :stats/get)
   :stats/get2            (z/traced get-stats-fn2 :stats/get)
   :state/stats-tags-get (z/traced stats-tags-fn :state/stats-tags-get)})