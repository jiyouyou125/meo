(ns iwaswhere-web.ui.charts.tasks
  (:require [reagent.core :as rc]
            [iwaswhere-web.ui.charts.common :as cc]))

(defn tasks-chart
  "Draws chart for opened and closed tasks, where the bars for the counts of
   newly opened tasks are drawn above a horizontal line and those for closed
   tasks below this line. The size of the the bars scales automatically
   depending on the maximum count found in the data.
   On mouse-over on any of the bars, the date and the values for the date are
   shown in an info div next to the bars."
  [task-stats chart-h]
  (let [local (rc/atom {})]
    (fn [task-stats chart-h]
      (let [indexed (map-indexed (fn [idx [_k v]] [idx v]) task-stats)
            max-cnt (apply max (map (fn [[_idx v]]
                                      (max (:tasks-cnt v) (:done-cnt v)))
                                    indexed))]
        [:div
         [:svg
          {:viewBox (str "0 0 600 " chart-h)}
          [:g
           [cc/chart-title "Tasks opened/closed"]
           (for [[idx v] indexed]
             (let [headline-reserved 50
                   chart-h-half (/ (- chart-h headline-reserved) 2)
                   y-scale (/ chart-h-half (or max-cnt 1))
                   h-tasks (* y-scale (:tasks-cnt v))
                   h-done (* y-scale (:done-cnt v))
                   x (* 10 idx)
                   mouse-enter-fn (cc/mouse-enter-fn local v)
                   mouse-leave-fn (cc/mouse-leave-fn local v)]
               ^{:key (str "tbar" (:date-string v) idx)}
               [:g {:on-mouse-enter mouse-enter-fn
                    :on-mouse-leave mouse-leave-fn}
                [:rect {:x      x
                        :y      (+ (- chart-h-half h-tasks) headline-reserved)
                        :width  9
                        :height h-tasks
                        :class  (cc/weekend-class "tasks" v)}]
                [:rect {:x      x
                        :y      (+ chart-h-half headline-reserved)
                        :width  9
                        :height h-done
                        :class  (cc/weekend-class "done" v)}]]))]]
         (when (:mouse-over @local)
           [:div.mouse-over-info (cc/info-div-pos @local)
            [:span (:date-string (:mouse-over @local))] [:br]
            [:span "Done: " (:done-cnt (:mouse-over @local))] [:br]
            [:span "Created: " (:tasks-cnt (:mouse-over @local))]
            [:br]])]))))