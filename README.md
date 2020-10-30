# autonormal

**Autonormal** is a library for automatically [normalizing](https://en.wikipedia.org/wiki/Database_normalization)
nested data into a flat map structure.


## Installation


## Usage

### Normalizing

A `db` is simply a map with a tabular structure of entities, potentially with
references to other entities.

**Autonormal** currently makes a very conventional assumption: your entities are
identified by a keyword whose name is `"id"`, e.g. `:id`, `:person/id`,
`:my.corp.product/id`, etc.

```clojure
(require '[autonormal.core :as a])

(def data
  {:person/id 0 :person/name "Rachel"
   :friend/list [{:person/id 1 :person/name "Marco"}
                 {:person/id 2 :person/name "Cassie"}
                 {:person/id 3 :person/name "Jake"}
                 {:person/id 4 :person/name "Tobias"}
                 {:person/id 5 :person/name "Ax"}]})

;; you can pass in multiple entities to instantiate a db, so `a/db` gets a vector
(def animorphs (a/db [data]))

animorphs
;; => {:person/id {0 {:person/id 0 
;;                    :person/name "Rachel"
;;                    :friend/list [[:person/id 1]
;;                                  [:person/id 2]
;;                                  [:person/id 3]
;;                                  [:person/id 4]
;;                                  [:person/id 5]]}
;;                 1 {:person/id 1 :person/name "Marco"}
;;                 2 {:person/id 2 :person/name "Cassie"}
;;                 3 {:person/id 3 :person/name "Jake"}
;;                 4 {:person/id 4 :person/name "Tobias"}
;;                 5 {:person/id 5 :person/name "Ax"}}}
```

This is very efficient for getting info about any particular entity; it's just a
`get-in` away:

```clojure
(get-in animorphs [:person/id 1])
;; => {:person/id 1 :person/name "Marco"}
```

You can `assoc`/`dissoc`/`update`/etc. this map in whatever way you would like.
However, if you want to accrete more potentially nested data, there's a helpful
`add` function to normalize it for you:

```clojure
;; Marco and Jake are each others best friend
(add animorphs {:person/id 1
                :friend/best {:person/id 3
                              :friend/best {:person/id 1}}})
;; => {:person/id {0 {:person/id 0 
;;                    :person/name "Rachel"
;;                    :friend/list [[:person/id 1]
;;                                  [:person/id 2]
;;                                  [:person/id 3]
;;                                  [:person/id 4]
;;                                  [:person/id 5]]}
;;                 1 {:person/id 1
;;                    :person/name "Marco" 
;;                    :friend/best [:person/id 3]}
;;                 2 {:person/id 2 :person/name "Cassie"}
;;                 3 {:person/id 3
;;                    :person/name "Jake"
;;                    :friend/best [:person/id 1]}
;;                 4 {:person/id 4 :person/name "Tobias"}
;;                 5 {:person/id 5 :person/name "Ax"}}}
```

Note that our `animorphs` db is an immutable hash map; `add` simply returns the
new value. It's up to you to decide how to track its value and keep it up to
date in your system, e.g. in an atom.

## Features

- [ ] Supports Clojure and ClojureScript
  - [x] Normalized DB
  - [x] EQL queries
  - [ ] Lazy map
    - [x] Clojure
    - [ ] ClojureScript
- [x] Auto normalization of nested data
- [x] A custom map implementation for lazy retrieval of entities 
- [ ] Full EQL query spec
  - [x] Props
  - [x] Joins
  - [x] Idents
  - [x] Unions
  - [ ] Recursion
  - [ ] Preserve query meta on results
- [ ] Schema
  - [ ] Custom ident keys

## Prior art

- [MapGraph](https://github.com/stuartsierra/mapgraph/blob/master/test/com/stuartsierra/mapgraph/compare.clj)
- [EntityDB](https://keechma.com/guides/entitydb/)
- [DataScript](https://github.com/tonsky/datascript/) and derivatives
- [Pathom](https://github.com/wilkerlucio/pathom)
- [juxt/pull](https://github.com/juxt/pull)
