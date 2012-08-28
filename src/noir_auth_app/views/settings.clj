(ns noir-auth-app.views.settings
	(use noir.core)
    ; the version of Noir currently used (1.2.2) uses Hiccup 0.3.7, in 1.0
    ; there's a new hiccup.element namespace which contains link-to for
    ; example, which was previously part of the hiccup.page-helpers namespace.
    ; https://github.com/ibdknox/noir/blob/1.2.2/project.clj
  	(use hiccup.form-helpers)
  	(use hiccup.page-helpers)

	(:require [noir.session :as session]
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


(defpage "/settings" {}
  ; In a previous version of the code, (session/flash-get) was called for
  ; each form below, but then only the first call was obtaining the flash
  ; contents because "flashes in Noir have the lifetime of one retrieval,
  ; meaning that after the first (flash-get) the value will be nil"
  ; http://webnoir.org/tutorials/sessions
  (let [{:keys [username email new_requested_email] :as user}
                                                        (common/current-user)
        flash-value (session/flash-get)]

    (common/layout (i18n/translate :settings-page-title)
        ; In Clojure using the key of a hash element as a function of the
        ; hash, returns the value for that key, and on the other hand,
        ; (:key nil) returns nil, so this works...
        (common/error-text (:username-form-errors flash-value) user)
        (form-to [:post "/username-changes"]
                 ; http://weavejester.github.com/hiccup/hiccup.form.html#var-text-field
                 [:p (text-field {:placeholder "Username"} 
                                 :username username)]
                 [:p (submit-button "change username")])
        (when new_requested_email 
              [:p (i18n/translate :email-change-confirmation-sent
                                  {:email new_requested_email})
                  [:br]
                  (link-to "/email-changes/resend-confirmation"
                           (i18n/translate :resend-confirmation))
                  " · "
                  (link-to {:data-method "post"} "/email-changes/cancel"
                           (i18n/translate :cancel-change))])
        (common/error-text (:email-form-errors flash-value) user)
        (form-to [:post "/email-changes"]
                 [:p (text-field {:placeholder "Email"} :email email)]
                 [:p (submit-button "change email")])
        (common/error-text (:password-form-notices flash-value))
        (form-to [:post "/password-changes"]
                 [:p (password-field {:placeholder "Password"} :password)]
                 [:p (submit-button "change password")])
        ; data-confirm inspired by how Rails 3 handles JavaScript
        ; confirmation messages
        ;   http://railscasts.com/episodes/205-unobtrusive-javascript?view=asciicast
        ;   https://github.com/rails/jquery-ujs/wiki/ajax
        ; data-action inspired by Chris Granger's Overtone controller. In
        ; Rails the action would typically be specified in href, but in
        ; Rails the action is specified with an HTTP method and a URL,
        ; while Granger's fetch library provides a higher level interface
        ; by which the action is simply specified with a remote function
        ; name, so it doesn't seem appropriate to put a function name where
        ; a URL is expected (actually, it might be ok if the function name
        ; were prefixed with something like "cljs:", similar to how
        ; the "javascript:" pseudo protocol prefix is used to put JavaScript
        ; code directly into an href, but then parsing that action value
        ; would be a little more complicated). Another reason to not put the
        ; function name in href is that if JavaScript is disabled, when
        ; clicking on the link the browser would try to open that, which
        ; would cause an error.
        ;   http://www.chris-granger.com/2012/02/20/overtone-and-clojurescript/
        ; The idea is to handle all confirmation messages like this with the
        ; same ClojureScript code, in the same way that Rails does.
        [:p (link-to {:data-confirm "Are you sure?"
                      :data-action "delete-account"
                      :data-callback "delete-account-callback"}
                     "#" "delete account")])))
        ; href is not required, but then the element is not displayed as a
        ; hyperlink
        ; http://dev.w3.org/html5/spec/single-page.html#attr-hyperlink-href
        ; [:p [:a {:data-confirm "Are you sure?"
        ;          :data-action "delete-account"}
        ;         "delete account"]]))

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
         :subject "Verification email"
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

;
(defpage "/email-changes/resend-confirmation" {}
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
  (let [result (users/change-password! (session/get :user-id) new-password)
        notices (if result [:password-changed] (vali/get-errors))]
    (session/flash-put! {:password-form-notices notices})
    (resp/redirect "/settings")))

