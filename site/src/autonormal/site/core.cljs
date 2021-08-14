(ns autonormal.site.core
  (:require
   ["react-dom" :as rdom]
   [autonormal.core :as a]
   [autonormal.ident :as a.ident]
   [autonormal.site.codemirror :as site.cm]
   [cljs.repl :as repl]
   [clojure.pprint :as pp]
   [clojure.string :as string]
   [clojure.tools.reader.edn :as edn]
   [goog.functions :as gfn]
   [helix.core :refer [defnc $]]
   [helix.core.alpha :as hx.alpha]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(def initial-data
  {:person/id 0 :person/name "Rachel"
   :friend/list [{:person/id 1 :person/name "Marco"}
                 {:person/id 2 :person/name "Cassie"}
                 {:person/id 3 :person/name "Jake"}
                 {:person/id 4 :person/name "Tobias"}
                 {:person/id 5 :person/name "Ax"}]})


(def ^:private vconj (fnil conj []))


(defnc button
  [props]
  (d/button
   {:& (update
       props
       :class
       (fnil conj [])
       (if (:disabled props)
         "bg-gray-400"
         "bg-blue-400")
       "px-2"
       "py-1"
       "rounded-md"
       "text-white")}))


(defnc pane
  [{:keys [title title-class class style children]}]
  (d/div
   {:class class}
   (d/h3
    {:class (into ["text-lg px-2 border-b"] title-class)
     :style style}
    title)
   children))


(defnc writable-pane
  [props]
  ($ pane
     {:& (-> props
             (update :class vconj "border" "border-solid" "border-blue-300")
             (update :title-class vconj "border-blue-300 text-blue-400"))}))


(defnc read-only-pane
  [props]
  ($ pane
     {:& (-> props
             (update :class vconj "border" "border-solid" "border-gray-400")
             (update :title-class vconj "border-gray-400 text-gray-500"))}))


(defnc db-add-input
  [{:keys [on-add]}]
  (let [[data set-data] (hooks/use-state "")]
    (d/div
     ($ writable-pane
        ($ site.cm/editor
           {:value data
            :on-change set-data}))
     ($ button
        {:on-click (fn [_]
                     (set-data "")
                     (on-add (edn/read-string data)))}
        "Add"))))


(defnc query-explorer
  [{:keys [db query set-query]}]
  (let [[result-pending? start-result] (hx.alpha/use-transition)
        set-query (hooks/use-memo
                   :once
                   (gfn/debounce set-query 400))
        result (hooks/use-memo
                [db query]
                (-> (try
                      (let [query-string (edn/read-string query)]
                        (with-out-str
                          (->> query-string
                               (a/pull db)
                               (pp/pprint))))
                      (catch js/Error e
                        (with-out-str
                          (pp/pprint (cljs.repl/Error->map e)))))
                    (string/trim)))]
    ;; simulate long render
    ;; (doseq [x (range 10000) y (range 10000)] (* x y))
    (d/div
     (d/div
      {:class "flex gap-2"
       :style {:min-height 300}}
      ($ writable-pane
         {:title "Query"
          :class ["flex-1 min-h-full"]}
         ($ site.cm/editor
            {:initial-value query
             :on-change (hx.alpha/with-transition
                          start-result
                          set-query)}))
      ($ read-only-pane
         {:title "Result"
          :class ["flex-1 transition-opacity delay-200 duration-400"
                  (if result-pending?
                    "opacity-20"
                    "opacity-100")]}
         ($ site.cm/editor
            {:value result})))
     (d/div
      {:class "py-2"}
      ($ read-only-pane
         {:title "Database explorer"}
         (let [[expanded set-expanded] (hooks/use-state nil)]
           (d/div
            {:class ["flex p-2 gap-2 flex-col"]}
            (if (empty? db)
              (d/span {:class "italic"} "DB is empty")
              (for [[k v] db
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
                           "px-3"]
                   :on-click (if expanded?
                               #(set-expanded nil)
                               #(set-expanded ident))}
                  (d/code {:class "code"}
                          (str k " " k2 )))
                 (d/div
                  {:class ["flex-1 px-2"
                           (when expanded? "bg-blue-100")
                           "overflow-scroll"]}
                  (d/pre
                   (if expanded?
                     (for [[k3 v3] v2]
                       (d/div
                        {:class "flex"
                         :key (str k3)}
                        (d/div
                         (d/div
                          {:class "px-3 bg-blue-300"}
                          (str k3)))
                        (d/div
                         {:class "px-2 flex flex-col gap-1"}
                         (cond
                           (a.ident/ident? v3) (pr-str v3)

                           (and (sequential? v3)
                                (every? a.ident/ident? v3))
                           (for [ident v3]
                             (d/div
                              {:key (str ident)
                               :class ["px-1"
                                       "border"
                                       "border-dotted"
                                       "border-blue-500"
                                       "hover:bg-blue-300"
                                       "cursor-pointer"]
                               :on-click #(set-expanded ident)}
                              (str ident)))

                           :else
                           (with-out-str
                             (pp/pprint v3))))))
                     (d/code
                      {:class "max-w-full code"}
                      pstr))))))))))))))


(defnc database-editor
  [{:keys [db set-db]}]
  ;; simulate long render
  ;; (doseq [x (range 10000) y (range 10000)] (* x y))
  (let [db-string (hooks/use-memo
                   [db]
                   (string/trim
                    (with-out-str
                      (pp/pprint db))))
        dbnc-set-db (hooks/use-memo
                     [set-db]
                     (gfn/debounce set-db 400))
        ;; this is used to remount the editor when we transact changes
        ;; to the db data
        [editor-inst set-inst] (hooks/use-state 0)]
    ($ writable-pane
       {:title "Database"}
       (d/div
        {:class "overflow-scroll"
         :style {:max-height "65vh"}}
        ($ site.cm/editor
           {:key editor-inst
            :initial-value db-string
            :on-change #(when-not (= db-string %)
                          (dbnc-set-db (edn/read-string %)))}))
       ($ button {:on-click #(do
                               (set-db {})
                               (set-inst inc))
                  :class ["m-1"]} "Reset")
       #_(d/div
        {:class "py-2"}
        ($ db-add-input {:on-add #(do
                                    (set-db a/add %)
                                    (set-inst inc))})))))


(defnc app
  []
  (let [[screen set-screen] (hooks/use-state :query-explorer)
        [db set-db] (hooks/use-state (a/db [initial-data]))
        [query set-query] (hooks/use-state "[]")
        [nav-pending? start-nav] (hx.alpha/use-transition)]
    (d/div
     {:class "container mx-auto p-3"}
     (d/h1
      {:class "text-xl mx-1 my-2 border-b-2 border-solid border-blue-400"}
      "Autonormal "
      (d/small {:class "italic"} "Playground"))
     (d/div
      {:class "pb-1 px-1 flex gap-1"}
      ($ button
         {:on-click (hx.alpha/with-transition
                      start-nav
                      #(set-screen :query-explorer))
          :disabled (= :query-explorer screen)}
         "Query Explorer")
      ($ button
         {:on-click (hx.alpha/with-transition
                      start-nav
                      #(set-screen :database-editor))
          :disabled (= :database-editor screen)}
         "Database Editor")
      (d/span
       {:class ["transition-opacity delay-200 duration-400 select-none"
                (if nav-pending? "opacity-70" "opacity-0")]}
       "Loading..."))
     (d/div
      {:class "p-1"}
      (case screen
        :query-explorer ($ query-explorer
                           {:db db
                            :query query
                            :set-query set-query})
        :database-editor ($ database-editor
                            {:db db
                             :set-db set-db})
        nil)))))


(defonce root (rdom/createRoot (js/document.getElementById "app")))


(defn ^:dev/after-load start
  []
  (.render root ($ app)))