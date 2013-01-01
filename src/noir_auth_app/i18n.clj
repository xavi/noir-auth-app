(ns noir-auth-app.i18n
  (use hiccup.page-helpers)
  (use noir-auth-app.utilities)

  (:require [noir-auth-app.config :as config]))


; Because only one language was needed, this actually doesn't handle
; internationalization now, but it's a step in that direction.
;
; For the moment, it allows the models to communicate validation errors
; through keywords, thus saving them from having to build error message
; strings that would require them to know view-specific information like
; page URLs. So, it allows the separation of concerns between the models
; and the views.
;
; Besides the validation error messages, other message strings have already
; been moved here too in preparation for the internationalization.

(def contact-link (str "<a href=\"mailto:" config/contact-email "\">"
                       config/contact-email "</a>"))

(def translations
  {:account-activation-failed-page-title
      (renderer-fn
          "Account activation failed — " config/app-name)
   :activation-code-not-found
      (renderer-fn
          "Activation code not found. Please make sure that the link that "
          "you opened in your browser is the same as the one you received "
          "by email. If problems continue, please contact us at "
          (:link-start-tag %) config/contact-email (:link-end-tag %) " .")
   :activation-code-sent
      (renderer-fn
          "Email sent with your activation code. Thanks for signing up!")
   :activation-code-taken
      (renderer-fn
          "Generated activation code is already taken. Please try it again.")
   :admin-page-title
      (renderer-fn
          "Admin — " config/app-name)
   :bad_login
      (renderer-fn
          "Wrong username/email or password")
   :cancel-change
      (renderer-fn
          "cancel change")
   :change-email
      (renderer-fn
          "change email")
   :change-password
      (renderer-fn
          "change password")
   :change-password-page-title
      (renderer-fn
          "Change password — " config/app-name)
   :change-username
      (renderer-fn
          "change username")
   :delete
      (renderer-fn
          "Delete")
   :delete-account
      (renderer-fn
          "delete account")
   :email
      (renderer-fn
          "Email")
   :email-change-code-not-found
      (renderer-fn
          "Email change code not found.")
   :email-change-confirmation-sent
      (renderer-fn
          "Email sent to " (:new_requested_email %)
          " with a link to confirm the address change.")
   :email-change-confirmed
      (renderer-fn
          "Email change confirmed.")
   :email-not-found
      (renderer-fn
          "Email not found")
   :email-taken
      (renderer-fn
          "Email already taken.")
   :expired-activation-code
      (renderer-fn
          "Expired activation code. " (:link-start-tag %)
          "Get a new activation email with a new code" (:link-end-tag %) ".")
   :expired-password-reset-code
      (renderer-fn
          "Expired reset code. You can request a new one below.")
   :forgot-password
      (renderer-fn
          "forgot password?")
   :forgot-password-page-title
      (renderer-fn
          "Forgot password — " config/app-name)
   :hello-user
      (renderer-fn
          "Hello " (:username %) "!")
   :home-page-title
      (renderer-fn
          config/app-name)
   :insert-error
      (renderer-fn
          "There was an error, please try again. If problems continue, "
          "contact us at " contact-link " .")
   :invalid-email
      (renderer-fn
          "Email not valid.")
   :invalid-username
      (renderer-fn
          "Username can contain only letters (no accents), numbers, "
          "dots (.), hyphens (-) and underscores (_).")
   :log-in
      (renderer-fn
          "Log in")
   :login-page-title
      (renderer-fn
          "Login — " config/app-name)
   :new-activation-email-sent
      (renderer-fn
          "New activation email sent.")
   :new-requested-email-taken
      (renderer-fn
          "Email already taken.")
   :new-requested-email-taken-by-not-yet-activated-account
      (renderer-fn
          "Email already taken but not confirmed yet. <a href=\""
          ; http://weavejester.github.com/hiccup/hiccup.util.html#var-url
          (url "/resend-activation" {:email (:new_requested_email %)})
          "\" data-method=\"post\">Resend confirmation email</a>.")
   :next
      (renderer-fn
          "next")
   :no-user-with-this-email
      (renderer-fn
          "No user with this email")
   :not-yet-activated
      (renderer-fn
          "Account not yet activated. " (:link-start-tag %)
          "Resend activation email" (:link-end-tag %) ".")
   :password
      (renderer-fn
          "Password")
   :password-change-instructions-sent
      (renderer-fn
          "Email sent with instructions on how to change your password. "
          "Please check your inbox.")
   :password-changed
      (renderer-fn
          "Your password has been changed.")
   :password-reset-code-not-found
      (renderer-fn
          "Reset code not found. You can try asking for a new one below. "
          "If problems continue, please contact us at " (:link-start-tag %)
          contact-link (:link-end-tag %) " .")
   :password-reset-code-taken
      (renderer-fn
          "Generated password reset code is already taken. "
          "Please try it again.")
   :password-too-short
      (renderer-fn
          "Password must be at least 5 characters.")
   :resend-activation-page-title
      (renderer-fn
          config/app-name)
   :resend-confirmation
      (renderer-fn
          "resend confirmation")
   :reset-password
      (renderer-fn
          "Reset password")
   :save
      (renderer-fn
          "Save")
   :settings-page-title
      (renderer-fn
          "Settings — " config/app-name)
   :sign-up
      (renderer-fn
          "Sign up")
   :sign-up-ended-with-ellipsis
      (renderer-fn
          "Sign up...")
   :signup-page-title
      (renderer-fn
          "Signup — " config/app-name)
   :taken-by-not-yet-activated-account
      (renderer-fn
          "Email already taken but not confirmed yet. " (:link-start-tag %)
          "Resend confirmation email" (:link-end-tag %) ".")
   :update-error
      (renderer-fn
          "There was an error, please try again. If problems continue, "
          "contact us at " contact-link " .")
   :user-already-active
      (renderer-fn
          "User already active, please "
          (:link-start-tag %) "log in" (:link-end-tag %) ".")
   :username
      (renderer-fn
          "Username")
   :username-or-email
      (renderer-fn
          "Username or email")
   :username-taken
      (renderer-fn
          "That username is already taken.")
   :username-too-long
      (renderer-fn
          "Username must be less than 30 characters.")
   :username-too-short
      (renderer-fn
          "Username must be at least 2 characters.")
   :verification-email
      (renderer-fn
          "Verification email")
   :welcome
      (renderer-fn
          "Welcome!")})


; http://www.ibm.com/developerworks/java/library/j-clojure-protocols/
(defprotocol Translatable
  ; overloading on arity in a protocol
  ; http://stackoverflow.com/questions/4892713/whats-wrong-with-the-following-clojure-protocol
  (translate [this] [this options]))

; http://stackoverflow.com/questions/10613128/how-to-use-optional-arguments-in-defprotocol
(extend-protocol Translatable
  ; translate is sometimes called with the result of calling Noir's
  ; get-errors function. Even though the docstring for this Noir function
  ; says that it returns a vector, it actually returns a LazySeq when called
  ; without arguments.
  ; https://github.com/ibdknox/noir/blob/master/src/noir/validation.clj
  ; That's why IPersistentCollection is used here. (Previously
  ; IPersistentVector was used, but then it didn't work for LazySeq.)
  ; All of Clojure's persistent data structures implement interfaces which
  ; extend clojure.lang.PersistentCollection.
  ; http://stackoverflow.com/a/3388996/974795
  clojure.lang.IPersistentCollection
    (translate
      ([this] (translate this {}))
      ([this options]
        (map #(translate % options) this)))
  clojure.lang.Keyword
    (translate
      ([this] (translate this {}))
      ([this options]
        ((this translations) options)))
  String
    (translate
      ([this] this)
      ([this options] this)))

