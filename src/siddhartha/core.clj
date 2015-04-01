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
