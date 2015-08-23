(ns nepscrape.floorsheet
  (:require [net.cgrand.enlive-html :as html]
            [nepscrape.util :as util]
            [nepscrape.schema :refer :all])
  (:import (java.time LocalDateTime)))

(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

(def ^:dynamic *FLOORSHEET-URL* "http://www.nepalstock.com.np/floorsheet")
(def ^:dynamic *FLOORSHEET-PAGE-URL* "http://www.nepalstock.com.np/main/floorsheet/index/%s/id/desc/")

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

(defn get-page-urls
  "go to *floorsheet-url*
  parse pagination component & gets page urls."
  [url]
  (let [pager-text (-> (fetch-url url)
                       (html/select [:div.pager html/first-child])
                       ((partial map html/text))
                       first)

        ; parse pager-text to get total page count
        count-page (fn [text]
                     (-> text                               ;Page 1/80
                         (clojure.string/split #"/")        ;split on /
                         last
                         ((partial filter #(Character/isDigit %))) ;filter digits only (removes escapse chars)
                         ((partial apply str))
                         (Integer/parseInt)))

        page-count (inc (count-page pager-text))

        ; make URLs
        page-urls (->> (range 1 page-count)
                       (map #(format *FLOORSHEET-PAGE-URL* %)))
        _ (println (str (count page-urls) " pages to scrape ..."))]
    ;(conj page-urls *floorsheet-url*)                       ; conj first page
    page-urls))

(comment
  (count (get-page-urls *FLOORSHEET-URL*)))

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
(defn- idx-into-field
  "Converts a vector [idx v] into [fieldname v]"
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
  "Extracts integer value from an input string."
  ([start end str]
   (Integer/parseInt (subs str start end)))
  ([start s]
   (Integer/parseInt (subs s start))))

; extract yr, month, day and # from contract-no field
; 201506082574632 => 2015, 06, 08, 2574632
(def extract-yr (partial extract-int 0 4))
(def extract-mth (partial extract-int 4 6))
(def extract-day (partial extract-int 6 8))
(def extract-id (partial extract-int 8))

(defn- split-contract-no
  "Splits contract-no field into year, month & day.
  str = 201506082574632 => {:year 2015 :month 6 :day 8 :day-sn 2574632}"
  [str]
  (let [yr (extract-yr str)
        mth (extract-mth str)
        day (extract-day str)
        day-sn (extract-id str)]
    {:year yr :month mth :day day :day-sn day-sn}))

(comment
  (split-contract-no "201506082574632"))

; creates Floorsheet record
;("20" "201506082574632" ["SCB" "Standard Chartered Bank Limited"] "21" "44" "100" "1924" "192400.00")
;=>
;{:amount      192400.0,
; :day         8,
; :day-sn      2574632,
; :symbol      "SCB",
; :month       6,
; :scrape-date "2015-07-12T08:06:03.609",
; :contract-no "201506082574632",
; :rate        1924.0,
; :year        2015,
; :sn          20.0,
; :quantity    100.0,
; :buyer       21.0,
; :company     "Standard Chartered Bank Limited",
; :seller      44.0}
(defn- into-floorsheet
  "Creates Floorsheet record."
  [s]
  (->>
    ; map into vector of [index s-value]
    (map-indexed vector s)

    ; map index in each vector into field name
    (map idx-into-field)

    (into (hash-map))

    ; extract company and ticker symbol out of :company vector
    ((fn [m]
       (let [[symbol name] (:company m)
             temp-m (dissoc m :company)]
         (assoc temp-m :symbol symbol :company name))))

    ;; extract out year, month, day and day's SN from contract-no
    ((fn [m] (merge m (split-contract-no (:contract-no m)))))

    ; add scrape-date
    ((fn [m] (assoc m :scrape-date (str (LocalDateTime/now)))))

    (map->Floorsheet)
    ))

(comment
  (scn {:amount 192400.0, :day 8, :day-sn 2574632, :symbol "SCB", :month 6, :contract-no "201506082574632", :rate 1924.0, :year 2015, :sn 20.0, :quantity 100.0, :buyer 21.0, :company "Standard Chartered Bank Limited", :seller 44.0})
  (into-floorsheet `("20" "201506082574632" ["SCB" "Standard Chartered Bank Limited"] "21" "44" "100" "1924" "192400.00"))
  (split-contract-no "201506082574632")
  )

(defn scrape-page
  [page-url]
  (let [selector [:#home-contents :table :tr]
        nodes (fetch-url page-url)]
    (->> (html/select nodes selector)

         ; drop first 2 rows
         (drop 2)

         ; drop last 3 rows
         (drop-last 3)

         (map #(html/select % [:td]))                       ;<td> nodes
         (map #(map node-text %))                           ; get each node's text

         (map into-floorsheet)                              ; convert Floorsheet record
         )))

(comment
  ;(def nodes (fetch-url *floorsheet-url*))
  (time (scrape-page *FLOORSHEET-URL*))                     ;10.357473 msecs
  )