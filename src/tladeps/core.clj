(ns tladeps.core
  (:require
   [babashka.deps :as deps]))

(def deps
  '{:deps
    {io.github.pfeodrippe/tla-edn-module {:mvn/version "0.2.0-SNAPSHOT"}}})

#_(-> @(deps/clojure (list "-Sdeps" deps
                         "-Scommand" "java -Dtlc2.overrides.TLCOverrides=TlaEdnModule.Overrides -cp {{classpath}} tlc2.TLC Abc.tla -config Abc.tla")
                   {:out :string
                    :err :string})
    :out)
