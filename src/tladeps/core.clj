#!/usr/bin/env bb
(ns tladeps.core
  (:require
   [babashka.deps :as deps]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [clojure.pprint :as pprint]
   [clojure.edn :as edn])
  (:import
   (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Deps ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def default-deps
  ;; We use `tla-edn` dependency because TLC is yet not being constantly
  ;; pushed to Maven Central .
  '{pfeodrippe/tla-edn {:mvn/version "0.7.0-SNAPSHOT"}})

;; Open a PR here with your dependency so it stays registered with a shortcut.
(def default-module-deps
  '{io.github.pfeodrippe/tla-edn-module {:mvn/version "0.2.0-SNAPSHOT"
                                         :tladeps/override "TlaEdnModule.Overrides"
                                         :tladeps/shortcut "edn"}
    io.github.pfeodrippe/tla-http-client-module {:mvn/version "0.1.0-SNAPSHOT"
                                                 :tladeps/override "TlaHttpClientModule.Overrides"
                                                 :tladeps/shortcut "http-client"}})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;; CLI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def cli-options
  (let [shortcuts (->> (vals default-module-deps) (mapv :tladeps/shortcut) set)]
    [["" "--tladeps-dep DEP" "Dependency shortcut"
      :multi true
      :default #{}
      :validate [shortcuts (str "Available default modules are " shortcuts)]
      :update-fn conj]
     ["" "--tladeps-raw-deps DEPS" "Dependency map in EDN format, e.g '{io.github.pfeodrippe/tla-edn-module {:mvn/version \"0.2.0-SNAPSHOT\" :tladeps/override \"TlaEdnModule.Overrides\"}}'"]
     ["" "--tladeps-classpath" "Returns only the classpath. You have to add tladeps overrides manually if needed, but this may be more composable if you want to keep using `java -cp ...` command to call tla tools"]
     ["" "--tladeps-help"]]))

(defn java-command
  [{:keys [:args :deps]}]
  (let [overrides (->> deps vals (mapv :tladeps/override) (str/join File/pathSeparator))]
    (->> ["java"
          (if (seq overrides)
            (str "-Dtlc2.overrides.TLCOverrides=" overrides)
            "")
          "-cp {{classpath}}"
          (str/join " " args)]
         (str/join " "))))

(defn -main
  [& args]
  (let [{:keys [:options :errors :summary]} (cli/parse-opts args cli-options)
        _ (when (or (empty? args)
                    (:tladeps-help options))
            (println (str "Usage:\n" summary))
            (System/exit 0))
        ;; If all are unknown options, then we pass it to tla2tools.
        _ (when-not (->> errors
                         (every? #(str/starts-with? % "Unknown option")))
            (pprint/pprint errors)
            (System/exit 1))
        ;; Remove our options from args.
        args (->> (-> (str/join " " args)
                      (str/replace #"--tladeps-dep [\w-]+" "")
                      (str/replace #"--tladeps-raw-deps" "")
                      (str/replace #"--tladeps-classpath" "")
                      (str/replace #"\{(.*?)\{(.*?)\}\}" "") ; For inlined deps.
                      str/trim
                      (str/split #" ")))
        deps (merge default-deps
                    (->> default-module-deps
                         (filter (comp (:tladeps-dep options) :tladeps/shortcut val))
                         (into {})
                         seq)
                    (-> (:tladeps-raw-deps options) edn/read-string))
        result (deps/clojure (list "-Sdeps" {:deps deps}
                                   "-Scommand" (if (:tladeps-classpath options)
                                                 "echo {{classpath}}"
                                                 (java-command {:args args
                                                                :deps deps})))
                             {:err :string})]
    ;; Stream stdout.
    (with-open [rdr (io/reader (:out result))]
      (binding [*in* rdr]
        (loop []
          (when-let [line (read-line)]
            (println line)
            (recur)))))
    @result
    (when (-> @result :err seq)
      (-> @result :err print))
    (System/exit 0)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
