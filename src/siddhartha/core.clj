(ns siddhartha.core
  (:require [clojure.core.async :as async]
            [clojure.set :as set]))

(defn throw-err
  [e]
  (when (instance? Throwable e)
    (throw e))
  e)

(defn <?
  "go blocking take with error handling"
  [ch]
  (throw-err (async/<! ch)))

(defn <??
  "blocking take with error handling"
  [ch]
  (throw-err (async/<!! ch)))

(defprotocol IAsyncProtocol
  (send-chan [_])
  (receive-chan [_])
  (sent-events [_])
  (received-events [_]))

(defn matching-arities? [source-fn arities]
  (every? (fn [parameters]
            (some (fn [args]
                    (let [[args parameters] (map (partial remove (partial = '_))
                                                 [args parameters])]
                      (and parameters
                           (if (= '& (last (butlast parameters)))
                             (>= (count args) (- (count parameters) 2))
                             (= (count parameters) (count args))))))
                  arities))
          (:arglists (meta source-fn))))
