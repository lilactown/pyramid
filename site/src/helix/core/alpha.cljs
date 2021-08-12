(ns helix.core.alpha
  (:require
   ["react" :as react]))


(defn with-transition
  ([f] (with-transition react/startTransition f))
  ([start-transition f]
   (fn [& args]
     (start-transition #(apply f args)))))


(def use-transition
  react/useTransition)
