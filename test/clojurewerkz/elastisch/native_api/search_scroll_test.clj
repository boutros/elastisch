(ns clojurewerkz.elastisch.native-api.search-scroll-test
  (:require [clojurewerkz.elastisch.native.document :as doc]
            [clojurewerkz.elastisch.native.index    :as idx]
            [clojurewerkz.elastisch.query           :as q]
            [clojurewerkz.elastisch.fixtures        :as fx]
            [clojurewerkz.elastisch.test.helpers    :as th])
  (:use clojure.test clojurewerkz.elastisch.native.response))

(th/maybe-connect-native-client)
(use-fixtures :each fx/reset-indexes fx/prepopulate-articles-index)

(deftest ^{:native true} test-basic-scan-query
  (let [index-name   "articles"
        mapping-type "article"
        response     (doc/search index-name mapping-type
                                 :query (q/query-string :query "*")
                                 :search_type "scan"
                                 :scroll "1m"
                                 :size 1)
        initial-hits  (hits-from response)
        scroll-id     (:_scroll_id response)
        scan-response (doc/scroll scroll-id :scroll "1m")
        scan-hits     (hits-from scan-response)]
    (is (any-hits? response))
    (is (= 4 (total-hits response)))
    ;; scan queries don't return any hits from the initial
    ;; search request
    (is (= 0 (count initial-hits)))
    (is (= 4 (total-hits scan-response)))
    (is (= 4 (count scan-hits)))))

(deftest ^{:native true} test-basic-scroll-query
  (let [index-name   "articles"
        mapping-type "article"
        response     (doc/search index-name mapping-type
                                 :query (q/query-string :query "*")
                                 :search_type "query_then_fetch"
                                 :scroll "1m"
                                 :size 2)
        initial-hits    (hits-from response)
        scroll-id       (:_scroll_id response)
        scroll-response (doc/scroll scroll-id :scroll "1m")
        scroll-hits     (hits-from scroll-response)]
    (is (any-hits? response))
    (is (= 4 (total-hits response)))
    (is (= 2 (count initial-hits)))
    (is (= 4 (total-hits scroll-response)))
    (is (= 2 (count scroll-hits)))))

(defn fetch-scroll-results
  [scroll-id results]
  (let [scroll-response (doc/scroll scroll-id :scroll "1m")
        hits            (hits-from scroll-response)]
    (if (seq hits)
      (recur (:_scroll_id scroll-response) (concat results hits))
      (concat results hits))))

(deftest ^{:native true} test-scroll-query-more-than-one-page
  (let [index-name   "articles"
        mapping-type "article"
        response     (doc/search index-name mapping-type
                                 :query (q/query-string :query "*")
                                 :search_type "query_then_fetch"
                                 :scroll "1m"
                                 :size 1)
        initial-hits (hits-from response)
        scroll-id    (:_scroll_id response)
        all-hits     (fetch-scroll-results scroll-id initial-hits)]
    (is (any-hits? response))
    (is (= 4 (total-hits response)))
    (is (= 4 (count all-hits)))))
