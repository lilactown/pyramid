# autonormal

[![Clojars Project](https://img.shields.io/clojars/v/lilactown/autonormal.svg)](https://clojars.org/lilactown/autonormal) [![cljdoc badge](https://cljdoc.org/badge/lilactown/autonormal)](https://cljdoc.org/d/lilactown/autonormal/CURRENT)


A library for storing graph data in a Clojure map that automatically
[normalizes](https://en.wikipedia.org/wiki/Database_normalization) nested data
and allows querying via [EQL](https://edn-query-language.org/eql/1.0.0/what-is-eql.html),
optimized for read (query) performance.

## Use cases

The primary use case this library was developed for was to act as a client side
cache for [pathom](https://github.com/wilkerlucio/pathom) APIs. However, you can imagine any time you might reach for
[DataScript](https://github.com/tonsky/datascript/) to store data as entities, but where you need fast nested /
recursive querying of many attributes and don't need the full expressive power
of datalog, as being a good use case for **autonormal**.

Another common use case is like a `select-keys` on steroids: the ability to do nested selections
on complex maps with nested collections pops up very often in code. Autonormal can take any non-normalized
map and execute an EQL query on it, returning the result.

## Project status

While feature complete, it has not been used in production yet.

## Usage

### Normalizing

A `db` is simply a map with a tabular structure of entities, potentially with
references to other entities.

**Autonormal** currently makes a default conventional assumption: your entities
are identified by a keyword whose name is `"id"`, e.g. `:id`, `:person/id`,
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

The map structure of a db is very efficient for getting info about any
particular entity; it's just a `get-in` away:

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
;;                     :person/name "Macro"
;;                     :friend/best {:person/id 3}}}
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
;; => {:species {:andalites [{:person/name "Ax"}]}}
```

Recursion is supported:

```clojure
(def query '[{[:person/id 0] [:person/id
                              :person/name
                              {:friend/list ...}]}])

(= (-> (a/pull animorphs-3 query)
       (get [:person/id 0]))
   data)
;; => true
```

See the EQL docs and tests in this repo for more examples of what's possible!


## More details

Collections like vectors, sets and lists should not mix entities and
non-entities. Collections are recursively walked to find entities.

To get meta-information about what entities were added or queried, use the
`add-report` and `pull-report` functions.

To delete an entity and all references to it, use the `delete` function.

## Tips & Tricks

### Replacing an entity

Data that is `add`ed about an existing entity are merged with whatever is in the
db. To replace an entity, `dissoc` it first:

```clojure
(-> (a/db [{:person/id 0 :foo "bar"}])
    (update :person/id dissoc 0)
    (a/add {:person/id 0 :bar "baz"}))
;; => {:person/id {0 {:person/id 0 :bar "baz"}}}
```

### Getting data for a specific entity

Since a db is a simple map, you can always use `get-in` to get basic info
regarding an entity. However, if your entity contains references, it will not
resolve those for you. Enter EQL!

To write an EQL query to get info about a specific entity, you can use an _ident_
to begin your query:

```clojure
(a/pull animorphs-3 [[:person/id 1]])
;; => {[:person/id 1] 
;;     {:person/id 1, :person/name "Marco", :friend/best #:person{:id 3}}}
```

You can add to the query to resolve references and get information about, e.g.
Marco's best friend:

```clojure
(a/pull animorphs-3 [{[:person/id 1] [:person/name
                                      {:friend/best [:person/name]}]}])
;; => {[:person/id 1] {:person/name "Marco", :friend/best #:person{:name "Jake"}}}
```

## Features

- [x] Supports Clojure and ClojureScript
- [x] Auto normalization
- [x] Full EQL query spec
  - [x] Props
  - [x] Joins
  - [x] Idents
  - [x] Unions
  - [x] Recursion
  - [x] Preserve query meta on results
  - [x] Parameters
- [x] Reports on what entities were added / visited while querying
- [x] `delete` an entity

## Prior art

- [Fulcro](https://fulcro.fulcrologic.com/)
- [MapGraph](https://github.com/stuartsierra/mapgraph/blob/master/test/com/stuartsierra/mapgraph/compare.clj)
- [EntityDB](https://keechma.com/guides/entitydb/)
- [DataScript](https://github.com/tonsky/datascript/) and derivatives
- [Pathom](https://github.com/wilkerlucio/pathom)
- [juxt/pull](https://github.com/juxt/pull)

## Copyright

Copyright © 2020 Will Acton. Distributed under the EPL 2.0.
