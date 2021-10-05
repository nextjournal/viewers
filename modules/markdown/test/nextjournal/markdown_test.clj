(ns nextjournal.markdown-test
  (:require [clojure.test :refer :all]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.data :as md.data]))

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
                                 {:content [{:text "strong" :type :text}]
                                  :type :strong}
                                 {:text " "
                                  :type :text}
                                 {:content [{:text "assertion" :type :text}]
                                  :type :em}]
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


(deftest ->hiccup-toc-test
  "Builds Toc"

  (let [md "# Title

## Section 1

[[TOC]]

## Section 2

### Section 2.1
"
        data (md/parse md)
        hiccup (md.data/->hiccup data)]

    (is (= {:content [{:content [{:text "Title"
                                  :type :text}]
                       :heading-level 1
                       :type :heading}
                      {:content [{:text "Section 1"
                                  :type :text}]
                       :heading-level 2
                       :type :heading}
                      {:type :toc}
                      {:content [{:text "Section 2"
                                  :type :text}]
                       :heading-level 2
                       :type :heading}
                      {:content [{:text "Section 2.1"
                                  :type :text}]
                       :heading-level 3
                       :type :heading}]
            :toc {:content [{:content [{:level 2
                                        :path [:content 1]
                                        :title "Section 1"
                                        :title-hiccup [:h2 "Section 1"]
                                        :type :toc}
                                       {:content [{:level 3
                                                   :path [:content 4]
                                                   :title "Section 2.1"
                                                   :title-hiccup [:h3 "Section 2.1"]
                                                   :type :toc}]
                                        :level 2
                                        :path [:content 3]
                                        :title "Section 2"
                                        :title-hiccup [:h2 "Section 2"]
                                        :type :toc}]
                             :level 1
                             :path [:content 0]
                             :title "Title"
                             :title-hiccup [:h1 "Title"]
                             :type :toc}]
                  :type :toc}
            :type :doc}
          data))

    (is (= [:div
            [:h1
             "Title"]
            [:h2
             "Section 1"]
            [:div.toc
             [:div
              nil
              [:ul
               [:li.toc-item
                [:div
                 [:h1
                  "Title"]
                 [:ul
                  [:li.toc-item
                   [:div
                    [:h2
                     "Section 1"]
                    nil]]
                  [:li.toc-item
                   [:div
                    [:h2
                     "Section 2"]
                    [:ul
                     [:li.toc-item
                      [:div
                       [:h3
                        "Section 2.1"]
                       nil]]]]]]]]]]]
            [:h2
             "Section 2"]
            [:h3
             "Section 2.1"]]
          hiccup))))

(comment
  (run-tests 'nextjournal.markdown-test)
  )
