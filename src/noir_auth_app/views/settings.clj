(ns noir-auth-app.views.settings
	(use noir.core)

	(:require [net.cgrand.enlive-html :as h]
            [noir.session :as session]
            [noir.validation :as vali]
            [noir.response :as resp]
            [noir-auth-app.models.mailer :as mailer]
            [noir-auth-app.models.user :as users]
            [noir-auth-app.views.common :as common]
            [noir-auth-app.i18n :as i18n]
            [noir-auth-app.config :as config]))


(pre-route "/settings" {}
  (common/ensure-logged-in))

(pre-route "/username-changes" {}
  (common/ensure-logged-in))

(pre-route "/email-changes*" {}
  (common/ensure-logged-in))

(pre-route "/password-changes" {}
  (common/ensure-logged-in))


(h/defsnippet settings-content "public/settings.html" [:.content :> h/any-node]
  [user errors]
  ; https://github.com/noir-clojure/lib-noir/blob/master/src/noir/validation.clj
  ; http://guides.rubyonrails.org/active_record_validations_callbacks.html#customizing-error-messages-css
  [:.username-form :.error-message]
    (h/clone-for [e (:username-form-errors errors)]
                        (h/html-content (common/build-error-message e user)))
  [:.email-notice]
    ; When there's a new email waiting to be confirmed, the transformation
    ; function returns the current node as is, otherwise it returns nil
    ; (which will remove the matched .email-notice element).
    ; https://groups.google.com/d/msg/enlive-clj/swkXHrzCU7U/qW081WsQiYQJ
    #(when (:new_requested_email user) %)
  [:.email-form :.error-message]
    (h/clone-for [e (:email-form-errors errors)]
                        (h/html-content (common/build-error-message e user)))
  [:.password-notice]
    (when-let [password-notice (:password-notice errors)]
        (h/content (i18n/translate password-notice)))
  [:.password-form :.error-message]
    (h/clone-for [e (:password-form-errors errors)]
                        (h/html-content (common/build-error-message e user)))
  [[:input (h/attr= :type "text")]]
    (common/set-field-value-from-model user))


(defpage "/settings" {}
  (let [user (common/current-user)]
    (common/layout {:title (i18n/translate :settings-page-title)
                    :nav (common/navigation-menu)
                    :content (settings-content user (session/flash-get))
                    :interpolation-map user})))


; curl -X POST -i http://127.0.0.1:5000/username-changes -d "username=test"
(defpage [:post "/username-changes"] {new-username :username}
  (users/change-username! (session/get :user-id) new-username)
  ; this may have to be changed when upgrading to Noir 1.3 (currently
  ; using 1.2.2) as in 1.3 flash-put! expects two parameters
  ; http://webnoir.org/autodoc/1.3.0/noir.session.html#var-flash-put%21
  (session/flash-put! {:username-form-errors (vali/get-errors)})
  (resp/redirect "/settings"))

;
(defn- email-email-change-code [{:keys 
                                    [new_requested_email email_change_code]}]
  (future
    (mailer/send-email 
        {:from config/emails-from
         :to new_requested_email
         :subject (i18n/translate :verification-email)
         :body (str (common/base-url)
                    "/email-changes/" email_change_code "/verify")})))

; The message string that corresponds to
; :taken-by-not-yet-activated-account assumes that the taken email is
; in the :email key (see the i18n module). This is correct when
; reporting a signup error, but not an email change error like this.
; In this case the taken email is in :new_requested_email .
; The same happens with the :email-taken error. To handle this, these
; errors are mapped to the keys of corresponding message strings that
; use the value of :new-requested-email instead of the value of :email.
;
; #TODO: maybe these should be considered different errors, and so the
; remapping be moved to the model (users/change-email!) ?
; But this would require that Noir provides a way to re-set the errors
; on a given field, and that doesn't seem to be possible currently, as
; the set-error function actually adds the error instead of replacing
; it. (I think the current set-error should probably be renamed to
; add-error and then provide a real set-error, like Rails' Errors#add
; and Errors#set methods.)
; https://github.com/ibdknox/noir/blob/master/src/noir/validation.clj
; http://api.rubyonrails.org/classes/ActiveModel/Errors.html
;
(defn- get-email-change-errors []
  ; http://clojuredocs.org/clojure_core/clojure.core/replace
  (replace {:taken-by-not-yet-activated-account
              :new-requested-email-taken-by-not-yet-activated-account
            ; actually this was not necessary because no value is
            ; interpolated into the current message string, but anyway
            :email-taken
              :new-requested-email-taken}
           ; Noir docs say that get-errors returns a vector of error
           ; strings or nil, but this is only true when it's called
           ; with a field name, not when called without params.
           (vali/get-errors)))

; curl -X POST -i http://127.0.0.1:5000/email-changes -d "email=test@example.com"
(defpage [:post "/email-changes"] {new-email :email}
  (when (not= new-email (:email (common/current-user)))
      (if-let [updated-user (users/request-email-change!
                                        (session/get :user-id) new-email)]
        (email-email-change-code updated-user)
        (session/flash-put!
            {:email-form-errors (i18n/translate
                                        (get-email-change-errors)
                                        {:new_requested_email new-email})})))
  (resp/redirect "/settings"))

; HTTP POST is used instead of GET for the same reason it's used for /logout
; (see comment for /logout in noir-auth-app.views.users). See also
; http://news.ycombinator.com/item?id=4439599
(defpage [:post "/email-changes/cancel"] {}
  (users/cancel-email-change! (session/get :user-id))
  (resp/redirect "/settings"))

; HTTP POST is used instead of GET for the same reason it's used for /logout
; (see comment for /logout in noir-auth-app.views.users)
(defpage [:post "/email-changes/resend-confirmation"] {}
  (email-email-change-code (common/current-user))
  (resp/redirect "/settings"))

; If the new email, which was available when it was requested, is now taken,
; then an appropriate error message (coming from the failed email uniqueness
; validation) will be displayed. The user may cancel the change to disable
; the verification link and get rid of the message about the email sent to the
; new address to confirm it.
;
; Notice that if the user by mistake specified an address of someone else,
; then this person will get the verification link. This is not a security
; problem though because the verification link only works if the user is
; logged in (see pre-route above) as the same user associated with the code
; in the link.
;
; Just as a reference, in Twitter, as soon as an email change is requested,
; the new address is reserved. So it's not possible that this verification
; fails because, for example, someone else signed up with the new email in
; the meantime. OTOH, I don't know for how much time the new requested
; address is reserved, but the thing is that if you specify the address of
; someone that is not a Twitter user, you're effectively preventing him from
; signing up to Twitter while the reservation holds.
(defpage "/email-changes/:email-change-code/verify" {:keys [email-change-code]}
  (if (users/change-email! (session/get :user-id) email-change-code)
      (session/flash-put! {:email-form-errors [:email-change-confirmed]})
      (session/flash-put! {:email-form-errors (get-email-change-errors)}))
  (resp/redirect "/settings"))

; #TODO: depending on the session length, the old password should probably be
; required to set a new one
;
; curl -X POST -i http://127.0.0.1:5000/password-changes -d "password=test"
(defpage [:post "/password-changes"] {new-password :password}
  (let [result (users/change-password! (session/get :user-id) new-password)]
    (session/flash-put! (if result
                            {:password-notice :password-changed}
                            {:password-form-errors (vali/get-errors)}))
    (resp/redirect "/settings")))

