(ns noir-auth-app.views.common
  ; Although Enlive now includes a Hiccup-style helper (since 1.1.0), there's
  ; some Hiccup functionality needed here that is not available in Enlive.
  (:use [noir.request :only [*request*]]
        [hiccup.core]
        [hiccup.page :only [include-js]]
        [hiccup.element :only [javascript-tag]]
        [hiccup.util :only [escape-html url]])

        ; [noir.core :only [defpartial]]
        ; [hiccup.core :only [escape-html]]
        ; [hiccup.page-helpers :only [include-css include-js javascript-tag
        ;                             html5 link-to url]])

  (:require [net.cgrand.enlive-html :as h]
            [noir-auth-app.models.user :as users]
            [noir-auth-app.i18n :as i18n]
            [noir-auth-app.config :as config]
            [clojure.string :as string]
            [noir.request :as req]
            [noir.response :as resp]
            [noir.session :as session]))


; http://items.sjbach.com/567/critiquing-clojure#2
(declare store-location current-user)


(defn include-client-code []
  (html
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

    ; Loads jQuery from Google's CDN ( http://jquery.com/download/ )
    ; This matches the version of jQuery in /externs/jquery.js
    (include-js "//ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js")
    (include-js "/js/cljs.js")))

; The third argument to defsnippet is a selector that indicates the root
; element within the loaded HTML file to which transformations should be
; applied. [:#navs] here ensures that we discard the <html> and <body>
; elements that Enlive adds implicitly when loading snippets and templates.
; p. 551 of "Clojure Programming"
; https://github.com/swannodette/enlive-tutorial/blob/master/src/tutorial/template3.clj
(h/defsnippet not-logged-in-nav "public/navs.html" [:#not-logged-in-nav] [])
(h/defsnippet logged-in-nav "public/navs.html" [:#logged-in-nav] [])

(defn navigation-menu []
  (if (current-user) (logged-in-nav) (not-logged-in-nav)))


;; Layouts

(defn translate-html-content
  "Returns a transformation function to be used with Enlive to translate the
  content of an HTML element as specified in its data-i18n attribute. The
  value of this attribute is used to find the translation."
  ; Reference
  ; http://stackoverflow.com/questions/12586849/append-to-an-attribute-in-enlive
  [interpolation-map]
  (fn [node]
    (let [t (i18n/translate (keyword (get-in node [:attrs :data-i18n]))
                            interpolation-map)]
      ((h/content t) node))))

(defn translate-html-attr
  "Returns a transformation function to be used with Enlive to translate the
  value of the specified HTML attribute.
  The translation must be specified in the HTML markup using another
  attribute with the same name as the one to translate but prefixed with
  data-i18n- . The value of this attribute will be used as the key to find
  the translation. Ex.
    <input name='password' type='password'
           placeholder='Password' data-i18n-placeholder='password'/>
    =>
    <input name='password' type='password'
           placeholder='Contraseña' data-i18n-placeholder='password'/>"
  [attr interpolation-map]
  (fn [node]
    (let [i18n-attr (keyword (str "data-i18n-" (name attr)))
          t (i18n/translate (keyword (get-in node [:attrs i18n-attr]))
                            interpolation-map)]
      ((h/set-attr attr t) node))))

; The content of the flash will be displayed as a notice.
(h/deftemplate layout "public/layout.html"
  ; https://github.com/swannodette/enlive-tutorial/blob/master/src/tutorial/template3.clj
  [{:keys [title nav content interpolation-map]}]

  ; content is a HOF ( http://en.wikipedia.org/wiki/Higher-order_function )
  ; that returns a function that will set the body of matched elements to the
  ; value(s) provided to content.
  ; See p. 547 of "Clojure Programming".
  [:title]
    (h/content title)

  ; layout.html uses relative paths for stylesheets (ex. css/default.css) so
  ; that they work locally. The problem is that the server uses this layout
  ; to serve pages in different URL paths (ex. /login and
  ; /activate/:activation-code), and for the stylesheet links to work for any
  ; page (whatever its URL path is), they should be specified using absolute
  ; paths (ex. /css/default.css). Below, these relative paths are converted
  ; to absolute so that they work for any page, whatever its URL path is.
  ; http://diveintohtml5.info/semantics.html#link
  ; http://diveintohtml5.info/semantics.html#rel-stylesheet
  ; http://cgrand.github.com/enlive/syntax.html
  ; p. 548 of "Clojure Programming"
  [[:link (h/attr= :rel "stylesheet")]]
    (fn [node]
      ((h/set-attr :href (str "/" (get-in node [:attrs :href]))) node))

  ; when there's no nav (navigation) to display, the when form evaluates to
  ; nil, and the nav placeholder is removed.
  ; p. 550 of "Clojure Programming"
  [:.nav]
    (when nav (h/content nav))

  ; flash notice displayed here in the layout like it's conventional in Rails
  ; http://guides.rubyonrails.org/action_controller_overview.html#the-flash
  ; http://webnoir.org/tutorials/sessions
  [:.notice]
    (when-let [notice (session/flash-get :notice)] (h/content notice))

  [:.content]
    (h/content content)

  ; Where content replaces the child content of a selected element, append
  ; appends to it.
  ; See p. 554 of "Clojure Programming"
  ; html-snippet prevents Enlive from escaping the code.
  [:body]
    (h/append (h/html-snippet (include-client-code)))

  [(h/attr? :data-i18n)]
    (translate-html-content interpolation-map)

  [(h/attr? :data-i18n-placeholder)]
    (translate-html-attr :placeholder interpolation-map)

  [(h/attr? :data-i18n-value)]
    (translate-html-attr :value interpolation-map))


;
(h/deftemplate admin-layout "public/admin-layout.html"
  [{:keys [title nav content interpolation-map]}]

  [:title]
    (h/content title)

  [:.nav]
    (when nav (h/content nav))

  [:.content]
    (h/content content)

  ; Where content replaces the child content of a selected element, append
  ; appends to it.
  ; See p. 554 of "Clojure Programming"
  [:body]
    (h/append (h/html-snippet (include-client-code)))

  [(h/attr? :data-i18n)]
    (translate-html-content interpolation-map)

  [(h/attr? :data-i18n-placeholder)]
    (translate-html-attr :placeholder interpolation-map)

  [(h/attr? :data-i18n-value)]
    (translate-html-attr :value interpolation-map))


;
(defn set-field-value-from-model
  "Returns a transformation function to be used with Enlive to set the value
  of an HTML field element to the corresponding value of the specified model."
  [model]
  (fn [node]
    (let [field-name (get-in node [:attrs :name])]
      ((h/set-attr :value (get model (keyword field-name))) node))))


; Returns an HTML string
(defn build-error-message [error-keyword & [interpolation-map]]
  ; References:
  ; http://guides.rubyonrails.org/i18n.html#using-safe-html-translations
  ; Don't Translate Markup
  ; http://developers.facebook.com/docs/internationalization/
  (case error-keyword
    ; activation errors
    :activation-code-not-found
        (i18n/translate
            :activation-code-not-found
            {:link-start-tag
                (str "<a href=\"mailto:" config/contact-email "\">")
             :link-end-tag
                "</a>"})
    :expired-activation-code
        (i18n/translate
            :expired-activation-code
            {:link-start-tag
                (str "<a data-method=\"post\" href=\""
                     (url "/resend-activation"
                          {:email (:email interpolation-map)})
                     "\" data-method=\"post\">")
             :link-end-tag
                "</a>"})
    ; login error
    :not-yet-activated
        (let [{:keys [username-or-email]} interpolation-map
              user (users/find-by-username-or-email username-or-email)
              link-start-tag
                (str "<a data-method=\"post\" href=\""
                     (url "/resend-activation" {:email (:email user)})
                     "\" data-method=\"post\">")
              link-end-tag "</a>"]
          ; Notice that this is not HTML escaped here, otherwise the HTML
          ; code for the links would be escaped too. So, if the i18n message
          ; string contains characters that are not HTML safe, they should be
          ; appropriately escaped in the source.
          (i18n/translate
              :not-yet-activated
              {:link-start-tag link-start-tag :link-end-tag link-end-tag}))
    ; password reset error
    :password-reset-code-not-found
        (i18n/translate
                :password-reset-code-not-found
                {:link-start-tag
                    (str "<a href=\"mailto:" config/contact-email "\">")
                 :link-end-tag
                    "</a>"})
    ; signup error
    :taken-by-not-yet-activated-account
        (let [{:keys [email]} interpolation-map
              link-start-tag
                (str "<a data-method=\"post\" href=\""
                     (url "/resend-activation" {:email email})
                     "\" data-method=\"post\">")
              link-end-tag "</a>"]
          (i18n/translate
              :taken-by-not-yet-activated-account
              {:link-start-tag link-start-tag :link-end-tag link-end-tag}))
    ; resend-activation error
    :user-already-active
        (i18n/translate
            :user-already-active
            {:link-start-tag "<a href=\"/login\">" :link-end-tag "</a>"})
    ; default
    ; http://weavejester.github.com/hiccup/hiccup.util.html#var-escape-html
    (escape-html (i18n/translate error-keyword interpolation-map))))


(defmacro ensure-logged-in [& body]
  `(if (session/get :user-id)
       (do ~@body)
       (do
         (store-location)
         (resp/redirect "/login"))))


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
  ; https://github.com/noir-clojure/lib-noir/blob/master/src/noir/request.clj
  (let [r *request*
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
  (let [r *request*
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

