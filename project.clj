; For an annotated example of all the options that may be set in a
; project.clj file see
; https://github.com/technomancy/leiningen/blob/master/sample.project.clj
(defproject noir-auth-app "0.3.0-SNAPSHOT"
  :description "A complete authentication web app based on Clojure/ClojureScript, Compojure, lib-noir, Enlive and MongoDB."
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-jetty-adapter "1.1.8"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.2"]
                 [enlive/enlive "1.1.1"]
                 [lib-noir "0.4.8"]
                 [congomongo "0.4.0"]
                 [com.draines/postal "1.9.2"]
                 [clj-time "0.4.4"]
                 [jayq "2.3.0"]
                 [shoreleave/shoreleave-remote-ring "0.3.0"]
                 [shoreleave/shoreleave-remote "0.3.0"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
   ;; Load these namespaces from within Leiningen to pick up hooks from them.
   ;; See "Hooking Into Default Leiningen Tasks" in p. 83 of
   ;; Sierra & VanderHart's "ClojureScript: Up and Running"
   ;; https://github.com/emezeske/lein-cljsbuild#hooks
  :hooks [leiningen.cljsbuild]
	:cljsbuild {
    :builds {
        ; :source-paths are the paths under which lein-cljsbuild will look
        ; for ClojureScript files to compile.
        ; More on :optimizations :whitespace and :externs in
        ; http://lukevanderhart.com/2011/09/30/using-javascript-and-clojurescript.html
        ; https://github.com/ibdknox/jayq#compiling
        ; Notice that the build configuration IDs (:dev, :prod), do not have
        ; to do with Leiningen profiles
        ; https://github.com/emezeske/lein-cljsbuild#multiple-build-configurations
        ; https://github.com/emezeske/lein-cljsbuild/blob/0.3.0/example-projects/advanced/project.clj
        :dev
        {:source-paths ["src-cljs"]
         :compiler {
            ; The path to the JavaScript file that will be output.
            ; Defaults to "target/cljsbuild-main.js".
            :output-to "resources/public/js/cljs-debug.js"
            :optimizations :whitespace
            :pretty-print true }}
        :prod
        {:source-paths ["src-cljs"]
         :compiler {
            :output-to "resources/public/js/cljs.js"
            :optimizations :advanced
            :pretty-print false
            :externs ["externs/jquery.js"]}}}}
  ; besides warning users of earlier versions of Leiningen, this also forces Heroku to use 2.x
  :min-lein-version "2.0.0"
  :main noir-auth-app.server
  :source-paths ["src"]
  ; Deployment to Heroku may take more than 7 minutes. It seems that
  ; approximately half of this time is consumed compiling ClojureScript with
  ; advanced optimizations.
  ;
  ; Date, Total Time, ClojureScript Advanced Optimizations Time, ClojureScript Whitespace Optimizations Time
  ; 2013-07-15 17:40, 7 min 7 s, 233 s (3 min 53 s)
  ; 2013-07-15 17:55, 6 min 3 s, 181 s (3 min 1 s), 13 s
  ;
  ; In contrast, it only takes 1 min 15 s to generate the jar with
  ; "lein uberjar" in my MBP (2.26 GHz Intel Core 2 Duo)
  ;
  ; Date, Total Time, ClojureScript Advanced Optimizations Time, ClojureScript Whitespace Optimizations Time
  ; 2013-07-15 17:50, 1 min 15 s, 25 s
  ; 2013-07-15 18:07, 1 min 25 s, 26 s, 16 s
  ;
  ; https://devcenter.heroku.com/articles/clojure
  ; https://github.com/heroku/heroku-buildpack-clojure#uberjar
  ; https://github.com/heroku/heroku-buildpack-clojure/issues/22#issuecomment-19766340
  :uberjar-name "noir-auth-app-standalone.jar")
