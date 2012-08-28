(ns noir-auth-app.i18n
  (use hiccup.page-helpers)

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

; Instead of
;   (def translations {:greeting #(str "hello " (:username %) "!")})
; another option would be to use format
;   (def translations {:greeting #(format "hello %s!" (:username %))})
; but that's much slower, as measured by
;   (time ((:greeting translations) {:username "xavi"}))
; 0.0768 ms vs 0.137 ms (average on 5 runs, excluding the 1st one to
; ignore compile time)
; So, with str this is ~1.78 times faster than with format.
(def translations
  {:account-activation-failed-page-title
      (fn [_]
          (str
            "Account activation failed — " config/app-name))
   :activation-code-not-found
      ; if using the anonymous function literal, and the function
      ; implementation doesn't reference any parameter like in this case,
      ; then the function doesn't expect any parameters, and calling it with
      ; one parameter would result in
      ; Wrong number of args (1) passed to: i18n$fn
      ; http://stackoverflow.com/q/7841643/974795
      ; To work around this, the fn macro is used...
      ;   http://clojuredocs.org/clojure_core/clojure.core/fn
      ; The underscore (_) is a Clojure naming convention for parameters that
      ; are not used
      ; http://java.ociweb.com/mark/clojure/article.html#Syntax
      (fn [_]
          (str 
            "Activation code not found. Please make sure that the link that "
            "you opened in your browser is the same as the one you received "
            "by email. If problems continue, please contact us at "
            contact-link " ."))
   :activation-code-sent
      (fn [_]
            "Email sent with your activation code. Thanks for signing up!")
   :activation-code-taken
      (fn [_] 
          (str
            "Generated activation code is already taken. "
            "Please try it again."))
   :admin-page-title
      (fn [_]
          (str
            "Admin — " config/app-name))
   :change-password-page-title
      (fn [_]
          (str
            "Change password — " config/app-name))
   :email-change-code-not-found
      (fn [_] 
            "Email change code not found.")
   :email-change-confirmed
      (fn [_] 
            "Email change confirmed.")
   :email-not-found
      (fn [_] 
            "Email not found")
   :email-taken
      (fn [_]
            "Email already taken.")
   :expired-activation-code
      #(str "Expired activation code. <a data-method=\"post\" href="\"
            (url "/resend-activation" {:email (:email %)})
            "\">Get a new activation email with a new code</a>.")
   :expired-password-reset-code
      (fn [_]
            "Expired reset code. You can request a new one below.")
   :forgot-password-page-title
      (fn [_]
          (str 
            "Forgot password — " config/app-name))
   :home-page-title
      (fn [_]
            config/app-name)
   :insert-error
      (fn [_]
          (str 
            "There was an error, please try again. If problems continue, "
            "contact us at " contact-link " ."))
   :invalid-email
      (fn [_] 
            "Email not valid.")
   :invalid-username
      (fn [_]
          (str
            "Username can contain only letters (no accents), numbers, "
            "dots (.), hyphens (-) and underscores (_)."))
   :login-page-title
      (fn [_]
          (str "Login — " config/app-name))
   :new-requested-email-taken
      (fn [_]
            "Email already taken.")
   :new-requested-email-taken-by-not-yet-activated-account
      #(str "Email already taken but not confirmed yet. <a href=\""
            ; http://weavejester.github.com/hiccup/hiccup.util.html#var-url
            (url "/resend-activation" {:email (:new_requested_email %)})
            "\" data-method=\"post\">Resend confirmation email</a>.")
   :not-yet-activated
      #(str "Account not yet activated. <a data-method=\"post\" href=\""
            (url "/resend-activation" {:email (:email %)})
            "\">Resend activation email</a>.")
   :password-changed
      (fn [_]
            "Your password has been changed.")
   :password-reset-code-not-found
      (fn [_]
          (str
            "Reset code not found. You can try asking for a new one below. "
            "If problems continue, please contact us at " contact-link " ."))
   :password-reset-code-taken
      (fn [_]
          (str
            "Generated password reset code is already taken. "
            "Please try it again."))
   :password-too-short
      (fn [_]
            "Password must be at least 5 characters.")
   :resend-activation-page-title
      (fn [_]
            config/app-name)
   :settings-page-title
      (fn [_]
          (str
            "Settings — " config/app-name))
   :signup-page-title
      (fn [_]
          (str
            "Signup — " config/app-name))
   :taken-by-not-yet-activated-account
      #(str "Email already taken but not confirmed yet. <a href=\""
            ; http://weavejester.github.com/hiccup/hiccup.util.html#var-url
            (url "/resend-activation" {:email (:email %)})
            "\" data-method=\"post\">Resend confirmation email</a>.")
   :update-error
      (fn [_]
          (str 
            "There was an error, please try again. If problems continue, "
            "contact us at " contact-link " ."))
   :username-taken
      (fn [_]
            "That username is already taken.")
   :username-too-long
      (fn [_]
            "Username must be less than 30 characters.")      
   :username-too-short
      (fn [_]
            "Username must be at least 2 characters.")})


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

