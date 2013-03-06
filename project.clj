; For an annotated example of all the options that may be set in a
; project.clj file see
; https://github.com/technomancy/leiningen/blob/master/sample.project.clj
(defproject noir-auth-app "0.3.0-SNAPSHOT"
  :description "A complete authentication web app based on Clojure/ClojureScript, Compojure, lib-noir, Enlive and MongoDB."
  :dependencies [[org.clojure/clojure "1.4.0"]
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
  :plugins [[lein-cljsbuild "0.3.0"]]
	:cljsbuild {
    :builds [{
        ; The path under which lein-cljsbuild will look for ClojureScript
        ; files to compile.
        :source-paths ["src"]
        ; The standard ClojureScript compiler options:
        ; (See the ClojureScript compiler documentation for details.)
        ;
        ; For jayq (see https://github.com/ibdknox/jayq )...
        ; If you're using advanced Clojurescript compilation
        ; you'll need to reference the jquery externs file. Add
        ; this to your compilation options:
        ; {
        ;   :optimizations :advanced
        ;   :externs ["externs/jquery.js"]
        ;   ...
        ; }
        ;
        ; More on :optimizations :whitespace and :externs in
        ; http://lukevanderhart.com/2011/09/30/using-javascript-and-clojurescript.html
        :compiler {
          ; The path to the JavaScript file that will be output.
          ; Defaults to "target/cljsbuild-main.js".
          :output-to "resources/public/js/cljs.js"

          ; TODO:
          ; Use multiple build configurations (:dev, :prod)?
          ; https://github.com/emezeske/lein-cljsbuild/tree/0.3.0#multiple-build-configurations
          ; ; development
          ; :optimizations :whitespace
          ; :pretty-print true

          ; production
          ; https://github.com/ibdknox/jayq#compiling
          :optimizations :advanced
          :pretty-print false
          :externs ["externs/jquery.js"]
        }}]}
  ; besides warning users of earlier versions of Leiningen, this also forces Heroku to use 2.x
  :min-lein-version "2.0.0"
  :main noir-auth-app.server)
