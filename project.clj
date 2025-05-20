(def version "4.2.23")
(def capsule-name "m4top")
(def capsule-jar (str  capsule-name "-" version ".jar"))

(defproject m4top "4.2.23-SNAPSHOT"
  :description "Top level artifact for M4 app."
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [marathon  "4.2.20-SNAPSHOT"]
                 ;;external libs
                 [joinr/nightclub "0.0.4-SNAPSHOT"
                  :exclusions [commons-codec commons-io]]
                 [eigenhombre/splasher "0.0.2"] ;;splash screen lib
                 ;;taa
                 [taa "ffa21e8c1c73d294321e59ecee1fe278c5caa1b5" #_"0.0.23-SNAPSHOT"]
                 [babashka/process "0.5.22"]
                 [taapost "0.1.0-SNAPSHOT"]
                 ]
  :plugins [[reifyhealth/lein-git-down "0.4.1"]]
  :middleware [lein-git-down.plugin/inject-properties]
  :repositories [["public-github" {:url "git://github.com"}]]
  :git-down {marathon {:coordinates fsdonks/m4}
             taa      {:coordinates fsdonks/taa}
             m4peer   {:coordinates fsdonks/m4}
             demand_builder  {:coordinates  fsdonks/demand_builder}
             proc     {:coordinates  fsdonks/proc}
             marathon-schemas {:coordinates fsdonks/marathon-schemas}
             hazeldemo {:coordinates joinr/hazeldemo}
             taapost {:coordinates fsdonks/taapost}}
  :profiles {:dev {:source-paths [;;"../spork/src" "../nightclub/src"
                                  ;;"../proc/src"
                                  ;;"../marathon-schemas/src"
                                  "../taa/src"
                                  "../taa/test/"
                                  ]}
             :uberjar  {:aot [marathon.main marathon.core marathon.analysis.random #_#_chazel.core hazeldemo.utils]
                        :main  marathon.main
                        :jvm-opts ^:replace ["-Xmx1000m" "-XX:NewSize=200m" "-server"]
                        }}
  :repl-options {:timeout 120000})
