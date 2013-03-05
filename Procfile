; Leiningen's code is completely isolated from project code. This means that
; two JVMs are necessary to complete any task that has to execute anything
; in the project: one for Leiningen itself, and a subprocess for the
; project.
; The higher-order trampoline task uses Leiningen to calculate the command
; needed to launch the project and then causes Leiningen's JVM to exit before
; launching the project's JVM.
; This is useful for long-running lein run processes (like web apps), as it
; saves memory.
; https://github.com/technomancy/leiningen/blob/stable/doc/TUTORIAL.md
; https://github.com/technomancy/leiningen/blob/stable/doc/FAQ.md
; https://github.com/technomancy/leiningen/wiki/Faster
web: lein trampoline run
