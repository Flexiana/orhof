(ns orhof.search.learn-test
  "Decision trees, random forests, regression trees and gradient boosting."
  (:require [clojure.test :refer :all]
            [orhof.search.learn :as sl]
            [orhof.test-util :as tu])
  (:import (java.util Random)))

(use-fixtures :each tu/with-seeded-rand)   ; deterministic rand for bootstrap samples and feature draws

;; Quinlan's play-tennis table
(def tennis
  (mapv #(zipmap [:outlook :temperature :humidity :wind :play] %)
        [[:sunny :hot :high :weak :no] [:sunny :hot :high :strong :no] [:overcast :hot :high :weak :yes]
         [:rain :mild :high :weak :yes] [:rain :cool :normal :weak :yes] [:rain :cool :normal :strong :no]
         [:overcast :cool :normal :strong :yes] [:sunny :mild :high :weak :no] [:sunny :cool :normal :weak :yes]
         [:rain :mild :normal :weak :yes] [:sunny :mild :normal :strong :yes] [:overcast :mild :high :strong :yes]
         [:overcast :hot :normal :weak :yes] [:rain :mild :high :strong :no]]))

(deftest test-decision-tree
  (let [tree (sl/decision-tree tennis [:outlook :temperature :humidity :wind] :play)]
    (testing "ID3 on play-tennis grows the textbook tree"
      (is (= :outlook (:attribute tree)))
      (is (= :humidity (:attribute (get-in tree [:branches :sunny]))))
      (is (= {:label :yes} (get-in tree [:branches :overcast])))
      (is (= :wind (:attribute (get-in tree [:branches :rain])))))
    (testing "it classifies all 14 rows"
      (is (every? #(= (:play %) (sl/classify tree %)) tennis)))
    (testing "an unseen value falls back to the majority"
      (is (= :yes (sl/classify tree {:outlook :fog})))))
  (testing "rows that agree make a leaf; so do rows nothing can separate"
    (is (= {:label :a} (sl/decision-tree [{:x 1 :y :a} {:x 2 :y :a}] [:x] :y)))
    (is (= {:label :b} (sl/decision-tree [{:x 1 :y :a} {:x 1 :y :b} {:x 1 :y :b}] [:x] :y))))
  (testing ":choose narrows the attributes of every split"
    (is (= :wind (:attribute (sl/decision-tree tennis [:outlook :temperature :humidity :wind] :play
                                               :choose (fn [attributes] (filter #{:wind} attributes))))))))

(defn noisy-rule-rows
  "Rows that follow the rule a = b or c = 2, with 10% of the labels flipped."
  [^Random rng n]
  (vec (repeatedly n #(let [row   (zipmap [:a :b :c :d :e :f] (repeatedly (fn [] (.nextInt rng 3))))
                            truth (if (or (= (:a row) (:b row)) (= 2 (:c row))) :yes :no)]
                        (assoc row
                               :truth truth
                               :label (if (< (.nextDouble rng) 0.1) ({:yes :no :no :yes} truth) truth))))))

(deftest test-random-forest
  (let [attributes [:a :b :c :d :e :f]
        trial      (fn [seed]
                     (let [rng      (Random. seed)
                           train    (noisy-rule-rows rng 300)
                           test     (noisy-rule-rows rng 300)
                           tree     (sl/decision-tree train attributes :label)
                           forest   (tu/with-seeded-rand seed
                                      #(sl/random-forest train attributes :label :n-trees 30 :n-features 3))
                           accuracy (fn [predict] (/ (count (filter #(= (:truth %) (predict %)) test)) 300.0))]
                       {:tree (accuracy #(sl/classify tree %)) :forest (accuracy #(sl/classify-forest forest %))}))
        results    (mapv trial [1 2 3 4 5])]
    (testing "on a noisy rule the forest is closer to the noise-free rule than one tree, on each of 5 seeds"
      (is (every? (fn [{:keys [tree forest]}] (> forest tree)) results)))
    (testing "by at least four points of accuracy on average"
      (is (> (- (/ (reduce + (map :forest results)) 5) (/ (reduce + (map :tree results)) 5)) 0.04))))
  (testing "the defaults: 25 trees, each a decision tree"
    (let [forest (sl/random-forest tennis [:outlook :temperature :humidity :wind] :play)]
      (is (= 25 (count forest)))
      (is (every? #(or (:attribute %) (:label %)) forest))
      (is (contains? #{:yes :no} (sl/classify-forest forest (first tennis)))))))

(deftest test-regression-tree
  (testing "a step function is recovered exactly"
    (let [tree (sl/regression-tree [{:x 1.0 :y 5.0} {:x 2.0 :y 5.0} {:x 3.0 :y 9.0}] [:x] :y)]
      (is (= {:feature :x :threshold 2.5 :left {:value 5.0} :right {:value 9.0}} tree))
      (is (= [5.0 9.0] [(sl/predict tree {:x 1.5}) (sl/predict tree {:x 2.9})]))))
  (testing ":max-depth 1 splits once; :max-depth 0 is the mean"
    (let [rows (mapv (fn [i] {:x (double i) :y (double (* i i))}) (range 10))]
      (is (contains? (sl/regression-tree rows [:x] :y :max-depth 1) :threshold))
      (is (contains? (:left (sl/regression-tree rows [:x] :y :max-depth 1)) :value))
      (is (= {:value 28.5} (sl/regression-tree rows [:x] :y :max-depth 0)))))
  (testing ":min-rows keeps both sides of a split populated"
    (let [rows (mapv (fn [i] {:x (double i) :y (if (zero? i) 100.0 0.0)}) (range 10))]
      (is (= 0.5 (:threshold (sl/regression-tree rows [:x] :y :max-depth 1))))
      (is (= 2.5 (:threshold (sl/regression-tree rows [:x] :y :max-depth 1 :min-rows 3)))))))

(deftest test-gradient-boosting
  (let [rows     (mapv (fn [i] (let [x (* 0.05 i)] {:x x :y (+ (Math/sin x) (* 0.3 (Math/cos (* 5 x))))})) (range 200))
        mean     (/ (reduce + (map :y rows)) 200)
        mse      (fn [predict] (/ (reduce + (map #(let [e (- (:y %) (predict %))] (* e e)) rows)) 200))
        baseline (mse (constantly mean))
        model    (sl/gradient-boosting rows [:x] :y :n-rounds 200 :lr 0.1 :max-depth 2)
        curve    (mapv (fn [k] (mse #(sl/predict-boosted (update model :trees subvec 0 k) %))) [0 1 5 20 50 100 200])]
    (testing "with no trees the model is the mean"
      (is (< (abs (- baseline (first curve))) 1e-12)))
    (testing "the training error falls at every checkpoint"
      (is (apply > curve)))
    (testing "200 rounds leave under 1% of the error of the mean"
      (is (< (peek curve) (* 0.01 baseline))))))
