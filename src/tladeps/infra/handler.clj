(ns tladeps.infra.handler
  (:require
   [clojure.pprint :as pp]
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

;; Check https://github.com/pulumi/examples/blob/master/aws-py-apigateway-lambda-serverless/__main__.py
;; for the example.
(defn infra-map
  []
  {::tladeps-bucket
   {::ro/id :tladeps-dbucket
    ::s3/Bucket_acl "private"
    ::s3/Bucket_tags {:Eita "danado"
                      "Ss" "asda"}
    ::s3/Bucket_versioning {::s3/BucketVersioning_enabled true}}

   ::lambda-role-policy
   {::iam/RolePolicy_policy "{\n  \"Version\": \"2012-10-17\",\n  \"Statement\": [{\n    \"Effect\": \"Allow\",\n    \"Action\": [\n      \"logs:CreateLogGroup\",\n      \"logs:CreateLogStream\",\n      \"logs:PutLogEvents\"\n    ],\n    \"Resource\": \"arn:aws:logs:*:*:*\"\n  }]\n}\n"
    ::ro/deps {::lambda-role (ro/ref ::lambda-role)}
    ::ro/handler (fn [{::keys [lambda-role]}]
                   {::iam/RolePolicy_role (.id lambda-role)})}

   ::lambda-role
   { ;; TODO: How to slurp this resource with the maven plugin?
    ::iam/Role_assumeRolePolicy (str "{\n  \"Version\": \"2012-10-17\",\n  \"Statement\": [\n    {\n      \"Action\": \"sts:AssumeRole\",\n      \"Principal\": {\n        \"Service\": [\"lambda.amazonaws.com\", \"apigateway.amazonaws.com\"]\n      },\n      \"Effect\": \"Allow\",\n      \"Sid\": \"\"\n    }\n  ]\n}\n")
    #_(slurp (io/resource "/tladeps/infra/lambda_role_policy.json"))}

   ::proxy
   {::lambda/Function_runtime "java11"
    ::lambda/Function_handler "tladeps.hello"
    ::lambda/Function_name "my-lambda"
    ::lambda/Function_code (com.pulumi.asset.AssetArchive.
                            {"." (com.pulumi.asset.FileArchive.
                                  "./target/tladeps-1.0.0.jar")})
    ::lambda/Function_timeout 10
    ::lambda/Function_memorySize 512
    #_ #_::lambda/Function_snapStart {::lambda/FunctionSnapStart_applyOn "PublishedVersions"}
    #_ #_::lambda/Function_publish true
    ::ro/deps {::lambda-role (ro/ref ::lambda-role)}
    ::ro/handler (fn [{::keys [lambda-role]}]
                   {::lambda/Function_role (.arn lambda-role)})}

   #_ #_::proxy-alias
   {::ro/deps {::proxy (ro/ref ::proxy)}
    ::ro/handler (fn [{::keys [proxy]}]
                   {::lambda/Alias_functionName (.arn proxy)
                    ::lambda/Alias_functionVersion "1"})}

   ;; Babashka
   #_{::lambda/Function_runtime "provided.al2"
      ::lambda/Function_handler "hello/handler"
      ::lambda/Function_code (com.pulumi.asset.AssetArchive.
                              {"." (com.pulumi.asset.FileArchive.
                                    "./target/hello-blambda.zip")})
      ::lambda/Function_architectures ["x86_64"]
      ::lambda/Function_memorySize 512
      ::lambda/Function_role {::ro/id :lambda-role
                              ::ro/adapter #(.arn %)
                              ;; TODO: How to slurp this resource with the maven plugin?
                              ::iam/Role_assumeRolePolicy (str "{\n  \"Version\": \"2012-10-17\",\n  \"Statement\": [\n    {\n      \"Action\": \"sts:AssumeRole\",\n      \"Principal\": {\n        \"Service\": [\"lambda.amazonaws.com\", \"apigateway.amazonaws.com\"]\n      },\n      \"Effect\": \"Allow\",\n      \"Sid\": \"\"\n    }\n  ]\n}\n")
                              #_(slurp (io/resource "/tladeps/infra/lambda_role_policy.json"))}
      ::ro/deps {::bb-layer (ro/ref ::bb-layer)}
      ::ro/handler (fn [{::keys [bb-layer]}]
                     {::lambda/Function_layers [(.arn bb-layer)]})}

   ;; Python
   #_{::lambda/Function_runtime "python3.7"
      ::lambda/Function_handler "hello.handler"
      ::lambda/Function_code (com.pulumi.asset.AssetArchive. {"." (com.pulumi.asset.FileArchive. "./hello_lambda")})
      ::lambda/Function_role {::ro/id :lambda-role
                              ::ro/adapter #(.arn %)
                              ;; TODO: How to slurp this resource with the maven plugin?
                              ::iam/Role_assumeRolePolicy (str "{\n  \"Version\": \"2012-10-17\",\n  \"Statement\": [\n    {\n      \"Action\": \"sts:AssumeRole\",\n      \"Principal\": {\n        \"Service\": [\"lambda.amazonaws.com\", \"apigateway.amazonaws.com\"]\n      },\n      \"Effect\": \"Allow\",\n      \"Sid\": \"\"\n    }\n  ]\n}\n")
                              #_(slurp (io/resource "/tladeps/infra/lambda_role_policy.json"))}}

   ::http-endpoint
   {::api/Api_protocolType "HTTP"}

   ::lambda-backend
   {::api/Integration_integrationType "AWS_PROXY"
    ::api/Integration_integrationMethod "POST"
    ::ro/deps {::http-endpoint (ro/ref ::http-endpoint)
               #_ #_::proxy-alias (ro/ref ::proxy-alias)
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
                   {::api/Stage_apiId (.id http-endpoint)
                    ::api/Stage_routeSettings
                    [{::api/StageRouteSetting_routeKey (.routeKey http-route)
                      ::api/StageRouteSetting_throttlingBurstLimit 1
                      ::api/StageRouteSetting_throttlingRateLimit 0.5}]})}

   ::http-invoke-permission
   {::lambda/Permission_action "lambda:invokeFunction"
    ::lambda/Permission_principal "apigateway.amazonaws.com"
    ::ro/deps {#_ #_::proxy-alias (ro/ref ::proxy-alias)
               ::proxy (ro/ref ::proxy)
               ::http-endpoint (ro/ref ::http-endpoint)}
    ::ro/handler (fn [{::keys [proxy http-endpoint]}]
                   {::lambda/Permission_function (.name proxy)
                    ::lambda/Permission_sourceArn (->  http-endpoint .executionArn
                                                       (ro/apply-value #(str % "/*/*")))})}

   ;; For the layer, see https://github.com/jmglov/blambda/tree/main/examples/hello-world
   ;; and its Terraform output.
   #_ #_
   ::bb-layer
   ;; TODO: We shouldn't use be the absolute path here.
   {::lambda/LayerVersion_compatibleRuntimes ["provided.al2" "provided"]
    ::lambda/LayerVersion_layerName "blambda"
    #_ #_::lambda/LayerVersion_sourceCodeHash (str (hash (slurp "/Users/paulo.feodrippe/dev/tladeps/target/blambda.zip")))
    ::lambda/LayerVersion_compatibleArchitectures ["x86_64"]
    ::lambda/LayerVersion_code (com.pulumi.asset.AssetArchive.
                                {"." (com.pulumi.asset.FileArchive.
                                      "./target/blambda.zip")})}})

#_(bean com.pulumi.aws.apigatewayv2.inputs.StageRouteSettingArgs$Builder)

;; DONE Input autocompletion
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
  (let [{::keys [tladeps-bucket http-stage]
         :as system}
        (ro/build-infra (infra-map))]
    (spit "events.edn" "")
    (ro/system-attrs system (fn [v]
                              (spit "result.edn" (with-out-str (pp/pprint v)))))
    (.. ctx (export "bucket-name" (.bucket tladeps-bucket)))
    (.. ctx (export "invoke-url" (-> (.invokeUrl http-stage)
                                     (ro/apply-value #(str % "/")))))))

(defn make-consumer
  []
  (reify Consumer
    (accept [_ ctx]
      (infra-handler ctx))))
