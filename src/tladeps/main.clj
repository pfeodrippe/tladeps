(ns tladeps.main
  (:gen-class)
  (:require
   [clojure.java.data.builder :as builder]
   [tladeps.infra])
  (:import
   (com.pulumi Pulumi)
   (com.pulumi.core Output)
   (java.util.function Consumer)))

(defn -main
  [& _args]
  (Pulumi/run
    (reify Consumer
      (accept [_ ctx]
        (tladeps.infra/infra-handler ctx)))))

(comment

  (builder/to-java
   DomainArgs {:name "ss"
               :ipAddress "192.168.10.10"}
   {:builder-fn "builder"})

  (builder/to-java
   java.util.Locale {:language "fr" :region "EG"})

  (-> (DomainArgs/builder)
      (.name "ss")
      (.ipAddress "fff"))

  (clojure.java.data/*to-java-object-missing-setter* DomainArgs)

  ())
