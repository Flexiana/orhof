(ns orhof.mdp.learning-test
  "Tests for learning algorithms."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.mdp.learning :as learn]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

(deftest test-mle
  (let [;; Estimate mean of normal-like data
        data [1.0 2.0 3.0 4.0 5.0]
        result (learn/mle (fn [theta data-point]
                            ;; log-likelihood: -(x - theta)^2
                            (- (* (- data-point theta) (- data-point theta))))
                          data [0.0] :max-iter 200)]
    (testing "MLE finds sample mean"
      (is (< (Math/abs (- (first (:x result)) 3.0)) 0.5)))))

(deftest test-bayesian-update
  (let [;; Beta-Bernoulli: observe 7 heads out of 10
        posterior (learn/bayesian-update
                    {:alpha 1.0 :beta 1.0}  ;; uniform prior
                    [1 1 1 1 1 1 1 0 0 0]   ;; data
                    (fn [prior datum]
                      (if (= datum 1)
                        (update prior :alpha + 1.0)
                        (update prior :beta + 1.0))))]
    (testing "Bayesian update accumulates evidence"
      (is (= 8.0 (:alpha posterior)))
      (is (= 4.0 (:beta posterior))))))

(deftest test-kde
  (let [;; Fit KDE to data from ~N(0, 1)
        data [-1.0 -0.5 0.0 0.5 1.0]
        density (learn/kde data :bandwidth 0.5)]
    (testing "KDE density is highest near data center"
      (is (> (density 0.0) (density 5.0))))))

(deftest test-em
  (let [;; Simple 2-component mixture: data from two clusters
        data [0.9 1.1 1.0 0.8 1.2   ;; cluster 1 near 1.0
              4.8 5.1 5.0 4.9 5.2]  ;; cluster 2 near 5.0
        result (learn/em-gmm data 2 :max-iter 50)]
    (testing "EM finds two cluster means"
      (let [means (sort (:means result))]
        (is (< (Math/abs (- (first means) 1.0)) 1.0))
        (is (< (Math/abs (- (second means) 5.0)) 1.0))))))

(deftest test-k2-search
  (let [;; Simple structure learning: A -> B (B depends on A)
        data [{:a 1 :b 1} {:a 1 :b 1} {:a 0 :b 0} {:a 0 :b 0}
              {:a 1 :b 1} {:a 0 :b 0} {:a 1 :b 0} {:a 0 :b 1}]
        result (learn/k2-search [:a :b] data :max-parents 1)]
    (testing "K2 finds some structure"
      (is (map? result))
      (is (contains? result :b)))))
