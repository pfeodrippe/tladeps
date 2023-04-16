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
                       (or (str/includes? group_name search)
                           (str/includes? jar_name search))
                       true)]
           [:.grid.gap-1 {:key (str group_name jar_name)}
            [:div
             [:span.opacity-60 group_name]
             " "
             [:b [:span.text-primary jar_name]]]
            [:span.text-xs
             [:span.text-accent "v"]
             [:b [:span version]]]]))
        [:b [:span.text-error "No TLA+ deps found!"]])])))

(rum/defc search-view
  []
  (let [[search set-search!] (rum/use-state "")]
    [:.grid.gap-4.p-24
     [:input.input.input-bordered.w-full.max-w-xs
      {:placeholder "Search..."
       :value search
       :on-change #(set-search! (.. % -target -value))}]
     (deps-view {:search search})]))

(rum/defc app
  []
  [:div
   [:.navbar.bg-base-100
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
;; - [ ] Deploy to tladeps.org
;; - [ ] Center search view a little bit more
;; - [ ] Make it work for mobile
;; - [ ] Copy the dep in a way that the VSCode extension understands
