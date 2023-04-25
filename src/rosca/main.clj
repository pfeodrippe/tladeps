(ns rosca.main
  (:refer-clojure :exclude [ref])
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [integrant.core :as ig])
  (:import
   (com.pulumi.core.annotations ResourceType)
   (com.pulumi.test PulumiTest)))

(defn- class-method
  ([^Class klass ^String method-name]
   (.. klass (getDeclaredMethod method-name nil)))
  ([^Class klass ^String method-name arg-classes]
   (.. klass (getDeclaredMethod method-name (into-array Class arg-classes)))))

(defonce aws-schema
  (delay (json/read-str (slurp "/Users/paulo.feodrippe/Downloads/schema.json"))))

(defn- klass->resource-type
  [klass]
  (some-> (.getAnnotation klass ResourceType)
          .type))

(defn- prop->attr
  [klass prop]
  (keyword (-> (.getPackageName klass)
               (str/replace #"com.pulumi." "rosca."))
           (str (.getSimpleName klass)
                "_"
                prop)))

(defn- resource-name->klass
  [reflections resource-name]
  (->> (sort-by str (into [] (.. reflections (getTypesAnnotatedWith ResourceType))))
       (filter (comp #{resource-name} klass->resource-type))
       first))

(defn- $ref->json-schema
  [$ref]
  (->> (-> @aws-schema
           (get "types")
           (get (str/replace $ref #"#/types/" "")))
       (into (sorted-map))))

(comment

  ($ref->json-schema
   (-> (klass->input-properties com.pulumi.aws.lambda.Function)
       (get "role")
       ))

  (-> @aws-schema
      (get "types")
      count)

  ())

(defn- args-klass->$ref
  [args-klass]
  (let [package (-> (.getPackageName args-klass)
                    (str/replace #"com.pulumi." "")
                    (str/split #"\."))
        klass-simple-name (-> (.getSimpleName args-klass)
                              (str/replace #"Args" ""))]
    (when (= (last package) "inputs")
      (format "#/types/%s/%s:%s"
              (->> (drop-last package)
                   (str/join ":"))
              klass-simple-name
              klass-simple-name))))

(defn- $ref->arg-klass
  [$ref]
  (let [[package simple-names] (->> (str/split $ref #"/")
                                    (drop 2))]
    (-> (format "com.pulumi.%s.inputs.%s"
                (str/replace package #":" ".")
                (-> (str/split simple-names #":")
                    last
                    (str "Args")))
        Class/forName)))

(defn- klass->input-properties
  [klass]
  (let [res (->> (-> @aws-schema
                     (get "resources")
                     (get (klass->resource-type klass))
                     (get "inputProperties"))
                 (into (sorted-map)))]
    (if (seq res)
      res
      ;; It may be an input.
      (some-> (args-klass->$ref klass)
              $ref->json-schema
              (get "properties")))))

(comment

  ($ref->json-schema
   "#/types/aws:apigatewayv2/AuthorizerJwtConfiguration:AuthorizerJwtConfiguration")

  (->> (-> @aws-schema
           (get "types")
           keys)
       (filter #(str/includes? % "DomainNameState"))
       sort
       (take 100))

  ())

(comment

  ;; Reflections for interning namespaced keywords for autocompletion.
  (do
    (import '(org.reflections Reflections))
    (import '(org.reflections.scanners SubTypesScanner))

    (defonce reflections
      (Reflections. "com.pulumi" (into-array org.reflections.scanners.Scanner [])))

    (->> (-> @aws-schema (get "resources"))
         #_(take 3)
         (mapv (fn [[resource-name _props]]
                 (let [klass (resource-name->klass reflections resource-name)
                       input-props (keys (klass->input-properties klass))]
                   [klass (->> input-props
                               (mapv (partial prop->attr klass)))])))
         (into (sorted-map-by (fn [k1 k2]
                                (compare (.getName k1) (.getName k2))))))
    nil)

  ())

(defn- adapt-prop
  ([[k v]]
   (adapt-prop [k v] {}))
  ([[k v] {:keys [input]}]
   (let [[k-ns k-name] ((juxt namespace name) k)
         [klass-simple-name attr] (str/split k-name #"_")]
     {:klass (if input
               (-> (str/replace k-ns #"rosca." "com.pulumi.")
                   (str ".inputs." klass-simple-name "Args")
                   Class/forName)
               (-> (str/replace k-ns #"rosca." "com.pulumi.")
                   (str "." klass-simple-name)
                   Class/forName))
      :m {attr v}})))

(defn- find-klass-method
  [klass type prop]
  (->> (bean klass)
       :declaredMethods
       (filter (comp #{prop} #(.getName %)))
       (filter (fn [v]
                 (->> (.getParameters v)
                      (mapv #(.getType %))
                      (filter #(isa? type %))
                      seq)))
       first))

(declare build-infra*)

(defn- build-on [instance ^Class klass props]
  (let [properties (klass->input-properties klass)]
    (reduce-kv (fn [_builder k v]
                 (let [$ref (get-in properties [(name k) "$ref"])
                       pulumi? (str/starts-with? (.getName (class v)) "com.pulumi")
                       values (cond
                                pulumi?
                                [v]

                                $ref
                                (if (::id v)
                                  ;; It's a standalone resource.
                                  [(:_bogus (build-infra* {:_bogus v}))]
                                  ;; Otherwise it's an Args.
                                  (let [props (->> (mapv #(adapt-prop % {:input true}) v)
                                                   (mapv :m)
                                                   (apply merge))
                                        klass ($ref->arg-klass $ref)]
                                    [(-> (.invoke ^java.lang.reflect.Method (class-method klass "builder") nil nil)
                                         (build-on klass props)
                                         .build)]))

                                (sequential? v)
                                v

                                (map? v)
                                [(java.util.Map/ofEntries
                                  (into-array java.util.Map$Entry
                                              (update-keys v (comp str symbol))))]

                                :else
                                [v])
                       setter (find-klass-method
                               (class instance)
                               (cond
                                 pulumi?
                                 (class v)

                                 (str/starts-with? (.getName (class (first values))) "com.pulumi")
                                 (class (first values))

                                 $ref
                                 ($ref->arg-klass $ref)

                                 :else
                                 (-> (get-in properties [(name k) "type"])
                                     {"object" java.util.Map
                                      "string" String
                                      "boolean" Boolean}))
                               (name k))]
                   (when-not setter
                     (throw (ex-info "No klass method found!"
                                     {:values values
                                      :k k
                                      :v v})))
                   (.invoke setter instance (into-array Object values))))
               instance
               props)))

(defn- construct [klass & args]
  (.newInstance
   (.getConstructor klass (into-array java.lang.Class (map type args)))
   (object-array args)))

(defn- build-resource
  [^String id klass props]
  (println :BUILDING_RESOURCE id)
  (try
    (let [args-klass (Class/forName (str (.getName klass) "Args"))
          builder (.invoke ^java.lang.reflect.Method (class-method args-klass "builder") nil nil)
          args (.build (build-on builder klass props))]
      (construct klass (str (symbol id)) args))
    (finally
      (println :FINISHED_BUILDING_RESOURCE id))))

(defn- build-infra*
  [resources]
  (->> resources
       (mapv (fn [[k attrs]]
               (let [adapted-props (mapv adapt-prop (dissoc attrs ::id ::adapter))
                     {:keys [klass]} (first adapted-props)]
                 [k
                  {:props (->> adapted-props
                               (mapv :m)
                               (apply merge))
                   :klass klass
                   ::id (-> (::id attrs k)
                            symbol
                            str
                            (str/replace "/" "___")
                            (str/replace "." "__"))
                   ::adapter (::adapter attrs)}])))
       (mapv (fn [[k {:keys [klass props]
                      ::keys [id adapter]}]]
               [k ((or adapter identity) (build-resource id klass props))]))
       (into {})))

(defn build-infra
  [config]
  (ig/build config (keys config)
            (fn [k {::keys [handler] :as v
                    :or {handler identity}}]
              (-> (build-infra*
                   {k (-> v
                          (merge (handler (merge v (::deps v))))
                          (dissoc ::handler ::deps))})
                  (get k)))))

(defn resource-attrs
  "It fetches the resources attributes available from Pulumi, it returns a promise.

  It will wait until _all_ the props are available, see
  https://www.pulumi.com/docs/intro/concepts/inputs-outputs.

  If used, `resource-handler` is a function which receives the promise value when all attrs
  are available."
  ([resource]
   (resource-attrs resource identity))
  ([resource resource-handler]
   (let [klass (class resource)
         props (keys (klass->input-properties klass))
         prom (promise)]
     (if (empty? props)
       (deliver prom {::id (.getResourceName resource)})
       (let [*keeper (atom {})]
         ;; I've tried to use `Output/all`, but Pulumi was complaining about it.
         (add-watch *keeper ::keeper (fn [_key _reference _old-v new-v]
                                       (when (= (count new-v) (count props))
                                         ;; It means that all the props were collected.
                                         (deliver prom (merge (->> new-v
                                                                   (remove (comp nil? val))
                                                                   (into (sorted-map)))
                                                              {::id (.getResourceName resource)}))
                                         (resource-handler @prom))))
         (->> props
              (mapv #(let [attr (prop->attr klass %)]
                       (try
                         (some-> (.invoke (class-method klass %) resource nil)
                                 (.applyValue (reify java.util.function.Function
                                                (apply [_ v]
                                                  (swap! *keeper assoc
                                                         attr
                                                         (if (instance? java.util.Optional v)
                                                           (when (.isPresent v)
                                                             (.get v))
                                                           v))))))
                         (catch Exception _)))))
         prom)))))

(def ref ig/ref)

(comment

  (def bucket
    (-> {:bucket
         {:rosca.aws.s3/Bucket_acl "private"
          :rosca.aws.s3/Bucket_tags {"Eita" "danado"}
          :rosca.aws.s3/Bucket_versioning
          {:rosca.aws.s3/BucketVersioning_enabled true}}}
        build-infra*
        :bucket))

  @(resource-attrs bucket)

  (klass->input-properties (class bucket))

  ())
