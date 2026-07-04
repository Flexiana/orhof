(ns orhof.mdp.inference-test
  "Tests for probabilistic inference methods."
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.mdp.inference :as inf]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

;; Classic rain/sprinkler/wet-grass Bayesian network
(def rain-bn
  (inf/make-bayesian-network
   {:rain      {:parents [] :cpd {[] {:true 0.2 :false 0.8}}}
    :sprinkler {:parents [:rain]
                :cpd {[:true]  {:true 0.01 :false 0.99}
                      [:false] {:true 0.4  :false 0.6}}}
    :wet-grass {:parents [:rain :sprinkler]
                :cpd {[:true :true]   {:true 0.99 :false 0.01}
                      [:true :false]  {:true 0.8  :false 0.2}
                      [:false :true]  {:true 0.9  :false 0.1}
                      [:false :false] {:true 0.0  :false 1.0}}}}))

(deftest test-make-bayesian-network
  (testing "BN has correct structure"
    (is (= 3 (count (:nodes rain-bn))))
    (is (= [:rain] (get-in rain-bn [:nodes :sprinkler :parents])))))

(deftest test-exact-inference
  (let [;; P(rain | wet-grass = true)
        result (inf/exact-inference rain-bn {:wet-grass :true} :rain)]
    (testing "Exact inference computes posterior"
      (is (some? (:true result)))
      (is (some? (:false result)))
      ;; P(rain|wet) should be > P(rain) = 0.2
      (is (> (:true result) 0.2))
      ;; Should sum to ~1
      (is (< (Math/abs (- (+ (:true result) (:false result)) 1.0)) 0.01)))))

(deftest test-variable-elimination
  (let [result (inf/variable-elimination rain-bn {:wet-grass :true} :rain)]
    (testing "Variable elimination matches exact inference"
      (is (> (:true result) 0.2))
      (is (< (Math/abs (- (+ (:true result) (:false result)) 1.0)) 0.01)))))

(deftest test-direct-sampling
  (let [samples (inf/direct-sampling rain-bn 5000)]
    (testing "Direct sampling produces valid assignments"
      (is (= 5000 (count samples)))
      (is (every? #(contains? % :rain) samples)))
    (testing "Marginal P(rain) ~ 0.2"
      (let [p-rain (/ (double (count (filter #(= :true (:rain %)) samples))) 5000)]
        (is (< (Math/abs (- p-rain 0.2)) 0.05))))))

(deftest test-rejection-sampling
  (let [samples (inf/rejection-sampling rain-bn {:wet-grass :true} 1000)]
    (testing "Rejection sampling conditions on evidence"
      (is (pos? (count samples)))
      (is (every? #(= :true (:wet-grass %)) samples)))))

(deftest test-likelihood-weighted-sampling
  (let [{:keys [samples weights]} (inf/likelihood-weighted-sampling rain-bn {:wet-grass :true} 2000)]
    (testing "Likelihood weighting produces weighted samples"
      (is (= 2000 (count samples)))
      (is (= 2000 (count weights)))
      ;; Some weights may be 0 (e.g., P(wet|no-rain,no-sprinkler) = 0)
      (is (some pos? weights)))))

(deftest test-gibbs-sampling
  (let [samples (inf/gibbs-sampling rain-bn {:wet-grass :true} 2000
                  :burn-in 200)]
    (testing "Gibbs sampling conditions on evidence"
      (is (pos? (count samples)))
      (is (every? #(= :true (:wet-grass %)) samples)))))
