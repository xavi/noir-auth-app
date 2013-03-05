(ns noir-auth-app.views.users
  (:use [compojure.core :only (defroutes GET POST)]
        [shoreleave.middleware.rpc :only (defremote)])
  (:require [net.cgrand.enlive-html :as h]
            [noir.session :as session]
            [noir.validation :as vali]
            [noir.response :as resp]
            [noir-auth-app.models.user :as users]
            [noir-auth-app.models.mailer :as mailer]
            [noir-auth-app.views.common :as common]
            [noir-auth-app.i18n :as i18n]
            [noir-auth-app.config :as config]))


(defn- email-activation-code [{:keys [email username activation_code]}]
  (future (mailer/send-email 
              {:from config/emails-from
               :to email
               :subject "Account activation"
               :body (str "Hi " username ",\n\n"
                          "To activate your " config/app-name 
                          " account just follow the link below:\n\n"
                          (str (common/base-url) "/activate/" activation_code)
                          "\n\nCheers!")})))


(h/defsnippet signup-content "public/signup.html" [:.content :> h/any-node]
  [user errors]
  ; https://github.com/noir-clojure/lib-noir/blob/master/src/noir/validation.clj
  ; http://guides.rubyonrails.org/active_record_validations_callbacks.html#customizing-error-messages-css
  [:.error-message] (h/clone-for [e errors]
                        (h/html-content (common/build-error-message e user)))
  [[:input (h/attr= :type "text")]] (common/set-field-value-from-model user))


(defn save-user-info-in-session [{:keys [_id lowercase_username]}]
  (when true ;(= lowercase_username "admin") 
        (session/put! :admin true))
  (session/put! :user-id _id))

; Notice that the rest param (the param after the &) is destructured
; ([interpolation-map] instead of simply interpolation-map). That's to get a
; map instead of a sequence containing a map, as Clojure sets the rest param
; with a sequence containing the "arguments that exceed the positional params"
; http://clojure.org/special_forms#Special Forms--(fn name? [params* ] exprs*)
(defn errors-content
  [errors & [interpolation-map]]
  ; The usual way to implement this would be to use defsnippet to load the
  ; HTML from a file, but the required HTML is so simple that I didn't want
  ; to create a new file just for it (although if more like this are needed
  ; they could be put together in a snippets.html file for example). So,
  ; sniptest is used in order to be able to specify the HTML inline. The
  ; problem is that sniptest returns a string, and when this string is
  ; inserted by deftemplate into the final HTML using Enlive's transformation
  ; function content, it's escaped. That's why the result of sniptest is
  ; post-processed by by html-snippet.
  ; https://github.com/cgrand/enlive/blob/master/src/net/cgrand/enlive_html.clj
  ; The html-snippet function concatenates the passed strings and returns a
  ; sequence of maps representing HTML elements (the same type of data
  ; returned by defsnippet). This will be appropriately handled by deftemplate
  ; (it will not be escaped).
  (h/html-snippet
      (h/sniptest "<p class=\"error-message\">Sample error message.</p>"
        [:.error-message]
            (h/clone-for [e errors]
                  (h/html-content 
                        (common/build-error-message e interpolation-map))))))


; [:.content :> h/any-node]
; all children (including text nodes and comments) of the .content element
; https://github.com/cgrand/enlive
(h/defsnippet login-content "public/login.html" [:.content :> h/any-node]
  [user errors]
  ; https://github.com/noir-clojure/lib-noir/blob/master/src/noir/validation.clj
  ; http://guides.rubyonrails.org/active_record_validations_callbacks.html#customizing-error-messages-css
  [:.error-message] (h/clone-for [e errors]
                        (h/html-content (common/build-error-message e user)))
  ; https://groups.google.com/group/enlive-clj/browse_thread/thread/2725d46c018beb7
  ; Remember that set-attr returns a function (as any other Enlive
  ; transformation function) that expects an element (the matching element).
  ; (see p. 550 of "Clojure Programming")
  ; About the nested vectors, see p. 549 of "Clojure Programming".
  ; See also "Selectors 101" in
  ; https://github.com/cgrand/enlive
  [[:input (h/attr= :type "text")]] (common/set-field-value-from-model user))


;;; Actions
;;;
;;; Note:
;;; The term "action" is used here to refer to a function that returns a
;;; response that is meant to be processed by Compojure to convert it into an
;;; appropriate Ring response.
;;; The use of this term is inspired by Rails
;;; http://guides.rubyonrails.org/action_controller_overview.html#methods-and-actions

; Besides the typical case of signing up when not logged in, it's also
; possible to sign up for an account while logged in with another account.
;
; The destructuring form used here is just like the one you'd use in a (let)
; form, so you can use all of clojure's powerful destructuring concepts.
; http://webnoir.org/tutorials/routes
; Note that
;   (defpage "/signup" [new-user]
; doesn't work, it produces
; java.lang.UnsupportedOperationException: nth not supported on this type: PersistentArrayMap
; Same for
;   (defpage "/signup" [{:as new-user}]
; It makes sense because the square brackets here don't have to confused as
; the parameters of a function, as they are actually the second parameter of
; defpage, which is a destructuring form for the params of the request
;   http://webnoir.org/tutorials/routes
;
(defn new-user-action [user]
  (common/layout {:title (i18n/translate :signup-page-title)
                  :nav (common/navigation-menu)
                  ; (vali/get-errors) returns something like...
                  ; (:password-too-short :invalid-email :invalid-username :username-too-short)
                  :content (signup-content user (vali/get-errors))}))

(defn create-user-action [new-user]
  (if-let [saved-user (users/create! new-user)]
    (do (email-activation-code saved-user)
        (session/flash-put! :notice (i18n/translate :activation-code-sent))
        (resp/redirect "/login"))
    (new-user-action new-user)))

; Should the activation/password resets tokens expire? YES
; http://stackoverflow.com/questions/4515469/why-should-we-make-account-activation-password-reset-links-expire-after-some-tim
; See for example the following case, which may be entirely possible:
; Suppose I request a password reset, I get an email with the token, but I
; never use it. After that, someone gains access to my email account. I realize
; about that, and log in to services where I was registered with that email to
; change it with a new, uncompromised email address. With an expiring password
; reset token, I would be safe now. With a non-expiring password reset token,
; the user that got access to my old email account, it can still use the old
; password reset code to get access to my account in the corresponding
; service and change the password, and the email again, thus stealing my
; account.
; So, activation codes and password reset codes have been made expiring, see
; activate! and change-password-with-reset-code! in models/user.clj
;
; When the activation is successful, the user will be automatically logged in.
; https://github.com/matthooks/authlogic-activation-tutorial
; http://stackoverflow.com/questions/2459285/do-i-need-to-auto-login-after-account-activation
(defn activate-action [activation-code]
  (if-let [user (users/activate! activation-code)]
    (do ; Before logging in the just activated user, clears out the session to
        ; effectively log out any currently logged in user, and remove any
        ; possibly sensitive information that could be stored there and could
        ; pose a security risk (ex. if the previously logged in user were an
        ; admin, the new user session would gain administrative privileges
        ; because it would inherit the :admin value stored in the session for
        ; the previous admin user).
        ; http://webnoir.org/autodoc/1.2.1/noir.session-api.html#noir.session/clear!
        (session/clear!)
        (save-user-info-in-session user)
        (session/flash-put! :notice "Your account has been activated. Welcome!")
        (resp/redirect "/"))
    ; If activation failed because the code has expired
    ; (:expired-activation-code error), the email associated to that code is
    ; needed to build the /resend-activation link that will be provided as
    ; part of the error message displayed to the user. This email is obtained
    ; as part of the user map for the specified activation code.
    (let [user (users/find-by-activation-code activation-code)]
        (common/layout {:title (i18n/translate :account-activation-failed-page-title)
                        :nav (common/navigation-menu)
                        :content (errors-content (vali/get-errors) user)}))))

; See Matt Hooks' Authlogic Activation Tutorial
; https://github.com/matthooks/authlogic-activation-tutorial
; In that tutorial (actually in the StackOverflow answer linked from the text
; "Here's an example of how you might resend the activation e-mail") the URL
; to resend an activation code is
;   /users/resend_activation?login=XXX
; This has been renamed here as...
;   /resend-activation?email=XXX
; Tokens expire. Here the token is re-generated before resending it.
; In Authlogic it's apparently re-generated each time a session is saved
; (through an after_save), I don't fully understand how it works.
; The updated_at column is used to check if the token has expired.
; https://github.com/binarylogic/authlogic/blob/master/lib/authlogic/session/perishable_token.rb
; https://github.com/binarylogic/authlogic/blob/master/lib/authlogic/acts_as_authentic/perishable_token.rb
;
; HTTP POST is used instead of GET for the same reason it's used for /logout
; (see comment for /logout below). See also
; http://news.ycombinator.com/item?id=4439599
;
(defn resend-activation-action [email]
  (if-let [user (users/reset-activation-code! email)]
    (do (email-activation-code user)
        (session/flash-put! :notice (i18n/translate :new-activation-email-sent))
        (resp/redirect "/login"))
    (common/layout {:title (i18n/translate :resend-activation-page-title)
                    :nav (common/navigation-menu)
                    :content (errors-content (vali/get-errors))})))

(defn new-session-action [user]
  (if (session/get :user-id)
      (resp/redirect "/")
      (common/layout {:title (i18n/translate :login-page-title)
                      :nav nil
                      :content (login-content user (vali/get-errors))})))

(defn create-session-action [credentials]
  (if-let [user (users/login! credentials)]
      (do
        (save-user-info-in-session user)
        (common/redirect-back-or-default "/"))
      (new-session-action credentials)))

; The reason why logouts are handled through HTTP POST instead of GET is to
; avoid that someone could log out a user by having him load a page containing
; an image tag like
;   <img src="http://example.com/logout" />
; http://stackoverflow.com/a/3522013/974795
(defn logout-action []
  (session/clear!)
  (resp/redirect "/"))

(defroutes users-routes
  ; https://github.com/weavejester/compojure/wiki/Destructuring-Syntax
  (GET "/signup" {params :params}
    (new-user-action params))
  (POST "/signup" {params :params}
    (create-user-action params))
  (GET "/login" {params :params}
    (new-session-action params))
  (POST "/login" {params :params}
    (create-session-action params))
  (GET "/activate/:activation-code" [activation-code]
    (activate-action activation-code))
  (POST "/resend-activation" [email]
    (resend-activation-action email))
  (POST "/logout" []
    (logout-action)))


; Deletes the current user's account
; https://github.com/shoreleave/shoreleave-remote-ring
(defremote delete-account []
  (when-let [user-id (session/get :user-id)]
        (users/delete! user-id)
        ; session/clear! returns an empty map, see the source (take into
        ; account that reset! returns the new value set 
        ;   http://clojuredocs.org/clojure_core/clojure.core/reset! )
        ; https://github.com/noir-clojure/lib-noir/blob/master/src/noir/session.clj
        (session/clear!)))
