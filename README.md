# autonormal

A library for storing graph data in a Clojure map that automatically
[normalizes](https://en.wikipedia.org/wiki/Database_normalization) nested data
and allows querying via [EQL](https://edn-query-language.org/eql/1.0.0/what-is-eql.html). It is optimized for read (query) performance.

## Installation

This library is still a work in progress; however, you can use git deps to
install and try it out.

## Use cases

The primary use case this library was developed for was to act as a client side
cache for [pathom]() APIs. However, you can imagine any time you might reach for
[DataScript]() to store data as entities, but where you need fast nested /
recursive querying of many attributes and don't need the full expressive power
of datalog, as being a good use case for **autonormal**.

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
(def animorphs-2
  (a/add animorphs {:person/id 1
                    :friend/best {:person/id 3
                                  :friend/best {:person/id 1}}}))
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

### Adding non-entities

Maps that are `add`ed are typically entities, but you can also add arbitrary
maps and `add` will merge the map with the database, normalizing and referencing
any nested entities. Example:

```clojure
(def animorphs-3
  (a/add animorphs-2 {:species {:andalites [{:person/id 5
                                             :person/species "andalite"}]}}))
;; => {:person/id {,,,
;;                 5 {:person/id 5
;;                    :person/name "Ax"
;;                    :person/species "andalite"}}
;;     :species {:andalites [[:person/id 5]]}}
```

### Querying

This library implements a fast EQL engine for Clojure data.

```clojure
(a/pull animorphs-3 [[:person/id 1]])
;; => {[:person/id 1] {:person/id 1
                       :person/name "Macro"
                       :friend/best [:person/id 3]}}
```

You can join on idents and keys within entities, and it will resolve any
references found in order to continue the query:

```clojure
(a/pull animorphs-3 [{[:person/id 1] [:person/name
                                      {:friend/best [:person/name]}]}])
;; => {[:person/id 1] {:person/name "Marco"
;;                     :friend/best {:person/name "Jake"}}}
```

Top-level keys in the db can also be joined on.

```clojure
(a/pull animorphs-3 [{:species [{:andalites [:person/name]}]}])
;; => {:species {:andalites {:person/name "Ax"}}}
```

Recursion is supported:

```clojure
(def query '[{[:person/id 0] [:person/id
                              :person/name
                              {:friend/list ...}]}])

(= (-> (a/pull animorphs-3 query)
       (get [:person/id 0]))
   data))
;; => true
```

See the EQL docs for more examples of what's possible!


## Features

- [x] Supports Clojure and ClojureScript
- [x] Auto normalization
- [ ] Full EQL query spec
  - [x] Props
  - [x] Joins
  - [x] Idents
  - [x] Unions
  - [ ] Recursion
    - [x] Infinite recursion
    - [ ] Bounded recursion
  - [ ] Preserve query meta on results
- [x] Custom schema

## Prior art

- [MapGraph](https://github.com/stuartsierra/mapgraph/blob/master/test/com/stuartsierra/mapgraph/compare.clj)
- [EntityDB](https://keechma.com/guides/entitydb/)
- [DataScript](https://github.com/tonsky/datascript/) and derivatives
- [Pathom](https://github.com/wilkerlucio/pathom)
- [juxt/pull](https://github.com/juxt/pull)
