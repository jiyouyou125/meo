(ns meo.jvm.imports.screenshot
  (:require [clojure.pprint :as pp]
            [me.raynes.conch :refer [programs let-programs]]
            [taoensso.timbre :refer [info]]
            [meo.jvm.file-utils :as fu]))

(programs scrot)

(defn import-screenshot [{:keys [put-fn msg-meta msg-payload]}]
  (let [filename (str fu/img-path (:filename msg-payload))
        os (System/getProperty "os.name")]
    (info "importing screenshot" filename)
    (when (= os "Mac OS X")
      (let-programs [screencapture "/usr/sbin/screencapture"]
                    (screencapture filename)))
    (when (= os "Linux")
      (scrot filename)))
  {:emit-msg [:cmd/schedule-new
              {:timeout 3000 :message (with-meta [:search/refresh] msg-meta)}]})
