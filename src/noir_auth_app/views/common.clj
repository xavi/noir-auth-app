(ns noir-auth-app.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page-helpers :only [include-css include-js javascript-tag
                                    html5 link-to url]])

  (:require [noir-auth-app.models.user :as users]
            [noir-auth-app.i18n :as i18n]
            [clojure.string :as string]
            [noir.request :as req]
            [noir.response :as resp]
            [noir.session :as session]))


; http://items.sjbach.com/567/critiquing-clojure#2
(declare store-location current-user)


;; Helper partials

; http://webnoir.org/autodoc/1.2.1/noir.core-api.html#noir.core/defpartial
(defpartial link-item [{:keys [url cls text]}]
  [:li
    (link-to {:class cls} url text)])

; This expects a vector or sequence of keywords and/or strings (keywords
; identify error messages, strings are the messages themselves), and
; optionally, a map. The map contains values that may have to be interpolated
; into the string.
;
; Notice that the rest param (the param after the &) is destructured
; ([options] instead of simply options). That's to get a map instead of a
; sequence containing a map, as Clojure sets the rest param with a sequence
; containing the "arguments that exceed the positional params"
; http://clojure.org/special_forms#Special Forms--(fn name? [params* ] exprs*)
(defpartial error-text [errors & [options]]
  (when errors
    [:p (string/join "<br/>" (i18n/translate errors options))]))

(defpartial include-client-code []
  ; JavaScript loaded just before the closing BODY tag in order to optimize
  ; page rendering.
  ;   http://www.peachpit.com/articles/article.aspx?p=1431494&seqNum=4
  ; It's also done by Chris Granger in the overtoneCljs example
  ;   https://github.com/ibdknox/overtoneCljs/blob/master/src/overtoneinterface/views/common.clj
  ; and here
  ;   http://djhworld.github.com/2012/02/12/getting-started-with-clojurescript-and-noir.html
  ;
  ; CLOSURE_NO_DEPS is to solve a problem with deps.js, see...
  ; https://groups.google.com/d/topic/clojure/_WkdBGPhI-Q/discussion
  (javascript-tag "var CLOSURE_NO_DEPS = true;")
  ; http://corfield.org/blog/post.cfm/getting-started-with-clojurescript-and-jquery-and-fw-1
  ; TODO: use Google's CDN?
  (include-js "http://code.jquery.com/jquery-1.7.1.min.js")
  (include-js "/js/cljs.js"))

(defpartial navigation-menu []
  [:div.nav 
    [:ul (if (current-user)
             ; see "Expanding seqs"
             ; https://github.com/weavejester/hiccup/wiki/Syntax
             (list (when (session/get :admin) [:li (link-to "/admin" "admin")])
                   [:li (link-to "/settings" "settings")]
                   [:li (link-to {:data-method "post"} "/logout" "log out")])
             [:li (link-to "/login" "log in")])]])


;; Layouts

(defpartial layout [title & content]
  (html5
    [:head
      [:title title]
      ; see the section "A room with a viewport" in Ethan Marcotte's
      ; "Responsive Web Design"
      [:meta {:name "viewport" :content "initial-scale=1.0, width=device-width"}]
      (include-css "/css/reset.css")
      (include-css "/css/default.css")
      [:link {:rel "icon" :type "image/png" :href "http://127.0.0.1:5000/favicon.ico?v=2"}]
      ]
    [:body
      [:div#page
        ; https://github.com/ring-clojure/ring/blob/master/SPEC
        (when-not (= (:uri (req/ring-request)) "/login") (navigation-menu))
        ; maybe ids make more sense here (div#nav, div#content)
        [:div.content content]]
      (include-client-code)]))

(defpartial admin-layout [& content]
  (html5
    [:head
      [:title (i18n/translate :admin-page-title)]
      ; see the section "A room with a viewport" in Ethan Marcotte's
      ; "Responsive Web Design"
      [:meta {:name "viewport" :content "initial-scale=1.0, width=device-width"}]
      (include-css "/css/reset.css" "/css/default.css" "/css/admin.css")]
    [:body
      [:div#page
        (navigation-menu)
        [:div.content content]]
      (include-client-code)]))


; (defn admin? []
;   (session/get :admin))

; (defn logged-in? []
;   (session/get :user-id))

(defn ensure-logged-in []
  (when-not (session/get :user-id)
      (store-location)
      (resp/redirect "/login")))

; Similar to Restful Authentication's current_user
; https://github.com/technoweenie/restful-authentication/blob/master/generators/authenticated/templates/authenticated_system.rb
; Also similar to Clearance's current_user, which is defined in
;   https://github.com/thoughtbot/clearance/blob/master/lib/clearance/authentication.rb
; and added to ApplicationController in
;   https://github.com/thoughtbot/clearance/blob/master/lib/generators/clearance/install/install_generator.rb
(defn current-user []
  (users/find-by-id (session/get :user-id)))


; middle truncation (similar to iOS UILineBreakModeMiddleTruncation)
(defn truncate [s length]
  ; Strings are sequences of characters. When you call Clojure sequence
  ; functions on a string, you get a sequence of characters back.
  ; (p. 50 of Halloway's Programming Clojure)
  (if (> (count s) length)
      (let [left-length (quot length 2)
            right-length (if (even? length) (dec left-length) left-length)]
        (apply str
               (concat (take left-length s) "…" (take-last right-length s))))
      s))


(defn base-url []
  ; http://webnoir.org/autodoc/1.2.1/noir.request-api.html
  (let [r (req/ring-request)
        s (name (r :scheme))
        ; http://clojuredocs.org/clojure_core/clojure.core/cond
        ; http://stackoverflow.com/questions/6321865/why-is-else-not-else-in-clojure
        p (cond (and (= s "http") (= (r :server-port) 80)) ""
                (and (= s "https") (= (r :server-port) 443)) ""
                :else (str ":" (r :server-port)))]
    (str (name (r :scheme)) "://" (r :server-name) p)))


(defn store-location
  "Stores the current URL in the session. Typically called just after a user
  tries to access a page that requires authentication without being logged in,
  and before he's redirected to the login page. It allows to redirect back the
  user to the originally requested page once he has logged in.

  If the location URL corresponds to an HTTP POST, redirecting (see
  redirect-back-or-default) may not work."
  ; The code uses the same naming typically used in Rails authentication gems
  ; for this functionality.
  ;   http://railspikes.com/2008/5/1/quick-tip-store_location-with-subdomains
  ;   https://github.com/technoweenie/restful-authentication/blob/master/generators/authenticated/templates/authenticated_system.rb
  ; I do not understand why Padrino stores the referring page instead of the current page
  ;   http://www.padrinorb.com/api/Padrino/Admin/Helpers/AuthenticationHelpers.html
  []
  (let [r (req/ring-request)
        query-string (r :query-string)]
    ; https://github.com/ring-clojure/ring/blob/master/SPEC
    (session/put! :return-to 
                  (str (base-url) (r :uri)
                       (when query-string (str "?" query-string))))))

; See comments on the store-location function defined above
; http://www.padrinorb.com/api/Padrino/Admin/Helpers/AuthenticationHelpers.html#redirect_back_or_default-instance_method
(defn redirect-back-or-default [default]
  (let [return-to (session/get :return-to)]
    (session/remove! :return-to)
    (resp/redirect (or return-to default))))

