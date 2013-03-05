(ns noir-auth-app.views.home
  (:use [compojure.core :only (defroutes GET)])
  (:require [noir-auth-app.views.common :as common]
            [noir-auth-app.i18n :as i18n]))

(defroutes home-routes
  (GET "/" []
    ; (layout) will display the content of the flash as a notice
    (common/layout {:title (i18n/translate :home-page-title)
                    :nav (common/navigation-menu)
                    :content (if-let [user (common/current-user)]
                                (i18n/translate :hello-user user)
                                (i18n/translate :welcome))})))
