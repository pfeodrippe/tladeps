(ns tladeps.main
  (:gen-class)
  (:require
   [tladeps.infra])
  (:import
   (com.pulumi Pulumi)))

(defn -main
  [& _args]
  (Pulumi/run (tladeps.infra/make-consumer)))
