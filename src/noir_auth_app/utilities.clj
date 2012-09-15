(ns noir-auth-app.utilities
  (:require [clojure.walk :as walk]))

; What's the convention for using an asterisk at the end of a function name in
; Clojure and other Lisp dialects?
; http://stackoverflow.com/q/5082850/974795
(defmacro str*
  "Concatenate strings at compile time (they have to be compile-time
  constants, of course)"
  ; http://stackoverflow.com/a/10445141/974795
  [& ss]
  (apply str (map eval ss)))


(defn replace-symbol [form old-sym-name new-sym-name]
  (walk/postwalk #(if (and (symbol? %) (= (name %) (name old-sym-name)))
                      (symbol new-sym-name)
                      %)
                 form))

(defn compile-template
  "Returns an optimized expression implementing the template specified in the
  first parameter (a sequence), replacing the \"%\" in the interpolation
  expressions with the symbol name specified in the second parameter.

  (compile-template '(\"hello \" (:name %) \"!\") 'm)
  =>
  (str \"hello \" (:name m) \"!\")"
  [ss sym-name]
  (let [ss (->> ss
                ; the (not= ...) allows the interpolation expression to be
                ; simply % instead of something like (:name %)
                (map #(if (and (symbol? %) (not= (name %) "%")) (eval %) %))
                ; For all elements that are not strings, replaces any % with
                ; the sym-name specified as a parameter. This is aimed at
                ; things like (:name %) .
                (map #(if (string? %)
                          %
                          (replace-symbol % "%" sym-name)))
                ; http://clojuredocs.org/clojure_core/clojure.core/partition-by
                (partition-by string?)
                ; Concatenates the strings on each sublist:
                ; (("hello " "Clojurian ") ((:name %) (:surname %)))
                ; =>
                ; (("hello Clojurian ") ((:name %) (:surname %)))
                (map #(if (string? (first %)) (list (apply str %)) %))
                ; (("hello Clojurian ") ((:name %) (:surname %)))
                ; =>
                ; ("hello Clojurian " (:name %) (:surname %))
                ; http://clojuredocs.org/clojure_core/clojure.core/flatten
                ; http://stackoverflow.com/questions/5232350/clojure-semi-flattening-a-nested-sequence
                (apply concat))]
    (if (= (count ss) 1) (first ss) (cons 'str ss))))

(defmacro renderer-fn
  "(def app-name \"Demo App\")
  (def f (renderer-fn \"Hello \" (:name %) \", \"
                      \"welcome to \" app-name \"! \"
                      \"You can also break long lines for code readability \"
                      \"without sacrificing performance.\"))

  f is now something like (use macroexpand on the renderer-fn macro to see it):

  (fn* ([& p__102] 
    (clojure.core/let [[m101] p__102]
      (str \"Hello \" (:name m101)
           \", welcome to Demo App! You can also break long lines for readability without sacrificing performance.\"))))

  and it can be used like this:

  (f {:name \"Xavi\"})
  =>
  \"Hello Xavi, welcome to Demo App! You can also break long lines for readability without sacrificing performance.\"

  and it's probably the fastest templating that you can get in Clojure

  (time (f {:name \"Xavi\"}))
  => \"Elapsed time: 0.068 msecs\"
  in my MBP (2.26 GHz Intel Core 2 Duo)
  "
  [& ss]
  (let [m (gensym "m")]
    `(fn [& [~m]] ~(compile-template ss (name m)))))

