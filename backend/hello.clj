(ns hello
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.data.json :as json]))

(gen-class
 :name tladeps.hello
 :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler
              org.crac.Resource]
 ;; For fast compilation, see https://github.com/viesti/clj-lambda-layered/commit/6ad218cb009f9eb662675fe3fe7586bcd6c17a88
 ;; and https://docs.aws.amazon.com/lambda/latest/dg/snapstart-runtime-hooks.html.
 :post-init register-crac
 :main false
 :prefix "hello-")

(defn hello-register-crac
  [this]
  (.register (org.crac.Core/getGlobalContext) this))

(defn hello-beforeCheckpoint
  [this context]
  (.log (.getLogger context) "Before checkpoint LOGGERRR")
  (println "Before checkpoint")
  (json/write-str
   {:statusCode 200
    :body (json/write-str {:hi "TRRRR"})})
  (println "Before checkpoint done"))

(defn hello-afterRestore
  [this context]
  (println "After restore"))

(defn hello-handleRequest
  #_[{:keys [name] :or {name "Blambda"} :as event} context]
  [_ is os context]
  (let [logger (.getLogger context)]
    (with-open [is is
                os os]
      (.log logger (slurp is))
      (.log logger (pr-str {:msg "Invoked with event",
                            :data {:event "ssss.."}}))
      (with-open [input (io/input-stream (.getBytes
                                          (json/write-str
                                           {:statusCode 200
                                            :body (json/write-str {:hi "TRRRR"})})))]
        (io/copy input os)))))
