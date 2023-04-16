(ns tladeps.main
  (:require
   [rum.core :as rum]
   ["react-dom/client" :as dom-client]))

(rum/defc app
  []
  [:div {:class "label"} "abc"])

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
