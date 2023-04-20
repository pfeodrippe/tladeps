(ns tladeps.infra
  (:import
   (com.pulumi.core Output)
   (com.pulumi.aws.s3 Bucket)))

(defn infra-handler
  [ctx]
  #_(.. ctx log (info (str ctx)))
  (bean Bucket)
  (let [bucket (Bucket. "tladeps-mybucksetggd")]
    (.. ctx (export "bucket-name" (.bucket bucket)))))

(try

  (when-not (System/getenv "PULUMI_MONITOR")
    (eval '(build/copy-dir nil)))

  (catch Exception _))
