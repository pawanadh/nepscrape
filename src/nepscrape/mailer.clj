(ns nepscrape.mailer
  (:require [postal.core :refer [send-message]])
  (:import (java.time LocalDateTime)
           (java.io File)))

(def def-email "nepscrape@gmail.com")
(def def-email-pass "nepsescrape")

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
  (send-email "nepscrape@gmail.com" "nepsescrape" "tlk.thp.mgr@gmail.com" "floorsheet.json" "shareprice.json"))

