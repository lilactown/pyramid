(ns autonormal.site.components
  (:require
   [helix.core :refer [defnc $]]
   [helix.dom :as d]))


(def ^:private vconj (fnil conj []))

(defnc button
  [{:keys [disabled] :as props}]
  (d/button
   {:& (update
        props
        :class
        vconj
        (if disabled
          "bg-gray-400"
          "bg-blue-400")
        "px-2"
        "py-1"
        (when-not disabled "shadow")
        "text-white")}))


(defnc tab
  [{:keys [active? class on-click children]}]
  (d/div
   {:on-click on-click
    :class (into ["border-b-2 border-solid"
                  (if active?
                    "border-blue-400"
                    "border-gray-300")
                  "cursor-pointer"] class)}
   children))


(defnc title-bar
  [props]
  (d/div
   {:& (-> props
           (update :class vconj
                   "p-1 px-3 bg-gray-200 border-l-4 border-solid border-gray-300"))}))


(defnc pane
  [{:keys [title title-class class style children]}]
  (d/div
   {:class class}
   ($ title-bar
      {:class title-class}
      (d/h3
       {:class ["text-lg"]
        :style style}
       title))
   children))


(defnc writable-pane
  [props]
  ($ pane
     {:& (-> props
             (update :class vconj "shadow" "border" "border-solid" "border-blue-300")
             (update :title-class vconj "bg-blue-100 border-blue-300 text-blue-400"))}))


(defnc read-only-pane
  [props]
  ($ pane
     {:& (-> props
             (update :class vconj "border" "border-solid" "border-gray-400")
             (update :title-class vconj "border-gray-400 text-gray-500"))}))
