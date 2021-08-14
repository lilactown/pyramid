(ns autonormal.site.tree
  (:require
   [autonormal.ident :as a.ident]
   [clojure.pprint :as pp]
   [helix.core :refer [defnc $]]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(defnc leaf
  [{:keys [data set-top-expanded]}]
  (let [[expanded set-expanded] (hooks/use-state nil)]
    (d/div
     {:class "flex flex-col gap-0.5"}
     (if-not (map? data)
       (cond
         (not (seqable? data))
         (d/pre
          (d/code
           {:class "code bg-yellow-100"}
           (with-out-str (pp/pprint data))))

         (a.ident/ident? data)
         (d/code
          {:class "code"}
          (d/div
           {:class ["px-1"
                    "bg-gray-300"
                    "hover:bg-blue-200"
                    "cursor-pointer"]
            :on-click #(set-top-expanded data)}
           (d/code {:class "code"} (pr-str data))))

         (and (sequential? data)
              (every? a.ident/ident? data))
         (for [ident data]
           (d/div
            {:key (str ident)
             :class ["px-1"
                     "bg-gray-300"
                     "hover:bg-blue-200"
                     "cursor-pointer"]
             :on-click #(set-top-expanded ident)}
            (d/code {:class "code"} (pr-str ident))))

         :else (d/pre
                (d/code
                 {:class "code bg-yellow-100"}
                 (with-out-str (pp/pprint data)))))
       (for [[k v] data]
         (d/div
          {:class "flex"
           :key (str k)}
          (d/div
           (d/div
            {:class ["px-3"
                     (if (= k expanded)
                       "bg-blue-300"
                       "bg-gray-300")
                     "hover:bg-blue-200"
                     "cursor-pointer"]
             :on-click (if (= k expanded)
                         #(set-expanded nil)
                         #(set-expanded k))}
            (d/code {:class "code"} (pr-str k))))
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
  (let [[expanded set-expanded] (hooks/use-state nil)]
    (d/div
     {:class ["flex p-2 gap-0.5 flex-col"]}
     (if (empty? data)
       (d/span {:class "italic"} "Data is empty")
       (for [[k v] data
             :when (map? v)
             [k2 v2] v
             :when (map? v2)
             :let [ident [k k2]
                   pstr (pr-str v2)
                   expanded? (= expanded ident)]]
         (d/div
          {:key (str ident)
           :class "flex"}
          (d/div
           {:class [(if expanded?
                      "bg-blue-300"
                      "bg-gray-300")
                    "cursor-pointer"
                    "hover:bg-blue-200"
                    "px-3"]
            :on-click (if expanded?
                        #(set-expanded nil)
                        #(set-expanded ident))}
           (d/code
            {:class "code"}
            (str k " " k2 )))
          (d/div
           {:class ["flex-1 px-2"
                    "overflow-scroll"]}
           (if expanded?
             ($ leaf {:data v2 :set-top-expanded set-expanded})
             (d/code
              {:class "max-w-full code"}
              (d/pre (d/code {:class "code"} pstr)))))))))))
