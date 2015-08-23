(ns nepscrape.util
  (:require [cheshire.core :as json]))

(defn- parse-if [str fn]
  (try
    (fn str)
    (catch NumberFormatException e
      str)))

(defn parse-if-double
  "Converts str into Double if convertible otherwise returns str."
  [str]
  (parse-if str (fn [arg] (Double/parseDouble arg))))

(defn parse-if-int
  "Converts str into int if convertible otherwise returns str."
  [str]
  (parse-if str (fn [arg] (Integer/parseInt arg))))

(defn delf-if-exists
  "Deletes file if exists."
  [file]
  (if (.exists (clojure.java.io/as-file file))
    (clojure.java.io/delete-file file)))

(defn write-json! [file data]
  (json/generate-stream data (clojure.java.io/writer file)))
