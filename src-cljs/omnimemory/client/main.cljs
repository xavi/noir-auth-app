(ns noir-auth-app.client.main
	(:require [jayq.core :as jq]
            [shoreleave.remotes.common :as shoreleave-common]
            [shoreleave.remotes.http-rpc :as rpc]))

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
; data-confirm inspired by how Rails 3 handles JavaScript confirmation
; messages
;   http://railscasts.com/episodes/205-unobtrusive-javascript?view=asciicast
;   https://github.com/rails/jquery-ujs/wiki/ajax
; data-action inspired by Chris Granger's Overtone controller. In Rails the
; action would typically be specified in href, but in Rails the action is
; specified with an HTTP method and a URL, while Granger's fetch library
; provides a higher level interface by which the action is simply specified
; with a remote function name, so it doesn't seem appropriate to put a
; function name where a URL is expected (actually, it might be ok if the
; function name were prefixed with something like "cljs:", similar to how
; the "javascript:" pseudo protocol prefix is used to put JavaScript code
; directly into an href, but then parsing that action value would be a little
; more complicated). Another reason to not put the function name in href is
; that if JavaScript is disabled, when clicking on the link the browser would
; try to open that, which would cause an error.
;   http://www.chris-granger.com/2012/02/20/overtone-and-clojurescript/
; The idea is to handle all confirmation messages like this with the same
; ClojureScript code, in the same way that Rails does.
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
        ; To specify a parameter in the HTML code for the remote function use...
        ;   data-params="<param-value>"
        ; To specify the empty string as the parameter...
        ;   data-params=""
        ; To specify no parameters, simply do not write the data-params
        ; attribute.
        (let [$me (jq/$ me)
              ; Previously using 'confirm' as the symbol name, but it
              ; conflicted with JavaScript's confirm() function, resulting
              ; in...
              ;   TypeError: string is not a function
              ; The generated code looked like...
              ;   if(cljs.core.truth_(confirm(confirm))) {
              ; Oddly, there was no conflict when using an older version of
              ; lein-cljsbuild which probably was using an older version of
              ; the ClojureScript compiler
              ; https://github.com/emezeske/lein-cljsbuild#clojurescript-version
              data-confirm (jq/data $me :confirm)
              action (keyword (jq/data $me :action))
              params (jq/data $me :params)
              ; referring to undefined without the js/ prefix was causing
              ; WARNING: Use of undeclared Var noir-auth-app.client.main/undefined
              params (if (= params js/undefined) nil [params])
              callback (jq/data $me :callback)]
          (when (js/confirm data-confirm)
                (rpc/remote-callback action
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

