(ns planwise.system
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [duct.component.endpoint :refer [endpoint-component]]
            [duct.component.handler :refer [handler-component]]
            [duct.component.hikaricp :refer [hikaricp]]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [duct.middleware.route-aliases :refer [wrap-route-aliases]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [compojure.response :as compojure]
            [ring.util.response :as response]

            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.backends.token :refer [jwe-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.core.nonce :as nonce]

            [planwise.component.compound-handler :refer [compound-handler-component]]
            [planwise.component.auth :refer [auth-service]]
            [planwise.component.facilities :refer [facilities-service]]
            [planwise.component.routing :refer [routing-service]]
            [planwise.component.projects :refer [projects-service]]
            [planwise.component.regions :refer [regions-service]]
            [planwise.component.users :refer [users-store]]
            [planwise.endpoint.home :refer [home-endpoint]]
            [planwise.endpoint.auth :refer [auth-endpoint]]
            [planwise.endpoint.facilities :refer [facilities-endpoint]]
            [planwise.endpoint.projects :refer [projects-endpoint]]
            [planwise.endpoint.regions :refer [regions-endpoint]]
            [planwise.endpoint.routing :refer [routing-endpoint]]
            [planwise.endpoint.monitor :refer [monitor-endpoint]]))

(timbre/refer-timbre)

(defn api-unauthorized-handler
  [request metadata]
  (let [authenticated? (authenticated? request)
        error-response {:error "Unauthorized"}
        status (if authenticated? 403 401)]
    (-> (response/response error-response)
        (response/content-type "application/json")
        (response/status status))))

(defn app-unauthorized-handler
  [request metadata]
  (cond
    (authenticated? request)
    (let [error-response (io/resource "planwise/errors/403.html")]
      (-> (compojure/render error-response request)
          (response/content-type "text/html")
          (response/status 403)))
    :else
    (let [current-url (:uri request)]
      (response/redirect (format "/login?next=%s" current-url)))))


(def jwe-options {:alg :a256kw :enc :a128gcm})
(def jwe-secret (nonce/random-bytes 32))

(def base-config
  {:auth {:guisso-url  "https://login.instedd.org/"
          :jwe-secret  jwe-secret
          :jwe-options jwe-options}
   :api {:middleware   [[wrap-authorization :auth-backend]
                        [wrap-authentication :auth-backend]
                        [wrap-json-params]
                        [wrap-json-response]
                        [wrap-defaults :api-defaults]]
         :auth-backend (jwe-backend {:secret jwe-secret
                                     :options jwe-options
                                     :unauthorized-handler api-unauthorized-handler})
         :api-defaults (meta-merge api-defaults {})}
   :app {:middleware   [[wrap-not-found :not-found]
                        [wrap-webjars]
                        [wrap-authorization :auth-backend]
                        [wrap-authentication :auth-backend]
                        [wrap-defaults :app-defaults]]
         :not-found    (io/resource "planwise/errors/404.html")
         :auth-backend (session-backend {:unauthorized-handler app-unauthorized-handler})
         :app-defaults (meta-merge site-defaults
                                   {:static {:resources "planwise/public"}
                                    :session {:store (cookie-store)
                                              :cookie-attrs {:max-age (* 24 3600)}
                                              :cookie-name "planwise-session"}})}

   :webapp {:middleware [[wrap-route-aliases :aliases]]
            :aliases    {}

            ; Vector order matters, api handler is evaluated first
            :handlers   [:api :app]}})

(defn new-system [config]
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :app                 (handler-component (:app config))
         :api                 (handler-component (:api config))
         :webapp              (compound-handler-component (:webapp config))
         :http                (jetty-server (:http config))
         :db                  (hikaricp (:db config))
         :auth                (auth-service (:auth config))
         :facilities          (facilities-service)
         :projects            (projects-service)
         :regions             (regions-service)
         :routing             (routing-service)
         :users-store         (users-store)
         :auth-endpoint       (endpoint-component auth-endpoint)
         :home-endpoint       (endpoint-component home-endpoint)
         :facilities-endpoint (endpoint-component facilities-endpoint)
         :projects-endpoint   (endpoint-component projects-endpoint)
         :regions-endpoint    (endpoint-component regions-endpoint)
         :routing-endpoint    (endpoint-component routing-endpoint)
         :monitor-endpoint    (endpoint-component monitor-endpoint))
        (component/system-using
         {; Server and handlers
          :http                {:app :webapp}
          :webapp              [:app :api]
          :api                 [:monitor-endpoint
                                :facilities-endpoint
                                :regions-endpoint
                                :projects-endpoint
                                :routing-endpoint]
          :app                 [:home-endpoint
                                :auth-endpoint]

          ; Components
          :facilities          [:db]
          :projects            [:db]
          :regions             [:db]
          :routing             [:db]
          :users-store         [:db]
          :auth                [:users-store]

          ; Endpoints
          :auth-endpoint       [:auth]
          :home-endpoint       [:auth]
          :facilities-endpoint [:facilities]
          :regions-endpoint    [:regions]
          :projects-endpoint   [:projects]
          :routing-endpoint    [:routing]}))))
