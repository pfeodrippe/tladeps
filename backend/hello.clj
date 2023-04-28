(ns hello
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.data.json :as json]))

(gen-class
 :name tladeps.hello
 :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler]
 :main false
 :prefix "hello-")

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
