(ns noir-auth-app.models.user

  ; The definition of 'find' below would override the mapping to the
  ; same-named var referred from clojure.core (with a warning), but
  ; explicitly excluding it here at least makes it clear that we're
  ; aware of the conflict, and removes the warning.
  ; http://books.google.com/books?id=I8KdEKceCHAC&pg=PA328&lpg=PA328&dq=excluding+vars+from+clojure.core+because+their+names&source=bl&ots=wNiNQ8T6f9&sig=mrdrzv51jhg_dOmx2MbSS2AaG_g&hl=en&sa=X&ei=yi0VUJvGCqTX0QXo5oGoDg&ved=0CGIQ6AEwAA
  ; The same technique is used in clojure.string
  ; https://github.com/clojure/clojure/blob/master/src/clj/clojure/string.clj
  ; Notice that clojure.core's find can still be called if it's fully qualified,
  ; i.e. by using clojure.core/find .
  ; http://blog.8thlight.com/colin-jones/2010/12/05/clojure-libs-and-namespaces-require-use-import-and-ns.html
  ;
  (:refer-clojure :exclude [find])
  
  (:require [clojure.string :as string]
            [somnium.congomongo :as db]
            [clj-time.core :as time]
            [noir.util.crypt :as crypt]
            [noir.validation :as vali]
            [monger.collection :as mc])

  (:use monger.operators)

  (:import java.security.SecureRandom)
  (:import [org.bson.types ObjectId]))
            

; Although the convention in Clojure is to use hyphens to separate words in
; multi-word identifiers, and they're valid characters for MongoDB key names
; http://www.mongodb.org/display/DOCS/Legal+Key+Names
; underscores will be used for these instead.
;
; The reason is that, in practice, hyphens are a source of problems because
; MongoDB uses JavaScript as the query language (and internally), and in
; JavaScript a hyphen is interpreted as a minus sign.
; https://groups.google.com/group/mongodb-user/browse_thread/thread/4045aea1c59bb7b2/fbaec4b1a47bee18
; http://blog.shlomoid.com/2011/08/how-to-fix-erroneously-named-mongodb.html
;
; OTOH, using underscores instead of hyphens will probably avoid problems if
; the database also has to be used from other languages and/or libraries.
; This is in part because underscores seem to be the convention in MongoDB.
; http://docs.mongodb.org/manual/use-cases/
; http://stackoverflow.com/questions/5916080/what-are-naming-conventions-for-mongodb
; https://github.com/square/cube/issues/44
;
;
; Notice that :_id is not included in this vector, although documents in the
; :users collection have that field
; http://www.mongodb.org/display/DOCS/Object+IDs
; http://stackoverflow.com/questions/2298870/mongodb-get-names-of-all-keys-in-collection
(def collection-fields 
      [:username :lowercase_username :email :crypted_password 
       :activation_code :activation_code_created_at
       :password_reset_code :password_reset_code_created_at
       :new_requested_email :email_change_code
       :created_at :updated_at])


(defn maybe-init
  "If it doesn't exist yet, it creates a collection named 'users'.
  If they don't exist yet, it creates the appropriate indexes."
  []
  (or (mc/exists? "users") 
      (mc/create "users"))

  ; MongoDB creates the _id index by default on all collections. It is a
  ; unique index even though the getIndexes() method will not print unique:
  ; true in the mongo shell.
  ; http://docs.mongodb.org/manual/core/indexes/#index-type-primary

  ; http://docs.mongodb.org/manual/core/indexes/#unique-index
                                        ; https://github.com/aboekhoff/congomongo/issues/82
  (mc/ensure-idex "users" {"lowercase_username" 1
                           "email" 1}
                  {:unique true})
  (mc/ensure-idex "users" {"created_at" 1})

  ; http://docs.mongodb.org/manual/core/indexes/#sparse-index
                                        ; http://stackoverflow.com/questions/8608567/sparse-indexes-and-null-values-in-mongo
  (mc/ensure-idex "users" {"activaction_code" 1
                           "password_reset_code" 1}
                  {:unique true :sparse true}))


;; Querying the database

(defn find [query]
  ; Congomongo's fetch uses keyword parameters, so the way to call fetch is...
  ;     (fetch :users :limit limit)
  ; instead of...
  ;     (fetch :users {:limit limit})
  ;   https://github.com/aboekhoff/congomongo/blob/master/src/somnium/congomongo.clj#L264
  ; More about keyword params in Clojure at
  ;   http://stackoverflow.com/questions/717963/clojure-keyword-arguments
  (mc/find-maps "users" query)

; sort and limit TODO 
  (mc/find-maps "users" {where}))

(defn find-by-id [id]  
  (mc/find-map-by-id "users" id))

(defn find-by-email [email]
  (mc/find-one-as-map "users" {:email (string/lower-case email)}))

(defn find-by-activation-code [activation-code]
  (mc/find-one-as-map "users" {:activation_code activation-code}))

(defn find-by-password-reset-code [password-reset-code]
  (mc/find-one-as-map "users" {:password_reset_code password-reset-code}))

(defn find-by-username-or-email [username-or-email]
  ; MongoDB indexes (and string equality tests in general) are case sensitive.
  ;   http://www.mongodb.org/display/DOCS/Indexes#Indexes-Behaviors
  ; Regular expressions can be used for case-insensitive queries, but this
  ; type of regular expressions cannot benefit from an index, so they are
  ; slow. Actually, only simple, case-sensitive prefix queries (rooted
  ; regexps) can benefit from an index.
  ; http://www.mongodb.org/display/DOCS/Advanced+Queries#AdvancedQueries-RegularExpressions
  ;
  ; http://stackoverflow.com/questions/5499451/case-insensitive-query-on-mongodb
  ;
  ; OTOH, MongoDB can only use one index per query, with the exception of $or
  ; queries, which can run multiple query plans and then de-dup the results
  ; http://www.mongodb.org/display/DOCS/Indexing+Advice+and+FAQ#IndexingAdviceandFAQ-Oneindexperquery.
  ; Thanks to this it is NOT necessary to add a compound index on username
  ; and email (on top of the individual indexes that are necessary anyway to
  ; guarante uniqueness of username and email).
  ;
  ; http://www.mongodb.org/display/DOCS/Advanced+Queries#AdvancedQueries-%24or
  (let [lc-username-or-email 
            (when username-or-email (string/lower-case username-or-email))]
    (mc/find-one-as-map "users"
                        { $or [{:lowercase_username lc-username-or-email}
                               {:email lc-username-or-email}]})))

; This function may be useful for any collection, not only for :users, that's
; why there's a collection-name parameter. The function should be eventually
; moved to another, more general, namespace (or extracted into a library).
; http://api.rubyonrails.org/classes/ActiveRecord/Validations/ClassMethods.html#method-i-validates_uniqueness_of
(defn validate-uniqueness
          [record attr-name collection-name
           & [{:keys [message] :or {message "has already been taken"}}]]
  (let [_id (:_id record)
        ; attr-name must be a keyword
        ; http://stackoverflow.com/questions/1527548/why-does-clojure-have-keywords-in-addition-to-symbols
        attr-value (attr-name record)]
    (vali/rule (not (mc/any?
                     collection-name (merge {attr-name attr-value}
                                            (when _id {:_id { $ne _id}}))))
               [attr-name message])))


(defn valid? [{:keys [_id username email password crypted_password
                                        activation_code password_reset_code]
               :as user}]
  
  ; Activation_code should be unique. This is enforced here and also at the
  ; database level (see the maybe-init function in this same namespace).
  (when activation_code
        (validate-uniqueness
            user
            :activation_code
            "users"
            {:message :activation-code-taken}))

  ; Database performance, as measured by timing the call to
  ; validate-uniqueness below:
  ; 282.346 ms running the app locally with the database in MongoLab
  ;   (274.277 + 308.953 + 215.688 + 306.588 + 306.222)/5
  ; 0.733 ms running the app locally with a local instance of MongoDB
  ;   (1.166 + 0.791 + 0.611 + 0.605 + 0.492)/5
  ; 2.73625 ms running the app in Heroku with the database in MongoLab
  ;   (2.872752 + 3.717144 + 2.269183 + 2.282804 + 2.539381)/5
  (when password_reset_code
        (validate-uniqueness
            user
            :password_reset_code 
            "users"
            {:message :password-reset-code-taken}))

  ; More about \A and \z anchors in...
  ; http://stackoverflow.com/questions/3632024/why-do-rubys-regular-expressions-use-a-and-z-instead-of-and
  ; http://www.regular-expressions.info/anchors.html
  ;
  ; The \w predefined character class matches a word character: [a-zA-Z_0-9]
  ; http://www.regular-expressions.info/charclass.html
  ; http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
  ;
  ; http://clojure.github.com/clojure/clojure.core-api.html#clojure.core/re-find
  ; http://clojuredocs.org/clojure_core/clojure.core/re-find
  ; http://clojure.github.com/clojure/clojure.core-api.html#clojure.core/re-matches
  ; http://clojuredocs.org/clojure_core/clojure.core/re-matches
  (vali/rule (re-matches #"\A[\w\.\-]+\z" username)
             [:username :invalid-username])

  ; http://ux.stackexchange.com/questions/14633/what-should-be-maximum-and-minimum-length-of-persons-user-name-password
  (vali/rule (vali/min-length? username 2) [:username :username-too-short])
  (vali/rule (vali/max-length? username 30) [:username :username-too-long])

  (when-not (vali/errors? :username)
      ; Because the uniqueness check must be done for :lowercase_username but
      ; the possible error must be set for :username, the validate-uniqueness
      ; function defined before cannot be used here.
      ;
      ; if _id is not nil it means that the record to validate is an update
      ; on a record already in the database.
      ; The code below uses the fact that the result of merging a hash with
      ; nil is the original hash.
      ; http://stackoverflow.com/questions/8992997/initializing-elements-of-a-map-conditionally-in-clojure
      (let [where (merge {:lowercase_username (string/lower-case username)}
                         (when _id {:_id {:$ne _id}}))]
          (vali/rule (not (mc/any? "users" where))
                     [:username :username-taken])))
  
  ; A nil email must be explicitly checked because noir.validation.is-email?
  ; produces a java.lang.NullPointerException if passed a nil
  ; http://webnoir.org/autodoc/1.2.1/noir.validation-api.html#noir.validation/is-email?
  ; http://webnoir.org/autodoc/1.2.1/noir.validation-api.html#noir.validation/set-error
  ; noir.validation/is-email? doesn't allow CAPS in emails!
  ; https://github.com/ibdknox/noir/blob/master/src/noir/validation.clj
  (let [lc-email (when email (string/lower-case email))]
      (if (or (nil? lc-email) (not (vali/is-email? lc-email)))
          (vali/set-error :email :invalid-email)
          (let [user (mc/find-one-as-map 
                      "users"
                      (merge {:email lc-email}
                             (when _id {:_id { $ne _id}})))]
              (when user
                  (if (:activation_code user)
                      (vali/set-error 
                            :email
                            ; By specifying a keyword as the error, instead of
                            ; a string, the code of this model doesn't have to
                            ; be concerned by view details like the URL that
                            ; in this case will be shown to the user to
                            ; request the resending of the activation code.
                            ; OTOH, this also serves as a first step towards
                            ; internationalization.
                            ;"Email already taken but not confirmed yet.")
                            :taken-by-not-yet-activated-account)
                      (vali/set-error :email :email-taken))))))

  ; Password can only (and must be) validated when creating a new user (in
  ; which case the crypted_password is blank, because the crypted_password is
  ; only set just before saving, after validations) or when changing the
  ; password of an existing user, in which case password will not be nil.
  ; (The user may try to change the password to blank, but that's an empty
  ; string, not nil, so it will be validated and rejected.)
  ;
  ; Password cannot be validated when it is not being changed, because in
  ; this case the password (in clear text) is not available. However, it's
  ; not necessary either (if it was valid and it hasn't changed, it must still
  ; be valid), so that's not a problem.
  ; This is like in Restful Authentication, where validations are performed
  ; in a similar case (the only difference being that in Restful
  ; Authentication "changing" the password to the empty string is not
  ; considered a change, and the password is not validated or stored in this
  ; case) as seen in the password_required? method and the before_save in
  ; https://github.com/technoweenie/restful-authentication/blob/master/lib/authentication/by_password.rb
  ;
  (if (or (string/blank? crypted_password) (not (nil? password)))
      (vali/rule (vali/min-length? password 5)
             [:password :password-too-short]))

  (not (vali/errors? :username :email :password :activation_code
                     :password_reset_code)))


;; Mutations and Checks

; http://items.sjbach.com/567/critiquing-clojure#2
(declare set-activation-code hexadecimalize generate-secure-token)
;          find-by-email)


(defn prepare-for-save
  "Removes from the passed user map the keys/values not meant to be stored in
  the database (like :password).

  If there's a mapping for :username, it stores its lowercase equivalent in
  :lowercase_username .

  Also, if there's a mapping for :password and it's not blank, it encrypts it
  and stores the encrypted value at the :crypted_password key."
  ; This works in
  ; conjunction with the password validation in the the valid? function with the
  ; end result being that only valid passwords are stored in the database:
  ; That's because for new records the validation will not accept a blank
  ; password (this works on the assumption that :crypted_password is blank for
  ; new records), and for existing records, even although a blank password will
  ; pass the validation, here it will be ignored and the stored :crypted_password
  ; won't be changed.
  ;
  ; This function acts somewhat like an ActiveRecord before_save callback
  ; http://api.rubyonrails.org/classes/ActiveRecord/Callbacks.html
  ; Used like in Restful Authentication's
  ;   before_save :encrypt_password
  ; https://github.com/technoweenie/restful-authentication/blob/master/lib/authentication/by_password.rb
  ;
  [{:keys [username email password] :as user}]

  ; in the database the passwords are prefixed with $2a$10.
  ; $2a indicates that the BLOWFISH algorithm was used.
  ; $10 is the "cost parameter". This is the base-2 logarithm of how many
  ; iterations were run (10 => 2^10 = 1024 iterations.) 
  ; http://net.tutsplus.com/tutorials/php/understanding-hash-functions-and-keeping-passwords-safe/  
  (select-keys 
      (merge user
             {:lowercase_username 
                (when username (string/lower-case username))}
             {:email (when email (string/lower-case email))}
             (when-not (nil? password) 
                       {:crypted_password (crypt/encrypt password)}))
      collection-fields))


;; Operations

(defn create!
  "Returns:
    - nil if validations or insertion into the database fails
    - the created object if validations are ok and it's successfully saved

  See:
    http://api.rubyonrails.org/classes/ActiveRecord/Persistence/ClassMethods.html#method-i-create
    http://api.rubyonrails.org/classes/ActiveRecord/Validations/ClassMethods.html#method-i-create-21"
  [attributes]
  (let [user (set-activation-code attributes)]
    (when (valid? user)
            ; Because "write concern" is set to :safe (see models.clj),
            ; "Exceptions are raised for network issues, and server errors;
            ; waits on a server for the write operation"
            ; http://api.mongodb.org/java/2.7.3/com/mongodb/WriteConcern.html#SAFE
            ; "server errors" may be, for example, duplicate key errors
            ;
            ; DuplicateKey E11000 duplicate key error index: heroku_app2289247.users.$email_1  dup key: { : null }  com.mongodb.CommandResult.getException (CommandResult.java:85)
            ; https://github.com/aboekhoff/congomongo/pull/62#issuecomment-5249364
            ;
            ; A :unique :sparse index on :activation_code is used to ensure
            ; its uniqueness at the database level too (besides the uniqueness
            ; check done at the application level by the valid? function)
            ; http://docs.mongodb.org/manual/core/indexes/#unique-index
            ;
            (try
              ; If :created_at is specified then that will be used instead of
              ; the current timestamp (like in Rails). This also works if a
              ; nil value is specified. (Rails, instead, doesn't honor a
              ; :created_at set to nil, and overwrites it with the current
              ; timestamp.)
              ; Same for :updated_at .
              (let [now (time/now)]
                ; insert! returns the inserted object, with the :_id set
                (mc/insert "users" 
                           (merge (prepare-for-save user)
                                  {:_id (ObjectId.)}
                                   (when-not (contains? user :created_at)
                                             {:created_at now})
                                   (when-not (contains? user :updated_at)
                                             {:updated_at now}))))
              (catch Exception e
                (println e)
                (vali/set-error :base :insert-error)
                nil)))))


(defn- update!
  "If the record resulting from the update is valid, it timestamps and saves
  the updated record. If there are no changes, nothing is saved.
  It's similar to ActiveRecord's update(id, attributes)
  http://api.rubyonrails.org/classes/ActiveRecord/Relation.html#method-i-update

  This can be used to change the values of things like username, which can be
  changed directly. It should not be used to change the value of email for
  example, because this change requires additional logic (an email has to be
  verified by the user before the change is effective).
  To avoid that this is accidentally used to change one of these fields
  requiring additional logic, this function is declared private (defn-) and a
  public wrapper is provided for the fields for which it can be used safely
  (ex. username can be changed safely, and change-username! is provided as a
  wrapper that simply calls this function).
  
  Only the $unset \"update operator\" is supported.
  http://docs.mongodb.org/manual/reference/operators/#update

  $unset allows to delete fields. To delete a field, specify :$unset as its
  new value. Example:
    (update! 23 {:password \"s3cret\" :password_reset_code :$unset})

  Notice that it's possible to delete fields in the same update as others are
  set. This doesn't seem to be possible in Mongoid for Ruby for example. It
  would be necessary to use the lower level Moped driver, but then validations
  and timestamping benefits would be lost, besides being more verbose
    http://mongoid.org/en/mongoid/docs/persistence.html

  Returns:
    - nil if validations fail
    - the updated object otherwise (see call to fetch-one at the end)

  Keyword params are NOT used for the optional hash param, so the way to call
  this is...
    (update! 23 {:username \"user1\"} {:skip-validations true})
  not...
    (update! 23 {:username \"user1\"} :skip-validations true)
  http://stackoverflow.com/questions/717963/clojure-keyword-arguments
  "
  [user-id attributes & [{:keys [skip-validations] 
                                       :or {skip-validations false}}]]
  (let [old-user (find-by-id user-id)
        ; http://stackoverflow.com/questions/2753874/how-to-filter-a-persistent-map-in-clojure
        ; http://clojuredocs.org/clojure_core/clojure.core/for
        ; http://docs.mongodb.org/manual/reference/operators/#_S_unset
        unset-map (select-keys attributes (for [[k v] attributes
                                                :when (= v :$unset)]
                                               k))
        ;
        old-user-without-deleted-fields
            (apply dissoc old-user (keys unset-map))
        attributes-without-deleted-ones
            (apply dissoc attributes (keys unset-map))
        updated-user 
            (merge old-user-without-deleted-fields
                   attributes-without-deleted-ones)]
        ;
        ; This should also work, but I like the code above better...
        ; merged-attrs (merge old-user attributes)
        ; updated-user (select-keys merged-attrs 
        ;                           (for [[k v] merged-attrs
        ;                                 :when (not= v :$unset)]
        ;                                k))

    (when (or skip-validations (valid? updated-user))
          ; CongoMongo's update! returns a WriteResult object from the
          ; underlying Java driver
          ; https://github.com/aboekhoff/congomongo/blob/master/src/somnium/congomongo.clj
          ; https://github.com/mongodb/mongo-java-driver/blob/master/src/main/com/mongodb/DBCollection.java
          ;
          ; Notice that if "write concern" were not set to :safe (see
          ; models.clj), MongoDB Java driver would never raise any exception.

          ; Updates and timestamps the record ONLY IF there are changes,
          ; like Rails
          ; https://github.com/rails/rails/blob/master/activerecord/lib/active_record/timestamp.rb
          ;
          ; prepare-for-save gets rid of any attributes that are not meant
          ; to be saved in the collection
          (let [prepared-user (prepare-for-save updated-user)]
            (if (= prepared-user old-user)
                
                old-user

                ; If :updated_at is specified and it's different than the
                ; old one, then that will be used instead of the current
                ; timestamp, like in Rails. The only problem, like in Rails,
                ; is that it's not possible to change the value of an
                ; attribute without changing the timestamp (unless it's done
                ; in two operations: first the attribute is updated, then a
                ; second update is used to replace the new timestamp with
                ; the old one).
                (let [updated-at
                         (if (and (contains? prepared-user :updated_at)
                                  (not= (:updated_at prepared-user)
                                        (:updated_at old-user)))
                             (:updated_at prepared-user)
                             (time/now))]
                  (try
                    ; The second argument is the "updated object or $
                    ; operators (e.g., $inc) which manipulate the object"
                    ; http://www.mongodb.org/display/DOCS/Updating#Updating-update%28%29
                    ; Notice that an "updated object" represents a whole
                    ; document, not only the fields that have to be modified.
                    ; This updated object will completely replace the old
                    ; object. To modify only some fields, the $ modifiers have
                    ; to be used.
                    (mc/update-by-id "users" user-id
                                {$set (assoc prepared-user
                                              :updated_at updated-at)
                                 $unset unset-map})
                    (find-by-id user-id)
                    (catch Exception e
                      (println e)
                      (vali/set-error :base :update-error)
                      nil))))))))


(defn delete!
  "Named after the equivalent Rails method
  http://api.rubyonrails.org/classes/ActiveRecord/Relation.html#method-i-delete

  The parameter may be a String or an ObjectId.

  Returns:
    - the number of records deleted"
  [user-id]
  ; CongoMongo's destroy! function (which uses the remove method of
  ; MongoDB's Java driver) is the most obvious way to delete a document but
  ; it doesn't allow to know if anything was deleted. Actually, it does, but
  ; only if the "write concern" is set to :safe, and by inspecting the
  ; WriteResult Java object that it returns.
  ;
  ; Because of this, the more general command function is used, as it allows
  ; to know the number of documents deleted independently of the
  ; "write concern", and it doesn't require messing with Java.
  ;
  ; If nothing was deleted, there's no :lastErrorObject, otherwise it contains
  ; an :n element with the number of documents deleted.
  ;   http://www.mongodb.org/display/DOCS/getLastError+Command
  ; The object-id function raises an exception when passed an ObjectId as a
  ; parameter. The code below works around this by first converting to a
  ; string. This allows the function to work for both String and ObjectId
  ; input params.
  ; CongoMongo provides a fetch-and-modify function that wraps MongoDB's
  ; findAndModify command, but I don't see the value of it, and I prefer to
  ; use the generic command function.
  (let [user-id (ObjectId (str user-id))]
    (mc/remove-by-id "users" user-id)))
;; I DON'T LIKE THET OLD ONE... ObjectId I need to be sure that the ID
;; really exist, if it does what else could go wrong ? I need to
;; assume tha mongo will do its job.


(defn activate!
  "An activation_code_created_at column is used to handle expirations. 

  Returns:
    - nil if activation fails (errors can be retrieved using noir.validation)
    - the user object if activation succeeds"
 [activation-code]
  (if-let [user (find-by-activation-code activation-code)]
    ; https://github.com/seancorfield/clj-time
    ; https://github.com/seancorfield/clj-time/blob/master/src/clj_time/core.clj
    (if (> (time/in-hours (time/interval (:activation_code_created_at user)
                                         (time/now)))
           1)
        (vali/set-error :activation_code :expired-activation-code)
        (update! (:_id user) 
                 {:activation_code :$unset
                  :activation_code_created_at :$unset}
                 {:skip-validations true}))
    (vali/set-error :activation_code :activation-code-not-found)))


(defn set-activation-code
  "It generates an activation code, stores it under the :activation_code key,
  and it also stores a timestamp of the operation in the
  :activation_code_created_at key"
  ; In the improbable case that the generated activation code is not unique,
  ; it will be detected by valid? or the database unique index on
  ; :activation_code. The user then will be asked to try it again.
  ; 
  ; As a reference, Devise (an auth solution for Rails), works around the
  ; uniqueness problem with a different strategy: it generates codes in a
  ; loop until a unique one is obtained. However, because that doesn't seem
  ; to be combined with an appropriate unique index, I would say it's subject
  ; to race conditions.
  ; http://stackoverflow.com/a/6128878/974795
  ; https://github.com/plataformatec/devise/blob/master/lib/generators/active_record/templates/migration.rb
  ;
  ; It takes ~1.26 ms in my MBP (2.26 GHz Intel Core 2 Duo)
  ; ~1.27 ms in Heroku
  [user]
  (merge user
         {:activation_code (hexadecimalize (generate-secure-token 20))
          :activation_code_created_at (time/now)}))


(defn reset-activation-code!
  "Returns:
    - the user object if the activation code was successfully reset
    - nil if activation code was not reset (errors can be retrieved using
      noir.validation)"
  [email]
  (if-let [user (find-by-email email)]
    (if (:activation_code user)
        (update! (:_id user) (set-activation-code user))
        ; set-error returns nil
        ; https://github.com/ibdknox/noir/blob/master/src/noir/validation.clj
        (vali/set-error :activation_code 
                        (str "User already active, please "
                             "<a href=\"/login\">log in</a>")))
    (vali/set-error :email "No user with this email")))


(defn login!
  "Returns:
    - the user object if login was successful
    - nil otherwise (client code can use Noir's validation API for error
      details)"
  [{:keys [username-or-email password]}]
  (let [{activation-code :activation_code
         stored-pass :crypted_password :as user}
                        (find-by-username-or-email username-or-email)]
    (if (nil? activation-code)
        (if (and stored-pass 
                 (crypt/compare password stored-pass))
            user
            (vali/set-error :password "Wrong username/email or password"))
        (vali/set-error :activation_code :not-yet-activated))))


;; Password resets
;; Reference:
;; https://github.com/weavejester/crypto-random/blob/master/src/crypto/random.clj
;; https://jira.atlassian.com/browse/CWD-1897?focusedCommentId=196759&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-196759

(defn generate-secure-token [size]
  ; http://clojuredocs.org/clojure_core/clojure.core/byte-array
  (let [seed (byte-array size)]
       ; http://docs.oracle.com/javase/6/docs/api/java/security/SecureRandom.html
       (.nextBytes (SecureRandom/getInstance "SHA1PRNG") seed)
       seed))

; About the parameter name (a-byte-array instead of byte-array) see
; Critiquing Clojure, All the good names are taken
; http://items.sjbach.com/567/critiquing-clojure#6
(defn hexadecimalize [a-byte-array]
  ; converts byte array to hex string
  ; http://stackoverflow.com/a/8015558/974795
  (string/lower-case (apply str (map #(format "%02X" %) a-byte-array))))

(defn set-password-reset-code!
  "Returns:
    - the generated password reset code
    - nil if anything goes wrong (errors can be retrieved using noir.validation)"
  [email]
  (if-let [user-id (:_id (find-by-email email))]
    (let [password-reset-code (hexadecimalize (generate-secure-token 20))]
      (and (update! user-id
                    {:password_reset_code password-reset-code
                     :password_reset_code_created_at (time/now)})
           password-reset-code))
    ; set-error returns nil, so set-password-reset-code! will return nil in
    ; this case
    ; https://github.com/ibdknox/noir/blob/master/src/noir/validation.clj
    (vali/set-error :email :email-not-found)))

; About the name of the function:
; An _existing_ code is validated by checking that it's in the database and
; has not expired. Compare this to the validation for a _new_ code, which
; checks that it's unique (see valid? function).
(defn validate-existing-password-reset-code 
  "Returns:
    - the user map associated to the specified code if all is ok
    - nil if the code was not found or it expired (error details can be
      obtained through the noir.validation API)"
  [reset-code]
  (if-let [user (find-by-password-reset-code reset-code)]
    ; https://github.com/seancorfield/clj-time
    ; https://github.com/seancorfield/clj-time/blob/master/src/clj_time/core.clj
    (if (> (time/in-hours
                    (time/interval (:password_reset_code_created_at user)
                                   (time/now)))
           1)
        (vali/set-error :password_reset_code :expired-password-reset-code)
        user)
    (vali/set-error :password_reset_code :password-reset-code-not-found)))

(defn change-password-with-reset-code!
  "This is called to change the password from the password reset form.

  Notice that a non-activated user can reset her password. That's not a
  security problem because if she has a password reset code, it means that
  she must have access to the email address to which it was sent. In a way,
  resetting a password also serves as an email verification and, in fact,
  in other authentication solutions (like
  https://github.com/plataformatec/devise ), when a password reset is done
  on a non-activated account, the account is automatically activated (i.e.
  its email address is confirmed).
  In our solution, activation is not automatic in this case, but on the
  other hand, when a non-activated user tries to log in, she will be
  provided with a link to get a new activation email.

  Returns:
    - nil if reset-code is not found in the database, or it's expired, or
      password is not valid, or there are any other errors
    - the updated user otherwise"
  [reset-code password]
  (when-let [user (validate-existing-password-reset-code reset-code)]
    ; Due to the unique sparse index defined on password_reset_code,
    ; MongoDB will not allow multiple records with the password_reset_code
    ; set not nil. That's why the field has to be deleted.
    (update! (:_id user)
             {:password_reset_code :$unset
              :password_reset_code_created_at :$unset 
              :password password})))

; Used to change the password of the logged in account
(defn change-password! [user-id password]
  (update! user-id {:password password}))

;
(defn change-username! [user-id new-username]
  (update! user-id {:username new-username}))

(defn request-email-change!
  "When the requested new-email is equal to the current email, this is still
  handled as a real change request.

  Returns:
    - nil if validations fail
    - the updated object otherwise"
  [user-id new-email]
  (let [user (find-by-id user-id)]
    ; note that in order to validate the requested new email without
    ; additional validation code, this is handled like if it were the real,
    ; current email.
    (when (valid? (merge user {:email new-email}))
          (update! user-id
                   {:new_requested_email new-email
                    :email_change_code (hexadecimalize
                                              (generate-secure-token 20))}
                   {:skip-validations true}))))

;
(defn cancel-email-change! [user-id]
  (update! user-id
           {:new_requested_email nil :email_change_code nil}
           {:skip-validations true}))

; Notice that to specify the email change that has to be made effective, the
; code (email-change-code) AND the user id have to be specified. (Because of
; the way the codes are generated, it's improbable that there are multiple
; active change requests with the same code, but it's still possible.)
;
; Unlike activation codes or password reset codes, email change codes do not
; expire. This doesn't pose a security problem because this code can only be
; used while being logged in as the user associated with the code. (Login is
; enforced by the client code of this function.)
(defn change-email! [user-id email-change-code]
  (if-let [{new-email :new_requested_email} 
           (mc/find-one-as-map "users"
                               {:_id user-id
                                :email_change_code email-change-code})]
    (update! user-id
             {:new_requested_email nil
              :email_change_code nil 
              :email new-email})
    ; set-error returns nil, see Noir code
    (vali/set-error :email_change_code :email-change-code-not-found)))

