(ns tladeps.infra.handler
  (:require
   [rosca.aws.s3 :as-alias s3]
   [rosca.main :as ro])
  (:import
   (com.pulumi.core Output)
   (com.pulumi.core.internal Internal)
   (com.pulumi.core.annotations ResourceType)
   (com.pulumi.test Mocks Mocks$ResourceResult PulumiTest)
   (java.util.function Consumer)))

(defn infra-map
  []
  {:tladeps-bucket
   {::ro/id :tladeps-dbucket
    ::s3/Bucket.acl "private"
    ::s3/Bucket.tags {:Eita "danado"
                      "Ss" "asda"}
    #_ #_::s3/Bucket.website "sss"}})

(defn infra-handler
  [ctx]
  #_(.. ctx log (info (str ctx)))
  (let [{:keys [tladeps-bucket]} (ro/build-infra (infra-map))]
    #_(ro/resource-attrs tladeps-bucket
                         (fn [v]
                           (spit "fff.edn"
                                 (with-out-str
                                   (clojure.pprint/pprint v)))))
    (.. ctx (export "bucket-name"
                    (.bucket tladeps-bucket)))))

(defn make-consumer
  []
  (reify Consumer
    (accept [_ ctx]
      (infra-handler ctx))))

(defn run-test
  [mocks handler]
  (PulumiTest/cleanup)
  (-> (PulumiTest/withMocks mocks)
      (.runTest
       (reify Consumer
         (accept [_ ctx]
           (handler ctx))))))

(comment

  (def my-mocks
    (reify Mocks
      (newResourceAsync [_ args]
        (java.util.concurrent.CompletableFuture/completedFuture
         (Mocks$ResourceResult/of
          (java.util.Optional/of (str (.name args) "-id"))
          (.inputs args))))))

  (->> (.. (run-test my-mocks infra-handler)
           resources)
       (mapv (comp deref ro/resource-attrs)))

  ())
