(ns orhof.search.graph-test
  "Shortest paths and reachability."
  (:require [clojure.test :refer :all]
            [orhof.mdp.online :as online]
            [orhof.search.graph :as sg])
  (:import (java.util Random)))

(def roads
  {:a {:b 7 :c 9 :f 14} :b {:a 7 :c 10 :d 15} :c {:a 9 :b 10 :d 11 :f 2}
   :d {:b 15 :c 11 :e 6} :e {:d 6 :f 9} :f {:a 14 :c 2 :e 9}})

(defn random-maze
  "A 20x20 grid with a quarter of the cells walled off. Returns the neighbor function."
  [^Random rng]
  (let [n     20
        walls (set (for [x (range n) y (range n)
                         :when (and (< (.nextDouble rng) 0.25) (not= [x y] [0 0]))]
                     [x y]))]
    (fn [[x y]]
      (for [[dx dy] [[1 0] [-1 0] [0 1] [0 -1]]
            :let [p [(+ x dx) (+ y dy)]]
            :when (and (every? #(< -1 % n) p) (not (walls p)))]
        [p 1]))))

(def goal [19 19])
(defn manhattan [[x y]] (+ (- 19 x) (- 19 y)))

(deftest test-shortest-path
  (testing "Dijkstra on a small road map"
    (is (= {:node :e :cost 20 :path [:a :c :f :e]} (sg/shortest-path roads :a #{:e}))))
  (testing "the start may be the goal"
    (is (= {:node :a :cost 0 :path [:a]} (sg/shortest-path roads :a #{:a}))))
  (testing "no path, no answer"
    (is (nil? (sg/shortest-path {:a {:b 1} :b {}} :a #{:z}))))
  (testing "neighbors may be pairs as well as a map"
    (is (= 3 (:cost (sg/shortest-path {:a [[:b 1] [:c 5]] :b [[:c 2]] :c []} :a #{:c})))))
  (testing "the graph may be infinite: the integers, where a step is +1 or *2"
    (is (= {:node 37 :cost 7 :path [1 2 4 8 9 18 36 37]}
           (sg/shortest-path (fn [n] [[(inc n) 1] [(* 2 n) 1]]) 1 #{37}))))
  (testing ":key says when two nodes are the same state"
    (let [expanded (atom 0)
          found    (sg/shortest-path (fn [{:keys [at]}]
                                       (swap! expanded inc)
                                       (for [[to cost] (roads at)] [{:at to :visited-at (System/nanoTime)} cost]))
                                     {:at :a}
                                     #(= :e (:at %))
                                     :key :at)]
      (is (= 20 (:cost found)))
      (is (<= @expanded (count roads))))))

(deftest test-a-star
  (let [rng    (Random. 2)
        mazes  (vec (repeatedly 60 #(random-maze rng)))
        runs   (map (fn [neighbors]
                      (let [n-plain (atom 0), n-guided (atom 0)
                            count-into (fn [counter] (fn [p] (swap! counter inc) (neighbors p)))]
                        {:plain    (sg/shortest-path (count-into n-plain) [0 0] #{goal})
                         :guided   (sg/shortest-path (count-into n-guided) [0 0] #{goal} :heuristic manhattan)
                         :n-plain  @n-plain
                         :n-guided @n-guided}))
                    mazes)]
    (testing "with a consistent heuristic A* finds the same cost as Dijkstra on 60 random mazes"
      (is (every? #(= (:cost (:plain %)) (:cost (:guided %))) runs))
      (is (< 10 (count (filter :plain runs)) 60) "some mazes have a path, some do not"))
    (testing "and expands fewer nodes"
      (is (< (reduce + (map :n-guided runs)) (reduce + (map :n-plain runs)))))
    (testing "the same cost as orhof.mdp.online/a-star"
      (is (every? (fn [[neighbors run]]
                    (or (nil? (:plain run))
                        (= (double (:cost (:plain run)))
                           (:cost (online/a-star [0 0] #(map first (neighbors %)) (constantly 1.0) manhattan #{goal})))))
                  (take 10 (map vector mazes runs)))))))

(deftest test-reachable
  (is (= #{:a :b :c} (sg/reachable {:a [:b] :b [:a :c] :d [:a]} :a)))
  (is (= #{:d :a :b :c} (sg/reachable {:a [:b] :b [:a :c] :d [:a]} :d)))
  (testing "several roots"
    (is (= #{1 2 3 10 11} (sg/reachable {1 [2] 2 [3] 10 [11]} 1 10))))
  (testing "a root with no successors reaches itself"
    (is (= #{:z} (sg/reachable {} :z)))))
