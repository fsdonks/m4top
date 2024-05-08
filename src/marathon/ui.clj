(ns marathon.ui
  (:require [marathon.core :as core]
            [marathon [run :as run]]
            [marathon.sampledata [branches :as branches]]
            [spork.cljgui.components [swing :as gui]]
            [spork         [mvc :as mvc]]
            [spork.data    [orderedmap :as ordered]]
            [spork.events  [observe :as obs]
             [native :as swing-events]]
            [spork.util
             [table :as tbl]
             [io :as io]
             [metaprogramming :as meta]]
            [nightclub [core :as night]]
            [nightcode [repl :as repl]]
            [seesaw.core]
            ;;plugins
            [stoke  [core :as stoke]
                    [io :as stokeio]]
            [demand_builder [core :as builder]])
  (:use [marathon.project]
        [clojure.repl])
  (:import [javax.swing JFrame UIManager]))


;;to retain backwards compatibility and minimal excision, we just pull in all
;;the functions from marathon.core, particularly the processing/scripting stuff.
;;This allows legacy clients to continue using them unimpeded, or use this
;;namespace as a catch all. It also allows UI dialogues to trivially move here.
(meta/import-vars
 [marathon.core
  +version+
  noisy
  toggle-noisy
  ;;new global chart options for controlling visualization
  ;;and rendering of multiple runs.  simplifies configuration
  ;;and scripting.  Default mirrors legacy behavior,
  ;;visuals are displayed, but no pptx is emitted.
  *chart-ops*
  spit-tables
  stoch-demand
  capacity-analysis
  high-water-batch-from
  high-water
  default-interests
  default-post-processed-run
  post-processed-run
  post-processed-run-with-samples
  requirements-analysis])


;;retained gui utils..
(def  path-history   (atom [(System/getProperty "user.dir")]))
(defn add-path! [p]  (swap! path-history conj p))
(defn active-path [] (last @path-history))

(defn select-file []
  (let [p (gui/select-file (active-path))]
    (do (add-path! p)
         p)))

(defn select-folder []
  (let [p (gui/select-folder (active-path))]
    (do (add-path! p)
        p)))

(defn notify [msg]
  (fn [] (gui/alert msg)))

(defn not-implemented
  ([msg] (fn [] (gui/alert (str msg " Not Implemented"))))
  ([] (not-implemented "")))


;This is a hack, need a more elegant solution.
(defn tbl->view [t & {:keys [sorted] :or
                       {sorted true}}]
    (gui/->swing-table (tbl/table-fields t)
                       (tbl/table-rows t)
                       :sorted sorted))

(def project-menu-spec
  (ordered/ordered-hash-map
   "Examine-Project"    "Provides a visual presentation of a project."
  ;  "Save-Project"    "Saves a project into the project path."
  ;  "Save-Project-As" "Saves a currently-loaded project into path."
 ;   "Convert-Project" "Convert a project from one format to another."
 ;   "Derive-Project"  "Allows one to derive multiple projects from the current."
 ;   "Migrate-Project" "Port data from a legacy version of marathon to a new one."
   ; "Audit-Project"   "Audits the current project."
   ; "Audit-Projects"  "Audits multiple projects"
    ))

(def processing-menu-spec
  (ordered/ordered-hash-map
   ;"Clean"              "Cleans a run"
   ;"Deployment-Vectors" "Analyzes deployments"
   ;;"Charts"             "Generate plots."
   "Capacity-Analysis"     "Performs a capacity run (default)"
   "Requirements-Analysis" "Performs a Requirements Run"
   "PostProcessed-Run"     "Capacity Analysis, post-process using Proc, rendering dwell/fill charts"
   "High-Water"             "Computes HighWater trails from DemandTrends."
   "High-Water-Batch-From"  "Computes Multiple HighWater trails from any DemandTrends found from a root folder.."
   "Stochastic-Demand"     "Generate stochastic demand files from a casebook."
   "Compute-Peaks"         "Extract the peak concurrent demands from a folder."
   "Debug-Run"             "Runs the capacity analysis with output directed toward a file"
   "Demand-Builder"        "Open the processing gui for the Demand Builder application."
;;   "Custom"             "Run a custom script on the project"
;;   "Eval"               "Evaluate an expression in the context"
   ))

(def debug-menu-spec
  (ordered/ordered-hash-map
   "Debug-Run"
      "Performs a capacity analysis with copious amounts of debug info."
    "Debug-Run-Heavy"
       "Capacity Analysis With ai-behavior level output."
  ;  "Save-Project"    "Saves a project into the project path."
  ;  "Save-Project-As" "Saves a currently-loaded project into path."
 ;   "Convert-Project" "Convert a project from one format to another."
 ;   "Derive-Project"  "Allows one to derive multiple projects from the current."
 ;   "Migrate-Project" "Port data from a legacy version of marathon to a new one."
   ; "Audit-Project"   "Audits the current project."
   ; "Audit-Projects"  "Audits multiple projects"
    ))

(def scripting-menu-spec
  (ordered/ordered-hash-map
   "Load-Script" "Load a clojure script into the environment."))
(def help-menu-spec
  (ordered/ordered-hash-map
   "Search-For" "Interactive Help"))

(def preferences-menu-spec
  (ordered/ordered-hash-map
   "Update" "Check for updates to Marathon."
   "Eval"   "Evaluate an expression in the context"))

(defn reactive-menu-system
  "Given a map of menu specs, builds a menu-system model with an integrated
   event stream that has a unique event for each menu item selected.  Returns
   an event stream that is a union or merge of all the menu events."
  [specs]
  (let [menus (reduce (fn [acc [name spec]]
                        (assoc acc name
                               (gui/map->reactive-menu name spec))))]
    (mvc/make-modelview nil menus
      {:menu-events (obs/multimerge-obs (vals menus))})))

(defn yes-no-box [msg]
  (gui/swing! (seesaw.core/confirm msg :option-type :yes-no)))

(defmacro with-alert [alert body]
  `(do (~'gui/alert ~alert)
       ~body))

(defmacro request-path [[bind alert] body]
  `(when-let [~bind (with-alert ~alert (~'select-file))]
     ~body))

(defn compute-peaks-dialogue []
  (request-path [the-path "Please select a file co-located in a folder with demand case files."]
    (let [dump-folder  (io/parent-path the-path)
          target       (io/dir-path dump-folder "peaks")
          _ (gui/alert (str "dumping peaks files to " target))]
      (stokeio/compute-peaks dump-folder target))))

;;a quick hack to compute the deciles from a set of peaks, in case we
;;don't need to compute the peaks and the deciles again.  We may
;;separate this from the compute-peaks entirely.
(defn compute-deciles-dialogue []
  (request-path [the-path "Please select a file co-located in a folder with demand peak files."]
    (let [dump-folder (io/parent-path the-path)
          target dump-folder
          _ (gui/alert (str "dumping peaks stats to " target))]
      (stokeio/compute-peak-stats dump-folder target))))

;a quick plugin for stochastic demand generation.
(defn stoch-demand-dialogue []
  (request-path [wbpath "Please select the location of valid case-book."]
     (stoch-demand wbpath)))

(defn clean-demand-dialogue []
  (request-path [wbpath "Please select the location of valid case-files."]
    (let [fl          (clojure.java.io/file wbpath)
          cases       {(str (io/fname fl) \_ "split.txt") (tbl/read-table fl)}
          dump-folder  (io/parent-path wbpath)
          _ (print (str "dumping to " dump-folder))]
      (core/spit-tables cases dump-folder))))

;;legacy auditing of marathon workbooks.....needs verification.
(defn audit-project-dialogue []
  (request-path [wbpath "Please select the location of valid case-files."]
    (let [fl           (clojure.java.io/file wbpath)
          cases       {(str (io/fname fl) \_ "split.txt") (tbl/read-table fl)}
          dump-folder (io/parent-path wbpath)
          _ (print (str "dumping to " dump-folder))]
      (core/spit-tables cases dump-folder))))

(defn capacity-analysis-dialogue []
    (request-path [wbpath "Please select the location of a valid MARATHON project file."]
                  (capacity-analysis wbpath)))

(defn high-water-batch-from-dialogue []
  (request-path [wbpath  "Please select the location of a valid MARATHON DemandTrends file,
                          or a file co-located in a parent folder of one or more DemandTrends files.
                          Child files and directories will be traversed, processing all DemandTrends.txt
                          files discovered."]
                (high-water-batch-from wbpath)))

(defn high-water-dialogue []
    (request-path [wbpath "Please select the location of a valid MARATHON DemandTrends file."]
                  (high-water wbpath)))

(defn interests-dialogue [wbpath]
  (if (yes-no-box "Would you like to select an interests file?")
    (request-path [wbpath "Please select the location of a valid MARATHON interests file."]
                  (clojure.edn/read-string (slurp wbpath)))
    (do (println [:using-default-interests 'marathon.sampledata.branches/branches])
        branches/branches)))

(defn post-processed-run-dialogue []
  (request-path [wbpath "Please select the location of a valid MARATHON project file."]
                (post-processed-run wbpath :path->interests interests-dialogue)))

(defn requirements-analysis-dialogue []
    (request-path [wbpath "Please select the location of a valid MARATHON requirements project file."]
           (requirements-analysis wbpath)))

(defn debug-run-dialogue []
  (request-path [wbpath "Please select the location of a valid MARATHON project file."]
    (let [root         (io/parent-path wbpath)
          dbgtgt       (str root "debug.txt")
          _ (println [:performing-debug-run :to dbgtgt])]
      (with-open [wrtr (clojure.java.io/writer dbgtgt)]
        (binding [*out* wrtr]
          (marathon.ces.core/debugging
           (capacity-analysis wbpath)))))))

(defn debug-run-dialogue! []
  (request-path [wbpath "Please select the location of a valid MARATHON project file."]
     (marathon.ces.core/debugging!
      (capacity-analysis wbpath)
      )))

(defn examine-project-dialogue []
  (request-path [wbpath "Please select the location of a valid MARATHON project file."]
     (run/examine-project wbpath)))

;;todo:
;;map out demand-builder subtasks...
(defn demand-builder-dialogue
  []
  (builder/gui :exit? false))


;;sparkcharts...
;;currently mimicing the api from da.
(comment
(defn emit-spark-charts
  "Load spark charts from a marathon audit trail directory specified by in and
   save all spark charts to the out directory.
   Calling an optional function f on each chart before saving."
  [in out & {:keys [group-fn f phase-lines? bounds]
             :or {group-fn (fn [s] "All")
                  f identity
                  phase-lines? true
                  bounds nil}}]
  (da/dump-spark-charts (io/as-directory outdir)
                        (io/as-directory outdir)))
)

;;commands that have a single meaning; typically dialogue-driven.
(def commands
  '{:stochastic-demand  (stoch-demand-dialogue)
    :compute-peaks      (compute-peaks-dialogue)
    :say-hello          (println "hello!")
    :capacity-analysis  (capacity-analysis-dialogue)
    :requirements-analysis  (requirements-analysis-dialogue)
    :debug-run          (debug-run-dialogue)
    :examine-project    (examine-project-dialogue)
    :search-for         (pprint/pprint
                         (apropos
                          (gui/input-box
                           :prompt "Enter A Topic")))
    :postprocessed-run  (post-processed-run-dialogue)
    :load-script        (load-file
                         (gui/input-box
                          :prompt "Select a Clojure script"))
    :high-water          (high-water-dialogue)
    :high-water-batch-from (high-water-batch-from-dialogue)
    :demand-builder        (demand-builder-dialogue)})

;;commands that have a file-path supplied.
;;TODO: look into shifting from map-dispatch
;;to multimethods.  Think that would be easier
;;to extend.
(defn contextual-command [e]
  (when-let [cmd (:command e)]
    (let [path (:path e)]
      (case cmd
        :stochastic-demand      `(~'stoch-demand ~path)
        :compute-peaks          '(compute-peaks-dialogue)
        :capacity-analysis      `(~'capacity-analysis ~path)
        :requirements-analysis  `(~'requirements-analysis ~path)
        :debug-run              `(~'debug-run ~path)
        :examine-project        `(~'run/examine-project ~path)
        :postprocessed-run      `(~'post-processed-run ~path)
        :load-script            `(~'load-file ~path)
        :high-water             `(~'high-water ~path)
        :high-water-batch-from  `(~'high-water-batch-from ~path)
        `(throw (Exception. (str [:unknown-command! ~e])))))))

;;holy wow this is terrible.  must be a better way...
(defn menu-handler
  "Default menu-handling function.  Handles events coming from the
   swing menu, which are typically just keywords, and dispatches them
   to the appropriate command."
  [e]
  (let [expr (or (get commands e)
                 (contextual-command e))]
    (repl/echo-repl! expr)))

;;provide a popup menu that shows processing options for a selected file.
(defn project-context-menu
  "Defines the popup context menu displayed when right-clicking on files
   in the project tree, to provide quick access to common tasks and avoid
   the file dialogue process..."
  [{:keys [name file] :as nd}]
  (let [{:keys [view control]}
        (gui/map->reactive-menu "Processing"
                                processing-menu-spec :popup? true)]
    (->> control
         :event-stream
         (obs/map-obs (fn [e] {:path (io/fpath file)
                               :command e}))
         (obs/subscribe menu-handler))
    view))

(def pop-fn (atom project-context-menu))

(defn install-popups!
  "Slight hack to install a popup context menu to the project window
  (right-click currently yields a menu identical to processing...)."
  [& {:keys [on-popup] :or {on-popup (fn [s] (@pop-fn s))}}]
  (let [show-popup!
          (fn show-popup! [^java.awt.event.MouseEvent e]
            (let [x (.getX e)
                  y (.getY e)
                  ^javax.swing.JTree t (.getSource e)
                  ^javax.swing.tree.TreePath path (.getPathForLocation t x y)]
              (when path
                (let [;_ (reset! last-path path)
                      tgt (.. path getLastPathComponent getUserObject)]
                   (.show (on-popup tgt) t x y)))))]
    (->> (spork.events.native/mouse-observer
                (nightcode.ui/get-project-tree))
         (:clicked)
         (obs/filter-obs spork.events.native/right-button?)
         (obs/subscribe show-popup!))))

;;TODO: migrate the rest of this to seesaw, get rid of the old redundant
;;crud...
(defn hub
  "Creates the graphical interface for M4, including project browser, file editors,
   a REPL, and a menu-bars for common processing tasks."
  [& {:keys [project exit? repl-options]}]
  (seesaw.core/invoke-now
   (binding [*ns* *ns*]
           (in-ns 'marathon.ui)
           (let [project-menu    (gui/map->reactive-menu "Project-Management"
                                                         project-menu-spec)
                 processing-menu (gui/map->reactive-menu "Processing"
                                                         processing-menu-spec)
                 debug-menu      (gui/map->reactive-menu "Debug"
                                                         debug-menu-spec)
                 scripting-menu  (gui/map->reactive-menu "Scripting"
                                                         scripting-menu-spec)
                 help-menu       (gui/map->reactive-menu "Help"
                                                         help-menu-spec)
                 main-menu       (gui/menu-bar (:view project-menu)
                                               (:view processing-menu)
                                               (:view scripting-menu)
                                               (:view debug-menu)
                                               (:view help-menu))
                 menu-events     (obs/multimerge-obs [(-> project-menu :control :event-stream)
                                                      (-> processing-menu :control :event-stream)
                                                      (-> debug-menu   :control :event-stream)
                                                      (-> help-menu    :control :event-stream)
                                                      (-> scripting-menu :control :event-stream)
                                                      ])
                 _                 (->> menu-events
                                        (obs/subscribe  menu-handler))
                 ]
             (night/attach! :window-args
                            {:title   (str "MARATHON " +version+)
                             :menubar main-menu
                             :on-close (if exit? :exit :dispose)
                             }
                            ;;setup our ns to be marathon.core
                            :init-eval (str "(ns " *ns* ")"))
             (add-watch nightcode.ui/root :popup
                        (fn [_ _ _ _]
                          (seesaw.core/invoke-later (install-popups!))
                          (remove-watch nightcode.ui/root :popup)
                          ))))))

(defn -main [& args]  (hub :exit? true))

(comment ;popup testing stuff
  (defn ^javax.swing.JPopupMenu item->menu
    "Return a popup menu as a function of the input..."
    [{:keys [name file] :as nd}]
    (doto ^javax.swing.JPopupMenu (javax.swing.JPopupMenu.)
      (.add (javax.swing.JMenuItem.  (or
                                      (io/fpath file) 
                                      "nothing")))))

  ;;tree user objects are files
  ;;(def last-path (atom nil))

  )
