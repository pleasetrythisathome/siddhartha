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

(defn async-handshake! [component]
  (assert (satisfies? IAsyncProtocol component) "component must satisfy IAsyncProtocol")
  (let [send-c (send-chan component)
        receive-c (receive-chan component)]

    (when-let [events (sent-events component)]
      (assert (and (seq events) send-c) "components with sent-events must provide a channel from send-chan")
      (async/put! send-c events (sent-events component)))

    (when-let [must-satisfy (received-events component)]
      (assert (and (seq must-satisfy) receive-c) "components with send events must provide a channel from receive-chan")
      (async/go
        (try (loop [satisfied #{}
                    failed #{}]
               (let [[v c] (async/alts! [receive-c
                                         (async/timeout 1000)])]
                 (cond
                   (= (first v) ::done)
                   (if (= (second v) component)
                     true
                     (do (async/put! send-c v)
                         (recur satisfied failed)))
                   (not v)
                   (throw (ex-info (str "failed matching async-satisifes")
                                   {:reason ::timeout
                                    :component component}))
                   :else
                   (let [[satisfied failed]
                         (reduce-kv (fn [[satisfied failed] k arities]
                                      (let [v (get must-satisfy k)]
                                        (if (and v (matching-arities? v arities))
                                          [(conj satisfied k) failed]
                                          (cond
                                            (get failed [k arities])
                                            (throw (ex-info (str "failed circuit matching async-satisifes " k)
                                                            {:reason ::failed
                                                             :event-key k
                                                             :arities arities
                                                             :component component}))
                                            (not send-c)
                                            (throw (ex-info (str "dead end matching async-satisifes " k)
                                                            {:reason ::dead-end
                                                             :event-key k
                                                             :arities arities
                                                             :component component}))
                                            :else
                                            (do
                                              (async/put! send-c {k arities})
                                              [satisfied (conj failed [k arities])])))))
                                    [satisfied failed] v)]
                     (when-not (seq (set/difference (set (keys must-satisfy)) satisfied))
                       (async/put! send-c [::done component]))
                     (recur satisfied failed)))))
             (catch Exception e
               e))))))
