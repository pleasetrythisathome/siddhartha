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
            :clojure       [[org.clojure/clojure "1.7.0-beta1"]]
            :clojurescript [[org.clojure/clojurescript "0.0-3165"]]
            :component
            {:clj          [[com.stuartsierra/component "0.2.3"]]
             :cljs         [[quile/component-cljs "0.2.4"]]}
            :html          [[hiccup "1.0.5"]]
            :logging       [[com.taoensso/timbre "3.4.0"]]
            :repl          [[com.cemerick/piggieback "0.2.0"]
                            [org.clojure/tools.nrepl "0.2.10"]
                            [cider/cider-nrepl "0.9.0-SNAPSHOT"]
                            [weasel "0.7.0-SNAPSHOT"]]
            :schema        [[prismatic/plumbing "0.4.2"]
                            [prismatic/schema "0.4.0"]]
            :react         [[org.omcljs/om "0.8.8"]]})

(def lib-deps [:async :clojure :clojurescript])

(set-env!
 :dependencies (vec
                (concat
                 (apply build-deps deps lib-deps)
                 (mapv #(conj % :scope "test")
                       '[[adzerk/bootlaces "0.1.11"]
                         [adzerk/boot-cljs "0.0-2814-4"]
                         [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT"]
                         [adzerk/boot-reload "0.2.6"]
                         [deraen/boot-cljx "0.2.2"]
                         [jeluard/boot-notify "0.1.2"]
                         [pandeiro/boot-http "0.6.2"]])))
 :source-paths #{"src"}
 :resource-paths #(conj % "resources"))

(require
 '[adzerk.bootlaces        :refer :all]
 '[adzerk.boot-cljs        :refer :all]
 '[adzerk.boot-cljs-repl   :refer :all]
 '[adzerk.boot-reload      :refer :all]
 '[deraen.boot-cljx        :refer :all]
 '[jeluard.boot-notify     :refer :all]
 '[pandeiro.boot-http      :refer [serve]])

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
  "watch and compile cljx, cljs, init cljs-repl and push changes to browser"
  []
  (set-env! :source-paths #(conj % "examples")
            :dependencies #(->> lib-deps
                                (apply disj (set (keys deps)))
                                (apply build-deps deps)
                                (concat %)
                                vec))
  (comp
   (serve :dir "target/")
   (watch)
   (notify)
   (cljx)
   (reload :port 3449)
   (cljs-repl :port 3448)
   (cljs :optimizations :none
         :source-map true
         :pretty-print true)))
