;;Shim class for running marathon without
;;aot compilation issues.
;;entrypoint for marathon gui.
(ns marathon.main
  (:require [babashka.process :as p]
            [clojure.string :as s])
  (:gen-class :main true))

;;This is the main entry point for marathon.
;;It's a good example of a shim-class, and
;;requires some arcane features to get things
;;working, since we're creating repls on other
;;threads.
(defn entry [& args]
  ;;clojure.set isn't imported by default, causing errors when
  ;;aot-compiling in some places.
  (require 'clojure.set)
  ;;if we don't use this, i.e. establish a root binding
  ;;for the *ns* var, we can't use in-ns later....
  ;;which leads to compile-time and run-time errors..
  (require  'splasher.core)
  (require  'clojure.java.io)
  (if (seq args)
    (case (first args)
      "repl" (clojure.main/repl)
      "peer" (binding [*ns* *ns*]
               (require 'm4peer.core)
               ((resolve 'hazeldemo.core/get-cluster))
               (clojure.main/repl :init (fn [] (in-ns 'm4peer.core))))
      (println [:CLI-ARG (first args)
                :not-recognized :expected :one-of ["repl" "peer"]]))
    (binding [*ns* *ns*]
      ;;rather than :require it in the ns-decl, we load it
      ;;at runtime.
      (future ((resolve 'splasher.core/splash!)
               (clojure.java.io/resource "m4logo.png")
               :duration 20000))
      (require 'marathon.ui)
      (in-ns 'marathon.ui)
      ;;if we don't use resolve, then we get compile-time aot
      ;;dependency on marathon.core.  This allows us to shim the
      ;;class.
      ((resolve 'marathon.ui/hub) :exit? true))))

;;Replacing functionality from capsule.  We now define our own
;;little optional process launcher.
;;So java -jar this.jar should go through this -main,
;;which in turn will launch a separat jvm process with
;;appropriate args, using marathon.main/entry.

;;we assume all args are jvm args to pass through.
;;:marathon/launch
;;default command is
;;java -Xmx4g -jar the-jar.jar entry
;;so -main gets
;;["entry" "java" "-Xmx4g" "-jar" "the-jar.jar"]
;;if args are empty (we 2x clicked), then construct command above.
;;if args were passed in, construct command, but splice in user args
;;so, say I want to launch in peer mode with 2g of memory
;;java -jar this.jar -Xmx2g peer
;;construct this path
;;["entry" "java" "-Xmx2g" "-jar" "this.jar" "peer"]


;;java -jar the-jar --> subp: java -Xmx4g -jar the-jar entry

;;java -jar the-jar -Xmx2g repl
;;java -Xmx2g -jar the-jar entry repl

;;if the first arg is entry, we pass the remaining args
;;to entry.

(def binary #{"-cp" "-jar"})

(defn scrape-jvm-args [xs]
  (loop [args xs
         jvm  []
         user []]
    (if-let [nxt (first args)]
      (cond (binary nxt)
            (let [l nxt
                  r (fnext args)]
              (recur (-> args rest rest)
                     (conj jvm l r)
                     user))
            (= (nth nxt 0) \-)
            (recur (rest args) (conj jvm nxt) user)
            :else
            (recur (rest args) jvm (conj user nxt)))
      [jvm user])))

(defn ensure-cp [jarpath jvm-args]
  (let [xs (set (map s/trim jvm-args))]
    (if-not (or (xs "-jar") (xs "-cp"))
      (conj (vec jvm-args) "-jar" jarpath)
      jvm-args)))

(defn java-cmd [jvm-args jarpath]
  (let [jvm-args        (->> (or (seq jvm-args) ["-Xmx4g" "-jar " jarpath])
                             (ensure-cp jarpath)
                             (s/join " "))]
    (str "java "  jvm-args)))

(def gc-re #"-XX:\+Use.+GC")

(defn gc-specified? [args]
  (->> args (some (fn [arg] (re-find gc-re arg)))))

(defn default-gc [args]
  (if-not (gc-specified? args)
    (conj args "-XX:+UseParallelGC")
    args))

(defn -main [& args]
  (if (= (first args) "entry") ;;launched.
    (apply entry (rest args))
    ;;build subprocess
    (let [jarpath         (System/getProperty "java.class.path")
          [jvm-args user] (scrape-jvm-args args)
          jvm-args        (default-gc jvm-args)
          cmd             (s/join " " [(java-cmd jvm-args jarpath) "entry" (s/join " " user)])]
      (prn {:launching-subprocess cmd
            :jvm-args jvm-args
            :user-args user
            :args args})
      (p/shell cmd))))
