(ns orhof.examples-test
  (:require [orhof.test-util :as tu]
            [clojure.test :refer :all]
            [orhof.examples :as ex]))

(clojure.test/use-fixtures :each tu/with-seeded-rand)   ; deterministic rand

(deftest test-meta-optimize-adam
  (let [result (ex/meta-optimize-adam)]
    (testing "Meta-optimizer finds valid hyperparameters"
      (is (vector? (:x result)))
      (is (= 3 (count (:x result))))
      (is (number? (:value result))))))

(deftest test-adversarial-rl
  (let [Q (ex/adversarial-rl)]
    (testing "Adversarial RL produces a Q-table"
      (is (map? Q))
      (is (not (empty? Q))))))

(deftest test-mcts-reachability
  (let [action (ex/mcts-reachability)]
    (testing "MCTS Reachability selects a valid action"
      (is (number? action))
      (is (contains? #{-1.0 0.0 1.0} action)))))

(deftest test-automated-scientist
  (let [result (ex/automated-scientist)]
    (testing "Automated scientist finds constants close to truth [2.5, 1.2]"
      (let [[c1 c2] (:x result)]
        (is (< (Math/abs (- c1 2.5)) 0.5))
        (is (< (Math/abs (- c2 1.2)) 0.5))))))
