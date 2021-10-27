(require '[meander.epsilon :as m])
(require '[pyramid.core :as p])

(def data
  {:person/id 0 :person/name "Rachel"
   :friend/list [{:person/id 1 :person/name "Marco"}
                 {:person/id 2 :person/name "Cassie"}
                 {:person/id 3 :person/name "Jake"}
                 {:person/id 4 :person/name "Tobias"}
                 {:person/id 5 :person/name "Ax"}]})

(def data-2
  {:person/id 1
   :friend/best {:person/id 3
                 :friend/best {:person/id 1}}})


(def data-3
  {:species {:andalites [{:person/id 5
                          :person/species "andalite"}]}})

(def animorphs (p/db [data]))

(def animorphs-2
  (p/add animorphs data-2))
(def animorphs-3
  (p/add animorphs-2 data-3))

(p/pull animorphs-3 [{[:person/id 1] [:person/name
                                      {:friend/best [:person/name]}]}])
;; => {[:person/id 1] {:person/name "Marco", :friend/best #:person{:name "Jake"}}}


(m/search animorphs-3
  {:person/id {?id {:person/name "Rachel"}}}
  [?id])


(require '[datascript.core :as ds])


(def ds-animorphs
  (-> {:person/id {:db/unique :db.unique/identity}
       :friend/best {:db/valueType :db.type/ref}
       :friend/list {:db/valueType :db.type/ref
                     :db/cardinality :db.cardinality/many}}
      (ds/empty-db)
      (ds/db-with [data])
      (ds/db-with [data-2])
      (ds/db-with [data-3])))


(def query-1
  '[:find ?name ?best-friend
    :where
    [?e :friend/best ?friend]
    [?e :person/name ?name]
    [?friend :person/name ?best-friend]])

(time
 (ds/q
  query-1
  ds-animorphs))

(require '[pyramid.query :as p.q])


(time (p.q/q query-1 animorphs-3))


(def query-2
  '[:find ?name ?friend
    :where
    [?e :friend/list ^:many ?friends]
    [?e :person/name ?name]
    [?friends :person/name ?friend]])

(time (ds/q query-2 ds-animorphs))

(time (p.q/q query-2 animorphs-3))


(time
 (p.q/q
  '[:find ?e ?v
    :where
    [?e :friend/list ^:many ?v]]
  animorphs-3))
