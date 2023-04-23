(ns tladeps.infra
  (:gen-class)
  (:require
   [tladeps.infra.handler :as infra.handler])
  (:import
   (com.pulumi Pulumi)))

(defn -main
  [& _args]
  (Pulumi/run (infra.handler/make-consumer)))
