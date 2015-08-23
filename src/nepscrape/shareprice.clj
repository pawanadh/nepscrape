(ns nepscrape.shareprice
  (:require [schema.core :as s]
            [nepscrape.schema :refer :all]))

(def asc compare)
(def desc #(compare %2 %1))

(s/defn min-max-price
  [a-comp-floorsheets :- FloorsheetVec]
  (let [sorted (sort-by :rate a-comp-floorsheets)
        min (first sorted)                                  ; todays's minimum
        max (last sorted)]                                  ; today's maximum
    [(:rate min) (:rate max)]))

(s/defn closing-price
  [a-comp-floorsheets :- FloorsheetVec]
  (let [sorted (->> a-comp-floorsheets
                    (sort-by :contract-no desc))]
    (:rate (first sorted))))

(s/defn traded-shares
  [a-comp-floorsheets :- FloorsheetVec]
  (->> a-comp-floorsheets
       (map :quantity)
       (reduce +)))

(s/defn total-amount
  [a-comp-floorsheets :- FloorsheetVec]
  (->> a-comp-floorsheets
       (map :amount)
       (reduce +)))

(s/defn txs-count
  [company-floorsheets :- FloorsheetVec]
  (count company-floorsheets))

(s/defn same-company?
  "Checks if all Floorsheets are of same company."
  [floorsheets :- FloorsheetVec]
  (= 1 (->> floorsheets
            (map #(:symbol %))
            (into #{})
            count)))

(s/defn stock-today
  [a-comp-fss :- FloorsheetVec]
  {:pre [(same-company? a-comp-fss)]}
  (let [r (first a-comp-fss)
        company (:company r)
        symbol (:symbol r)
        year (:year r)
        month (:month r)
        day (:day r)
        txs-count (txs-count a-comp-fss)
        [min max] (min-max-price a-comp-fss)
        closing (closing-price a-comp-fss)
        tot-amt (total-amount a-comp-fss)
        traded-shares (traded-shares a-comp-fss)]
    (map->StockToday {:company       company
                      :symbol        symbol
                      :year          year
                      :month         month
                      :day           day
                      :txs-count     txs-count
                      :min           min
                      :max           max
                      :closing       closing
                      :traded-shares traded-shares
                      :tot-amt       tot-amt})))

(s/defn stocks-today
  [floorsheets :- FloorsheetVec]
  (->> floorsheets
       (group-by :symbol)
       vals
       (map stock-today)))

;(s/validate {:a s/Str :b s/Num} {:a "tilak" :b 90.9})
;
;(s/check Floorsheet (map->Floorsheet {:amount      12480.0,
;                                      :day         9,
;                                      :day-sn      2612616,
;                                      :symbol      "ADBL",
;                                      :month       7,
;                                      :scrape-date "2015-07-09T11:17:29.930",
;                                      :contract-no "201507092612616",
;                                      :rate        416.0,
;                                      :year        2015,
;                                      :sn          79,
;                                      :quantity    30.0,
;                                      :buyer       1,
;                                      :company     "Agriculture Development Bank Limited",
;                                      :seller      36}))
(def cfs [(map->Floorsheet {:amount      12480.0,
                            :day         9,
                            :day-sn      2612616,
                            :symbol      "ADBL",
                            :month       7,
                            :scrape-date "2015-07-09T11:17:29.930",
                            :contract-no "201507092612616",
                            :rate        416.0,
                            :year        2015,
                            :sn          79,
                            :quantity    30.0,
                            :buyer       1,
                            :company     "Agriculture Development Bank Limited",
                            :seller      36})
          (map->Floorsheet {:amount      12480.0,
                            :day         9,
                            :day-sn      2612616,
                            :symbol      "ADBL",
                            :month       7,
                            :scrape-date "2015-07-09T11:17:29.930",
                            :contract-no "201507092612616",
                            :rate        416.0,
                            :year        2015,
                            :sn          79,
                            :quantity    30.0,
                            :buyer       1,
                            :company     "Agriculture Development Bank Limited",
                            :seller      36})])
