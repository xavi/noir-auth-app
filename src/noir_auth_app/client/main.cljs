(ns noir-auth-app.client.main
	(:require [jayq.core :as jq]
            [fetch.remotes :as remotes]))


; Handling AJAX callbacks
;
; In Rails there are helpers like link_to that generate HTML specifying how
; UI events like clicks are to be handled (in the case of link_to for example
; this HTML includes the attributes data-confirm, data-method and href, as seen
; in the examples of
; http://api.rubyonrails.org/classes/ActionView/Helpers/UrlHelper.html#method-i-link_to
; ).
; OTOH, the handlers of AJAX requests, which may be started by these UI events,
; cannot be specified through these Rails helpers, but they have to be
; specified separately in the JavaScript code.
; https://github.com/rails/jquery-ujs/wiki/ajax
; https://github.com/rails/jquery-ujs/wiki/External-articles
;
; I think it would be simpler if these callbacks could be specified in the
; HTML, in the same way that actions for UI events can be specified. This way
; all the related event handlers are visible and grouped in the HTML (no need
; to search for any AJAX event handlers in the JavaScript code). Besides,
; there's less code, because there's no need to attach AJAX event handlers to
; corresponding DOM elements (see "Example usage" in
; https://github.com/rails/jquery-ujs/wiki/ajax ).
;
; To implement this in ClojureScript, it's necessary to find a way to call a
; function (the callback) from a string (the name of the callback, as
; specified in the HTML).
; In Clojure this is possible with ns-resolve, but in ClojureScript it doesn't
; work...
;
;(js/alert ((ns-resolve *ns* (symbol "+")) 2 3))
;
; In ClojureScript...
; resolve is not implemented (neither ns-resolve, or the *ns* definition). 
; Therefore, if we want to get a function from a string, we
; have to make the lookup ourselves.
; http://metaphysicaldeveloper.wordpress.com/2011/08/28/clojurescript-vs-coffeescript/
; https://github.com/danielribeiro/ClojureCoffeeScriptGame/blob/master/clojure/game.clj#L179

(defn delete-account-callback [data]
  ; http://stackoverflow.com/questions/503093/how-can-i-make-a-redirect-page-in-jquery-javascript
  (js/window.location.replace "/"))

(defn delete-user-callback [data]
  (case data
        0 (js/alert "The user was NOT deleted")
        1 (js/window.location.reload)
        (js/alert (str "Unknown response: " data))))

(def callbacks {:delete-account-callback delete-account-callback
                :delete-user-callback delete-user-callback})

; Handles links that require user confirmation and trigger calls to remote
; functions on the server (AJAX).
;
; bind
; https://github.com/ibdknox/jayq/blob/master/src/jayq/core.cljs
(jq/bind 
    (jq/$ "a[data-confirm]")
    :click
    (fn [e]
      ; https://github.com/ibdknox/overtoneCljs/blob/master/src/overtoneinterface/client/main.cljs
      ; The following code calls the preventDefault method on the event e, it
      ; follows the syntax
      ;   (.the-method target-object args...)
      ; Notice that e is a jQuery Event object, not the original JavaScript event
      ; (.log js/console e)
      ; http://api.jquery.com/bind/
      ; http://api.jquery.com/category/events/event-object/
      ;
      ; http://stackoverflow.com/questions/1357118/event-preventdefault-vs-return-false
      ;   http://stackoverflow.com/a/5607002
      (.preventDefault e)
      ; To get at the js "this", we'll simply use the macro (this-as some-symbol-meaning-this ... )
      ; http://www.chris-granger.com/2012/02/20/overtone-and-clojurescript/
      (this-as me
        ; notice the use of jQuery's data function to get the HTML5 data-* attribute
        ; http://api.jquery.com/data/
        ;
        ; fetch uses pr-str to serialize things
        ;   https://github.com/ibdknox/fetch/blob/master/src/fetch/remotes.cljs
        ; pr-str serializes JavaScript's undefined as the empty string (try
        ; (pr-str undefined) in http://himera.herokuapp.com , and compare it
        ; with (pr-str "")).
        ; In fetch's server-side, params are deserialized with Clojure's
        ; read-string
        ;   https://github.com/ibdknox/fetch/blob/master/src/noir/fetch/remotes.clj
        ; read-string produces an exception when its argument is "" 
        ; (vs. "\"\"")
        ;   http://clojuredocs.org/clojure_core/clojure.core/read-string
        ; So, fetch doesn't allow to pass undefined to a remote function.
        ; Actually, it makes sense, because undefined doesn't exist in
        ; Clojure. To work around this, undefined params are sent as nil.
        ; OTOH, as Clojure supports arity overloading, how to distinguish
        ; between a call to a remote function with no arguments and a call to
        ; the same function with a nil argument?
        ; Well, in the server side, the params are read with read-string and
        ; then the remote function is called with 'apply'
        ;   https://github.com/ibdknox/fetch/blob/master/src/noir/fetch/remotes.clj
        ; If f is a function with no arguments, then this works...
        ;   (apply f nil)
        ; If f is a function with one argument and it has to be called with a
        ; nil value for that argument then it should be called like this...
        ;   (apply f [nil])
        ; That's the reason of the params conversions below. In summary, to
        ; specify a parameter in the HTML code for the remote function use...
        ;   data-params="<param-value>"
        ; To specify the empty string as the parameter...
        ;   data-params=""
        ; To specify no parameters, simply do not write the data-params
        ; attribute.
        (let [$me (jq/$ me)
              confirm (jq/data $me :confirm)
              action (jq/data $me :action)
              params (jq/data $me :params)
              ; referring to undefined without the js/ prefix was causing
              ; WARNING: Use of undeclared Var noir-auth-app.client.main/undefined
              params (if (= params js/undefined) nil [params])
              callback (jq/data $me :callback)]
          (when (js/confirm confirm)
                ; http://www.chris-granger.com/2012/02/20/overtone-and-clojurescript/
                (remotes/remote-callback action
                                         params
                                         ((keyword callback) callbacks)))))))


; This allows to specify links in HTML that will send POST requests
; (instead of GET). The way to do this is with the data-method attribute.
; Ex:
;   <a href="/logout" data-method="post">logout</a>
; It's modeled after the equivalent Rails functionality
; http://api.rubyonrails.org/classes/ActionView/Helpers/UrlHelper.html#method-i-link_to
; https://github.com/rails/jquery-ujs/blob/master/src/rails.js
(jq/delegate
    (jq/$ js/document)
    "a[data-method=\"post\"]"
    :click
    (fn [e]
      (this-as me
        (let [$link (jq/$ me)
              $form (jq/$ (str "<form method=\"post\" action=\""
                               (.attr $link "href")
                               "\"></form>"))]

          ; form.hide().append(metadata_input).appendTo('body');
          ;(.appendTo (.hide $form) "body")
          (-> (.hide $form) (.appendTo "body"))

          (.submit $form)
          ; returning false cancels the default action and prevents the event
          ; from bubbling up
          ; http://api.jquery.com/delegate/
          false))))

