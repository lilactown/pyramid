# pyramid

A library for storing and querying graph data in Clojure.

Features:
* Graph query engine that works on any in-memory data store
* Algorithm for taking trees of data and storing them in normal form in Clojure
  data.

## Install & docs

[![Clojars Project](https://img.shields.io/clojars/v/town.lilac/pyramid.svg)](https://clojars.org/town.lilac/pyramid) [![cljdoc badge](https://cljdoc.org/badge/town.lilac/pyramid)](https://cljdoc.org/d/town.lilac/pyramid/CURRENT)


## Why

Clojure is well known for its graph databases like datomic and datascript which
implement a datalog-like query language. There are contexts where the power vs
performance tradeoffs of these query languages don't make sense, which is where
pyramid can shine.

## What

Pyramid aims to be useful at each evolutionary stage of a progam where one needs
to traverse and make selections out of graphs of data.

### Selection

Pyramid starts by working with Clojure data structures. A simple program that
uses pyramid can use a query to select speficic data out of a large, deeply
nested tree, like a supercharged `select-keys`.

### Transformation

Pyramid combines querying with the [Visitor pattern](https://en.wikipedia.org/wiki/Visitor_pattern)
in a powerful way, allowing one to easily perform transformations of selections
of data. Simply annotate parts of your query with metadata
`{:visitor (fn visit [data selection] ,,,)}` and the `visit` function will be
used to transform the data in a depth-first, post-order traversal (just like
`clojure.walk/postwalk`).

### Accretion

A more complex program may need to keep track of that data over time, or query
data that contains cycles, which can be done by creating a `pyramid.core/db`.
Adding data to a db will [normalize](https://en.wikipedia.org/wiki/Database_normalization)
the data into a flat structure allowing for easy updating of entities as new
data is obtained and allow relationships that are hard to represent in trees.
Queries can traverse the references inside this data.

### Durability

A program may grow to need durable storage and other features that more full
featured in-memory databases provide. Pyramid provides a protocol, `IPullable`,
which can be extended to allow queries to run over any store that data can be
looked up by a tuple, `[primary-key value]`. This is generalizable to most
databases like Datomic, DataScript, Asami and SQLite.

### Full stack

The above shows the evolution of a single program, but many programs never grow
beyond the accretion stage. Pyramid has been used primarily in user interfaces
where data is stored in a data structure and queried over time to show different
views on a large graph. Through its protocols, it can now be extended to be used
with durable storage on the server as well.

## Concepts

**Query**: A query is written using [EQL](https://edn-query-language.org/eql/1.0.0/what-is-eql.html),
a query language implemented inside Clojure. It provides the ability to select
data in a nested, recursive way, making it ideal for traversing graphs of data.
It does not provide arbitrary logic like SQL or Datalog.

**Entity map**: a Clojure map which contains information that uniquely identifies
the domain entity it is about. E.g. `{:person/id 1234 :person/name "Bill" :person/age 67}`.

**Lookup ref**: a 2-element Clojure vector that has a keyword and a value which
together act as a pointer to a domain entity. E.g. `[:person/id 1234]`.

## Usage

See [./docs/GUIDE.md](docs/GUIDE.md)

## Prior art

- [Fulcro](https://fulcro.fulcrologic.com/)
- @souenzzo's POC [eql-refdb](https://github.com/souenzzo/eql-refdb)
- [MapGraph](https://github.com/stuartsierra/mapgraph/blob/master/test/com/stuartsierra/mapgraph/compare.clj)
- [EntityDB](https://keechma.com/guides/entitydb/)
- [DataScript](https://github.com/tonsky/datascript/) and derivatives
- [Pathom](https://github.com/wilkerlucio/pathom)
- [juxt/pull](https://github.com/juxt/pull)
- [ribelo/doxa](https://github.com/ribelo/doxa)

## Copyright

Copyright © 2022 Will Acton. Distributed under the EPL 2.0.
