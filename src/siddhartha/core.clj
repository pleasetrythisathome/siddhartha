(ns siddhartha.core
  (:require [clojure.core.async :as async]
            [clojure.set :as set]))

(defn throw-err [e]
  (when (instance? Throwable e) (throw e))
  e)

(defn <? [ch]
  (throw-err (async/<! ch)))

(defn <?? [ch]
  (throw-err (async/<!! ch)))
