(defproject m4top "4.2.24-SNAPSHOT"
  :description "Top level artifact for M4 app."
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :exclusions [org.clojure/clojurescript
               com.google.javascript/closure-compiler-unshaded]
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [marathon  "4.2.20-SNAPSHOT"
                  :exclusions [com.cnuernber/ham-fisted com.taoensso/nippy org.clojure/data.csv
                               commons-codec
                               org.clojure/core.rrb-vector]]
                 [org.clojure/data.csv "1.1.0"]
                 [org.clojure/core.rrb-vector "0.2.0"]
                 ;;external libs
                 [joinr/nightclub "0.0.5-SNAPSHOT"
                  :exclusions [commons-codec commons-io org.clojure/core.async hiccup
                               #_org.clojure/java.classpath
                               #_org.clojure/data.codec]]
                 [eigenhombre/splasher "0.0.2"] ;;splash screen lib
                 ;;taa
                 [taa "0.0.26-SNAPSHOT"
                  :exclusions [commons-codec]]
                 [com.taoensso/nippy "2.15.3" :exclusions [com.taoensso/encore com.taoensso/truss]]
                 [babashka/process "0.5.22"]
                 [taapost "0.1.4-SNAPSHOT"
                  :exclusions [jfree/jfreechart jfree/jfreechart com.taoensso/nippy
                               commons-codec]]
                 ;;these were tacked on due to taapost, we can look at cleaning this up
                 [commons-codec "1.15"]
                 [com.taoensso/timbre "6.6.1"]
                 [com.taoensso/encore  "3.128.0"]
                 [com.taoensso/truss "1.12.0"]]
  :plugins [[reifyhealth/lein-git-down "0.4.1"]]
  :middleware [lein-git-down.plugin/inject-properties]
  :repositories [["public-github" {:url "git://github.com"}]]
  :git-down {marathon {:coordinates fsdonks/m4}
             taa      {:coordinates fsdonks/taa}
             m4peer   {:coordinates fsdonks/m4peer}
             demand_builder  {:coordinates  fsdonks/demand_builder}
             proc     {:coordinates  fsdonks/proc}
             marathon-schemas {:coordinates fsdonks/marathon-schemas}
             hazeldemo {:coordinates joinr/hazeldemo}
             taapost {:coordinates fsdonks/taapost}
             roz  {:coordinates  joinr/roz}}
  :profiles {:dev {:source-paths [;;"../spork/src" "../nightclub/src"
                                  ;;"../proc/src"
                                  ;;"../marathon-schemas/src"
                                  "../taa/src"
                                  "../taa/test/"
                                  ]
                   :aot [chazel.core hazeldemo.utils]}
             :uberjar  {:aot [marathon.main marathon.core marathon.analysis.random chazel.core hazeldemo.utils
                              tech.v3.dataset]
                        :main  marathon.main
                        :jvm-opts ^:replace ["-Xmx1000m" "-XX:NewSize=200m" "-server"]
                        }}
  :repl-options {:timeout 120000})
