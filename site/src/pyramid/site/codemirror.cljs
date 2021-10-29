(ns pyramid.site.codemirror
  (:require
   ["@codemirror/closebrackets" :refer [closeBrackets]]
   ["@codemirror/fold" :as fold]
   ["@codemirror/gutter" :refer [lineNumbers]]
   ["@codemirror/highlight" :as highlight]
   ["@codemirror/history" :refer [history historyKeymap]]
   ["@codemirror/state" :refer [EditorState]]
   ["@codemirror/view" :as view :refer [EditorView]]
   ["lezer" :as lezer]
   ["lezer-generator" :as lg]
   ["lezer-tree" :as lz-tree]
   [clojure.string :as string]
   [helix.core :refer [defnc $]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   [nextjournal.clojure-mode :as cm-clj]
   [nextjournal.clojure-mode.extensions.close-brackets :as close-brackets]
   [nextjournal.clojure-mode.extensions.formatting :as format]
   [nextjournal.clojure-mode.extensions.selection-history :as sel-history]
   [nextjournal.clojure-mode.keymap :as keymap]
   [nextjournal.clojure-mode.live-grammar :as live-grammar]
   [nextjournal.clojure-mode.node :as n]
   [nextjournal.clojure-mode.selections :as sel]))


(def theme
  (.theme
   EditorView
   #js {".cm-content" #js {:white-space "pre-wrap"
                           :padding "10px 8px"
                           :min-height "100%"}
        ".cm-line" #js {:font-family "Fira Code"
                        :font-size "0.8rem"}
        "&.cm-focused" #js {:outline "none"}
        ".cm-gutters" #js {:background "transparent"
                           :border "none"}
        ".cm-gutterElement" #js {:margin-left "5px"}}))


(def extensions
  #js [theme
       (history)
       highlight/defaultHighlightStyle
       (view/drawSelection)
       #_(fold/foldGutter)
       (.. EditorState -allowMultipleSelections (of true))
       cm-clj/default-extensions
       (.of view/keymap cm-clj/complete-keymap)
       (.of view/keymap historyKeymap)])


(defn new-cm
  [{:keys [parent initial-value on-change]}]
  (EditorView.
   #js {:state
        (.create
         EditorState
         #js {:doc initial-value
              :extensions
              (cond-> extensions
                ;; readonly
                (nil? on-change)
                (.concat
                 (-> EditorView
                     (.-editable)
                     (.of false)))

                (some? on-change)
                (.concat
                 (-> EditorView
                     (.-updateListener)
                     (.of
                      (fn [^js update]
                        (when (.-docChanged update)
                          (-> (.. update -state -doc toString)
                              (on-change))))))))})
        :parent parent}))


(defnc editor
  [{:keys [initial-value on-change value style]}]
  (let [cm-instance (hooks/use-ref nil)
        cm-mount (hooks/use-callback
                  :once
                  #(when %
                     (reset!
                      cm-instance
                      (new-cm
                       {:parent %
                        :initial-value (or initial-value "")
                        :on-change on-change}))))]
    (hooks/use-effect
     :once
     ;; on unmount
     #(when-let [^js cm @cm-instance]
        (.destroy cm)))

    (hooks/use-layout-effect
     [value]
     (when value
       (when-let [cm @cm-instance]
         (when (not= (string/join "\n" (.. cm -state -doc -text))
                     value)
           (let [tx (-> (.-state cm)
                        (.update
                         #js {:changes
                              ;; replace entire text with value
                              #js {:from 0
                                   :to (.. cm -state -doc -length)
                                   :insert value}}))]
             (.dispatch cm tx))))))
    (d/div
     {:class "min-h-full"
      :style style
      :ref cm-mount})))
