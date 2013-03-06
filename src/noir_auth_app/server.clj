(ns noir-auth-app.server
  (:use [ring.middleware keyword-params
                         nested-params
                         params]
        [ring.middleware.session.memory :only (memory-store)]
        [ring.middleware.file-info :only (wrap-file-info)]
        [ring.middleware.multipart-params :only (wrap-multipart-params)]
        [compojure.core :only (defroutes)]
        [noir.validation :only (wrap-noir-validation)]
        [noir.cookies :only (wrap-noir-cookies)]
        [noir.session :only (mem wrap-noir-session wrap-noir-flash)]
        [noir-auth-app.views.home :only (home-routes)]
        [noir-auth-app.views.users :only (users-routes)]
        [noir-auth-app.views.password-resets :only (password-resets-routes)]
        [noir-auth-app.views.settings :only (settings-routes)]
        [noir-auth-app.views.admin :only (admin-routes)])
  (:require [ring.adapter.jetty :as ring]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [shoreleave.middleware.rpc :as shoreleave]
            [noir.util.middleware :as noir]
            [noir-auth-app.models :as models]))

; Defines a Ring handler function from a sequence of routes.
; http://weavejester.github.com/compojure/compojure.core.html#var-defroutes
; Notice that a Compojure route is also a Ring handler function in itself.
; https://github.com/weavejester/compojure/wiki/Routes-In-Detail
(defroutes app-routes
  home-routes
  users-routes
  password-resets-routes
  settings-routes
  admin-routes
  ; http://weavejester.github.com/compojure/compojure.route.html
  (route/resources "/")
  (route/not-found "Not found"))

; lib-noir's app-handler takes a sequential collection of routes and returns
; a handler wrapped in Noir's base middleware
; http://yogthos.github.com/lib-noir/noir.util.middleware.html
; https://github.com/noir-clojure/lib-noir/blob/master/src/noir/util/middleware.clj
; Used this app-handler function as a baseline for the required middlewares.
; Because of the interdependencies between the required handlers/middlewares
; it was not possible to use that function. Anyway, it's clearer to specify
; these explicitly.
;
; shoreleave/wrap-rpc is a middleware. For more about Ring middlewares see
; https://github.com/ring-clojure/ring/wiki/Concepts
; wrap-keyword-params, wrap-nested-params and wrap-params are required by
; shoreleave-remote-ring
; https://github.com/shoreleave/shoreleave-remote-ring
;
; The order is important! (ex. in order for the app-routes handler to respond
; to a Ring request it may need to use sessions, and these will only be
; available if wrap-noir-session middleware, which is part of Noir's base
; middleware, has already been executed for that request)
(def application (-> app-routes
                     shoreleave/wrap-rpc
                     ; api handler includes wrap-keyword-params,
                     ; wrap-nested-params, and wrap-params
                     ; (these 3 middlewares are requred by
                     ; shoreleave-remote-ring)
                     ; https://github.com/weavejester/compojure/blob/master/src/compojure/handler.clj
                     ; https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/keyword_params.clj
                     ; https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/nested_params.clj
                     ; https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/params.clj
                     handler/api
                     wrap-file-info
                     wrap-multipart-params
                     noir/wrap-request-map
                     wrap-noir-validation
                     wrap-noir-cookies
                     wrap-noir-flash
                     (wrap-noir-session {:store (memory-store mem)})))

(defn start [port]
  (ring/run-jetty application {:port port :join? false}))

; https://pinboard.in/u:xavi/t:clojure/t:deployment
(defn -main []
  (models/maybe-init)
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (start port)))
