(ns scim-patch.core-test
  (:require [clojure.test :refer :all]
            [scim-patch.core :as sut])
  (:import [clojure.lang ExceptionInfo]))

(def schema {:attributes
             {:userName
              {:type :string}

              :name
              {:type
               {:attributes
                {:formatted
                 {:type :string}
                 :honorificPrefix
                 {:type         :string
                  :multi-valued true}}}}

              :phoneNumbers
              {:multi-valued true
               :type
               {:attributes
                {:value
                 {:type :string}
                 :display
                 {:type :string}
                 :type
                 {:type :string}
                 :primary
                 {:type :boolean}
                 :index
                 {:type :integer}}}}

              :x509Certificates
              {:multi-valued true
               :type
               {:attributes
                {:value
                 {:type :binary}
                 :display
                 {:type :string}
                 :primary
                 {:type :boolean}}}}

              :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User
              {:type
               {:attributes
                {:employeeNumber
                 {:type :string}
                 :emails
                 {:type         :string
                  :multi-valued true}
                 :manager
                 {:type
                  {:attributes
                   {:displayName
                    {:type :string}
                    :emails
                    {:type         :string
                     :multi-valued true}}}}}}}}})

(defmacro get-ex-data
  [body]
  `(try
     ~body
     (catch ExceptionInfo e#
       (ex-data e#))))

(deftest multiple-ops
  (testing "multiple operations in one patch"
    (is (= {:userName "foo"
            :name     {:formatted "bar"}}
          (sut/patch schema {} [{:op    "add"
                                 :path  "userName"
                                 :value "foo"}
                                {:op    "add"
                                 :path  "name.formatted"
                                 :value "bar"}])))))

(deftest op-add-attr-path-level-1
  (testing "add operation, no filter, single valued, level 1"
    (is (= {:userName "bar"}
          (sut/patch schema {:userName "foo"} {:op    "add"
                                               :path  "userName"
                                               :value "bar"}))))

  (testing "add operation, no filter, multivalued, level 1"
    (is (= {:phoneNumbers [{:value "555-555-5555"
                            :type  "work"}
                           {:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}]}
            {:op    "add"
             :path  "phoneNumbers"
             :value [{:value "555-555-4444"
                      :type  "mobile"}]})))))

(deftest op-add-attr-path-level-2
  (testing "add operation, no filter, single valued, subattribute, level 2"
    (is (= {:name {:formatted "bar"}}
          (sut/patch schema {:name {:formatted "foo"}} {:op    "add"
                                                        :path  "name.formatted"
                                                        :value "bar"}))))

  (testing "add operation, no filter, single valued, uri, level 2"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:employeeNumber "12345"}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {}}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber"
             :value "12345"}))))

  (testing "add operation, no filter, multivalued, subattribute, level 2"
    (is (= {:name {:honorificPrefix ["Mr." "Dr."]}}
          (sut/patch schema {:name {:honorificPrefix ["Mr."]}}
            {:op    "add"
             :path  "name.honorificPrefix"
             :value ["Dr."]}))))

  (testing "add operation, no filter, multivalued, uri, level 2"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:emails ["test1@example.com" "test2@example.com"]}}
          (sut/patch schema {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:emails ["test1@example.com"]}}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:emails"
             :value ["test2@example.com"]})))))

(deftest op-add-attr-path-level-3
  (testing "add operation, no filter, single valued, level 3"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:displayName "Eddie Brock"}}}
          (sut/patch schema {}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.displayName"
             :value "Eddie Brock"}))))

  (testing "add operation, no filter, multivalued, level 3"
    (is (= {:urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:emails ["test1@example.com" "test2@example.com"]}}}
          (sut/patch schema {}
            {:op    "add"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.emails"
             :value ["test1@example.com" "test2@example.com"]})))))

(deftest op-add-no-path
  (testing "add operation, no path"
    (is (= {:userName "foo"
            :name     {:formatted "bar"}}
          (sut/patch schema {} {:op    "add"
                                :value {:userName "foo"
                                        :name     {:formatted "bar"}}})))))

(deftest op-add-nonexisting-target-location
  (testing "add operation: If the target location does not exist, the attribute and value are added"
    (is (= {:userName "foo"
            :name     {:formatted "bar"}}
          (sut/patch schema {:userName "foo"} {:op    "add"
                                               :path  "name"
                                               :value {:formatted "bar"}})))))

(deftest op-add-attrpath-filter
  (testing "add operation: simple attrpath filter"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type eq \"Home\"]"
             :value {:type "Home" :value "3334445555"}}))))

  (testing "add operation: bad attr path"
    (is (= {:status 400 :scimType :invalidPath}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "add"
               :path  "telephoneNumbers"
               :value [{:type "Home" :value "3334445555"}]})))))

  (testing "add operation: bad filter path"
    (is (= {:status 400 :scimType :invalidFilter}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "add"
               :path  "phoneNumbers[number eq 1]"
               :value [{:type "Home" :value "3334445555"}]})))))

  (testing "add operation: filter on scalar attribute"
    (is (= {:status 400 :scimType :invalidFilter}
          (get-ex-data
            (sut/patch schema {:userName "foo"}
              {:op    "add"
               :path  "userName[number eq 1]"
               :value "bar"})))))

  (testing "add operation: attrpath filter with subattr"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555" :display "333-444-5555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "3334445555"}]}
            {:op    "add"
             :path  "phoneNumbers[type eq \"Home\"].display"
             :value "333-444-5555"}))))

  (testing "add operation: attrpath filter with bad subattr"
    (is (= {:status 400 :scimType :invalidPath}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "add"
               :path  "phoneNumbers[type eq \"Work\"].display1"
               :value "333-444-5555"}))))))

(deftest op-add-attrpath-filter-operators
  (testing "add operation: pr operator"
    (is (= {:phoneNumbers [{:type "Cell" :value "3334445555" :display "333-444-5555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333" :display "111-222-3333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[display pr]"
             :value {:type "Cell" :value "3334445555" :display "333-444-5555"}}))))

  (testing "add operation: ne operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type ne \"Home\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: co operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type co \"or\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: sw operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type sw \"Wo\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: ew operator"
    (is (= {:phoneNumbers [{:type "Work" :value "3334445555"}
                           {:type "Home" :value "2223334444"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}
                                            {:type "Home" :value "2223334444"}]}
            {:op    "add"
             :path  "phoneNumbers[type ew \"rk\"]"
             :value {:type "Work" :value "3334445555"}}))))

  (testing "add operation: string operation on non-string value"
    (is (= {:status 400 :scimType :invalidFilter}
          (get-ex-data
            (sut/patch schema {:x509Certificates [{:primary true}]}
              {:op   "add"
               :path "x509Certificates[primary co \"true\"]"})))))

  (testing "add operation: gt operator"
    (is (= {:phoneNumbers [{:index 1} {:index 2 :display "111-222-3333"}]}
          (sut/patch schema {:phoneNumbers [{:index 1} {:index 2}]}
            {:op    "add"
             :path  "phoneNumbers[index gt 1].display"
             :value "111-222-3333"}))))

  (testing "add operation: ge operator"
    (is (= {:phoneNumbers [{:index 0}
                           {:index 1 :display "111-222-3333"}
                           {:index 2 :display "111-222-3333"}]}
          (sut/patch schema {:phoneNumbers [{:index 0} {:index 1} {:index 2}]}
            {:op    "add"
             :path  "phoneNumbers[index ge 1].display"
             :value "111-222-3333"}))))

  (testing "add operation: lt operator"
    (is (= {:phoneNumbers [{:index 1 :display "111-222-3333"} {:index 2}]}
          (sut/patch schema {:phoneNumbers [{:index 1} {:index 2}]}
            {:op    "add"
             :path  "phoneNumbers[index lt 2].display"
             :value "111-222-3333"}))))

  (testing "add operation: gt operator"
    (is (= {:phoneNumbers [{:index 1 :display "111-222-3333"}
                           {:index 2 :display "111-222-3333"}
                           {:index 3}]}
          (sut/patch schema {:phoneNumbers [{:index 1} {:index 2} {:index 3}]}
            {:op    "add"
             :path  "phoneNumbers[index le 2].display"
             :value "111-222-3333"})))))

(deftest op-remove-missing-path
  (testing "remove operation: missing path"
    (is (= {:status 400 :scimType :noTarget}
          (get-ex-data
            (sut/patch schema {} {:op "remove"})))))

  (testing "remove operation: blank path"
    (is (= {:status 400 :scimType :noTarget}
          (get-ex-data
            (sut/patch schema {} {:op "remove" :path "     "}))))))

(deftest op-remove-single-valued-attribute
  (testing "remove operation: single valued attribute, level 1"
    (is (= {:name {:formatted "bar"}}
          (sut/patch schema {:userName "foo"
                             :name     {:formatted "bar"}}
            {:op   "remove"
             :path "userName"}))))

  (testing "remove operation: single valued attribute, level 2"
    (is (= {:userName "foo" :name {}}
          (sut/patch schema {:userName "foo"
                             :name     {:formatted "bar"}}
            {:op   "remove"
             :path "name.formatted"}))))

  (testing "remove operation: single valued attribute, level 3"
    (is (= {:userName "foo" :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {}}}
          (sut/patch schema {:userName "foo"
                             :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:displayName "Eddie Brock"}}}
            {:op   "remove"
             :path "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.displayName"})))))

(deftest op-multi-valued-no-filter
  (testing "remove operation: multi valued attribute, level 1"
    (is (= {:userName "foo"}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}]}
            {:op    "remove"
             :path  "phoneNumbers"}))))

  (testing "remove operation: multi valued attribute, level 2"
    (is (= {:userName "foo" :name {}}
          (sut/patch schema {:userName "foo"
                             :name {:honorificPrefix ["Mr." "Dr."]}}
            {:op    "remove"
             :path  "name.honorificPrefix"}))))

  (testing "remove operation: multi valued attribute, level 3"
    (is (= {:userName "foo" :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {}}}
          (sut/patch schema {:userName "foo"
                             :urn:ietf:params:scim:schemas:extension:enterprise:2.0:User {:manager {:emails ["test1@example.com" "test2@example.com"]}}
}
            {:op    "remove"
             :path  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.emails"})))))

(deftest op-remove-multi-valued-with-filter

  (testing "remove operation: multi valued attribute, value filter"
    (is (= {:userName     "foo"
            :phoneNumbers [{:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}]}
            {:op   "remove"
             :path "phoneNumbers[type eq \"work\"]"}))))

  (testing "remove operation: multi valued attribute, value filter, complex conditions"
    (is (= {:userName     "foo"
            :phoneNumbers [{:value "555-555-4444"
                            :type  "mobile"}]}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}
                                            {:value "111-222-3333"
                                             :type  "other"}]}
            {:op   "remove"
             :path "phoneNumbers[type eq \"work\" or not (value ew \"444\") and (value pr)]"}))))

  (testing "remove operation: multi valued attribute, value filter, all values removed"
    (is (= {:userName "foo"}
          (sut/patch schema {:userName     "foo"
                             :phoneNumbers [{:value "555-555-5555"
                                             :type  "work"}
                                            {:value "555-555-4444"
                                             :type  "mobile"}
                                            {:value "111-555-3333"
                                             :type  "other"}]}
            {:op   "remove"
             :path "phoneNumbers[value co \"-555-\"]"}))))

  (testing "remove operation: filter on scalar attribute"
    (is (= {:status 400 :scimType :invalidFilter}
          (get-ex-data
            (sut/patch schema {:userName "foo"}
              {:op    "remove"
               :path  "userName[number eq 1]"})))))

  (testing "remove operation: attrpath filter with subattr"
    (is (= {:phoneNumbers [{:type "Work" :value "1112223333"}
                           {:type "Home" :value "3334445555" :display "333-444-5555"}]}
          (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333" :display "111-222-3333"}
                                            {:type "Home" :value "3334445555" :display "333-444-5555"}]}
            {:op    "remove"
             :path  "phoneNumbers[type eq \"Work\"].display"}))))

  (testing "remove operation: attrpath filter with bad subattr"
    (is (= {:status 400 :scimType :invalidPath}
          (get-ex-data
            (sut/patch schema {:phoneNumbers [{:type "Work" :value "1112223333"}]}
              {:op    "remove"
               :path  "phoneNumbers[type eq \"Work\"].display1"}))))))

;;
;; negative test cases
;;

(deftest invalid-operation
  (testing "unknown operation"
    (is (= {:status 400 :scimType :invalidSyntax}
          (get-ex-data
            (sut/patch schema {} {:op "blah"}))))))

(deftest filter-parse-failure
  (testing "syntax error in value filter"
    (is (= {:status 400 :scimType :invalidPath}
          (get-ex-data
            (sut/patch schema {} {:op "add" :path "phoneNumbers[type or value]"}))))))

(deftest multi-valued-attr-in-attr-path
  (testing "multi-valued attribute in attr path"
    (is (= {:status 400 :scimType :invalidPath}
          (get-ex-data
            (sut/patch schema {} {:op "remove" :path "phoneNumbers.type"}))))))

(deftest scalar-value-for-multi-valued-attr
  (testing "add operation: scalar value for multi-valued attribute"
    (is (= {:status 400 :scimType :invalidValue}
          (get-ex-data
            (sut/patch schema {} {:op    "add"
                                  :path  "phoneNumbers"
                                  :value "blah"}))))))

(deftest filter-unsupported-comparisons
  (testing "unsupported compare operations"
    (is (= {:status 400 :scimType :invalidFilter}
          (get-ex-data
            (sut/patch schema {:x509Certificates [{:value "foo" :display "bar"}]}
              {:op   "remove"
               :path "x509Certificates[value gt \"foo\"]"}))))
    (is (= {:status 400 :scimType :invalidFilter}
          (get-ex-data
            (sut/patch schema {:x509Certificates [{:value "foo" :primary true}]}
              {:op   "remove"
               :path "x509Certificates[primary gt false]"}))))))
