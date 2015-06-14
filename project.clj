(defproject nepscrape "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [enlive "1.1.5"]
                           [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                           [com.novemberain/monger "2.1.0"]
                           [cheshire "5.5.0"]]
            :aot :all
            :main nepscrape.core
            :profiles {:dev {:plugins [[lein-dotenv "RELEASE"]
                                       [lein-marginalia "0.8.0"]]}})
