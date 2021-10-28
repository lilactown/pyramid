(ns pyramid.zip
  (:require
   [fast-zip.core :as zip]))


(defn make-node
  [node children]
  (if (map-entry? node)
    (into [] children)
    (into (empty node) children)))


(defn tree-zipper
  [tree]
  (zipper coll? seq make-tree-node tree))
