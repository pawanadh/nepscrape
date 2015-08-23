(ns nepscrape.mongo
  (:require [monger.core :as mongo]
            [monger.collection :as mcoll]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn mongo-connect [uri]
  (mongo/connect-via-uri uri))

;(defn upsert-floorsheet!
;  "Upserts a doc."
;  [db coll m id-key]
;  (let [doc (assoc m :_id (id-key m))
;        cond {:_id (id-key m)}
;        opts {:upsert true}]
;    (mcoll/update db coll cond doc opts)))

(defn upsert-floorsheet! [mdb mcoll s]
  (doseq [m s]
    (let [id-key :contract-no
          doc (assoc m :_id (id-key m))
          cond {:_id (id-key m)}
          opts {:upsert true}]
      (mcoll/update mdb mcoll cond doc opts))))

(def custom-formatter (f/formatter "yyyyMMdd"))

;(defn upsert-shareprice!
;  "Upserts share prices."
;  [db coll m]
;  (let [id (str (:symbol m) "-" (f/unparse custom-formatter (t/now)))
;        doc (assoc m :_id id)
;        cond {:_id id}
;        opts {:upsert true}]
;    (mcoll/update db coll cond doc opts)))

(defn upsert-shareprice!
  "Saves share prices."
  [mdb mcoll s]
  (doseq [m s]
    (let [id (str (:symbol m) "-" (f/unparse custom-formatter (t/now)))
          doc (assoc m :_id id)
          cond {:_id id}
          opts {:upsert true}]
      (mcoll/update mdb mcoll cond doc opts))))



