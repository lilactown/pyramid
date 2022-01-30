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

(require '[datascript.db])

(require '[pyramid.pull :as pull])


(def ds-animorphs
  (-> {:person/id {:db/unique :db.unique/identity}
       :friend/best {:db/valueType :db.type/ref}
       :friend/list {:db/valueType :db.type/ref
                     :db/cardinality :db.cardinality/many}}
      (ds/empty-db)
      (ds/db-with [data])
      (ds/db-with [data-2])
      (ds/db-with [data-3])))


(-> (ds/entity ds-animorphs [:person/id 1])
    (get :person/name))
;; => "Marco"

(-> (ds/entity ds-animorphs [:person/id 1])
    (get :friend/best)
    (get :person/name))
;; => "Jake"


(datascript.db/entid ds-animorphs [:person/id 1])
;; => 2

(datascript.db/-search ds-animorphs [(datascript.db/entid ds-animorphs [:person/id 1])])
;; => (#datascript/Datom [2 :friend/best 4 536870914 true] #datascript/Datom [2 :person/id 1 536870913 true] #datascript/Datom [2 :person/name "Marco" 536870913 true])


(ds/pull ds-animorphs '[*] [:person/id 1])
;; => {:db/id 2, :friend/best #:db{:id 4}, :person/id 1, :person/name "Marco"}


(defn- resolve-attr [db a datoms]
  (if (datascript.db/multival? db a)
    (if (datascript.db/ref? db a)
      (reduce #(conj %1 [:db/id (:v %2)])
              #{} datoms)
      (reduce #(conj %1 (:v %2)) #{} datoms))
    (if (datascript.db/ref? db a)
      [:db/id (:v (first datoms))]
      (:v (first datoms)))))


(defn resolve-datascript-entity
  [db lookup-ref]
  (let [eid (if (= :db/id (first lookup-ref))
              (second lookup-ref)
              (datascript.db/entid db lookup-ref))
        attrs (datascript.db/-search db [eid])]
    (into {}
          (for [attr (map second attrs)
                :let [datoms (datascript.db/-search db [eid attr])]]
            [attr (resolve-attr db attr datoms)]))))


(resolve-datascript-entity ds-animorphs [:person/id 1])
;; => {:friend/best [:db/id 4], :person/id 1, :person/name "Marco"}

(resolve-datascript-entity ds-animorphs [:db/id 2])
;; => {:friend/best [:db/id 4], :person/id 1, :person/name "Marco"}

(extend-protocol pull/IPullable
  datascript.db.DB
  (resolve-ref
    ([db lookup-ref] (resolve-datascript-entity db lookup-ref))
    ([db lookup-ref not-found]
     (or (pull/resolve-ref db lookup-ref) not-found))))


(p/pull ds-animorphs [[:person/id 0]])
;; => {[:person/id 0] {:friend/list #{#:person{:id 5} #:person{:id 1} #:person{:id 2} #:person{:id 3} #:person{:id 4}}, :person/id 0, :person/name "Rachel"}}

(p/pull ds-animorphs [{[:person/id 1] [:person/name
                                       {:friend/best [:person/name]}]}])
;; => {[:person/id 1] {:person/name "Marco", :friend/best #:person{:name "Jake"}}}


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


(p.q/q
 '[:find ?name ?best-friend
   :where
   [?e :friend/best ?bf]
   [?e :person/name ?name]
   [?bf :person/name ?best-friend]]
 animorphs-3)


(require '[asami.core :as a])


(a/create-database "asami:mem://pyramid-example2")


(def conn (a/connect "asami:mem://pyramid-example2"))


(require '[asami.graph :as ag])


(a/transact
 conn {:tx-data
       [(-> data
            (assoc :db/id -1)
            (update :friend/list
                    (partial map-indexed #(assoc %2 :db/id (- -2 %1)))))
        (-> data-2
            (assoc :db/id -2)
            (update :friend/best assoc :db/id -4))
        (assoc-in data-3 [:species :andalites 0 :db/id] -6)]})

(a/db conn)

(ag/resolve-triple (a/graph (a/db conn)) '?e :person/id 0)
;; => ([:tg/node-26575])

(ag/resolve-triple (a/graph (a/db conn)) :tg/node-26575 '?a '?v)
;; => ([:person/id 0] [:person/name "Rachel"] [:tg/owns :tg/node-26581] [:tg/owns :tg/node-26583] [:tg/owns :tg/node-26585] [:tg/owns :tg/node-26577] [:tg/owns :tg/node-26576] [:tg/owns :tg/node-26579] [:friend/list :tg/node-26576] [:db/ident :tg/node-26575] [:tg/entity true])

(->> (ag/resolve-triple (a/graph (a/db conn)) :tg/node-26576 '?a '?v)
     (filter #(= :tg/contains (first %)))
     (map #(ag/resolve-triple
            (a/graph (a/db conn))
            (second %) '?a '?v)))

(require '[clojure.string :as string])

(defn- resolve-asami-node
  [graph eid]
  #_(prn eid)
  (let [datoms (ag/resolve-triple graph eid '?a '?v)]
    (cond
      (some #(= :tg/contains (first %)) datoms)
      (->> datoms
           (keep #(when (= :tg/contains (first %))
                    (second %)))
           (map #(resolve-asami-node graph %)))

      #_(some #(= [:tg/entity true] %) datoms)
      :else
      (into
       {}
       (comp
        (remove #(= :tg/owns (first %)))
        (map (fn [[k v]]
               (if (and (keyword? v)
                        (not= :db/ident k)
                        (string/starts-with? (str v) ":tg/node-"))
                 [k (resolve-asami-node graph v)]
                 [k v]))))
       datoms))))

(defn resolve-asami-entity
  [db lookup-ref]
  (let [graph (a/graph db)
        [[eid]] (ag/resolve-triple graph '?e (nth lookup-ref 0) (nth lookup-ref 1))
        #_#_datoms (ag/resolve-triple graph eid '?a '?v)]
    #_(into {} (remove #(= :tg/owns (first %))) datoms)
    (resolve-asami-node graph eid)))

(resolve-asami-entity (a/db conn) [:person/id 0])

(a/q '[:find ?e
       :where [?e :person/id 1]]
     (a/db conn))


(def kw->subs (atom {}))

(defn reg-sub
  [kw compute-fn]
  (swap! kw->subs assoc kw (make-reaction (memoize compute-fn))))
