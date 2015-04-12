(ns dropdown.core
  (:require [cljs.core.async :as async]
            [quile.component :as component]
            [rum]
            [siddhartha.core :as sid :refer (AsyncNode send-chan send!)]
            [dropdown.rum :refer [fc]]))

(enable-console-print!)

(extend-type cljs.core.async.impl.channels.ManyToManyChannel
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    (async/close! this)
    this))

(defprotocol ReactComponent
  (react-cmp [_]))

(defprotocol DropdownActions
  (open [_])
  (close [_]))

(defprotocol DropdownEvents
  (click-toggle [_ toggled?])
  (click-item [_ key]))

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
  DropdownActions
  (open [_]
    (.log js/console "open"))
  (close [_]
    (.log js/console "close"))
  ReactComponent
  (react-cmp [this]
    (fc {:display-name "dropdown"
         :mixins [(rum/local false :open)]
         :state? true
         :render
         (fn [{:keys [open]}]
           [:div
            [:button
             {:on-click #(send! this ::click-toggle (swap! open not))}
             (if @open
               "close"
               "open")]
            (when @open
              [:ul
               (for [n (range 5)]
                 [:button
                  {:on-click #(send! this ::click-item n)
                   :key n}
                  "item " n])])])})))

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
    (.log js/console (str "click-toggle " toggled?)))
  (on-click-item [_ key]
    (.log js/console (str "click-item " key))))

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
(rum/mount ((react-cmp (:dropdown system))) (.-body js/document))

(comment
  (async/put! (:to-dropdown system) [::open])
  (async/put! (:to-dropdown system) [::close])
  (async/put! (:from-dropdown system) [::click-toggle true])
  (async/put! (:from-dropdown system) [::click-item :some-button])
  )
