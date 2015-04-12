(ns dropdown.core
  (:require [cljs.core.async :as async]
            [quile.component :as component]
            [siddhartha.core :as sid :refer (AsyncNode)]))

(enable-console-print!)

(extend-type cljs.core.async.impl.channels.ManyToManyChannel
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    (async/close! this)
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
    {::open (var open)
     ::close (var close)})
  DropdownEvents
  (open [_]
    (print :open))
  (close [_]
    (print :close)))

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
    {::click-toggle (var on-click-toggle)
     ::click-item (var on-click-item)})
  DropdownEventsHandler
  (on-click-toggle [_ toggled?]
    (print :click-toggle toggled?))
  (on-click-item [_ key]
    (print :click-item key)))

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

(sid/start-signal-graph! (vals system))

(comment
  (async/put! (:to-dropdown system) [::open])
  (async/put! (:to-dropdown system) [::close])
  (async/put! (:from-dropdown system) [::click-toggle true])
  (async/put! (:from-dropdown system) [::click-item :some-button])
  )
