(defproject matthiasn/iwaswhere-web "0.0-SNAPSHOT"
  :description "iWasWhere - a personal information manager"
  :url "https://github.com/matthiasn/systems-toolbox"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE"
            :url  "https://www.gnu.org/licenses/agpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta4"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/tools.logging "0.4.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [hiccup "1.0.5"]
                 [clj-pid "0.1.2"]
                 [clj-time "0.14.0"]
                 [clj-http "3.7.0"]
                 [enlive "1.1.6"]
                 [me.raynes/fs "1.4.6"]
                 [markdown-clj "1.0.1"]
                 [clj-pdf "2.2.29"]
                 [cheshire "5.8.0"]
                 [com.taoensso/nippy "2.13.0" :exclusions [com.taoensso/encore]]
                 [com.taoensso/timbre "4.10.0" :exclusions [io.aviso/pretty]]
                 [cljsjs/moment "2.17.1-1"]
                 [com.drewnoakes/metadata-extractor "2.10.1"]
                 [ubergraph "0.4.0"]
                 [factual/geo "1.2.1"]
                 [camel-snake-kebab "0.4.0"]
                 [matthiasn/systems-toolbox "0.6.24"]
                 [matthiasn/systems-toolbox-kafka "0.6.13"]
                 [matthiasn/systems-toolbox-sente "0.6.19"]
                 [matthiasn/systems-toolbox-electron "0.6.14"]
                 [reagent "0.7.0" :exclusions [cljsjs/react cljsjs/react-dom]]
                 [re-frame "0.10.2"]
                 [secretary "1.2.3"]
                 [capacitor "0.6.0"]
                 [clucy "0.4.0"]
                 [seesaw "1.4.5"]
                 [clj.qrgen "0.4.0"]
                 [image-resizer "0.1.10"]
                 [danlentz/clj-uuid "0.1.7"]
                 [org.webjars.bower/fontawesome "4.7.0"]
                 [org.webjars.npm/randomcolor "0.4.4"]
                 [org.webjars.bower/normalize-css "5.0.0"]
                 [org.webjars.bower/leaflet "0.7.7"]
                 [org.webjars.npm/github-com-mrkelly-lato "0.3.0"]
                 [org.webjars.npm/intl "1.2.4"]]

  :source-paths ["src/cljc" "src/clj/"]

  :clean-targets ^{:protect false} ["resources/public/js/build" "prod" "target"
                                    "out" "dev"]
  :auto-clean false
  :uberjar-name "iwaswhere.jar"

  :main iwaswhere-web.core
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow" "-XX:+AggressiveOpts"]

  :profiles {:uberjar      {:aot :all}
             :test-reagent {:dependencies [[cljsjs/react "15.6.1-2"]
                                           [cljsjs/react-dom "15.6.1-2"]
                                           [cljsjs/create-react-class "15.6.0-2"]]}}

  :doo {:paths {:karma "./node_modules/karma/bin/karma"}}

  :plugins [[lein-cljsbuild "1.1.7"
             :exclusions [org.apache.commons/commons-compress]]
            [lein-figwheel "0.5.14"]
            [test2junit "1.3.3"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.6.14"]]

  ;:global-vars {*assert* false}

  :test2junit-run-ant true

  :aliases {"sass" ["do"
                    ["shell" "sass" "src/scss/iwaswhere.scss" "resources/public/css/iwaswhere.css"]
                    ["shell" "sass" "src/scss/updater.scss" "resources/public/css/updater.css"]
                    ["shell" "sass" "src/scss/loader.scss" "resources/public/css/loader.css"]]
            "dist" ["do"
                    ["clean"]
                    ["test"]
                    ["cljsbuild" "once" "main"]
                    ["cljsbuild" "once" "renderer"]
                    ["cljsbuild" "once" "geocoder"]
                    ["cljsbuild" "once" "updater"]
                    ["sass"]
                    ["uberjar"]
                    ["shell" "cp" "target/iwaswhere.jar" "bin/"]]}

  :cljsbuild {:test-commands {"cljs-test" ["phantomjs" "test/phantom/test.js" "test/phantom/test.html"]}
              :builds        [{:id           "main"
                               :source-paths ["src/cljc" "src/cljs"]
                               :compiler     {:main           iww.electron.main.core
                                              :target         :nodejs
                                              :output-to      "prod/main/main.js"
                                              :output-dir     "out/main"
                                              :externs        ["externs/externs.js"
                                                               "externs/misc.js"]
                                              :npm-deps       {:electron-log     "2.2.7"
                                                               :electron-updater "2.8.7"
                                                               :moment           "2.18.1"
                                                               :electron         "1.7.8"}
                                              ;:install-deps   true
                                              :optimizations  :simple
                                              :parallel-build true}}
                              {:id           "geocoder"
                               :source-paths ["src/cljc" "src/cljs"]
                               :compiler     {:main           iww.electron.geocoder.core
                                              :target         :nodejs
                                              :output-to      "prod/geocoder/geocoder.js"
                                              :output-dir     "out/geocoder"
                                              ;:source-map     "prod/geonames/geonames.js.map"
                                              :externs        ["externs/externs.js"
                                                               "externs/misc.js"]
                                              :npm-deps       {:electron-log           "2.2.7"
                                                               :electron-updater       "2.8.7"
                                                               :local-reverse-geocoder "0.3.2"
                                                               :electron               "1.7.8"}
                                              ;:install-deps   true
                                              :optimizations  :simple
                                              :parallel-build true}}
                              {:id           "renderer"
                               :source-paths ["src/cljc" "src/cljs"]
                               :compiler     {:main           iww.electron.renderer.core
                                              :output-to      "prod/renderer/renderer.js"
                                              ;:source-map     "prod/renderer/renderer.js.map"
                                              :target         :nodejs
                                              :output-dir     "out/renderer"
                                              :externs        ["externs/externs.js"
                                                               "externs/misc.js"
                                                               "externs/leaflet.ext.js"]
                                              :npm-deps       {:electron-log          "2.2.7"
                                                               :react                 "15.6.1"
                                                               :react-dom             "15.6.1"
                                                               :draft-js              "0.10.3"
                                                               :moment                "2.18.1"
                                                               :electron-spellchecker "1.1.2"
                                                               :electron              "1.7.8"}
                                              ;:install-deps   true
                                              :optimizations  :simple
                                              :parallel-build true}}
                              {:id           "renderer-dev"
                               :source-paths ["src/cljc" "src/cljs"]
                               :compiler     {:main           iww.electron.renderer.core
                                              :output-to      "dev/renderer/renderer.js"
                                              ;:source-map     "prod/renderer/renderer.js.map"
                                              :source-map     true
                                              :target         :nodejs
                                              :output-dir     "dev/renderer"
                                              :externs        ["externs/externs.js"
                                                               "externs/misc.js"
                                                               "externs/leaflet.ext.js"]
                                              :npm-deps       {:electron-log          "2.2.7"
                                                               :react                 "15.6.1"
                                                               :react-dom             "15.6.1"
                                                               :draft-js              "0.10.3"
                                                               :moment                "2.18.1"
                                                               :electron-spellchecker "1.1.2"
                                                               :electron              "1.7.8"}
                                              ;:install-deps   true
                                              :optimizations  :none
                                              :parallel-build true}}
                              {:id           "updater"
                               :source-paths ["src/cljs"]
                               :compiler     {:main           iww.electron.update.core
                                              :output-to      "prod/updater/update.js"
                                              :target         :nodejs
                                              :output-dir     "out/updater"
                                              :externs        ["externs/externs.js"]
                                              :npm-deps       {:electron-log "2.2.7"
                                                               :electron     "1.7.8"}
                                              ;:install-deps   true
                                              :optimizations  :simple
                                              :parallel-build true}}

                              {:id           "cljs-test"
                               :source-paths ["src/cljs" "src/cljc" "test"]
                               :compiler     {:output-to     "out/testable.js"
                                              :output-dir    "out/"
                                              :main          iwaswhere-web.runner
                                              :process-shim  false
                                              :optimizations :whitespace}}]})
