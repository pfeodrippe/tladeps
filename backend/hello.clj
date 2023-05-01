(ns hello
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [hato.client :as http]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [fipp.edn :as pp])
  (:import
   (java.nio.file Files)))

(gen-class
 :name tladeps.hello
 :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler
              #_org.crac.Resource]
 ;; For fast compilation, see https://github.com/viesti/clj-lambda-layered/commit/6ad218cb009f9eb662675fe3fe7586bcd6c17a88
 ;; and https://docs.aws.amazon.com/lambda/latest/dg/snapstart-runtime-hooks.html.
 #_ #_:post-init register-crac
 :main false
 :prefix "hello-")

#_(defn hello-register-crac
  [this]
  (.register (org.crac.Core/getGlobalContext) this))

#_(defn hello-beforeCheckpoint
  [this context]
  (.log (.getLogger context) "Before checkpoint LOGGERRR")
  (println "Before checkpoint")
  (json/write-str
   {:statusCode 200
    :body (json/write-str {:hi "TRRRR"})})
  (println "Before checkpoint done"))

#_(defn hello-afterRestore
  [this context]
    (println "After restore"))

(defn json-request
  [req]
  (-> (http/request req)
      (update :body json/read-str :key-fn keyword)))

(comment

  (->> (json-request {:url "https://clojars.org/search?q=tladeps&format=json"})
       :body
       :results
       (mapv (fn [{:keys [jar_name group_name]}]
               (:body (json-request {:url (format "https://clojars.org/api/artifacts/%s/%s"
                                                  group_name
                                                  jar_name)})))))

  ())

#_(def body
    {:jar-info {:jar_name "tladeps-http-client-module"
                :group_name "io.github.pfeodrippe"
                :version "0.14.0"
                :description nil
                :created "1682832097782"}})

(def body
  {:jars [{:jar-info
           {:jar_name "tladeps-http-client-module"
            :group_name "io.github.pfeodrippe"
            :version "0.14.0"
            :description nil
            :created "1682832097782"}}
          {:jar-info
           {:jar_name "tladeps-edn-module"
            :group_name "io.github.pfeodrippe"
            :version "0.5.0"
            :description nil
            :created "1682832097782"}}]})

(defn build-jar-url
  [{:keys [jar_name group_name version]}]
  (format "https://repo.clojars.org/%s/%s/%s/%s-%s.jar"
          (str/replace group_name #"\." "/")
          jar_name
          version
          jar_name version))

#_(build-jar-url (:jar-info body))

(defn fetch-jar-data
  [{:keys [jar-info]}]
  (let [{:keys [group_name jar_name version]} jar-info
        file (Files/createTempFile
              "jar" ".jar"
              (into-array java.nio.file.attribute.FileAttribute []))
        jar-name (str file)
        response (http/request {:url (build-jar-url jar-info)
                                :method :get
                                :as :stream})
        jar-file (with-open [is (:body response)
                             os (java.io.FileOutputStream. jar-name)]
                   (io/copy is os)
                   (java.util.jar.JarFile. (io/file jar-name)))]
    (try
      {(symbol (str group_name "/" jar_name))
       {:mvn/version version
        :tladeps/edn-namespaces
        (->> (enumeration-seq (.entries jar-file))
             (filter (comp #(re-matches #"tladeps/exports/.*edn" %) str))
             (mapcat #(edn/read-string (slurp (.getInputStream jar-file %))))
             distinct
             sort
             vec)}}
      (finally
        (Files/delete file)))))

(defn handle-body
  [{:keys [jars] :as body}]
  {:jar-data
   (pr-str
    (if jars
      (->> (:jars body)
           (mapv fetch-jar-data)
           (apply merge))
      (fetch-jar-data body)))})

#_(handle-body body)

;; DONE Return deps-like map
;; DONE Make Edn module export
;; DONE Can select multiple deps
;; TODO Do a PR for the TLA+ extension so we can integrate 3rd party tools
;;      Run arbitrary command

(defn hello-handleRequest
  #_[{:keys [name] :or {name "Blambda"} :as event} context]
  [_ is os context]
  (let [logger (.getLogger context)]
    (with-open [is is
                os os]
      (let [event (json/read-str (slurp is) :key-fn keyword)
            body  (some-> (:body event)
                          (json/read-str :key-fn keyword))
            response (if (and body (= (:httpMethod event) "POST"))
                       {:statusCode 200
                        :headers {"Access-Control-Allow-Origin" "*"}
                        :body (json/write-str (fetch-jar-data body))}
                       {:statusCode 200
                        :headers {"Access-Control-Allow-Origin" "*"}
                        :body (json/write-str {:hi "Hello!!"})})]
        (.log logger (with-out-str
                       (pp/pprint
                        {:msg "Invoked with event"
                         :data {:body body
                                :event event}})))
        (with-open [input (io/input-stream
                           (.getBytes
                            (json/write-str response)))]
          (io/copy input os))))))
