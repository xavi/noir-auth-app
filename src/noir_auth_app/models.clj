;; Called from server.clj . Architecture inspired by the simpledb-based blog app
;; https://github.com/ibdknox/Noir-blog/tree/master/src/noir_blog
;; https://github.com/ibdknox/simpledb
;; (:require [simpledb.core :as db])

(ns noir-auth-app.models
  (:require [noir-auth-app.models.user :as user])
  (:use somnium.congomongo)
  (:use somnium.congomongo.coerce)
  (:use [somnium.congomongo.config :only [*mongo-config*]]))

; Fixes
;   can't serialize class org.joda.time.DateTime -
;                                 (class java.lang.IllegalArgumentException)
; See "custom type conversions" in
; https://github.com/aboekhoff/congomongo
;
; Datetimes are stored in MongoDB as BSON's UTC DateTimes
;   http://www.mongodb.org/display/DOCS/Dates
; which the Java driver converts to java.util.Date objects. These objects
; do not contain time zone information
;   http://docs.oracle.com/javase/6/docs/api/java/util/Date.html
; CongoMongo, in combination with the ConvertibleFromMongo extended protocol
; below, converts java.util.Date objects to org.joda.time.DateTime objects
;   #<DateTime 2012-08-07T22:04:53.558+02:00>
; These objects contain time zone information, as can be checked by trying
; this in the REPL
;   (.getZone (:created_at (somnium.congomongo/fetch-one :users)))
; getZone() is implemented by AbstractInstant, which is available through
; this class hierarchy
; org.joda.time.Date < BaseDateTime < AbstractDateTime < AbstractInstant
;   https://github.com/JodaOrg/joda-time/blob/master/src/main/java/org/joda/time/base/AbstractInstant.java
; When creating an org.joda.time.Date object, a time zone can be specified
;   https://github.com/JodaOrg/joda-time/blob/master/src/main/java/org/joda/time/DateTime.java
; Initially, no time zone was specified when these DateTime objects were
; created in the mongo->clojure function below, meaning that they were
; created with the default time zone, i.e. the server's time zone. They were
; created correctly, because the time in java.util.Date was interpreted as
; UTC, which it really was, and then changed appropriately according to the
; default time zone. However, this meant that by default these DateTimes were
; converted to strings for display using that time zone, ex. 
; 2012-06-16T21:30:17.001+02:00 , see toString in
;   https://github.com/JodaOrg/joda-time/blob/master/src/main/java/org/joda/time/base/AbstractInstant.java
; In order to get these DateTimes displayed in UTC by default, the UTC time
; zone is now specified when creating them.
;   http://joda-time.sourceforge.net/userguide.html#TimeZones
(extend-protocol ConvertibleFromMongo
  java.util.Date
  (mongo->clojure [^java.util.Date d keywordize]
      ;(new org.joda.time.DateTime d)))
      (new org.joda.time.DateTime d (org.joda.time.DateTimeZone/forID "UTC"))))
;
(extend-protocol ConvertibleToMongo
  org.joda.time.DateTime
  (clojure->mongo [^org.joda.time.DateTime dt] (.toDate dt)))


;; http://thecomputersarewinning.com/post/clojure-heroku-noir-mongo
(defn maybe-init
  "Checks if connection and collection exist, otherwise initialize."
  []
  ; If global connection doesn't exist yet
  ; https://github.com/aboekhoff/congomongo/blob/master/src/somnium/congomongo.clj
  (when-not (connection? *mongo-config*)
    ; Reads the environment variable containing the MongoDB connection URI.
    ; In Heroku this is set using
    ;   heroku config:add MONGODB_URI=mongodb://username:password@example.com/dbname
    ; When running the app locally, this can be set in a .env file, which will
    ; be automatically read when starting the app with Foreman (foreman start).
    (let [mongo-url (get (System/getenv) "MONGODB_URI")]

      ; Looking at the logs, I see that *mongo-config* persists through
      ; requests (at least when running in my dev box).
      ;
      ; *mongo-config* is set in the set-connection! function (called below)
      ; defined in
      ;   https://github.com/aboekhoff/congomongo/blob/master/src/somnium/congomongo.clj
      ; This function uses alter-var-root and thread-bound? to set
      ; *mongo-config*.
      ;   http://clojuredocs.org/clojure_core/clojure.core/thread-bound_q
      ;   http://clojuredocs.org/clojure_core/clojure.core/alter-var-root

      (set-connection! (make-connection mongo-url))

      ; https://github.com/aboekhoff/congomongo/blob/master/src/somnium/congomongo.clj
      ; http://api.mongodb.org/java/2.10.1/com/mongodb/WriteConcern.html
      (set-write-concern *mongo-config* :safe)

      (user/maybe-init))))

