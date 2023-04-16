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

(rum/defc app
  []
  [:<>
   [:.navbar.bg-base-100
    [:a {:class "btn btn-ghost normal-case text-xl"}
     "TLA Deps"]]
   [:.grid.gap-4.p-24 #_(-> (grid-template-areas [[:search]
                                                [:a]])
                            (style {:grid-template-columns "1fr 5fr"}))
    [:input.input.input-bordered.w-full.max-w-xs {:placeholder "Search..."}]
    (repeat 5 [:div "Z"])]])

(defonce root
  (.. dom-client
      (createRoot (.getElementById js/document "app"))))

(comment

  (p/let [res (js/fetch "https://clojars.org/search?q=tla&format=json")
          body (.json res)]
    (pp/pprint (js->clj body :keywordize-keys true)))

  ())

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
;; - [ ] Fetch the deps
;; - [ ] Present the deps
