(ns noir-auth-app.views.admin
  (:use noir.core
        noir.fetch.remotes
        hiccup.page-helpers)
  (:require [noir.request :as req]
            [noir.response :as resp]
            [noir.session :as session]
            [clj-time.format :as time-format]
            [noir-auth-app.models.user :as users]
            [somnium.congomongo :as db]
            [noir-auth-app.views.common :as common]))


;; Links

(def user-actions [{:url "/blog/admin/user/add" :text "Add a user"}])

;;
; http://stackoverflow.com/questions/4095714/what-is-the-idiomatic-way-to-prepend-to-a-vector-in-clojure
(def users-fields (into [:_id] users/collection-fields))

;
(def truncated-users-fields (map #(common/truncate (name %) 15) users-fields))


;; Partials

(defpartial action-item [{:keys [url text]}]
  [:li (link-to url text)])

(defpartial users-header []
  [:tr (map #(identity [:th %]) truncated-users-fields)
       [:th "Actions"]])

(defpartial user-item [user]
  ; without (identity) 
  ;   Wrong number of args (0) passed to: PersistentVector
  ; http://stackoverflow.com/questions/4921566/clojure-returning-a-vector-from-an-anonymous-function
  [:tr (map #(identity [:td (% user)]) users-fields)
       [:td "Delete (disabled in this demo)"]])
       ; [:td (link-to {:data-confirm "Are you sure?"
       ;                :data-action "delete-user"
       ;                :data-params (:_id user)
       ;                :data-callback "delete-user-callback"}
       ;               "#" "Delete")]])
                     

;;

;; force you to be an admin to get to the admin section
(pre-route "/admin*" {}
  (when-not (session/get :admin)
      (common/store-location)
      (resp/redirect "/login")))


;; Pages

; Notice that if the URL is
;   /admin/users?until=2012-06-16T21:30:17.001+02:00
; then the 'until' param is decoded as
;   2012-06-16T21:30:17.001 02:00
; because '+' means a space
;   http://stackoverflow.com/questions/2678551/when-to-encode-space-to-plus-and-when-to-20
; OTOH, the clj-time's parse function returns nil on that string.
; So, to get the correct behaviour, the '+' must be encoded (%2B)
;   /admin/users?until=2012-06-16T21:30:17.001%2B02:00
; Times without time zone information are interpreted in UTC, but they also work
;   /admin/users?until=2012-06-16T21:30:17.001
;
; http://127.0.0.1:5000/admin/users?until=2012-07-07T22%3A09%3A44.109%2B02%3A00
;
; Example of use of "until" (and "since") in an API
; http://developers.facebook.com/docs/reference/api/
;
(defpage "/admin" {:keys [until]}
  
  ; Caveats:
  ;
  ; Notice that this paging doesn't work with nullable columns:
  ; When sorting in descending order, like in this case, records with nulls
  ; in the sorting column are positioned at the end of the result set, which
  ; means that they can only be selected if they're part of the first page,
  ; because no :where condition is restricting the selection of the records
  ; shown in the first page. However, for any subsequent page, the selected
  ; records must have a value for the sorting column (:created_at) which is
  ; less than or equal than the one used to specify the desired page (the
  ; 'until' value in this case). Because MongoDB doesn't consider null to be
  ; less than or equal to any other value (except null itself?), no records
  ; with null will be selected for any page after the first one.
  ;
  ; If there's a value of created_at for which the number of records with
  ; that value is greater than the page-size, then it's not possible to
  ; display them all (as currently implemented), and paging will not work
  ; ("next" links will lead to the same page).
  ;
  ; http://www.mongodb.org/display/DOCS/Advanced+Queries#AdvancedQueries-%7B%7Bsort%28%29%7D%7D
  (let [page-size 2
        limit (inc page-size)
        parsed-until-time (time-format/parse until)
        where (when until {:created_at {:$lte parsed-until-time}})
        users-batch
            (users/find {:where where
                         :sort {:created_at -1}
                         :limit limit})
        next-page-first-created-at
            (when (> (count users-batch) page-size)
                  (:created_at (last users-batch)))
        page-users 
            (if next-page-first-created-at (butlast users-batch) users-batch)]

    (common/admin-layout
        (when (and until (nil? parsed-until-time)) 
              [:p "Unparseable <code>until</code> parameter"])
        ; http://www.w3.org/TR/html-markup/table.html#table
        [:table
          [:thead (users-header)]
          [:tbody (map user-item page-users)]]
        ; http://weavejester.github.com/hiccup/hiccup.util.html#var-url
        (when next-page-first-created-at
              ; http://www.usabilitypost.com/2012/01/10/pressed-button-state-with-css3/
              [:p (link-to {:class "button"}
                           (url "/admin" 
                                {:until next-page-first-created-at})
                           "next")]))))

;
; https://github.com/ibdknox/fetch
(defremote delete-user [user-id]
  (when (session/get :admin)
        ; https://github.com/aboekhoff/congomongo/issues/77
        (println "admin will delete" user-id)
        (users/delete! user-id)))

