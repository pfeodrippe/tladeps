(ns rosca.main
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str])
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

(defn- klass->input-properties
  [klass]
  (->> (-> @aws-schema
           (get "resources")
           (get (klass->resource-type klass))
           (get "inputProperties"))
       (into (sorted-map))))

(comment

  (do
    (import '(org.reflections Reflections))
    (import '(org.reflections.scanners SubTypesScanner))

    (defonce reflections
      (Reflections. "com.pulumi" (into-array org.reflections.scanners.Scanner [])))

    (->> (-> @aws-schema (get "resources"))
         (mapv (fn [[resource-name _props]]
                 (let [klass (resource-name->klass reflections resource-name)
                       input-props (keys (klass->input-properties klass))]
                   [klass (->> input-props
                               (mapv (partial prop->attr klass)))])))
         (into (sorted-map-by (fn [k1 k2]
                                (compare (.getName k1) (.getName k2)))))))

  ())

(defn- build-on [instance ^Class klass props]
  (let [properties (klass->input-properties klass)]
    (reduce-kv (fn [_builder k v]
                 (let [values (cond
                                (sequential? v)
                                v

                                (map? v)
                                [(java.util.Map/ofEntries
                                  (into-array java.util.Map$Entry
                                              (update-keys v (comp str symbol))))]

                                :else
                                [v])
                       setter (class-method (class instance)
                                            (name k)
                                            (mapv {"object" java.util.Map
                                                   "string" String}
                                                  [(get-in properties [(name k) "type"])]))]
                   (.invoke setter instance (into-array Object values))))
               instance
               props)))

(defn- construct [klass & args]
  (.newInstance
   (.getConstructor klass (into-array java.lang.Class (map type args)))
   (object-array args)))

(defn- build-resource
  [^String id klass props]
  (let [args-klass (Class/forName (str (.getName klass) "Args"))
        builder (.invoke ^java.lang.reflect.Method (class-method args-klass "builder") nil nil)
        args (.build (build-on builder klass props))]
    (construct klass (str (symbol id)) args)))

(defn- adapt-prop
  [[k v]]
  (let [[k-ns k-name] ((juxt namespace name) k)
        [klass-simple-name attr] (str/split k-name #"_")]
    {:klass (-> (str/replace k-ns #"rosca." "com.pulumi.")
                (str "." klass-simple-name)
                Class/forName)
     :m {attr v}}))

(defn build-infra
  [resources]
  (->> resources
       (mapv (fn [[k props]]
               (let [adapted-props (mapv adapt-prop (dissoc props ::id))
                     {:keys [klass]} (first adapted-props)]
                 [k
                  {:attrs (->> adapted-props
                               (mapv :m)
                               (apply merge))
                   :klass klass
                   ::id (::id props k)}])))
       (mapv (fn [[k {:keys [klass attrs]
                      ::keys [id]}]]
               [k (build-resource id klass attrs)]))
       (into {})))

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

(comment

  (def bucket (:bucket
               (build-infra {:bucket
                             {:rosca.aws.s3/Bucket_acl "private"
                              :rosca.aws.s3/Bucket_tags {"Eita" "danado"}}})))

  @(resource-attrs bucket)

  (klass->input-properties (class bucket))

  ())
