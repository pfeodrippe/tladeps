(ns tladeps.infra.handler
  (:require
   [clojure.java.io :as io]
   [rosca.aws.s3 :as-alias s3]
   [rosca.aws.lambda :as-alias lambda]
   [rosca.aws.iam :as-alias iam]
   [rosca.aws.apigatewayv2 :as-alias api]
   [rosca.main :as ro])
  (:import
   (com.pulumi.core Output)
   (com.pulumi.core.internal Internal)
   (com.pulumi.core.annotations ResourceType)
   (com.pulumi.test Mocks Mocks$ResourceResult PulumiTest)
   (java.util.function Consumer)))

(defn run-test
  [mocks handler]
  (PulumiTest/cleanup)
  (-> (PulumiTest/withMocks mocks)
      (.runTest
       (reify Consumer
         (accept [_ ctx]
           (handler ctx))))))

;; Check https://github.com/pulumi/examples/blob/master/aws-py-apigateway-lambda-serverless/__main__.py for
;; the example.
(defn infra-map
  []
  {::tladeps-bucket
   {::ro/id :tladeps-dbucket
    ::s3/Bucket_acl "private"
    ::s3/Bucket_tags {:Eita "danado"
                      "Ss" "asda"}
    ::s3/Bucket_versioning {::s3/BucketVersioning_enabled true}}

   ::proxy
   {::lambda/Function_runtime "python3.7"
    ::lambda/Function_handler "hello.handler"
    ::lambda/Function_code (com.pulumi.asset.AssetArchive. {"." (com.pulumi.asset.FileArchive. "./hello_lambda")})
    ::lambda/Function_role {::ro/id :lambda-role
                            ::ro/adapter #(.arn %)
                            ;; TODO: How to slurp this resource with the maven plugin?
                            ::iam/Role_assumeRolePolicy (str "{\n  \"Version\": \"2012-10-17\",\n  \"Statement\": [\n    {\n      \"Action\": \"sts:AssumeRole\",\n      \"Principal\": {\n        \"Service\": \"lambda.amazonaws.com\"\n      },\n      \"Effect\": \"Allow\",\n      \"Sid\": \"\"\n    }\n  ]\n}\n")
                            #_(slurp (io/resource "/tladeps/infra/lambda_role_policy.json"))}}

   ::http-endpoint
   {::api/Api_protocolType "HTTP"}

   ::lambda-backend
   {::api/Integration_integrationType "AWS_PROXY"
    ::api/Integration_integrationMethod "ANY"
    ::ro/deps {::http-endpoint (ro/ref ::http-endpoint)
               ::proxy (ro/ref ::proxy)}
    ::ro/handler (fn [{::keys [http-endpoint proxy]}]
                   {::api/Integration_apiId (.id http-endpoint)
                    ::api/Integration_integrationUri (.arn proxy)})}

   ::http-route
   {::api/Route_routeKey "ANY /{proxy+}"
    ::ro/deps {::http-endpoint (ro/ref ::http-endpoint)
               ::lambda-backend (ro/ref ::lambda-backend)}
    ::ro/handler (fn [{::keys [http-endpoint lambda-backend]}]
                   {::api/Route_apiId (.id http-endpoint)
                    ::api/Route_target (-> lambda-backend .id
                                           (ro/apply-value #(str "integrations/" %)))})}

   ::http-stage
   {::api/Stage_autoDeploy true
    ::ro/deps {::http-endpoint (ro/ref ::http-endpoint)
               ::http-route (ro/ref ::http-route)}
    ::ro/handler (fn [{::keys [http-endpoint http-route]}]
                   {::api/Stage_apiId (.routeKey http-route)
                    #_ #_::api/Stage_routeSettings ::api/Stage_description})}})

;; TODO Input autocompletion
;; TODO Simplify simple attr ref
;; TODO Make deps a set
;; TODO We could have a macro to help to figure out th dependencies that can replace
;;      ::ro/handler and ::ro/deps at the same time
;; TODO Create abstraction so we don't need to use the `.` java operator

(comment

  (declare infra-handler)

  (def my-mocks
    (reify Mocks
      (newResourceAsync [_ args]
        (java.util.concurrent.CompletableFuture/completedFuture
         (Mocks$ResourceResult/of
          (java.util.Optional/of (str (.name args) "-id"))
          (.inputs args))))))

  (->> (.. (run-test my-mocks infra-handler)
           resources)
       (mapv (comp deref ro/resource-attrs)))

  ())

(defn infra-handler
  [ctx]
  (let [{::keys [tladeps-bucket]} (ro/build-infra (infra-map))]
    (.. ctx (export "bucket-name" (.bucket tladeps-bucket)))))

(defn make-consumer
  []
  (reify Consumer
    (accept [_ ctx]
      (infra-handler ctx))))
