(ns nepscrape.mongo
  (:require [monger.core :as mongo]
            [monger.collection :as mcoll]))

(def ^:dynamic *nepse-db* "nepse")
(def ^:dynamic *nepse-coll* "floorsheet")
(def ^:dynamic *mongodb-uri* (str "mongodb://127.0.0.1:27017/" *nepse-db*))

(defn mongo-connect [uri]
  (mongo/connect-via-uri uri))

(defn upsert!
  "Upserts a doc."
  [db coll m]
  (let [doc (assoc m :_id (:contract-no m))
        cond {:_id (:contract-no m)}
        opts {:upsert true}]
    (mcoll/update db coll cond doc opts)))

(comment
  (upsert! (:db mongo-conn) "floorsheet" {:amount 192400.99, :symbol "SCB", :contract-no "201506082574632", :rate 1924.0, :sn 20.0, :quantity 100.0, :buyer 21.0, :company "Standard Chartered Bank Limited", :seller 44.0})
  )
