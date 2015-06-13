(ns nepscrape.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.core.async :as async]
            [nepscrape.util :as util]
            [nepscrape.mongo :refer :all]))


(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

(def ^:dynamic *floorsheet-url* "http://www.nepalstock.com.np/floorsheet")
(def ^:dynamic *floorsheet-page-url-format* "http://www.nepalstock.com.np/main/floorsheet/index/%s/id/desc/")

(defrecord Stock [sn contract-no symbol company buyer seller quantity rate amount])

(def FLOORSHEET-FIELDS {0  :sn
                        1  :contract-no
                        2  :company
                        3  :buyer
                        4  :seller
                        5  :quantity
                        6  :rate
                        7  :amount
                        8  :year
                        9  :month
                        10 :day
                        11 :day-sn})

(defn page-urls
  "Parse pagination component in floor sheet page and gets # of pages."
  []
  (let [pager-text (-> (fetch-url *floorsheet-url*)
                       (html/select [:div.pager html/first-child])
                       ((partial map html/text))
                       first)

        parse-page-count (fn [text]
                           (-> text                         ;Page 1/80
                               (clojure.string/split #"/")  ;split on /
                               last
                               ((partial filter #(Character/isDigit %))) ;filter digits only (removes escapse chars)
                               ((partial apply str))
                               (Integer/parseInt)))]

    (->> (range 2 (parse-page-count pager-text))
         (map #(format *floorsheet-page-url-format* %)))))

(defn- node-text
  "If :title attribute is available in td-node, returns a vector of :title attribute value
  and node text, otherwise returns node text.

  NOTE: two differnet return types, not a good thing.
  "
  [td-node]
  (let [company (get-in td-node [:attrs :title])
        text (html/text td-node)]
    (if company [text company] text)))

; converts index into field names
(defn idx-into-field
  "Converts a vector [n v] into [fieldname v]"
  [[a b]]
  (let [field (get FLOORSHEET-FIELDS a)
        contract-no? (partial = 1)]
    (if (or (vector? b) (contract-no? a))
      ;let contract-no and company be string
      [field b]
      ;otherwise convert into double
      [field (util/parse-double b)])))

(comment
  (idx-into-field [0 "123"]))

(defn- extract-int
  ([start end s]
   (Integer/parseInt (subs s start end)))
  ([start s]
   (Integer/parseInt (subs s start))))

; extract yr, month, day and # from contract-no field
(def extract-yr (partial extract-int 0 4))
(def extract-mth (partial extract-int 4 6))
(def extract-day (partial extract-int 6 8))
(def extract-id (partial extract-int 8))

; creates Stock record
(defn s->Stock
  ;("20" "201506082574632" ["SCB" "Standard Chartered Bank Limited"] "21" "44" "100" "1924" "192400.00")
  [s]
  (->> (map-indexed vector s)                               ; map into vector of [index s-value]
       (map idx-into-field)                                 ; map index in each vector into field name
       (into (hash-map))                                    ; convert into map
       (map->Stock)                                         ; convert into Stock record

       ; extract company and ticker symbol out of company vector
       ((fn [m]
          (let [[symbol name] (:company m)
                temp-m (dissoc m :company)]
            (assoc temp-m :symbol symbol :company name))))

       ; extract out year, month, day and day's SN from contract-no
       ((fn [m]
          (let [contract-no (:contract-no m)
                yr (extract-yr contract-no)
                mth (extract-mth contract-no)
                day (extract-day contract-no)
                day-sn (extract-id contract-no)]
            (assoc m :year yr :month mth :day day :day-sn day-sn))))
       ))

(comment
  (s->Stock `("20" "201506082574632" ["SCB" "Standard Chartered Bank Limited"] "21" "44" "100" "1924" "192400.00")))


(defn scrape-page
  [page-url]
  (let [selector [:#home-contents :table :tr]
        nodes (fetch-url page-url)]
    (->> (html/select nodes selector)

         (drop 2)                                           ; drop first 2 rows
         (drop-last 3)                                      ; drop last 3 rows

         (map #(html/select % [:td]))
         (map #(map node-text %))                           ; get each node's text

         (map s->Stock)                                     ; convert to Stock record
         )))

(comment
  ;(def nodes (fetch-url *floorsheet-url*))
  (time (scrape-page *floorsheet-url*))                     ;10.357473 msecs
  )

(defn save! [mdb mcoll s]
  (doseq [m s]
    (upsert! mdb mcoll m)))

(defn -main [& args]
  (let [nepse-db "nepse"
        coll "floorsheet"
        mongodb-uri (str "mongodb://127.0.0.1:27017/" nepse-db)
        conn (mongo-connect mongodb-uri)
        db (:db conn)
        page-urls (page-urls)
        _ (println (str (count page-urls) " pages."))
        fs-chan (async/chan (count page-urls))]

    (doseq [url page-urls]
      (async/go
        (let [data (scrape-page url)]
          (async/>! fs-chan data)
          (println (str (count data) " docs pushed to fs-chan.")))))

    ; save sequential
    ;(doseq [_ page-urls]
    ;  (save! db coll (async/<!! fs-chan))
    ;  (println "Data saved."))

    ; use threads for saving data
    ; uses callback technique suggested in Joy of Clojure book.
    (let [fs (for [_ page-urls]
               (future
                 (let [data (async/<!! fs-chan)]
                   (save! db coll data)
                   (println (str (count data) " docs saved.")))))]

      ; wait till all futures completes.
      (doseq [f fs] @f))

    (println "DONE.")))
