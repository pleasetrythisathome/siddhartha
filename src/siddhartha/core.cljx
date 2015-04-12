(ns siddhartha.core
  (:require #+clj  [clojure.core.async :as async :refer [go go-loop]]
            #+cljs [cljs.core.async :as async]
            [clojure.set :as set])
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [siddhartha.core :refer [<?]]))

#+cljs
(enable-console-print!)

(defn throw-err
  [e]
  (when (instance? #+clj Throwable #+cljs js/Error e)
    #+cljs
    (.log js/console e)
    (throw e))
  e)

(defn take-catch [c callback]
  (async/take! c #(-> % throw-err callback)))

#+clj
(defmacro <?
  "cljs go blocking take with error handling"
  [ch]
  `(siddhartha.core.throw-err (cljs.core.async/<! ~ch)))

#+clj
(defn <??
  "blocking take with error handling"
  [ch]
  `(siddhartha.core.throw-err (async/<!! ~ch)))

(defprotocol AsyncNode
  (send-chan [_])
  (receive-chan [_])
  (sent-events [_])
  (received-events [_]))

(defn matching-arities? [source-fn arities]
  (let [arglists (-> source-fn
                     meta
                     :arglists
                     #+cljs second)]
    (assert (seq arglists) (str "no arglists exist for var: " source-fn))
    (every? (fn [parameters]
              (some (fn [args]
                      (let [[args parameters] (map (partial remove (partial = '_))
                                                   [args parameters])]
                        (and parameters
                             (if (= '& (last (butlast parameters)))
                               (>= (count args) (- (count parameters) 2))
                               (= (count parameters) (count args))))))
                    arities))
            arglists)))

(defn async-handshake!
  ([component] (async-handshake! component 1000))
  ([component timeout]
   (assert (satisfies? AsyncNode component) "component must satisfy AsyncNode")
   (let [send-c (send-chan component)
         receive-c (receive-chan component)]

     (when-let [events (sent-events component)]
       (assert (and (seq events) send-c) "components with sent-events must provide a channel from send-chan")
       (async/put! send-c events (sent-events component)))

     (assert (and (seq (received-events component)) receive-c) "components with received-events must provide a channel from receive-chan")
     (go
       (try (loop [matched #{}
                   visited #{}]
              (let [[v c] (async/alts! [receive-c
                                        (async/timeout timeout)])
                    unmatched (fn [matched]
                                (-> (set (keys (received-events component)))
                                    (set/difference matched)))]
                (cond
                  (= (first v) ::satisfied)
                  (if (= (second v) component)
                    true
                    (do (async/put! send-c v)
                        (recur matched visited)))
                  (not v)
                  (throw (let [unmatched (unmatched matched)]
                           (ex-info (str "timeout matching events: " unmatched)
                                    {:reason ::timeout
                                     :matched matched
                                     :unmatched unmatched
                                     :component component})))
                  :else
                  (let [events v
                        [matched visited]
                        (reduce-kv (fn [[matched visited] key sent-arities]
                                     (let [received-event-handler (get (received-events component) key)]
                                       (if (and received-event-handler
                                                (matching-arities? received-event-handler sent-arities))
                                         [(conj matched key) visited]
                                         (cond
                                           (not send-c)
                                           (throw (ex-info (str "reached signal graph edge without matching event " key)
                                                           {:reason ::graph-edge
                                                            :event-key key
                                                            :sent-arities sent-arities
                                                            :component component}))
                                           (get visited [key sent-arities])
                                           (throw (ex-info (str "revisited node signal graph without matching event " key)
                                                           {:reason ::revisited
                                                            :event-key key
                                                            :sent-arities sent-arities
                                                            :component component}))
                                           :else
                                           (do
                                             (async/put! send-c {key sent-arities})
                                             [matched (conj visited [key sent-arities])])))))
                                   [matched visited] events)]
                    (when-not (seq (unmatched matched))
                      (async/put! send-c [::satisfied component]))
                    (recur matched visited)))))
            (catch #+clj Exception #+cljs js/Error e
              e))))))

(defn start-receive-loop! [component]
  (assert (satisfies? AsyncNode component) "component must satisfy IAsyncProtocol")
  (let [receive-c (receive-chan component)
        send-c (send-chan component)
        events (received-events component)]
    (assert (and (seq events) receive-c) "components with send events must provide a channel from receive-chan")
    (when (seq events)
      (-> (loop []
            (when-let [event (async/<! receive-c)]
              (let [[key & args] event
                    args (or args [])]
                (if-let [handler-var (get events key)]
                  (cond
                    (not (matching-arities? handler-var [args]))
                    (throw (ex-info (str "malformed args, event:" key)
                                    {:reason ::malformed-args
                                     :event-key key
                                     :args args
                                     :expected (:arglists (meta handler-var))
                                     :handler (meta handler-var)}))
                    :else
                    (apply @handler-var component args))
                  (when send-c
                    (async/put! send-c event))))
              (recur)))
          (try (catch #+clj Exception #+cljs js/Error e
                      e))
          go
          (async/take! throw-err)))))

(defn start-signal-graph! [nodes]
  (let [nodes (filter #(satisfies? AsyncNode %) nodes)]
    #+cljs
    (let [handshakes (doall
                      (for [node nodes]
                        (async-handshake! node)))]
      (take-catch
       (go-loop [handshakes handshakes]
         (let [[v c] (async/alts! (vec handshakes))]
           (throw-err v)
           (when-let [remaining (seq (disj (set handshakes) c))]
             (recur remaining))))
       #(doseq [node nodes]
          (start-receive-loop! node))))
    #+clj
    (when (->> (doall
                (for [node nodes]
                  (async-handshake! node)))
               (mapv <??)
               (reduce =))
      (doseq [node nodes]
        (start-receive-loop! node)))))
