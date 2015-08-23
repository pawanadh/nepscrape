(ns nepscrape.schema
  (:require [schema.core :as s]))

;; data shape

(s/defrecord Floorsheet [sn :- s/Int
                         contract-no :- s/Str
                         year :- s/Int
                         month :- s/Int
                         day :- s/Int
                         day-sn :- s/Int

                         symbol :- s/Str
                         company :- s/Str

                         buyer :- s/Int
                         seller :- s/Int

                         quantity :- s/Num
                         rate :- s/Num
                         amount :- s/Num

                         scrape-date :- s/Str
                         ])
(s/def FloorsheetVec [Floorsheet])

(s/defrecord StockToday [company :- s/Str
                         symbol :- s/Str
                         year :- s/Int
                         month :- s/Int
                         day :- s/Int
                         txs-count :- s/Int
                         min :- s/Num
                         max :- s/Num
                         closing :- s/Num
                         prev-closing :- s/Num
                         traded-shares :- s/Int
                         tot-amt :- s/Num
                         diff :- s/Num])

(s/def StockTodayVec [StockToday])
