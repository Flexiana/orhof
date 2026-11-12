(ns orhof.search.mdp-test
  "Forward search, branch and bound, sparse sampling and belief-space planning
   against the Bellman operator of orhof.mdp.planning and hand-written oracles."
  (:require [clojure.test :refer :all]
            [orhof.mdp.planning :as plan]
            [orhof.search.mdp :as sm]
            [orhof.test-util :as tu])
  (:import (java.util Random)))

(use-fixtures :each tu/with-seeded-rand)   ; deterministic rand for sparse sampling

;; 4x4 grid: -1 a step until the corner [3 3], gamma 0.9
(def grid-mdp
  (plan/make-mdp (for [r (range 4) c (range 4)] [r c])
                 (fn [_] [:up :down :left :right])
                 (fn [[r c] a]
                   {(case a
                      :up    [(max 0 (dec r)) c]
                      :down  [(min 3 (inc r)) c]
                      :left  [r (max 0 (dec c))]
                      :right [r (min 3 (inc c))]) 1.0})
                 (fn [s _] (if (= s [3 3]) 0.0 -1.0))
                 0.9))

(defn random-mdp [^Random rng n-states n-actions]
  (let [states (vec (range n-states))
        T (into {} (for [s states a (range n-actions)]
                     [[s a] (let [succ (vec (distinct (repeatedly (inc (.nextInt rng 3)) #(.nextInt rng n-states))))
                                  ws   (repeatedly (count succ) #(inc (.nextInt rng 4)))
                                  z    (reduce + ws)]
                              (zipmap succ (map #(/ (double %) z) ws)))]))
        R (into {} (for [s states a (range n-actions)] [[s a] (- (.nextInt rng 21) 10.0)]))]
    (plan/make-mdp states (constantly (range n-actions)) (fn [s a] (T [s a])) (fn [s a] (R [s a])) 0.9)))

(defn sweeps
  "The oracle: k applications of the Bellman operator to the zero function."
  [{:keys [states] :as mdp} k]
  (let [bellman (plan/bellman-operator mdp)]
    (nth (iterate (fn [V] (zipmap states (map (bellman V) states)))
                  (zipmap states (repeat 0.0)))
         k)))

(defn close? [x y] (< (abs (- x y)) 1e-9))

(def mdps (delay (let [rng (Random. 5)] (vec (repeatedly 30 #(random-mdp rng 6 3))))))

(deftest test-forward-search
  (testing "the grid: four steps cost 1 + 0.9 + 0.81 + 0.729"
    (let [{:keys [action value]} (sm/forward-search grid-mdp [0 0] :depth 4)]
      (is (close? -3.439 value))
      (is (contains? #{:up :down :left :right} action))))
  (testing "next to the goal it steps onto it"
    (is (= :right (:action (sm/forward-search grid-mdp [3 2] :depth 3)))))
  (testing "depth 0 is the leaf estimate"
    (is (= {:value 7.0} (sm/forward-search grid-mdp [0 0] :depth 0 :leaf (constantly 7.0)))))
  (testing "depth d = d Bellman sweeps, on 30 random stochastic MDPs"
    (is (every? (fn [mdp]
                  (every? (fn [d]
                            (let [V (sweeps mdp d)]
                              (every? #(close? (V %) (:value (sm/forward-search mdp % :depth d))) (:states mdp))))
                          [1 2 4]))
                @mdps)))
  (testing "its action is greedy with respect to one sweep less"
    (is (every? (fn [mdp]
                  (let [V (sweeps mdp 2)]
                    (every? (fn [s]
                              (close? (plan/lookahead mdp V s (:action (sm/forward-search mdp s :depth 3)))
                                      (:value (sm/forward-search mdp s :depth 3))))
                            (:states mdp))))
                @mdps)))
  (testing "memoisation: 16 states x 12 depths, not 4^12 paths"
    (let [calls (atom 0)
          mdp   (update grid-mdp :reward (fn [r] (fn [s a] (swap! calls inc) (r s a))))]
      (sm/forward-search mdp [0 0] :depth 12)
      (is (< @calls (* 16 4 12)))))
  (testing "no depth is too deep: 1,000 steps ahead is finite-horizon dynamic programming"
    (is (close? -4.68559 (:value (sm/forward-search grid-mdp [0 0] :depth 1000))))))

(deftest test-branch-and-bound
  (let [evals    (atom 0)
        counting (fn [mdp] (update mdp :reward (fn [r] (fn [s a] (swap! evals inc) (r s a)))))
        run      (fn [f] (reset! evals 0) [(:value (f)) @evals])
        results  (for [mdp @mdps
                       :let [V-pi (plan/policy-evaluation mdp (constantly 0) :tol 1e-10 :max-iter 5000)
                             V*   (:V (plan/value-iteration mdp :tol 1e-10 :max-iter 5000))
                             q*   (fn [s a] (plan/lookahead mdp V* s a))
                             hi   (/ 10.0 (- 1 0.9))                    ; no return can exceed r-max / (1 - gamma)
                             [v-fs n-fs]       (run #(sm/forward-search (counting mdp) 0 :depth 4 :leaf V-pi))
                             [v-loose n-loose] (run #(sm/branch-and-bound (counting mdp) 0 V-pi (constantly hi) :depth 4))
                             [v-tight n-tight] (run #(sm/branch-and-bound (counting mdp) 0 V-pi q* :depth 4))]]
                   {:equal (and (close? v-fs v-loose) (close? v-fs v-tight))
                    :fs n-fs :loose n-loose :tight n-tight})]
    (testing "the same values as forward search on 30 random MDPs"
      (is (every? :equal results)))
    (testing "a bound that cannot prune costs nothing extra"
      (is (= (reduce + (map :fs results)) (reduce + (map :loose results)))))
    (testing "the tightest bound, Q*, saves action evaluations"
      (is (< (reduce + (map :tight results)) (reduce + (map :fs results)))))))

(deftest test-sparse-sampling
  (testing "on a deterministic MDP one sample is exact"
    (is (close? (:value (sm/forward-search grid-mdp [0 0] :depth 5))
                (:value (sm/sparse-sampling grid-mdp [0 0] :depth 5 :n-samples 1)))))
  (testing "400 samples per action come within 0.5 of the exact depth-2 value"
    (let [mdp    (random-mdp (Random. 9) 5 2)
          exact  (:value (sm/forward-search mdp 0 :depth 2))
          approx (:value (sm/sparse-sampling mdp 0 :depth 2 :n-samples 400))]
      (is (< (abs (- exact approx)) 0.5))))
  (testing "it returns an action of the MDP"
    (is (contains? #{0 1} (:action (sm/sparse-sampling (random-mdp (Random. 9) 5 2) 0 :depth 2 :n-samples 5))))))

;; =============================================================
;; POMDP: the tiger problem
;; =============================================================

(def tiger
  {:actions     (constantly [:listen :open-left :open-right])
   :transition  (fn [s a] (if (= a :listen) {s 1.0} {:left 0.5 :right 0.5}))
   :observation (fn [a s']
                  (if (= a :listen)
                    (if (= s' :left) {:hear-left 0.85 :hear-right 0.15} {:hear-left 0.15 :hear-right 0.85})
                    {:hear-left 0.5 :hear-right 0.5}))
   :reward      (fn [s a]
                  (cond (= a :listen) -1.0
                        (= a (if (= s :left) :open-left :open-right)) -100.0
                        :else 10.0))
   :gamma       0.95})

(defn tiger-value
  "The oracle, written out by hand: p = P(tiger behind the left door)."
  [p d]
  (if (zero? d)
    0.0
    (let [listen     (let [pl (+ (* 0.85 p) (* 0.15 (- 1 p))), pr (- 1 pl)]
                       (+ -1.0 (* 0.95 (+ (* pl (tiger-value (/ (* 0.85 p) pl) (dec d)))
                                          (* pr (tiger-value (/ (* 0.15 p) pr) (dec d)))))))
          reset      (* 0.95 (tiger-value 0.5 (dec d)))
          open-left  (+ (* p -100.0) (* (- 1 p) 10.0) reset)
          open-right (+ (* p 10.0) (* (- 1 p) -100.0) reset)]
      (max listen open-left open-right))))

(deftest test-belief-mdp
  (let [plan (fn [p d] (sm/forward-search (sm/belief-mdp tiger) {:left p :right (- 1 p)} :depth d))]
    (testing "a belief step is a Bayes update: hearing the tiger on the left makes left 0.85 likely"
      (let [successors ((:transition (sm/belief-mdp tiger)) {:left 0.5 :right 0.5} :listen)]
        (is (= 2 (count successors)))
        (is (close? 1.0 (reduce + (vals successors))))
        (is (some #(close? 0.85 (:left %)) (keys successors)))))
    (testing "forward search in belief space = the hand-written recursion, depths 1 to 5"
      (is (every? (fn [[p d]] (close? (:value (plan p d)) (tiger-value p d)))
                  (for [p [0.5 0.3 0.9 0.97] d [1 2 3 4 5]] [p d]))))
    (testing "listen when unsure, open the other door when sure"
      (is (= [:listen :open-right :open-left]
             [(:action (plan 0.5 4)) (:action (plan 0.99 4)) (:action (plan 0.01 4))])))))

;; =============================================================
;; Algorithms compose
;; =============================================================

(deftest test-composition-with-value-iteration
  (testing "every grid state can be reached from the middle"
    (is (= 16 (count (sm/reachable-states grid-mdp [2 2])))))
  (let [mdp (assoc grid-mdp :states (sm/reachable-states grid-mdp [2 2]))   ; explore feeds value iteration
        V   (:V (plan/value-iteration mdp))]
    (testing "value iteration over the reachable states"
      (is (close? -4.68559 (V [0 0]))))
    (testing "with the converged values at the horizon, forward search is exact at any depth"
      (is (every? #(< (abs (- -4.68559 (:value (sm/forward-search grid-mdp [0 0] :depth % :leaf V)))) 1e-5)
                  [1 2 3])))
    (testing "and so is branch and bound, with those values as both bounds"
      (is (< (abs (- -4.68559
                     (:value (sm/branch-and-bound grid-mdp [0 0] V (fn [s a] (plan/lookahead grid-mdp V s a))
                                                  :depth 3))))
             1e-5)))))
