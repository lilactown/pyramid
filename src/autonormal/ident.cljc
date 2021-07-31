(ns autonormal.ident
  "Tools for identifying entity maps & creating ident functions.

  An 'ident function' is a function that takes a map and returns a tuple
  [:key val] that uniquely identifies the entity the map describes."
  (:refer-clojure :exclude [ident?]))


(defn ident
  "Takes a key and value and returns an ident."
  [k v]
  [k v])


(defn ident?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))))


(defn by*
  "Takes a collection of functions. Returns an ident function that calls the
  first function on a map, then the second, and so on until one of the functions
  returns a non-nil value, which should be an ident.

  Returns nil if all functions return nil."
  [fns]
  (fn ident-by
    [entity]
    (some #(% entity) fns)))


(defn by
  "Takes a number of functions. Returns an ident function that calls the
  first function on a map, then the second, and so on until one of the functions
  returns a non-nil value, which should be an ident.

  Returns nil if all functions return nil."
  [& fns]
  (by* fns))


(comment
  (defn person
    [e]
    (when-some [pid (:person/id e)]
      [:person/id pid]))

  (defn item
    [e]
    (when-some [id (:item/id e)]
      [:item/id id]))

  (def identify (by person item))

  (identify {:person/id 1})

  (identify {:item/id 1})

  (identify {:food/id 1})

  ;; composes
  (def identify2 (by identify (fn static-ident [_e] [:foo 1])))

  (identify2 {:person/id 1})

  (identify2 {:item/id 1})

  (identify2 {:asdf 1}))


(defn identify-by-key
  "Takes an entity and a key, and returns an ident using key and value in entity
  if found. Otherwise returns nil."
  [entity key]
  (let [v (get entity key ::not-found)]
    (when (not= v ::not-found)
      (ident key v))))


(defn by-keys*
  "Takes a collection of keys. Returns an ident function that looks for the
  first key in a map, then the second, and so on until it finds a matching
  key, then returns an ident using that key and the value found.

  Returns nil if no keys are found."
  [keys]
  (fn identify-by-keys
    [entity]
    (some #(identify-by-key entity %) keys)))


(defn by-keys
  "Takes a number of keys. Returns an ident function that looks for the
  first key in a map, then the second, and so on until it finds a matching
  key, then returns an ident using that key and the value found.

  Returns nil if no keys are found."
  [& keys]
  (by-keys* keys))


(comment
  (def identify-keys (by-keys :person/id :item/id))

  (identify-keys {:person/id 1})

  (identify-keys {:item/id 1})

  (identify-keys {:food/id 1}))
