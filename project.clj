(defproject nepscrape "0.1.0-SNAPSHOT"
  :description "NEPSE data scrapper."
  :url ""
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [enlive "1.1.5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.novemberain/monger "2.1.0"]
                 [cheshire "5.5.0"]
                 [prismatic/schema "0.4.3"]
                 [clj-http "2.0.0"]
                 [clj-time "0.11.0"]
                 [com.draines/postal "1.11.3"]
                 [org.immutant/scheduling "2.0.2"]]
  :aot :all
  :main nepscrape.core
  :profiles {:dev {:plugins [[lein-dotenv "RELEASE"]
                             [lein-marginalia "0.8.0"]]}})
