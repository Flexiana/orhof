(ns orhof.search.diff-test
  "Backpropagation over expression graphs against analytic derivatives,
   central differences and orhof.diff."
  (:require [clojure.test :refer :all]
            [orhof.diff :as d]
            [orhof.search.diff :as sd])
  (:import (java.util Random)))

(defn close? [x y] (< (abs (- x y)) 1e-9))

(defn numeric-gradient
  "The oracle: central differences on the value."
  [expression env]
  (into {} (for [[k v] env]
             [k (/ (- (:value (sd/backprop expression (assoc env k (+ v 1e-6))))
                      (:value (sd/backprop expression (assoc env k (- v 1e-6)))))
                   2e-6)])))

(defn random-expression [^Random rng depth]
  (if (or (zero? depth) (< (.nextDouble rng) 0.2))
    ([:x :y :z 0.5 2.0] (.nextInt rng 5))
    (let [sub #(random-expression rng (dec depth))]
      (case (.nextInt rng 6)
        0 [:+ (sub) (sub)]
        1 [:* (sub) (sub)]
        2 [:- (sub) (sub)]
        3 [:sin (sub)]
        4 [:tanh (sub)]
        5 (let [shared (sub)] [:* shared [:cos shared]])))))          ; the same node used twice

(deftest test-backprop
  (testing "xy + sin x at (2, 3)"
    (let [{:keys [value gradient]} (sd/backprop [:+ [:* :x :y] [:sin :x]] {:x 2.0 :y 3.0})]
      (is (close? (+ 6.0 (Math/sin 2.0)) value))
      (is (close? (+ 3.0 (Math/cos 2.0)) (:x gradient)))
      (is (close? 2.0 (:y gradient)))))
  (testing "every operation against its textbook derivative"
    (are [expression expected] (close? expected (:x (:gradient (sd/backprop expression {:x 0.7 :y 1.9}))))
      [:+ :x :y]      1.0
      [:- :x :y]      1.0
      [:- :y :x]     -1.0
      [:- :x]        -1.0
      [:* :x :y]      1.9
      [:* :x :x :x]   (* 3 0.7 0.7)
      [:/ :x :y]      (/ 1.0 1.9)
      [:/ :y :x]      (- (/ 1.9 (* 0.7 0.7)))
      [:sin :x]       (Math/cos 0.7)
      [:cos :x]       (- (Math/sin 0.7))
      [:exp :x]       (Math/exp 0.7)
      [:log :x]       (/ 1.0 0.7)
      [:tanh :x]      (- 1.0 (Math/pow (Math/tanh 0.7) 2))))
  (testing "a variable the expression does not use has no entry; a constant has no gradient"
    (is (= {:x 3.0} (:gradient (sd/backprop [:* :x :x] {:x 1.5 :y 2.0}))))
    (is (= {:value 7.0 :gradient {}} (sd/backprop 7.0 {:x 1.0}))))
  (testing "a new operation is one map entry"
    (let [ops (assoc sd/operations :relu {:value    #(max 0.0 %)
                                          :partials (fn [[x] _] [(if (pos? x) 1.0 0.0)])})]
      (is (= {:value 2.0 :gradient {:x 1.0}} (sd/backprop [:relu [:- :x 1.0]] {:x 3.0} :operations ops)))
      (is (= {:value 0.0 :gradient {:x 0.0}} (sd/backprop [:relu [:- :x 1.0]] {:x 0.5} :operations ops))))))

(deftest test-shared-nodes
  (testing "(x*x)*(x*x): 4x^3, whether the square is one node or two equal ones"
    (let [square [:* :x :x]]
      (is (close? (* 4 1.5 1.5 1.5) (:x (:gradient (sd/backprop [:* square square] {:x 1.5})))))
      (is (close? (* 4 1.5 1.5 1.5) (:x (:gradient (sd/backprop [:* [:* :x :x] [:* :x :x]] {:x 1.5})))))))
  (testing "s = xy, t = s + s, t*s = 2x^2y^2: gradient (4xy^2, 4x^2y)"
    (let [s [:* :x :y], t [:+ s s]]
      (is (= {:value 72.0 :gradient {:x 72.0 :y 48.0}} (sd/backprop [:* t s] {:x 2.0 :y 3.0})))))
  (testing "a shared node is evaluated once: 60 doublings are 61 nodes, not 2^60 paths"
    (let [evaluations (atom 0)
          ops         (update-in sd/operations [:+ :value] (fn [f] (fn [& xs] (swap! evaluations inc) (apply f xs))))
          doubled     (reduce (fn [e _] [:+ e e]) :x (range 60))
          {:keys [value gradient]} (sd/backprop doubled {:x 1.0} :operations ops)]
      (is (= (Math/pow 2.0 60) value (:x gradient)))
      (is (= 60 @evaluations)))))

(deftest test-backprop-against-oracles
  (let [rng (Random. 17)
        env {:x 0.7 :y -1.3 :z 0.4}]
    (testing "central differences on 300 random expression graphs: worst gap below 1e-5"
      (is (< (reduce max
                     (for [expression (repeatedly 300 #(random-expression rng 6))
                           :let [g  (:gradient (sd/backprop expression env))
                                 ng (numeric-gradient expression env)]
                           k (keys env)]
                       (abs (- (get g k 0.0) (ng k)))))
             1e-5)))
    (testing "forward-mode differentiation in orhof.diff, on an expression without shared nodes"
      (let [f       (fn [x] (d/dual+ (d/dual* x (d/dual-sin x)) (d/dual-exp x)))
            forward (d/dual-deriv (f (d/dual 0.7 1.0)))]
        (is (close? forward (:x (:gradient (sd/backprop [:+ [:* :x [:sin :x]] [:exp :x]] {:x 0.7})))))))))

(deftest test-depth
  (testing "a chain 20,000 operations deep, far beyond what recursion could reach: x + x + ... + x"
    (let [chain (reduce (fn [e _] [:+ e :x]) :x (range 20000))]
      (is (= {:value 20001.0 :gradient {:x 20001.0}} (sd/backprop chain {:x 1.0}))))))

(deftest test-training-a-network
  ;; a 2-2-1 tanh network on XOR: gradient descent is iterate, the gradient is backprop
  (let [neuron (fn [inputs weights bias] [:tanh (into [:+ bias] (map (fn [x w] [:* x w]) inputs weights))])
        net    (fn [x1 x2]
                 (neuron [(neuron [x1 x2] [:w11 :w12] :b1) (neuron [x1 x2] [:w21 :w22] :b2)] [:v1 :v2] :c))
        loss   (into [:+] (for [[x1 x2 y] [[0.0 0.0 -1.0] [0.0 1.0 1.0] [1.0 0.0 1.0] [1.0 1.0 -1.0]]]
                            (let [e [:- (net x1 x2) y]] [:* e e])))
        step   (fn [env] (merge-with - env (update-vals (:gradient (sd/backprop loss env)) #(* 0.1 %))))
        train  (fn [seed]
                 (let [rng   (Random. seed)
                       start (zipmap [:w11 :w12 :b1 :w21 :w22 :b2 :v1 :v2 :c] (repeatedly #(- (.nextDouble rng) 0.5)))]
                   [(:value (sd/backprop loss start))
                    (:value (sd/backprop loss (nth (iterate step start) 600)))]))
        runs   (mapv train [1 2 3 4 5])]
    (testing "every start begins with a loss above 3"
      (is (every? #(> (first %) 3.0) runs)))
    (testing "600 steps bring the loss below 0.01 from 4 of 5 starts; the fifth sits in a local minimum"
      (is (= 4 (count (filter #(< (second %) 0.01) runs)))))))
