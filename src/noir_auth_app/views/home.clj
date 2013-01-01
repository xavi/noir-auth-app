(ns noir-auth-app.views.home
  (use [noir.core])
  (:require [net.cgrand.enlive-html :as h]
            [noir-auth-app.views.common :as common]
            [noir-auth-app.i18n :as i18n]))

(defpage "/" []
  ; (layout) will display the content of the flash as a notice
  (common/layout {:title (i18n/translate :home-page-title)
                  :nav (common/navigation-menu)
                  :content (if-let [user (common/current-user)]
                              (i18n/translate :hello-user user)
                              (i18n/translate :welcome))}))
