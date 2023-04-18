(ns tladeps.main
  (:require
   [clojure.string :as str]
   [rum.core :as rum]
   ["react-dom/client" :as dom-client]
   [promesa.core :as p :include-macros true]
   [cljs.pprint :as pp]))

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

(rum/defc deps-view
  [{:keys [search]}]
  (let [[deps set-deps!] (rum/use-state nil)]
    (rum/use-effect! #(do (p/let [deps (fetch-tla-deps)]
                            (set-deps! (:results deps)))
                          (fn [])))
    (when deps
      [:.grid.gap-8
       (or
        (seq
         (for [{:keys [jar_name group_name version description]}
               (sort-by :jar_name deps)
               :when (if (seq (str/trim search))
                       (or (str/includes? (str/lower-case group_name) search)
                           (str/includes? (str/lower-case jar_name) search))
                       true)]
           [:.grid.gap-1 {:key (str group_name jar_name)}
            [:div
             [:span.opacity-60 group_name]
             " "
             [:b [:span.text-primary jar_name]]]
            [:span.text-xs
             [:span.text-accent "v"]
             [:b [:span version]]]]))
        [:b [:span.text-error "No TLA deps found!"]])])))

(def tla-deps
  [:span.p-3
   [:span.text-primary.lowercase
    "TLA+"]
   [:span.text-base-content.uppercase
    "deps"]])

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

     [:span
      [:span.text-md
       "Install "
       [:a.text-accent {:href "https://github.com/babashka/babashka#quickstart"
                        :target "_blank"}
        "babashka"]
       " and "
       [:a.text-accent {:href "https://github.com/pfeodrippe/tladeps#installation"
                        :target "_blank"}
        "tladeps"]
       "."]
      [:br]
      [:span.text-md
       "Copy one of the modules below, you will have a command to run."]
      [:br]
      [:span.text-md
       "Run it in your terminal and copy the result, this is your "
       [:b "classpath"]
       "."]]

     [:.divider]

     [:span.text-sm
      ""]

     [:span.text-warning.text-sm
      "This project is an experiment and it's not related to the TLA+ foundation or TLA+ ownership (at the moment)."]
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
;; - [ ] Copy the dep in a way that the VSCode extension understands
