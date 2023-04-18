(ns tladeps.main
  (:require
   [clojure.string :as str]
   [rum.core :as rum]
   ["react-dom/client" :as dom-client]
   [promesa.core :as p :include-macros true]
   [cljs.pprint :as pp]
   [clojure.edn :as edn]))

(defn grid-template-areas
  [grid]
  {:style
   {:grid-template-areas
    (->> grid
         (mapv (fn [row]
                 (pr-str (str/join " " (map name row)))))
         (str/join " "))}})

(defn grid-area
  [identifier]
  {:style
   {:grid-area identifier}})

(defn style
  [attrs m]
  (update attrs :style merge m))

(defonce fetch-tla-deps
  (memoize
   (fn []
     (p/let [res (js/fetch "https://clojars.org/search?q=tladeps&format=json")
             body (.json res)]
       (js->clj body :keywordize-keys true)))))

(defn build-tladeps-command
  [{:keys [group_name jar_name version]}]
  (p/let [module-res (js/fetch (str "https://clojars.org/api/artifacts/"
                                    group_name "/" jar_name))
          module-body (.json module-res)
          body (p/->
                (js/fetch (:homepage (js->clj module-body :keywordize-keys true)))
                .text)]
    (let [deps
          {(symbol (str group_name "/" jar_name))
           {:mvn/version version
            :tladeps/override (:tladeps/override (edn/read-string body))}}]
      (.writeText js/navigator.clipboard
                  (str "tladeps --tladeps-classpath --tladeps-raw-deps '"
                       (pr-str deps)
                       "'")))))

(rum/defc deps-view
  [{:keys [search]}]
  (let [[deps set-deps!] (rum/use-state nil)]
    (rum/use-effect! #(do (p/let [deps (fetch-tla-deps)]
                            (set-deps! (:results deps)))
                          (fn [])))
    (when deps
      [:.grid.gap-10
       (or
        (seq
         (for [{:keys [jar_name group_name version description]
                :as jar-info}
               (sort-by :jar_name deps)
               :when (if (seq (str/trim search))
                       (or (str/includes? (str/lower-case group_name) search)
                           (str/includes? (str/lower-case jar_name) search))
                       true)]
           [:.grid.gap-1 (merge (-> (grid-template-areas
                                     [[:a :b]
                                      [:c :b]])
                                    (style {:grid-template-columns "1fr 1fr"}))
                                {:key (str group_name jar_name)})
            [:span (grid-area :a)
             [:span.opacity-60 group_name]
             " "
             [:b [:span.text-primary jar_name]]]
            [:span.text-xs (grid-area :c)
             [:span.text-accent "v"]
             [:b [:span version]]]
            [:button.btn.btn-wide (merge (grid-area :b)
                                         {:on-click #(build-tladeps-command jar-info)})
             "Copy"]]))
        [:b [:span.text-error "No TLA deps found!"]])])))

(def tla-deps
  [:span.p-3
   [:span.text-primary.lowercase
    "TLA+"]
   [:span.text-base-content.uppercase
    "deps"]])

(rum/defc link
  [{:keys [href]} & args]
  [:a.text-accent {:href href
                   :target "_blank"}
   args])

(rum/defc search-view
  []
  (let [[search set-search!] (rum/use-state "")]
    [:.grid.gap-4.p-6.lg:p-24
     [:span
      [:span.text-2xl
       "Hi 👋 "]
      [:br]
      [:span.text-xl
       [:span.text-lg.bg-gray-700.rounded-lg  {:style {:margin-left "-3px"}}
        tla-deps]
       " is a project aimed at making TLA+ dependencies more decentralized. "
       "It's best used with the TLA+ VSCode extension, see below."]]

     [:.divider]
     [:span.text-lg.text-secondary
      "Instructions"]

     [:span.text-md
      [:span
       "Install "
       (link {:href "https://github.com/babashka/babashka#quickstart"}
             "babashka")
       " and "
       (link {:href "https://github.com/pfeodrippe/tladeps#installation"}
             "tladeps")
       "."]
      [:br]

      [:span
       "Copy one of the modules below, you will have a command to run."]
      [:br]

      [:span
       "Run it in your terminal and copy the result, this will download the necessary deps and it will return the "
       [:b "classpath"]
       "."]
      [:br]
      [:br]

      [:span
       "The "
       (link {:href "https://github.com/tlaplus/vscode-tlaplus"}
             "VSCode extension")
       " lets you add deps to the classpath, see the "
       (link {:href "https://github.com/tlaplus/vscode-tlaplus/wiki/Java-Options"}
             "Java Options")
       ", with it you should be able to use any deps you find here in this page, e.g. try the EDN module below, check "
       (link {:href "https://github.com/pfeodrippe/tla-edn-module"}
             "the repo")
       " for instructions on how to use this module."]]

     [:.divider]

     [:span.text-warning-sm
      "This project is an experiment and it's not related to the TLA+ foundation or TLA+ ownership."]
     [:br]

     [:input.input.input-bordered
      {:placeholder "Search..."
       :value search
       :on-change #(set-search! (.. % -target -value))}]
     (deps-view {:search (str/lower-case search)})]))

(rum/defc app
  []
  [:div
   [:.navbar.bg-base-300
    [:a {:class "btn btn-ghost normal-case text-xl"}
     [:span.text-primary.lowercase
      "TLA+"]
     [:span.text-base-content.uppercase
      "deps"]]]
   (search-view)])

(defonce root
  (.. dom-client
      (createRoot (.getElementById js/document "app"))))

(defn- app!
  []
  (.. root
      (render (app))))

(defn ^:export refresh
  "During development, shadow-cljs will call this on every hot reload of source. See shadow-cljs.edn"
  []
  (app!)
  (js/console.log "Hot reload"))

(defn ^:export main!
  []
  (app!)
  (js/console.log "Loaded"))

;; TODO:
;; - [x] Fetch the deps
;; - [x] Present the deps
;; - [x] Filter deps
;; - [x] Deploy to tladeps.org
;; - [x] Make it work for mobile
;; - [x] Copy the dep in a way that the VSCode extension understands
