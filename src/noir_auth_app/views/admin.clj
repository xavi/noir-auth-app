(ns noir-auth-app.views.admin
  (:use [compojure.core :only (defroutes GET)]
        [hiccup.util :only (url)]
        [shoreleave.middleware.rpc :only (defremote)])
  (:require [net.cgrand.enlive-html :as h]
            [noir.request :as req]
            [noir.response :as resp]
            [noir.session :as session]
            [clj-time.format :as time-format]
            [noir-auth-app.models.user :as users]
            [noir-auth-app.i18n :as i18n]
            [noir-auth-app.views.common :as common]))

;;
; http://stackoverflow.com/questions/4095714/what-is-the-idiomatic-way-to-prepend-to-a-vector-in-clojure
(def users-fields (into [:_id] users/collection-fields))

;
(def truncated-users-fields (map #(common/truncate (name %) 15) users-fields))

;; ensures that only admin users can get to the admin section
(defmacro ensure-admin [& body]
  `(if (session/get :admin)
       (do ~@body)
       (do
         (common/store-location)
         (resp/redirect "/login"))))


; The template contains several sample header and data cells (<th> and <td>),
; but only one header cell and one data cell are intended to be used as
; models, so the extra ones have to be removed. They're removed here, instead
; of the defsnippet below, so that they don't have to be removed on every
; request.
; https://groups.google.com/group/enlive-clj/browse_thread/thread/874636a5839eea8e/5b5d6729f5e4c982
(def index-resource
  ; The "at" form is the most important form in Enlive. There are implicit at
  ; forms in snippet and template.
  ; https://github.com/cgrand/enlive#the-at-form
  (h/flatmap #(h/at %
                  [:tbody [:tr (h/but h/first-child)]]
                    nil
                  ; Selects all <th> and <td> nodes that do not contain
                  ; "actions" in their class attribute and are not the first
                  ; child of their parent.
                  ; Enlive's "but" is equivalent to CSS :not (it's named
                  ; "but" to not clash with clojure.core/not).
                  ; In Enlive selectors, Clojure sets mean "or".
                  ; https://github.com/cgrand/enlive#selectors-101
                  ; http://clojure.org/data_structures#Data Structures-Sets
                  [[#{:th :td}
                        (h/but (h/attr-contains :class "actions"))
                        (h/but h/first-child)]]
                    nil)
             ; loads the HTML resource and returns a seq of nodes
             (h/html-resource "public/admin/index.html")))


(h/defsnippet index-content index-resource [:.content :> h/any-node]
  [{:keys [error users-fields truncated-users-fields page-users
           next-page-first-created-at]}]

  [:.error-message]
    (when error (h/html-content error))

  [[:th (h/but (h/attr-contains :class "actions"))]]
    (h/clone-for [field-name truncated-users-fields]
        (h/content field-name))

  ; https://groups.google.com/group/enlive-clj/msg/75e4c631427546da
  [:tbody :tr]
    (h/clone-for [user page-users]
        ; https://github.com/cgrand/enlive/wiki/Table-and-Layout-Tutorial%2C-Part-4%3A-Duplicating-Elements-and-Nested-Transformations
        [[:td (h/but (h/attr-contains :class "actions"))]]
          (h/clone-for [k users-fields]
              ; (:_id user) is an ObjectId, the (str) below is used to
              ; convert it to a string, otherwise
              ;   java.lang.IllegalArgumentException:
              ;   Don't know how to create ISeq from: org.bson.types.ObjectId
              ; See users/delete! comments for details about ObjectId.
              (h/content (str (k user))))
        [(h/attr= :data-action "delete-user")]
          (h/substitute "Delete (disabled in this demo)"))
          ; (h/set-attr :data-callback "delete-user-callback"
          ;             :data-confirm "Are you sure?"
          ;             :data-params (:_id user)))

  ; If there's a next page, or the <p> doesn't contain the button link,
  ; returns the <p> node as is, otherwise removes it.
  [:p]
    ; Enlive's "select" returns the seq of nodes or fragments matched by the
    ; specified selector
    ; https://github.com/cgrand/enlive/blob/master/src/net/cgrand/enlive_html.clj
    ; http://clojure.github.com/clojure/clojure.core-api.html#clojure.core/empty?
    #(if (or next-page-first-created-at (empty? (h/select % [:a.button])))
         %
         nil)

  [:a.button]
    (when next-page-first-created-at
          (h/set-attr :href (url "/admin" 
                                 {:until next-page-first-created-at}))))


;;; Actions

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
(defn index-action [until]
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

    (doall  ; see comment in common/navigation-menu
      (common/admin-layout
        {:title (i18n/translate :admin-page-title)
         :nav (common/navigation-menu)
         :content
            (index-content {:error
                                (when (and until (nil? parsed-until-time))
                                      "Unparseable <code>until</code> parameter")
                            :users-fields users-fields
                            :truncated-users-fields truncated-users-fields
                            :page-users page-users
                            :next-page-first-created-at
                                next-page-first-created-at})}))))


(defroutes admin-routes
  ; https://github.com/weavejester/compojure/wiki/Destructuring-Syntax
  (GET "/admin" [until]
    (ensure-admin (index-action until))))


;;; Remotes

; https://github.com/shoreleave/shoreleave-remote-ring
(defremote delete-user [user-id]
  (when (session/get :admin)
        ; https://github.com/aboekhoff/congomongo/issues/77
        (println "admin will delete" user-id)
        (users/delete! user-id)))
