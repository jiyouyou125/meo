(ns iwaswhere-web.client-store
  (:require #?(:cljs [alandipert.storage-atom :refer [local-storage]])
    [matthiasn.systems-toolbox.component :as st]
    [iwaswhere-web.keepalive :as ka]
    [iwaswhere-web.client-store-entry :as cse]
    [iwaswhere-web.client-store-search :as s]))

(defn print-duration
  [msg-meta]
  (when msg-meta
    (let [store-meta (:client/store-cmp msg-meta)
          store-duration (- (:in-ts store-meta) (:out-ts store-meta))
          cli-ws-meta (:client/ws-cmp msg-meta)
          cli-ws-duration (- (:out-ts cli-ws-meta) (:in-ts cli-ws-meta))
          srv-ws-meta (:server/ws-cmp msg-meta)
          srv-ws-duration (- (:in-ts srv-ws-meta) (:out-ts srv-ws-meta))
          srv-store-meta (:server/store-cmp msg-meta)
          srv-store-duration (- (:out-ts srv-store-meta) (:in-ts srv-store-meta))]
      (prn msg-meta)
      (prn "duration from and to :client/store-cmp in ms:" store-duration)
      (prn "duration from and to :client/ws-cmp in ms:" cli-ws-duration)
      (prn "ms from :client/store-cmp to :client/ws-cmp:" (- (:in-ts cli-ws-meta) (:out-ts store-meta)))
      (prn "ms from :client/ws-cmp to :client/store-cmp:" (- (:in-ts store-meta) (:out-ts cli-ws-meta)))
      (prn "duration from and to :server/ws-cmp in ms:" srv-ws-duration)
      (prn "duration from and to :server/store-cmp in ms:" srv-store-duration))))

(defn new-state-fn
  "Update client side state with list of journal entries received from backend."
  [{:keys [current-state msg-payload msg-meta]}]
  (print-duration msg-meta)
  (let [entries-map (into {} (map (fn [entry] [(:timestamp entry) entry]) (:entries msg-payload)))
        new-state (-> current-state
                      (assoc-in [:entries] (:entries msg-payload))
                      (assoc-in [:entries-map] entries-map)
                      (assoc-in [:cfg :hashtags] (:hashtags msg-payload))
                      (assoc-in [:stats] (:stats msg-payload))
                      (assoc-in [:duration-ms] (:duration-ms msg-payload))
                      (assoc-in [:cfg :mentions] (:mentions msg-payload)))]
    {:new-state new-state}))

(defn initial-state-fn
  "Creates the initial component state atom. Holds a list of entries from the backend,
  a map with temporary entries that are being edited but not saved yet, and sets that
  contain information for which entries to show the map, or the edit mode."
  [_put-fn]
  (let [initial-state (atom {:entries        []
                             :last-alive     (st/now)
                             :new-entries    @cse/new-entries-ls
                             :current-query  {}
                             :cfg            {:active             nil
                                              :show-maps-for      #{}
                                              :show-comments-for  #{}
                                              :sort-by-upvotes    false
                                              :show-all-maps      false
                                              :show-hashtags      true
                                              :comments-w-entries true
                                              :show-context       true
                                              :show-pvt           false}})]
    {:state initial-state}))

(defn toggle-set-fn
  "Toggles for example the visibility of a map or the edit mode for an individual
  journal entry. Requires the key to exist on the application state as a set."
  [{:keys [current-state msg-payload]}]
  (let [path (:path msg-payload)
        timestamp (:timestamp msg-payload)
        new-state (if (contains? (get-in current-state path) timestamp)
                    (update-in current-state path disj timestamp)
                    (update-in current-state path conj timestamp))]
    {:new-state new-state}))

(defn toggle-key-fn
  "Toggles config key."
  [{:keys [current-state msg-payload]}]
  (let [path (:path msg-payload)]
    {:new-state (update-in current-state path not)}))

(defn show-more-fn
  "Runs previous query but with more results. Also updates the number to show in the UI."
  [{:keys [current-state]}]
  (let [current-query (:current-query current-state)
        new-query (update-in current-query [:n] + 20)
        new-state (assoc-in current-state [:current-query] new-query)]
    {:new-state new-state
     :emit-msg  [:state/get new-query]}))

(defn set-active-fn
  "Sets entry in payload as the active entry for which to show linked entries."
  [{:keys [current-state msg-payload]}]
  {:new-state (assoc-in current-state [:cfg :active] msg-payload)})

(defn cmp-map
  "Creates map for the component which holds the client-side application state."
  [cmp-id]
  {:cmp-id            cmp-id
   :state-fn          initial-state-fn
   :snapshot-xform-fn #(dissoc % :last-alive)
   :state-spec        :state/client-store-spec
   :handler-map       (merge cse/entry-handler-map
                             s/search-handler-map
                             {:state/new          new-state-fn
                              :show/more          show-more-fn
                              :cmd/set-active     set-active-fn
                              :cmd/toggle         toggle-set-fn
                              :cmd/toggle-key     toggle-key-fn
                              :cmd/keep-alive     ka/reset-fn
                              :cmd/keep-alive-res ka/set-alive-fn})})