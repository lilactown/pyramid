# CHANGELOG.md

## 3.3.0

This update includes a significant rewrite of the algorithm which traverses &
pulls data out of the db based on an EQL query. It has a minor if any performance
impact, but allows working with arbitrarily nested data.

### Added

- Pull queries now use protocol `IPullable` to resolve entities, allowing
  pyramid to query arbitrary data stores using EQL.
- Datalog queries now use protocol `IQueryable` to get all entities from an
  object, allowing pyramid to query arbitraty data stores in memory.
- `pull-report` now returns an `:indices` key with a set of top-level indices
  that are used in the query.
- `add-report` now returns an `:indices` key with a set of top-level indices
  that are modified through adding the data to the db
- Support for babashka

### Fixed

- Fix error when a reference to an entity that doesn't exist in the db is queried.
Now returns a map with the ident conj'd.


## 3.2.0

This update includes a significant rewrite of the normalization algorithm which
in the real world results in a 50% reduction in time spent normalization and
also supports arbitrary levels of nesting (up to computer memory limits).

### Fixed

- Maintain correct order of sequences of entities when being normalized
- Fix #14: data loss in p/add with nested maps

## 3.1.4

### Fixed

 - Fixed a bug in adding data where joins with params whose keys look like an entity
 was being replaced with a ref incorrectly

## 3.1.3

### Fixed

- Fixed a bug in adding data where lists (such as joins with params) got reversed


## 3.0.0 to 3.1.2

Renamed to pyramid.
Experimental datalog-like query engine. 
Internal refactor to use zippers.

## 2.0.0

### Breaking

In 1.2.0, we try to figure out how to identify a map by running a fn on each key
without context. This precludes more complex logic, such as preferring one ID
over another or using a composite of multiple keys to produce an ident.

Now, when you create a new db value, the optional second argument should be a
function that takes the entire map as a value and returns either an ident (a
tuple [key val] that uniquely identifies the entity in your system) or nil.


### Added

Additionally, a new namespace `autonormal.ident` is available for composing
functions for identifying entities.

```clojure
(require '[autonormal.core :as a]
         '[autonormal.ident :as ident])

;; creates a new db that will use `person/id` and `:food/name`
;; to identify entities
(a/db
  []
  (fn [entity]
    ;; first, check for :person/id
    (if-let [person-id (:person/id entity)]
      [:person/id person-id]
      ;; else, check for :food/name. return nil otherwise
      (when-let [food-name (:food/name entity)]
        [:food/name food-name]))))


;; autonormal.ident/by is a helper to compose functions that take an entity
;; and return either an ident or nil
(a/db [] (ident/by
           (fn [entity]
             (when-let [person-id (:person/id entity)]
               [:person/id person-id]))
           (fn [entity]
             (when-let [food-name (:food/name entity)]
               [:food/name food-name]))))


;; autonormal.ident/by-keys is a helper that handles the specific case of
;; composing keys 
(a/db [] (ident/by-keys :person/id :food/name))
```

## 1.2.0

### Added

- `db` now takes a second argument, `identify`, which is a function used to determine whether a key is
used to identify the entity or not

## 1.1.1

### Fixed

- `:autonormal.core/not-found` values were still present in union entries, are now filtered out appropriately

## 1.1.0

This is a minor bump to better reflect changes made in 1.0.3 and 1.0.2.

## Added

* More docstrings

## 1.0.3

### Added

* `delete`, which dissocs an entity from the db and removes all references to it

### Fixed

* Adding and creating databases with entities that have non-entities inside collections

## 1.0.2

### Added

* `add-report`, which returns a map with keys `:db`, containing the updated map,
  and `:entities`, which contains the set of lookup refs modified

* `pull-report`, which returns a map with keys `:data`, containing the result of
  the EQL query, and `:entities`, which contains the set of lookup refs
  queried in `:data`
