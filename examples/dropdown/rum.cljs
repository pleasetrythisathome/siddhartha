(ns dropdown.rum
  (:require [cljs.core.async :as async :refer [put! chan pub]]
            [clojure.set :as set]
            [goog.dom :as gdom]
            [plumbing.core :refer-macros [defnk fnk <-]]
            [quile.component :as component]
            [sablono.core :as html :refer-macros [html]]
            [schema.core :as s]
            [rum]))

(defnk fc [render {state? false} {mixins []} {display-name ""} {key nil} {ref nil}]
  (let [render-ctor (if state?
                      rum/render-state->mixin
                      rum/render->mixin)
        render-mixin (render-ctor (fn [& args]
                                    (html (apply render args))))
        class        (rum/build-class (cons render-mixin mixins) display-name)
        opts         (->> {"key" key
                           "ref" ref}
                          (filter second)
                          (mapcat identity)
                          (apply js-obj))
        ctor         (fn [& args]
                       (let [props {:rum/key "key"
                                    :rum/ref "ref"}
                             as (take-while #(not (props %)) args)
                             ps (->> (drop-while #(not (props %)) args)
                                     (partition 2)
                                     (mapcat (fn [[k v]] [(props k) v])))
                             state (rum/args->state as)]
                         (rum/element class state (apply js-obj ps))))]
    (with-meta ctor {:rum/class class})))
