;; On the name of the file and the name of the namespace (underscores and dashes)
;; http://en.wikibooks.org/wiki/Clojure_Programming/Concepts#Load_and_Reload
;; http://stackoverflow.com/questions/6709131/what-are-clojures-naming-conventions#comment7945879_6709278
(ns noir-auth-app.views.password-resets
  (use noir.core)
  (use hiccup.page-helpers)
  
  (:require [net.cgrand.enlive-html :as h]
            [noir.session :as session]
            [noir.validation :as vali]
            [noir.request :as req]
            [noir.response :as resp]
            [postal.core :as postal]
            [noir-auth-app.models.user :as users]
            [noir-auth-app.models.mailer :as mailer]
            [noir-auth-app.views.common :as common]
            [noir-auth-app.i18n :as i18n]
            [noir-auth-app.config :as config]))


(h/defsnippet new-content
              "public/password-resets/new.html" [:.content :> h/any-node]
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


(defpage "/password-resets" {:keys [email] :as params}
  ; Called when the forgot password link is clicked, but
  ; /password-resets/:reset-code/edit also redirects here when there are
  ; errors, which are stored in the flash under the :reset-code-errors key.
  (let [errors (or (:reset-code-errors (session/flash-get))
                   (vali/get-errors))]
    (common/layout {:title (i18n/translate :forgot-password-page-title)
                    :nav (common/navigation-menu)
                    :content (new-content params errors)})))


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
            (i18n/translate :password-change-instructions-sent))
        (resp/redirect "/login"))
    (render "/password-resets" params)))


(h/defsnippet edit-content
              "public/password-resets/edit.html" [:.content :> h/any-node]
  [params errors]
  [:form]
    ; It's assumed that the HTML snippet already contains
    ;   <input name="_method" type="hidden" value="PUT">
    ; which is necessary for Noir to understand that this has to be handled
    ; with the :put route even though the form is submitted with an HTTP POST.
    ; Actually this is handled by Compojure
    ; https://github.com/weavejester/compojure/blob/master/src/compojure/core.clj#L13
    (h/set-attr :action (str "/password-resets/" (:reset-code params)))
  ; https://github.com/noir-clojure/lib-noir/blob/master/src/noir/validation.clj
  ; http://guides.rubyonrails.org/active_record_validations_callbacks.html#customizing-error-messages-css
  [:.error-message]
    (h/clone-for [e errors]
        (h/html-content (common/build-error-message e params)))
  ; https://groups.google.com/group/enlive-clj/browse_thread/thread/2725d46c018beb7
  ; Remember that set-attr returns a function (as any other Enlive
  ; transformation function) that expects an element (the matching element).
  ; (see p. 550 of "Clojure Programming")
  ; About the nested vectors, see p. 549 of "Clojure Programming".
  ; See also "Selectors 101" in
  ; https://github.com/cgrand/enlive
  [[:input (h/attr= :type "text")]]
    (common/set-field-value-from-model params))


(defpage "/password-resets/:reset-code/edit" {:keys [reset-code] :as params}
  (if (users/validate-existing-password-reset-code reset-code)

      ; When the new password sent via HTTP PUT to "/password-resets" is
      ; not valid, this page is rendered again to show the errors.
      (common/layout {:title (i18n/translate :change-password-page-title)
                      :nav (common/navigation-menu)
                      :content (edit-content params (vali/get-errors))})

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
            error-keywords (distinct (vali/get-errors))]
        (session/flash-put! {:reset-code-errors error-keywords})
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

