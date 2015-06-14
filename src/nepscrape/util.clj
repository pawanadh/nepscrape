(ns nepscrape.util)

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
