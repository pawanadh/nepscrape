(ns nepscrape.core
  (:require
    [clojure.core.async :as async]
    [nepscrape.mongo :refer :all]
    [nepscrape.shareprice :refer :all]
    [nepscrape.floorsheet :refer :all]
    [nepscrape.util :as u]
    [nepscrape.mailer :refer [send-email def-email def-email-pass]]
    ;[nepscrape.mailer :as m]
    [immutant.scheduling :refer [schedule cron]])
  (:import (nepscrape.core.SToday)))

;(defn save! [mdb mcoll s]
;  (doseq [m s]
;    (upsert-floorsheet! mdb mcoll m :contract-no)))

;(defn save-share-price!
;  "Saves share prices."
;  [mdb mcoll s]
;  (doseq [m s]
;    (upsert-shareprice! mdb mcoll m)))

(defn req-page-scrape
  "Starts go processes for each url. Returns a seq of channels."
  [urls]
  (for [url urls]
    (async/go
      (let [data (scrape-page url)
            _ (println (str url " = " (count data)))]
        data))))

(defn scrape-floorsheet
  "Scrapes floorsheet data."
  []
  (->> *FLOORSHEET-URL*                                     ;main page url
       get-page-urls                                        ;parse and get page urls
       req-page-scrape                                      ;start go processes to scrape pages, one go block per page
       async/merge                                          ;merge page channels into single channel
       (async/into [])                                      ;collect into a vec
       async/<!!
       flatten))

(comment
  (def fs-data (scrape-floorsheet))
  (def share (stocks-today fs-data)))

(defn scrape-save-file!
  []
  (let [floorsheets (scrape-floorsheet)
        stocks (stocks-today floorsheets)
        fs-file "floorsheet.json"
        sp-file "shareprice.json"]
    (do
      ; delete files
      (u/delf-if-exists fs-file)
      (u/delf-if-exists sp-file)

      (u/write-json! fs-file floorsheets)
      (u/write-json! sp-file stocks)

      (println (str "Data saved in files - " fs-file " and " sp-file)))))

(comment
  (scrape-save-file!))

(defn scrape-save-mongodb!
  [m-uri]
  (let [mconn (mongo-connect m-uri)
        mdb (:db mconn)
        floorsheets (scrape-floorsheet)
        stocks (stocks-today floorsheets)]
    (do
      (upsert-floorsheet! mdb "floorsheet" floorsheets)
      (upsert-shareprice! mdb "shareprice" stocks))))

(defn scrape-email!
  "Scrape save to a file and email files."
  [from pass to]
  (do
    (scrape-save-file!)
    (send-email from pass to "floorsheet.json" "shareprice.json")))

(comment
  (scrape-email! def-email def-email-pass "tlk.thp.mgr@gmail.com"))

; *
(defn -main
  [& args]
  {:pre []}
  (let [[out & opts] args
        file? (and (= out "-file") (= 1 1))
        mongo? (and (= out "-mongo") (= 2 2))
        email? (and (= out "-email") (= 3 (count opts)))]

    (if (or file? mongo? email?)
      (case out
        "-file" (scrape-save-file!)

        "-mongo" (let [[m-uri m-coll] opts]
                   (scrape-save-mongodb! m-uri))
        "-email" (let [[from pass to] opts]
                   (scrape-email! from pass to)))

      ; else
      (let []
        (println "Usage:")
        (println "lein run -mongo mongodb://server:port/db")
        (println "lein run -file")
        (println "lein run -email from password to")))

    (println "** DONE **")
    ))

;(defn save-to-mongo<!!
;  "Saves data available in channels into MongoDB. It does one <!! per channel.
;  Starts a future for channel and blocks till all futures are complete."
;  [mongo-db mongo-coll chs]
;  (let [fs (for [ch chs]
;             (future
;               (let [data (async/<!! ch)]
;                 (save! mongo-db mongo-coll data)
;                 (println (str (count data) " docs => MonogoDB.")))))]
;    (println (str "Futures = " (count fs)))
;    (doseq [f fs] @f)))

;(defn save-stocks-to-mongo!!
;  [mongo-db mongo-coll atm]
;  (-> @atm
;      (stocks-today)
;      (save! mongo-db mongo-coll)))

;(defn collect-into-atom<!!
;  "Collects data from a seq of channels into an atom and returns that atom.
;  It performs one  <!! per channel."
;  [chs]
;  (let [res (atom [])]
;    (doseq [ch chs]
;      (swap! res into (async/<!! ch)))
;    res))


;; Jobs Scheduling

(def ^:dynamic *from* def-email)
(def ^:dynamic *from-pass* def-email-pass)
(def ^:dynamic *to* "tlk.thp.mgr@gmail.com")

(defn scrape-email-job
  []
  (scrape-email! *from* *from-pass* *to*))

(comment
  (schedule scrape-email-job (cron "0 03 8 ? * *")))