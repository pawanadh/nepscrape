(ns nepscrape.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.core.async :as async]
            [nepscrape.util :as util]
            [nepscrape.mongo :refer :all]
            [cheshire.core :as json])
  (:import (java.time LocalDateTime)))


(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

(def ^:dynamic *floorsheet-url* "http://www.nepalstock.com.np/floorsheet")
(def ^:dynamic *floorsheet-page-url-format* "http://www.nepalstock.com.np/main/floorsheet/index/%s/id/desc/")

(defrecord Stock [sn contract-no symbol company buyer seller quantity rate amount year month day day-sn scrape-date])

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
                        11 :day-sn
                        12 :scrape-date})

(defn page-urls
  "goto *floorsheet-url*
  parse pagination component & gets # of pages."
  [url]
  (let [pager-text (-> (fetch-url url)
                       (html/select [:div.pager html/first-child])
                       ((partial map html/text))
                       first)

        parse-page-count (fn [text]
                           (-> text                         ;Page 1/80
                               (clojure.string/split #"/")  ;split on /
                               last
                               ((partial filter #(Character/isDigit %))) ;filter digits only (removes escapse chars)
                               ((partial apply str))
                               (Integer/parseInt)))

        ; make URLs
        page-urls (->> (range 1 (inc (parse-page-count pager-text)))
                       (map #(format *floorsheet-page-url-format* %)))]
    ;(conj page-urls *floorsheet-url*)                       ; conj first page
    page-urls
    ))

(comment
  (count (page-urls *floorsheet-url*)))

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
      [field (util/parse-if-double b)])))

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

(defn split-contract-no
  "str = 201506082574632 => {:year 2015 :month 6 :day 8 :day-sn 2574632}"
  [str]
  (let [
        yr (extract-yr str)
        mth (extract-mth str)
        day (extract-day str)
        day-sn (extract-id str)]
    {:year yr :month mth :day day :day-sn day-sn}))

(comment
  (split-contract-no "201506082574632"))

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
       ((fn [m] (merge m (split-contract-no (:contract-no m)))))
       ((fn [m] (assoc m :scrape-date (str (LocalDateTime/now)))))
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

;(defn request-downloads
;  "Starts go processes for each url (in urls) to scrape data from the page.
;  Each go process puts data in ch channel."
;  [urls ch]
;  (doseq [url urls]
;    (async/thread
;      (let [data (scrape-page url)]
;        (async/>!! ch data)
;        (println (str url " :: " (count data) " => channel."))))))

; *
(defn req-downloads [urls]
  "Starts go processes for each url. Returns a seq of channels."
  (for [url urls]
    (async/go
      (let [data (scrape-page url)
            _ (println (str url " = " (count data)))]
        data))))
; *
(defn save-to-mongo<!!
  "Saves data available in channels into MongoDB. It does one <!! per channel.
  Starts a future for channel and blocks till all futures are complete."
  [mongo-db mongo-coll chs]
  (let [fs (for [ch chs]
             (future
               (let [data (async/<!! ch)]
                 (save! mongo-db mongo-coll data)
                 (println (str (count data) " docs => MonogoDB.")))))]
    (println (str "Futures = " (count fs)))
    (doseq [f fs] @f)))

(defn collect-into-atom<!!
  "Collects data from a seq of channels into an atom and returns that atom.
  It performs one  <!! per channel."
  [chs]
  (let [res (atom [])]
    (doseq [ch chs]
      (swap! res into (async/<!! ch)))
    res))

(defn write-data! [file data]
  (json/generate-stream data (clojure.java.io/writer file)))

; *
(defn -main [& args]
  {:pre []}
  (let [[out & opts] args
        file? (and (= out "-file") (= 1 (count opts)))
        mongo? (and (= out "-mongo") (= 2 (count opts)))]

    (if (or file? mongo?)
      (case out
        "-file" (let [[file] opts]
                  (->> *floorsheet-url*
                       (page-urls)
                       (req-downloads)
                       (collect-into-atom<!!)
                       deref
                       (write-data! file)))

        "-mongo" (let [[m-uri m-coll] opts
                       m-conn (mongo-connect m-uri)
                       m-db (:db m-conn)]

                   (->> *floorsheet-url*
                        (page-urls)
                        (req-downloads)
                        (save-to-mongo<!! m-db m-coll))))

      ; else
      (let []
        (println "Usage:")
        (println "lein run -mongo mongodb://server:port/db coll")
        (println "lein run -file file.json")))

    (println "** DONE **")
    ))

;(defn -main [& args]
;  (let [[out & opts] args
;        file? (= out "-file")
;        mongo? (= out "-mongo")]
;
;    (if (or file? mongo?)
;      (let [urls (page-urls)
;            page-count (count urls)
;            _ (println (str page-count " pages."))
;
;            ch (async/chan (count urls))]
;
;        ; go processes to scrape floorsheet pages.
;        (request-downloads urls ch)
;
;        (case out
;          "-file" (let [[file] opts
;                        res (atom [])]
;                    (doseq [_ urls]
;                      (println "=> atom")
;                      (swap! res into (async/<!! ch)))
;
;                    (println (str file ", Total stocks = " (count @res)))
;                    (json/generate-stream @res (clojure.java.io/writer file)))
;
;          "-mongo" (let [[m-uri m-coll] opts
;                         m-conn (mongo-connect m-uri)
;                         m-db (:db m-conn)]
;                     ; use threads for saving data
;                     ; uses future as callback technique suggested in Joy of Clojure book.
;                     (let [fs (for [_ urls]
;                                (future
;                                  (let [data (async/<!! ch)]
;                                    (save! m-db m-coll data)
;                                    (println (str (count data) " docs saved.")))))]
;
;                       ; wait till all futures completes.
;                       (doseq [f fs] @f))))
;
;        (println "** DONE **"))
;
;      ; output not supported
;      (let []
;        (println "Usage:")
;        (println "lein run -mongo mongodb://server:port/db coll")
;        (println "lein run -file file.json")))
;    ))

; lein run -mongo mongodb://127.0.0.1:27017/nepse floorsheet
; lein run -file floorsheet.json