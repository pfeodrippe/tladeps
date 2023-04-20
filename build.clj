(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def lib 'tladeps/tladeps)
(def version (format "1.2.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn copy-dir [_]
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir}))

(defn copy [_]
  (clean nil)
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :ns-compile '[tladeps.main]
                  :class-dir class-dir})
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (copy-dir nil)
  #_(b/jar {:class-dir class-dir
            :jar-file jar-file}))
