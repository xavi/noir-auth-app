(ns noir-auth-app.config)

(def app-name "noir-auth-app")

(def emails-from (get (System/getenv) "EMAILS_FROM"))
(def contact-email (get (System/getenv) "CONTACT_EMAIL"))
