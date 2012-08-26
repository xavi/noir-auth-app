;; On the name of the file and the name of the namespace (underscores and dashes)
;; http://en.wikibooks.org/wiki/Clojure_Programming/Concepts#Load_and_Reload
;; http://stackoverflow.com/questions/6709131/what-are-clojures-naming-conventions#comment7945879_6709278
(ns noir-auth-app.views.password-resets
  (use noir.core)
  (use hiccup.form-helpers)
  (use hiccup.page-helpers)
  
  (:require [noir.session :as session]
            [noir.validation :as vali]
            [noir.request :as req]
            [noir.response :as resp]
            [postal.core :as postal]
            [noir-auth-app.models.user :as users]
            [noir-auth-app.models.mailer :as mailer]
            [noir-auth-app.views.common :as common]
            [noir-auth-app.i18n :as i18n]
            [noir-auth-app.config :as config]))


(defpage "/password-resets" {:keys [email]}  
  (common/layout (i18n/translate :forgot-password-page-title)

      ; /password-resets/:reset-code/edit redirects here when there are
      ; errors, and it stores those errors in the flash
      (common/error-text (:reset-code-errors (session/flash-get)))

      ; Noir docs say that get-errors returns a vector of error
      ; strings or nil, but this is only true when it's called
      ; with a field name, not when called without params. In this latter
      ; case, a sequence is always returned (when there are no errors, the
      ; sequence is empty). The not-empty function is used below to get nil
      ; when the sequence is empty.
      ; http://clojure.github.com/clojure/clojure.core-api.html#clojure.core/not-empty
      (when-let [errors (not-empty (vali/get-errors))]
          (common/error-text errors))
      (form-to [:post "/password-resets"]
               [:p (text-field {:placeholder "Email"} :email email)]
               (submit-button "Reset password"))))


; It's ok to provide an email for a not yet activated account, see comments in
; the change-password-with-reset-code! function for details.
;
(defpage [:post "/password-resets"] {:keys [email] :as params}
  (if-let [reset-code (users/set-password-reset-code! email)]
    (do (future
            (mailer/send-email 
                {:from config/emails-from
                 :to email
                 :subject "How to reset your password"
                 :body (str "Hi!\n\n"
                            "If you have forgotten your " config/app-name
                            " password, you can choose a new one by using "
                            "the form linked below:\n\n"
                            (common/base-url)
                            "/password-resets/" reset-code "/edit")}))
        (session/flash-put! 
            (str "Email sent with instructions on how to change your "
                 "password. Please check your inbox."))
        (resp/redirect "/login"))
    (render "/password-resets" params)))


(defpage "/password-resets/:reset-code/edit" {:keys [reset-code]}
  (if (users/validate-existing-password-reset-code reset-code)

      (common/layout (i18n/translate :change-password-page-title)
          ; When the new password sent via HTTP PUT to "/password-resets" is
          ; not valid, this page is rendered again, and the line below
          ; displays the error.
          (common/error-text (vali/get-errors))
          (form-to [:put (url "/password-resets/" reset-code)]
                   [:p (password-field {:placeholder "password"} :password)]
                   [:p (submit-button "Save")]))

      ; actually email can only be retrieved if the error is
      ; :expired-password-reset-code, but there's no need to write
      ; specific code for the other possible error
      ; (:password-reset-code-not-found) as the same code will work when
      ; email is nil.
      (let [{email :email :as user}
                              (users/find-by-password-reset-code reset-code)
            ; 'distinct' is necessary because PUT "/password-resets" renders
            ; this page when there are errors on :password_reset_code, and as
            ; this page also validates the reset code, errors are duplicated
            error-keywords (distinct (vali/get-errors))
            error-strings (i18n/translate error-keywords user)]
        (session/flash-put! {:reset-code-errors error-strings})
        (resp/redirect (url "/password-resets" {:email email})))))


(defpage [:put "/password-resets/:reset-code"] {:keys [reset-code password] :as params}
  (if (users/change-password-with-reset-code! reset-code password)
      (do (session/flash-put! "Password changed successfully.")
          (resp/redirect "/login"))
      ; TODO:
      ; if all errors are on :password_reset_code, maybe should redirect to
      ; "/password-resets" directly, intead of the render below, which in this
      ; case will end up redirecting to "/password-resets" anyway
      (render "/password-resets/:reset-code/edit" params)))

