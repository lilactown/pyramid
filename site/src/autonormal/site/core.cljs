(ns autonormal.site.core
  (:require
   ["react-dom" :as rdom]
   [autonormal.core :as a]
   [autonormal.site.codemirror :as site.cm]
   [autonormal.site.components :as c]
   [autonormal.site.tree :as tree]
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
  (let [friends '([:person/id 0]
                  [:person/id 1]
                  [:person/id 2]
                  [:person/id 3]
                  [:person/id 4]
                  [:person/id 5])]
    [{:person/id 0 :person/name "Rachel"
      :friend/list (remove #{[:person/id 0]} friends)}
     {:person/id 1
      :person/name "Marco"
      :friend/best [:person/id 3]
      :friend/list (remove #{[:person/id 1]} friends)}
     {:person/id 2 :person/name "Cassie"
      :friend/list (remove #{[:person/id 2]} friends)}
     {:person/id 3
      :person/name "Jake"
      :friend/best [:person/id 1]
      :friend/list (remove #{[:person/id 3]} friends)}
     {:person/id 4 :person/name "Tobias"
      :friend/best {:person/id 5}
      :friend/list (remove #{[:person/id 4]} friends)}
     {:person/id 5 :person/name "Ax"
      :friend/best {:person/id 4}
      :friend/list (remove #{[:person/id 5]} friends)}
     {:species {:andalites [[:person/id 5]]}}
     {:a
      {:deeply
       {:nested
        {:map
         {:of
          {:very
           {:important
            {:data [:person/id 0]}}}}}}}}]))


(defnc db-add-input
  [{:keys [on-add]}]
  (let [[data set-data] (hooks/use-state "")]
    (d/div
     ($ c/writable-pane
        ($ site.cm/editor
           {:value data
            :on-change set-data}))
     ($ c/button
        {:on-click (fn [_]
                     (set-data "")
                     (on-add (edn/read-string
                              {:default tagged-literal}
                              data)))}
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
                      (let [query-string (edn/read-string
                                          {:default tagged-literal}
                                          query)]
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
      ($ c/writable-pane
         {:title "Query"
          :class ["flex-1 min-h-full"]}
         ($ site.cm/editor
            {:initial-value query
             :on-change (hx.alpha/with-transition
                          start-result
                          set-query)}))
      ($ c/read-only-pane
         {:title "Result"
          :class ["flex-1 transition-opacity delay-200 duration-400"
                  (if result-pending?
                    "opacity-20"
                    "opacity-100")]}
         ($ site.cm/editor
            {:value result})))
     (d/div
      {:class "py-2"}
      ($ c/read-only-pane
         {:title "Database explorer"}
         ($ tree/data-tree {:data db}))))))


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
    ($ c/writable-pane
       {:title "Database"}
       (d/div
        {:class "overflow-scroll"
         :style {:max-height "65vh"}}
        ($ site.cm/editor
           {:key editor-inst
            :initial-value db-string
            :on-change
            #(when-not (= db-string %)
               (try
                 (dbnc-set-db (edn/read-string {:default tagged-literal} %))
                 (catch js/Error e
                   (js/console.error e))))}))
       ($ c/button
          {:on-click #(do
                        (set-db {})
                        (set-inst inc))
           :class ["m-1"]}
          "Reset")
       ($ c/button
          {:on-click (fn [_]
                       (set-db #(a/db [%]))
                       (set-inst inc))}
          "Normalize")
       #_(d/div
          {:class "py-2"}
          ($ db-add-input {:on-add #(do
                                      (set-db a/add %)
                                      (set-inst inc))})))))


(defnc app
  []
  (let [[screen set-screen] (hooks/use-state :query-explorer)
        [db set-db] (hooks/use-state (a/db initial-data))
        [query set-query] (hooks/use-state "[]")
        [nav-pending? start-nav] (hx.alpha/use-transition)]
    (d/div
     {:class "container mx-auto p-3"}
     (d/div
      {:class "pb-1 flex gap-1"}
      (d/h1
       {:class "text-xl ml-1 mr-8 my-2 border-solid border-blue-400"}
       "Autonormal "
       (d/small {:class "italic"} "Playground"))
      ($ c/tab
         {:on-click (hx.alpha/with-transition
                      start-nav
                      #(set-screen :query-explorer))
          :active? (= :query-explorer screen)
          :class ["mx-1 my-2"]}
         "Explorer")
      ($ c/tab
         {:on-click (hx.alpha/with-transition
                      start-nav
                      #(set-screen :database-editor))
          :active? (= :database-editor screen)
          :class ["mx-1 my-2"]}
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
