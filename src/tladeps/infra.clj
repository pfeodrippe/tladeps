(ns tladeps.infra
  (:require
   [clojure.java.data :as j]
   [clojure.java.data.builder :as builder]
   [jsonista.core :as json]
   [clojure.string :as str])
  (:import
   (com.pulumi.core Output)
   (com.pulumi.core.annotations ResourceType)
   (com.pulumi.aws.s3 Bucket BucketArgs BucketArgs$Builder)
   (com.pulumi.test Mocks Mocks$ResourceResult PulumiTest)
   (java.util.function Consumer)))

(defn infra-handler
  [ctx]
  #_(.. ctx log (info (str ctx)))
  (let [bucket (Bucket. "tladeps-mybssucksetggd")]
    (.. ctx (export "bucket-name" (.bucket bucket)))))

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
       (mapv bean))

  (def aws-schema
    (json/read-value (slurp "/Users/paulo.feodrippe/Downloads/schema.json")
                     #_(json/object-mapper {:decode-key-fn true})))

  (->> (-> aws-schema
           (get "resources")
           (get "aws:s3/bucket:Bucket")
           (get "properties"))
       (into (sorted-map)))

  (->> (-> aws-schema
           (get "resources")
           keys
           #_(get "aws:s3/BucketCorsRule:BucketCorsRule")
           #_keys)
       sort)

  (do (defn- class-method
        ([^Class klass ^String method-name]
         (.. klass (getDeclaredMethod method-name nil)))
        ([^Class klass ^String method-name arg-classes]
         (.. klass (getDeclaredMethod method-name (into-array Class arg-classes)))))

      (def klass Bucket)

      (defn klass->resource-type
        [klass]
        (-> (.getAnnotation klass ResourceType)
            .type))

      (defn- build-on [instance ^Class klass props]
        (let [properties (->> (-> aws-schema
                                  (get "resources")
                                  (get (klass->resource-type klass))
                                  (get "properties"))
                              (into (sorted-map)))]
          (def properties properties)
          (reduce-kv (fn [_builder k v]
                       (let [values (cond
                                      (sequential? v)
                                      v

                                      (map? v)
                                      [(java.util.Map/ofEntries
                                        (into-array java.util.Map$Entry v))]

                                      :else
                                      [v])
                             setter (class-method (class instance)
                                                  (name k)
                                                  (mapv {"object" java.util.Map
                                                         "string" String}
                                                        [(get-in properties [(name k) "type"])]))]
                         (.invoke setter instance (into-array Object values))))
                     instance
                     props))))

  (let [klass Bucket
        props {:acl "private"
               :tags {"Eita" "danado"}}
        builder (.invoke ^java.lang.reflect.Method (class-method BucketArgs "builder") nil nil)]
    (def builder builder)
    (build-on builder klass props)
    #_(.invoke (class-method (class builder) "tags" [java.util.Map$Entry])
               (-> (build-on builder klass props)
                   #_(.tags (java.util.Map/ofEntries
                             #_(into-array java.util.Map$Entry [(java.util.Map/entry "xxx" "ggg")])
                             (into-array java.util.Map$Entry {"xxx" "ggg"})))))
    #_(-> (build-on builder klass props)
          .build
          .acl
          .get
          .getValueOptional
          deref
          .get))

  (bean BucketArgs$Builder)

  ())

(try

  (when-not (System/getenv "PULUMI_MONITOR")
    (eval '(build/copy-dir nil)))

  (catch Exception _))
