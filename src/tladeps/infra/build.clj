(ns tladeps.infra.build
  (:require
   [clojure.tools.build.api :as b]))

(def class-dir
  "target/classes")

(defn copy-dir [_]
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir}))
