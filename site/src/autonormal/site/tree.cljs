(ns autonormal.site.tree
  (:require
   [autonormal.ident :as a.ident]
   [autonormal.site.components :as c]
   [clojure.pprint :as pp]
   [helix.core :refer [defnc $]]
   [helix.core.alpha :as hx.alpha]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(defnc expandable
  [{:keys [data expanded? on-click]}]
  (d/div
   {:class ["px-2 py-0.5"
            (when-not expanded?
              "shadow")
            (if expanded?
              "bg-blue-300"
              "bg-gray-300")
            "hover:bg-blue-200"
            "cursor-pointer"]
    :on-click on-click}
   (d/code {:class "code"} (pr-str data))))


(defnc leaf
  [{:keys [data set-top-expanded]}]
  (let [[expanded set-expanded] (hooks/use-state nil)]
    (d/div
     {:class "flex flex-col gap-1"}
     (if-not (map? data)
       (cond
         (not (seqable? data))
         (d/pre
          (d/code
           {:class "code bg-yellow-100"}
           (with-out-str (pp/pprint data))))

         (a.ident/ident? data)
         ($ expandable
            {:on-click #(set-top-expanded data)
             :data data})

         (and (sequential? data)
              (every? a.ident/ident? data))
         (for [ident data]
           ($ expandable
              {:key (str ident)
               :on-click #(set-top-expanded ident)
               :data ident}))

         :else (d/pre
                (d/code
                 {:class "code bg-yellow-100"}
                 (with-out-str (pp/pprint data)))))
       (for [[k v] data]
         (d/div
          {:class "flex"
           :key (str k)}
          (d/div
           ($ expandable
              {:data k
               :expanded? (= k expanded)
               :on-click (if (= k expanded)
                           #(set-expanded nil)
                           #(set-expanded k))}))
          (d/div
           {:class "px-2"}
           (if (= k expanded)
             ($ leaf {:data v :set-top-expanded set-top-expanded})
             (d/pre
              (d/code
               {:class "code"}
               (with-out-str (pp/pprint v))))))))))))


(defnc data-tree
  [{:keys [data]}]
  (let [[expanded set-expanded] (hooks/use-state nil)
        [expand-pending? start-expand] (hx.alpha/use-transition)]
    (d/div
     {:class ["flex p-2 gap-1 flex-col"]}
     (if (empty? data)
       (d/span {:class "italic"} "No data")
       (let [entities (for [[k v] data
                          :when (map? v)
                          [k2 v2] v
                          :when (and (a.ident/ident? [k k2])
                                     (map? v2))]
                      [[k k2] v2])
             non-entities (for [[k v] data
                              :when (not
                                     (contains? (set (map ffirst entities)) k))]
                          [k v])]
         (helix.core/<>
          ($ c/title-bar {:class ["mb-1"]} "Entities")
          (for [[ident v] entities
                :let [pstr (pr-str v)
                      expanded? (= expanded ident)]]
            (d/div
             {:key (str ident)
              :class "flex"}
             ($ expandable
                {:expanded? expanded?
                 :on-click (hx.alpha/with-transition
                             start-expand
                             (if expanded?
                               #(set-expanded nil)
                               #(set-expanded ident)))
                 :data ident})
             (d/div
              {:class ["flex-1 px-2"
                       "overflow-scroll"]}
              (if expanded?
                ($ leaf {:data v
                         :set-top-expanded (hx.alpha/with-transition
                                             start-expand
                                             set-expanded)})
                (d/code
                 {:class "max-w-full code"}
                 (d/pre (d/code {:class "code"} pstr)))))))
          ($ c/title-bar {:class ["my-2"]} "Other")
          ($ leaf {:data (into {} non-entities)
                   :set-top-expanded (hx.alpha/with-transition
                                       start-expand
                                       set-expanded)})))))))
