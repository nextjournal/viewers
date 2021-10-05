(ns nextjournal.markdown-test
  (:require [clojure.test :refer :all]
            [nextjournal.markdown :as md]))

(def markdown-text
  "# Hello

some **strong** _assertion_

- [ ] one
- [x] two
- [ ] three
")

(deftest parse-test
  (testing "ingests markdown returns nested nodes"
    (is (= {:content [{:content [{:text "Hello"
                                  :type :text}]
                       :heading-level 1
                       :type :heading}
                      {:content [{:text "some "
                                  :type :text}
                                 {:marks [{:mark :strong}]
                                  :text "strong"
                                  :type :text}
                                 {:text " "
                                  :type :text}
                                 {:marks [{:mark :em}]
                                  :text "assertion"
                                  :type :text}]
                       :type :paragraph}
                      {:attrs {:has-todos true}
                       :content [{:attrs {:checked false
                                          :todo true}
                                  :content [{:content [{:text "one"
                                                        :type :text}]
                                             :type :paragraph}]
                                  :type :todo-item}
                                 {:attrs {:checked true
                                          :todo true}
                                  :content [{:content [{:text "two"
                                                        :type :text}]
                                             :type :paragraph}]
                                  :type :todo-item}
                                 {:attrs {:checked false
                                          :todo true}
                                  :content [{:content [{:text "three"
                                                        :type :text}]
                                             :type :paragraph}]
                                  :type :todo-item}]
                       :type :todo-list}]
            :toc {:type :toc
                  :content [{:level 1
                             :type :toc
                             :path [:content 0]
                             :title "Hello"
                             :title-hiccup [:h1 "Hello"]}]}
            :type :doc}
           (md/parse markdown-text)))))

(deftest ->hiccup-test
  "ingests markdown returns hiccup"
  (is (= [:div
          [:h1
           "Hello"]
          [:p
           "some "
           [:strong
            "strong"]
           " "
           [:em
            "assertion"]]
          [:ul
           {:data-todo-list true}
           [:li
            {"data-checked" false
             "data-todo" true}
            [:p
             "one"]]
           [:li
            {"data-checked" true
             "data-todo" true}
            [:p
             "two"]]
           [:li
            {"data-checked" false
             "data-todo" true}
            [:p
             "three"]]]]
         (md/->hiccup markdown-text))))

(comment
  (run-tests 'nextjournal.markdown-test)
  )
