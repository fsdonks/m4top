;;Shim class for running marathon without
;;aot compilation issues.
;;entrypoint for marathon gui.
(ns marathon.main
  (:gen-class :main true))

;;This is the main entry point for marathon.
;;It's a good example of a shim-class, and
;;requires some arcane features to get things
;;working, since we're creating repls on other
;;threads.
(defn -main [& args]
  ;;clojure.set isn't imported by default, causing errors when
  ;;aot-compiling in some places.
  (require 'clojure.set)
  ;;if we don't use this, i.e. establish a root binding
  ;;for the *ns* var, we can't use in-ns later....
  ;;which leads to compile-time and run-time errors..
  (require  'splasher.core)
  (require  'clojure.java.io)
  (if (seq args)
    (clojure.main/repl)
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
