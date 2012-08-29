(ns noir-auth-app.utilities)

; What's the convention for using an asterisk at the end of a function name in
; Clojure and other Lisp dialects?
; http://stackoverflow.com/q/5082850/974795
(defmacro str*
  "Concatenate strings at compile time (they have to be compile-time
  constants, of course)"
  ; http://stackoverflow.com/a/10445141/974795
  [& ss]
  (apply str (map eval ss)))

