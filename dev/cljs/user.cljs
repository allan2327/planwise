(ns cljs.user
  (:require [figwheel.client :as figwheel]
            [devtools.core :as devtools]
            [schema.core :as s]
            [planwise.client.core :as client]))

;; Enable Schema validations client-side
(s/set-fn-validation! true)

(js/console.info "Starting in development mode")

(devtools/install! [:custom-formatters :sanity-hints])

(enable-console-print!)

(figwheel/start {:websocket-url "ws://localhost:3449/figwheel-ws"
                 :on-jsload client/mount-root})
