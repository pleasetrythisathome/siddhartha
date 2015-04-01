(defn make-korks [korks]
  (cond-> korks
    (keyword? korks) vector))

(defn flatten-vals
  "takes a hashmap and recursively returns a flattened list of all the values"
  [coll]
  (if ((every-pred coll? sequential?) coll)
    coll
    (mapcat flatten-vals (vals coll))))

(defn build-deps [deps & korks]
  (->> korks
       (mapv (comp (partial get-in deps) make-korks))
       (mapcat flatten-vals)
       (into [])))

(def deps '{:async         [[org.clojure/core.async "0.1.346.0-17112a-alpha"]]
            :clojure       [[org.clojure/clojure "1.7.0-alpha5"]]
            :clojurescript [[org.clojure/clojurescript "0.0-3126"]]
            :component
            {:clj          [[com.stuartsierra/component "0.2.3"]]
             :cljs         [[quile/component-cljs "0.2.4"]]}
            :html          [[hiccup "1.0.5"]]
            :logging       [[com.taoensso/timbre "3.3.1"]]
            :repl          [[com.cemerick/piggieback "0.1.5"]
                            [weasel "0.6.0-SNAPSHOT"]]
            :schema        [[prismatic/plumbing "0.4.1"]
                            [prismatic/schema "0.4.0"]]
            :rum           [[rum "0.2.6"]]})

(set-env!
 :dependencies (vec
                (concat
                 (apply build-deps deps (keys deps))
                 (mapv #(conj % :scope "test")
                       '[[adzerk/bootlaces "0.1.11"]
                         [adzerk/boot-cljs "0.0-2814-3"]
                         [adzerk/boot-cljs-repl "0.1.9"]
                         [adzerk/boot-reload "0.2.4"]
                         [deraen/boot-cljx "0.2.2"]
                         [jeluard/boot-notify "0.1.2"]])))
 :source-paths #{"src"}
 :resource-paths #(conj % "resources"))

(require
 '[adzerk.bootlaces        :refer :all]
 '[adzerk.boot-cljs        :refer :all]
 '[adzerk.boot-cljs-repl   :refer :all]
 '[adzerk.boot-reload      :refer :all]
 '[deraen.boot-cljx        :refer :all]
 '[jeluard.boot-notify     :refer :all])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 pom {:project 'pleasetrythisathome/siddhartha
      :version +version+
      :description "Siddhartha"
      :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}
      :url "https://github.com/pleasetrythisathome/siddhartha"
      :scm {:url "https://github.com/pleasetrythisathome/siddhartha"}})

(deftask dev
  "watch and compile cljx, css, cljs, init cljs-repl and push changes to browser"
  []
  (comp
   (watch)
   (notify)
   (cljx)
   (reload :port 3449)
   (cljs-repl :port 3448)
   (cljs :optimizations :none
         :source-map true
         :pretty-print true)))
