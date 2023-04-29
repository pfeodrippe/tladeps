(ns tladeps.eita
  (:require
   [hato.client :as http])
  (:import
   (org.apache.lucene.index IndexReader MultiBits)
   (org.apache.maven.index
    Indexer DefaultIndexer DefaultSearchEngine DefaultIndexerEngine
    DefaultQueryCreator)
   (org.apache.maven.index.context IndexCreator IndexUtils)
   (org.apache.maven.index.creator
    JarFileContentsIndexCreator MinimalArtifactInfoIndexCreator MavenPluginArtifactInfoIndexCreator)
   (org.apache.maven.index.updater IndexUpdateRequest ResourceFetcher DefaultIndexUpdater)
   (org.apache.maven.index.incremental DefaultIncrementalHandler)))

(comment

  ;; From https://github.com/apache/maven-indexer/blob/master/indexer-examples/indexer-examples-basic/src/main/java/org/apache/maven/index/examples/BasicUsageExample.java#L95
  ;; and
  ;; https://stackoverflow.com/questions/5776519/how-to-parse-unzip-unpack-maven-repository-indexes-generated-by-nexus

  (defonce indexer
    (DefaultIndexer. (DefaultSearchEngine.) (DefaultIndexerEngine.) (DefaultQueryCreator.)))

  (defonce index-updater
    (DefaultIndexUpdater. (DefaultIncrementalHandler.) []))

  (defonce context
    (let [local-cache (java.io.File. "target/clojars-cache")
          index-dir (java.io.File. "target/clojars-index")
          indexers [(JarFileContentsIndexCreator.)
                    (MinimalArtifactInfoIndexCreator.)
                    (MavenPluginArtifactInfoIndexCreator.)]]

      (-> indexer
          (.createIndexingContext "clojars-context",
                                  "clojars",
                                  local-cache
                                  index-dir
                                  "https://repo.clojars.org",
                                  nil
                                  true,
                                  true,
                                  indexers))
      #_(type (into-array IndexCreator indexers))))

  ;; Update index.
  (let [request (IndexUpdateRequest.
                 context
                 (let [*state (atom {})]
                   (reify ResourceFetcher
                     (connect [_ _id url]
                       (swap! *state assoc :uri url))

                     (disconnect [_])

                     (retrieve [_ n]
                       (let [res (http/request {:url (str (@*state :uri) "/" n)
                                                :method :get
                                                :as :stream})]
                         (if (= (:status res) 200)
                           (:body res)
                           (throw (ex-info "Oh oh" (dissoc res :http-client)))))))))]
    (bean (.fetchAndUpdateIndex index-updater request)))

  ;; Search.
  (let [searcher (.acquireIndexSearcher context)
        ir (.getIndexReader searcher)]
    (try
      (bean ir)
      #_(MultiBits/getLiveDocs ir)
      (let [doc (.document ir 5050)]
        (into (sorted-map)
              (bean (IndexUtils/constructArtifactInfo doc context))))
      #_(.maxDoc ir)
      (finally
        (.releaseIndexSearcher context searcher))))

  (def jar-file
    (with-open [is (:body (http/request {:url "https://repo.clojars.org/io/github/pfeodrippe/tladeps-edn-module/0.4.0/tladeps-edn-module-0.4.0.jar"
                                         :method :get
                                         :as :stream}))
                os (java.io.FileOutputStream. "eita.jar")]
      (clojure.java.io/copy is os)
      (java.util.jar.JarFile. (clojure.java.io/file "eita.jar"))))

  (count (.entries jar-file))

  (slurp (.getInputStream jar-file (.getJarEntry jar-file "Edn.tla")))


  ())
