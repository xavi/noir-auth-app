(ns noir-auth-app.views.users
  (use noir.core)
  (use noir.fetch.remotes)
  (use hiccup.form-helpers)
  (use hiccup.page-helpers)
  
  (:require [noir.session :as session]
            [noir.validation :as vali]
            [noir.response :as resp]
            [noir-auth-app.models.user :as users]
            [noir-auth-app.models.mailer :as mailer]
            [noir-auth-app.views.common :as common]
            [noir-auth-app.i18n :as i18n]
            [noir-auth-app.config :as config]))


;; Partials
 
(defpartial user-fields [{:keys [username email] :as usr}]
            ; If the given field (ex. :username) has an error, on-error will
            ; execute the specified function, which will be passed the errors
            ; vector for that field (although this is not explicitly stated
            ; in the docs, 
            ;   http://webnoir.org/autodoc/1.2.1/noir.validation-api.html#noir.validation/on-error
            ; ), and return its value.

            ; :base is not an actual field, just a field name used by
            ; convention (borrowed from Rails) in this application to
            ; associate it the errors that are not specific of any attribute.
            (vali/on-error :base #(common/error-text % {:email email}))
            (vali/on-error :username common/error-text)

            ; :email errors may be keywords (instead of strings) that have to
            ; be translated into strings. Using keywords allows to decouple
            ; the model (which sets the errors) from this view, and it's a
            ; first step towards internationalization.
            ; See also noir-auth-app.models.user/valid?, where errors are set.
            ;(translate-any-errors :activation_code)
            (vali/on-error :email #(common/error-text % {:email email}))
            (vali/on-error :activation_code
                           #(common/error-text % {:email email}))
            (vali/on-error :password common/error-text)

            [:p (text-field {:placeholder "Username"} :username username)]
            [:p (text-field {:placeholder "Email"} :email email)]
            [:p (password-field {:placeholder "Password"} :password)])


(defpartial login-fields [{:keys [username-or-email]}]
            (vali/on-error :password common/error-text)
            ; Instead of the 'when' below, this would have been shorter...
            ;   (vali/on-error :activation_code common/error-text)
            ; but then the model (ns noir-auth-app.models.user) would have to be
            ; concerned with the URL to resend the activation code which is
            ; something that only the view layer (this noir-auth-app.views.users)
            ; should be concerned about.
            ; That's why I think this 'when' is simpler, less entangled.
            (when (vali/errors? :activation_code)
                  (common/error-text 
                      (i18n/translate (vali/get-errors :activation_code)
                                      (users/find-by-username-or-email
                                                        username-or-email))))
            ; In Twitter and Duolingo it's possible to log in with
            ; "Username or email".
            ; By making it possible to log in by username or email, then not
            ; remembering your username should not be a problem.
            ;
            ; I would have liked to autofocus on the first field (using the
            ; autofocus attribute) but unfortunately it doesn't have the
            ; expected behaviour in Firefox, as it hides the placeholder. (In
            ; Chrome, instead, placeholder is hidden on type, not on focus.)
            [:p (text-field {:placeholder "Username or email"} 
                            :username-or-email username-or-email)]
            [:p (password-field {:placeholder "Password"} :password)])


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
(defpage "/signup" {:as user}
  (common/layout (i18n/translate :signup-page-title)
    ; http://weavejester.github.com/hiccup/hiccup.form.html#var-form-to
    (form-to [:post "/signup"]
             (user-fields user)
             (submit-button "Sign up"))))

(defpage [:post "/signup"] {:as new-user}
  (if-let [saved-user (users/create! new-user)]
    (do (email-activation-code saved-user)
        (session/flash-put! (i18n/translate :activation-code-sent))
        (resp/redirect "/login"))
    ; http://webnoir.org/autodoc/1.2.1/noir.core-api.html#noir.core/render
    (render "/signup" new-user)))

(defn save-user-info-in-session [{:keys [_id lowercase_username]}]
  (when (= lowercase_username "admin") (session/put! :admin true))
  (session/put! :user-id _id))

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
(defpage "/activate/:activation-code" {:keys [activation-code]}
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
        (session/flash-put! "Your account has been activated. Welcome!")
        (resp/redirect "/"))
    ; If activation failed because the code has expired
    ; (:expired-activation-code error), the email associated to that code is
    ; needed to build the /resend-activation link that will be provided as
    ; part of the error message displayed to the user. This email is obtained
    ; as part of the user map for the specified activation code.
    (let [user (users/find-by-activation-code activation-code)]
        (common/layout (i18n/translate :account-activation-failed-page-title)
                       (common/error-text 
                            (i18n/translate (vali/get-errors) user))))))

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
(defpage "/resend-activation" {email :email}
  (if-let [user (users/reset-activation-code! email)]
    (do (email-activation-code user)
        (session/flash-put! "New activation email sent.")
        (resp/redirect "/login"))
    (common/layout (i18n/translate :resend-activation-page-title)
        (common/error-text (vali/get-errors)))))


; https://github.com/ibdknox/Noir-blog/blob/master/src/noir_blog/views/admin.clj
(defpage "/login" {:as user}
  (if (session/get :user-id)
      (resp/redirect "/")
      ;
      ; Notice that here 5 parameters are passed to common/layout. Because of
      ; the & introducing the 'content' argument in the common/layout definition,
      ; Clojure will bind 'content' to a _sequence_ of the last four parameters.
      ;
      (common/layout (i18n/translate :login-page-title)
        ; named "notice" like the same type of flash messages in Rails
        ; http://guides.rubyonrails.org/action_controller_overview.html#the-flash
        ; http://webnoir.org/tutorials/sessions
        (when-let [notice (session/flash-get)] [:p.notice notice])
        (form-to [:post "/login"]
                 (login-fields user)
                 [:p (link-to "/password-resets" "forgot password?")]
                 ; Here's a button that's nested in a paragraph, both have
                 ; vertical margins (see CSS) but there's no margin collapsing
                 ; because the button is an inline-block element (see CSS
                 ; display property in Chrome for example) and margin collapsing
                 ; only happens with block elements
                 ; http://pinboard.in/u:xavi/b:dfef44c4248b
                 [:p (submit-button "Log in")])
        [:hr]
        ; Here there is margin collapsing between the paragraph and the
        ; nested button, i.e. the margin for <p> and the margin for <a> do not
        ; add up. Because of this, the desired total margin must be specified
        ; in one of the elements, in this case the button (see CSS).
        [:p (link-to {:class "button signup"} "/signup" "Sign up...")])))


(defpage [:post "/login"] {:as credentials}
  (if-let [user (users/login! credentials)]
      (do
        (save-user-info-in-session user)
        (common/redirect-back-or-default "/"))
      (render "/login" credentials)))

; The reason why logouts are handled through HTTP POST instead of GET is to
; avoid that someone could log out a user by having him load a page containing
; an image tag like
;   <img src="http://example.com/logout" />
; http://stackoverflow.com/a/3522013/974795
(defpage [:post "/logout"] {}
  ; http://webnoir.org/autodoc/1.2.1/noir.session-api.html#noir.session/clear!
  (session/clear!)
  (resp/redirect "/"))



; Deletes the current user's account
; https://github.com/ibdknox/fetch
(defremote delete-account []
  (when-let [user-id (session/get :user-id)]
        (users/delete! user-id)
        ; session/clear! returns an empty map, see the source (take into
        ; account that reset! returns the new value set 
        ;   http://clojuredocs.org/clojure_core/clojure.core/reset! )
        ; http://webnoir.org/autodoc/1.2.1/noir.session-api.html#noir.session/clear!
        (session/clear!)))

