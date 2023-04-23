(ns rosca.main
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str])
  (:import
   (com.pulumi.core.annotations ResourceType)))

(defn- class-method
  ([^Class klass ^String method-name]
   (.. klass (getDeclaredMethod method-name nil)))
  ([^Class klass ^String method-name arg-classes]
   (.. klass (getDeclaredMethod method-name (into-array Class arg-classes)))))

(defn- klass->resource-type
  [klass]
  (-> (.getAnnotation klass ResourceType)
      .type))

(defn- build-on [raw-schema instance ^Class klass props]
  (let [properties (->> (-> raw-schema
                            (get "resources")
                            (get (klass->resource-type klass))
                            (get "properties"))
                        (into (sorted-map)))]
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
               props)))

(defn construct [klass & args]
  (.newInstance
   (.getConstructor klass (into-array java.lang.Class (map type args)))
   (object-array args)))

(defn build-resource
  [^String id klass raw-schema props]
  (let [args-klass (Class/forName (str (.getName klass) "Args"))
        builder (.invoke ^java.lang.reflect.Method (class-method args-klass "builder") nil nil)
        args (.build (build-on raw-schema builder klass props))]
    (construct klass (str (symbol id)) args)))

(def resources
  {:bucket
   {:rosca.aws.s3/Bucket.acl "private"
    :rosca.aws.s3/Bucket.tags {"Eita" "danado"}}})

(defn adapt-prop
  [[k v]]
  (let [[k-name k-ns] ((juxt namespace name) k)
        [klass-simple-name attr] (str/split k-ns #"\.")]
    {:klass (-> (str/replace k-name #"rosca." "com.pulumi.")
                (str "." klass-simple-name)
                Class/forName)
     :m {attr v}}))

(defonce aws-schema
  (delay (json/read-str (slurp "/Users/paulo.feodrippe/Downloads/schema.json"))))

(defn build-system
  [resources]
  (->> resources
       (mapv (fn [[id props]]
               (let [adapted-props (mapv adapt-prop props)
                     {:keys [klass]} (first adapted-props)]
                 [id {:attrs (->> adapted-props
                                  (mapv :m)
                                  (apply merge))
                      :klass klass}])))
       (mapv (fn [[id {:keys [klass attrs]}]]
               [id (build-resource id klass @aws-schema attrs)]))
       (into {})))

(comment

  ())
