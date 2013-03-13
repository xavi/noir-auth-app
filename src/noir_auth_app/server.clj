(ns noir-auth-app.server
  (:use [ring.middleware keyword-params
                         nested-params
                         params]
        [ring.middleware.session.memory :only (memory-store)]
        [ring.middleware.file-info :only (wrap-file-info)]
        [ring.middleware.reload :only (wrap-reload)]
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

; http://mmcgrana.github.com/2010/07/develop-deploy-clojure-web-applications.html
(defn wrap-if [handler pred wrapper & args]
  (if pred
      (apply wrapper handler args)
      handler))

(def production?
  (= "production" (get (System/getenv) "APP_ENV")))

(def development?
  (not production?))

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
; When running in the development environment, wrap-reload will check on each
; request if any source files have been modified, and if so, will reload the
; namespaces defined in those files plus any other depending namespaces. Ex.
; if /noir-auth-app/views/home.clj is modified, then on the next request,
; noir-auth-app.views.home and noir-auth-app.server will be reloaded (because
; the latter depends on the former). This can be checked by adding a
;   (println *ns* "reloaded")
; on both files (home.clj and server.clj). Now if home.clj is modified, when
; the next is served both messages will be printed in the log.
; http://ring-clojure.github.com/ring/ring.middleware.reload.html
; https://github.com/ring-clojure/ring/blob/master/ring-devel/src/ring/middleware/reload.clj
;
; A key detail for this auto-reload to have the desired effect is to
; reference app-routes as a var (#'app-routes expands to (var app-routes)
; http://clojure.org/special_forms#var). This way, the handler function
; 'application', which is passed to run-jetty, will look up the value of
; app-routes on each request, and because this namespace will have been
; reloaded by wrap-reload by then (if the file of another namespace on which
; it depends has been modified), app-routes will have been re-evaluated and
; so it will now have the updated code.
;
; If app-routes were referenced as a symbol (i.e. app-routes), then it would
; be evaluated when "application" is defined, and its value passed as-is, as
; part of the "application" value, to the run-jetty call below. So, if later
; this namespace were reloaded by wrap-reload because a file changed, then
; although "application" would be re-evaluated, this wouldn't affect the
; value that run-jetty was passed when it was started. So the old handler
; would still be serving the requests and the change wouldn't have any
; effect.
;
; The order is important! (ex. in order for the app-routes handler to respond
; to a Ring request it may need to use sessions, and these will only be
; available if wrap-noir-session middleware, which is part of Noir's base
; middleware, has already been executed for that request)
(def application (-> #'app-routes
                     shoreleave/wrap-rpc
                     ; api handler includes wrap-keyword-params,
                     ; wrap-nested-params, and wrap-params
                     ; (these 3 middlewares are required by
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
                     (wrap-noir-session {:store (memory-store mem)})
                     (wrap-if development? wrap-reload {:dirs ["src"]})))

(defn start [port]
  (ring/run-jetty application {:port port :join? false}))

; https://pinboard.in/u:xavi/t:clojure/t:deployment
(defn -main []
  (models/maybe-init)
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (start port)))
