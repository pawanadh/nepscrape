(ns nepscrape.mailer
  (:require [postal.core :refer [send-message]])
  (:import (java.time LocalDateTime)
           (java.io File)))

(def def-email "")
(def def-email-pass "")

(defn send-email
  ([from pass to & attachments]
   (let [body-attachments (into [] (->> attachments
                                        (map (fn [f]
                                               {:type         :attachment
                                                :content      (File. f)
                                                :content-type "application/json"}))))
         body-type {:type    "text/plain"
                    :content "Please find today's NEPSE data attached herewith."}

         body (conj [] body-type body-attachments)

         subject (str "NEPSE today - " (LocalDateTime/now))

         conn {:host "smtp.gmail.com"
               :ssl  true
               :user from
               :pass pass}]
     (send-message conn {:from    from
                         :to      to
                         :subject subject
                         :body    body}))))
(comment
  (send-email "from@gmail.com" "pass" "to@gmail.com" "floorsheet.json" "shareprice.json"))

