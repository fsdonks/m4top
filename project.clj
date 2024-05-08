(def version "4.2.19")
(def capsule-name "m4top")
(def capsule-jar (str  capsule-name "-" version ".jar"))

(defproject m4top "4.2.19-SNAPSHOT"
  :description "Top level artifact for M4 app."
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [marathon  "4.2.16-SNAPSHOT"]
                 ;;external libs
                 [joinr/nightclub "0.0.4-SNAPSHOT"
                  :exclusions [commons-codec commons-io]]
                 [eigenhombre/splasher "0.0.2"] ;;splash screen lib
                 ;;taa
                 [taa "0.0.20-SNAPSHOT"]
                 ]
  :plugins [[reifyhealth/lein-git-down "0.4.1"]]
  :middleware [lein-git-down.plugin/inject-properties]
  :repositories [["public-github" {:url "git://github.com"}]]
  :git-down {marathon {:coordinates fsdonks/m4}
             taa      {:coordinates fsdonks/taa}}
  :profiles {:dev {:source-paths [;;"../spork/src" "../nightclub/src"
                                  ;;"../proc/src"
                                  ;;"../marathon-schemas/src"
                                  ]}
             :uberjar {:aot  [marathon.main]
                       :main  marathon.main
                       :jvm-opts ^:replace ["-Xmx1000m" "-XX:NewSize=200m" "-server"]
                       :plugins [[lein-capsule "0.2.1"]]
                       }
             :uberjar-all {:aot [marathon.main marathon.core]
                           :main  marathon.main
                           :jvm-opts ^:replace ["-Xmx1000m" "-XX:NewSize=200m" "-server"]
                           :plugins [[lein-capsule "0.2.1"]]
                           }}
  :repl-options {:timeout 120000}
  ;;; Capsule plugin configuration section, optional
  :capsule {:application {:name    ~capsule-name
                          :version ~version}
            :types {:fat {:name   ~capsule-jar}}
            :execution {:runtime {:jvm-args ["-Xmx4g"]}
                        :boot    {:main-class  "marathon.main"}}})
