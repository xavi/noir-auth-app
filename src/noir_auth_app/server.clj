(ns noir-auth-app.server
  (:require [noir.server :as server]
            [noir-auth-app.models :as models]))

(server/load-views "src/noir_auth_app/views/")

; For time-based caching (Last-Modified, If-Modified-Since) of
; /resources/public/js/cljs.js and other files, 
;   https://devcenter.heroku.com/articles/increasing-application-performance-with-http-cache-headers#conditional_requests
; it seems that Ring's built-in
; (but not installed by default?) wrap-file-info middleware could be added
;   https://github.com/mmcgrana/ring/blob/master/ring-core/src/ring/middleware/file_info.clj
; See also
;   http://webnoir.org/tutorials/middleware
;   https://github.com/ohpauleez/shoreleave
; Actually, looking at the headers from noir-auth-app.herokuapp.com with
; Chrome, the Last-Modified is already included for files in
; /resources/public/ . There's no reference to Ring's wrap-file-info in Noir,
; but there is in Compojure, which Noir uses to serve files from 'public'
;   https://github.com/weavejester/compojure/blob/master/src/compojure/route.clj
; So, mystery solved, that's where the Last-Modified comes from!
; #TODO: My only doubt now is...
; even though the responses have a Last-Modified header, they do not include a
; Cache-Control header, and the Heroku article linked above implies that the
; latter is always required for the browser to "switch on" the caching
; behavior. Actually, in the Last-Modified example, there's also a 
; Cache-Control, so... is this really needed?

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (models/maybe-init)
    (server/start port {:mode mode
                        :ns 'noir-auth-app})))

