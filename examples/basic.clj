(ns examples.basic
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [siddhartha.core :refer :all]
            [taoensso.timbre :as log])
  (:import [clojure.core.async.impl.channels ManyToManyChannel]))

(extend-type ManyToManyChannel
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    (close! this)
    this))

(defprotocol DropdownEvents
  (open [_])
  (close [_]))

(defrecord Dropdown [send-c receive-c]
  AsyncNode
  (send-chan [_]
    send-c)
  (receive-chan [_]
    receive-c)
  (sent-events [_]
    {::click-toggle '[[toggled?]]
     ::click-item '[[key]]})
  (received-events [_]
    {::open #'open
     ::close #'close})
  DropdownEvents
  (open [_]
    (log/info :open))
  (close [_]
    (log/info :close)))

(defprotocol DropdownEventsHandler
  (on-click-toggle [_ toggled?])
  (on-click-item [_ key]))

(defrecord DropdownHandler [send-c receive-c]
  AsyncNode
  (send-chan [_]
    send-c)
  (receive-chan [_]
    receive-c)
  (sent-events [_]
    {::open [[]]
     ::close [[]]})
  (received-events [_]
    {::click-toggle #'on-click-toggle
     ::click-item #'on-click-item})
  DropdownEventsHandler
  (on-click-toggle [_ toggled?]
    (log/info :click-toggle toggled?))
  (on-click-item [_ key]
    (log/info :click-item key)))

(def system
  (component/start
   (component/system-map
    :to-dropdown (async/chan)
    :from-dropdown (async/chan)
    :dropdown (-> (map->Dropdown {})
                  (component/using {:send-c :from-dropdown
                                    :receive-c :to-dropdown}))
    :dropdown-handler (-> (map->DropdownHandler {})
                          (component/using {:send-c :to-dropdown
                                            :receive-c :from-dropdown})))))

(start-signal-graph! (vals system))

(comment
  (async/put! (:to-dropdown system) [::open])
  (async/put! (:to-dropdown system) [::close])
  (async/put! (:from-dropdown system) [::click-toggle true])
  (async/put! (:from-dropdown system) [::click-item :some-button])
  )
