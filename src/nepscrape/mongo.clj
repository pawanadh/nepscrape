(ns nepscrape.mongo
  (:require [monger.core :as mongo]
            [monger.collection :as mcoll]))

(defn mongo-connect [uri]
  (mongo/connect-via-uri uri))

(defn upsert!
  "Upserts a doc."
  [db coll m]
  (let [doc (assoc m :_id (:contract-no m))
        cond {:_id (:contract-no m)}
        opts {:upsert true}]
    (mcoll/update db coll cond doc opts)))