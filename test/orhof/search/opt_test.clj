(ns orhof.search.opt-test
  "Lipschitz search, DIRECT, the simplex method and integer programming
   against grid search, known minima, vertex enumeration and brute force."
  (:require [clojure.test :refer :all]
            [orhof.opt.linear :as lp]
            [orhof.search.opt :as so])
  (:import (java.util Random)))

;; =============================================================
;; Lipschitz branch and bound
;; =============================================================

(deftest test-lipschitz-min
  (let [f     (fn [x] (+ (Math/sin x) (Math/sin (/ (* 10.0 x) 3.0))))
        L     (+ 1.0 (/ 10.0 3.0))
        evals (atom 0)
        {:keys [x value] :as result} (so/lipschitz-min (fn [x] (swap! evals inc) (f x)) 2.7 7.5 L)
        grid  (reduce min (map f (range 2.7 7.5 1e-5)))]
    (testing "matches a 480,000-point grid to 1e-6"
      (is (< (abs (- value grid)) 1e-6))
      (is (= value (f x))))
    (testing "from under a fiftieth of the evaluations, which it reports"
      (is (= @evals (:evals result)))
      (is (< (:evals result) 9600))))
  (testing "a looser tolerance costs less"
    (is (< (:evals (so/lipschitz-min #(Math/sin %) 0.0 10.0 1.0 :tol 1e-2))
           (:evals (so/lipschitz-min #(Math/sin %) 0.0 10.0 1.0 :tol 1e-6))))))

;; =============================================================
;; DIRECT on the test functions of Jones, Perttunen and Stuckman
;; =============================================================

(defn branin [[x1 x2]]
  (+ (Math/pow (+ x2 (* (/ -5.1 (* 4 Math/PI Math/PI)) x1 x1) (* (/ 5 Math/PI) x1) -6) 2)
     (* 10 (- 1 (/ 1 (* 8 Math/PI))) (Math/cos x1))
     10))

(defn camel [[x y]]
  (+ (* (+ 4 (* -2.1 x x) (/ (* x x x x) 3)) x x) (* x y) (* (+ -4 (* 4 y y)) y y)))

(defn goldstein-price [[x y]]
  (* (+ 1 (* (Math/pow (+ x y 1) 2) (+ 19 (* -14 x) (* 3 x x) (* -14 y) (* 6 x y) (* 3 y y))))
     (+ 30 (* (Math/pow (- (* 2 x) (* 3 y)) 2) (+ 18 (* -32 x) (* 12 x x) (* 48 y) (* -36 x y) (* 27 y y))))))

(defn hartmann3 [x]
  (let [A [[3.0 10 30] [0.1 10 35] [3.0 10 30] [0.1 10 35]]
        P [[0.3689 0.1170 0.2673] [0.4699 0.4387 0.7470] [0.1091 0.8732 0.5547] [0.03815 0.5743 0.8828]]
        c [1.0 1.2 3.0 3.2]]
    (- (reduce + (map (fn [ci Ai Pi]
                        (* ci (Math/exp (- (reduce + (map (fn [a p xj] (* a (Math/pow (- xj p) 2))) Ai Pi x))))))
                      c A P)))))

(def problems
  [["Branin"          branin          [[-5.0 0.0] [10.0 15.0]]         0.397887]
   ["six-hump camel"  camel           [[-3.0 -2.0] [3.0 2.0]]         -1.031628]
   ["Goldstein-Price" goldstein-price [[-2.0 -2.0] [2.0 2.0]]          3.0]
   ["Hartmann 3"      hartmann3       [[0.0 0.0 0.0] [1.0 1.0 1.0]]   -3.862782]])

(deftest test-direct
  (doseq [[label f bounds optimum] problems]
    (testing (str label ": within 0.01% of the known minimum in 400 evaluations")
      (let [evals (atom 0)
            {:keys [x value] :as result} (so/direct (fn [x] (swap! evals inc) (f x)) bounds :max-evals 400)]
        (is (< (/ (- value optimum) (abs optimum)) 1e-4))
        (is (= @evals (:evals result)))
        (is (< (abs (- value (f x))) 1e-12))
        (is (every? true? (map <= (first bounds) x (second bounds)))))))
  (testing "a bigger budget never gives a worse point"
    (let [values (map #(:value (so/direct branin [[-5.0 0.0] [10.0 15.0]] :max-evals %)) [25 50 100 200])]
      (is (apply >= values)))))

;; =============================================================
;; Simplex
;; =============================================================

(defn solve-linear
  "Solves the square system M x = v exactly by Gauss-Jordan elimination; nil if singular."
  [M v]
  (let [n (count M)]
    (loop [rows (mapv conj M v), col 0]
      (if (= col n)
        (mapv peek rows)
        (when-let [p (first (filter #(not (zero? ((rows %) col))) (range col n)))]
          (let [rows  (assoc rows col (rows p) p (rows col))
                pivot (mapv #(/ % ((rows col) col)) (rows col))]
            (recur (vec (map-indexed (fn [i row]
                                       (if (= i col) pivot (mapv #(- %1 (* (row col) %2)) row pivot)))
                                     rows))
                   (inc col))))))))

(defn subsets [k xs]
  (cond (zero? k) [[]]
        (empty? xs) []
        :else (concat (map #(cons (first xs) %) (subsets (dec k) (rest xs)))
                      (subsets k (rest xs)))))

(defn vertex-enumeration
  "The oracle for bounded linear programs: the optimum is at a vertex, and a
   vertex is where n of the constraints (including x >= 0) hold with equality."
  [c A b]
  (let [n      (count c)
        rows   (into (mapv vector A b)
                     (for [j (range n)] [(assoc (vec (repeat n 0)) j -1) 0]))      ; -x_j <= 0
        ok?    (fn [x] (every? (fn [[row bi]] (<= (reduce + (map * row x)) bi)) rows))
        points (for [chosen (subsets n rows)
                     :let [x (solve-linear (mapv first chosen) (mapv second chosen))]
                     :when (and x (ok? x))]
                 x)]
    (when (seq points)
      (reduce min (map (fn [x] (reduce + (map * c x))) points)))))

(defn random-bounded-lp
  "A random LP in a box 0 <= x_j <= 6, so it is optimal or infeasible, never unbounded."
  [^Random rng n m]
  {:c (vec (repeatedly n #(- (.nextInt rng 21) 10)))
   :A (into (vec (repeatedly m (fn [] (vec (repeatedly n #(- (.nextInt rng 13) 4))))))
            (for [j (range n)] (assoc (vec (repeat n 0)) j 1)))
   :b (into (vec (repeatedly m #(- (.nextInt rng 40) 12))) (repeat n 6))})

(deftest test-linear-program
  (testing "a textbook problem: maximize 3x + 5y, x <= 4, 2y <= 12, 3x + 2y <= 18"
    (is (= {:status :optimal :x [2 6] :value -36}
           (so/linear-program [-3 -5] [[1 0] [0 2] [3 2]] [4 12 18]))))
  (testing ">= constraints, as rows with a negative right-hand side: min x + y, x + 2y >= 4, 3x + y >= 6"
    (is (= {:status :optimal :x [8/5 6/5] :value 14/5}
           (so/linear-program [1 1] [[-1 -2] [-3 -1]] [-4 -6]))))
  (testing "an equality, as a pair of rows: min x + y, x + y = 3"
    (is (= 3 (:value (so/linear-program [1 1] [[1 1] [-1 -1]] [3 -3])))))
  (testing "infeasible and unbounded problems are reported"
    (is (= {:status :infeasible} (so/linear-program [-1 -1] [[1 1] [-1 -1]] [1 -3])))
    (is (= {:status :unbounded} (so/linear-program [-1 -1] [[1 -1]] [1]))))
  (testing "a degenerate vertex does not make it cycle"
    (is (= :optimal (:status (so/linear-program [-3/4 150 -1/50 6]
                                                [[1/4 -60 -1/25 9] [1/2 -90 -1/50 3] [0 0 1 0]]
                                                [0 0 1])))))
  (testing "doubles work too"
    (let [{:keys [x value]} (so/linear-program [-3.0 -5.0] [[1.0 0.0] [0.0 2.0] [3.0 2.0]] [4.0 12.0 18.0])]
      (is (< (abs (- -36.0 value)) 1e-9))
      (is (every? true? (map #(< (abs (- %1 %2)) 1e-9) [2.0 6.0] x))))))

(deftest test-linear-program-against-oracles
  (let [rng (Random. 21)
        lps (vec (repeatedly 150 #(random-bounded-lp rng (+ 2 (.nextInt rng 2)) (+ 2 (.nextInt rng 3)))))]
    (testing "exact agreement with vertex enumeration on 150 random bounded LPs, infeasible ones included"
      (let [results (map (fn [{:keys [c A b]}]
                           (let [mine (so/linear-program c A b), truth (vertex-enumeration c A b)]
                             {:feasible (some? truth)
                              :agree    (if truth
                                          (and (= :optimal (:status mine)) (= truth (:value mine)))
                                          (= :infeasible (:status mine)))}))
                         lps)]
        (is (every? :agree results))
        (is (< 20 (count (filter :feasible results)) 140) "the sample has both kinds")))
    (testing "its solution is feasible and attains its value"
      (is (every? (fn [{:keys [c A b]}]
                    (let [{:keys [status x value]} (so/linear-program c A b)]
                      (or (not= :optimal status)
                          (and (every? #(>= % 0) x)
                               (every? true? (map (fn [row bi] (<= (reduce + (map * row x)) bi)) A b))
                               (= value (reduce + (map * c x)))))))
                  lps))))
  (testing "agrees with orhof.opt.linear/simplex where that applies (b >= 0)"
    (let [rng (Random. 22)]
      (is (every? true?
                  (repeatedly 100
                              #(let [{:keys [c A b]} (update (random-bounded-lp rng 3 3) :b (partial mapv abs))
                                     mine  (so/linear-program c A b)
                                     other (lp/simplex c A b)]
                                 (< (abs (- (:value mine) (:objective other))) 1e-6))))))))

;; =============================================================
;; Integer programming
;; =============================================================

(defn random-ip
  "A random integer program in the box 0 <= x_j <= 4."
  [^Random rng n m]
  {:c     (vec (repeatedly n #(- (.nextInt rng 15) 11)))
   :A     (into (vec (repeatedly m (fn [] (vec (repeatedly n #(- (.nextInt rng 9) 2))))))
                (for [j (range n)] (assoc (vec (repeat n 0)) j 1)))
   :b     (into (vec (repeatedly m #(+ 3 (.nextInt rng 25)))) (repeat n 4))
   :upper 4})

(defn brute-force-ip
  "The oracle: every integer point of the box."
  [{:keys [c A b upper]}]
  (let [n        (count c)
        points   (reduce (fn [ps _] (for [p ps, v (range (inc upper))] (conj p v))) [[]] (range n))
        feasible (filter (fn [x] (every? true? (map (fn [row bi] (<= (reduce + (map * row x)) bi)) A b))) points)]
    (when (seq feasible)
      (reduce min (map (fn [x] (reduce + (map * c x))) feasible)))))

(defn agrees-with-brute-force? [{:keys [c A b] :as ip}]
  (let [mine (so/integer-program c A b), truth (brute-force-ip ip)]
    (if truth
      (and (= :optimal (:status mine)) (= truth (:value mine)) (every? integer? (:x mine)))
      (= :infeasible (:status mine)))))

(deftest test-integer-program
  (testing "exact results print as plain integers and ratios"
    (is (= "{:status :optimal, :x [2 6], :value -36}"
           (pr-str (so/linear-program [-3 -5] [[1 0] [0 2] [3 2]] [4 12 18])))))
  (testing "the docstring knapsack: the relaxation says x = 3, y = 3/2; the integers say x = 4, y = 0"
    (is (= {:status :optimal :x [3 3/2] :value -21} (so/linear-program [-5 -4] [[6 4] [1 2]] [24 6])))
    (is (= {:status :optimal :x [4 0] :value -20} (so/integer-program [-5 -4] [[6 4] [1 2]] [24 6]))))
  (testing "branching rounds a fractional relaxation: max x + y, 2x + 2y <= 5"
    (is (= -2 (:value (so/integer-program [-1 -1] [[2 2]] [5])))))
  (testing "no feasible point at all"
    (is (= {:status :infeasible} (so/integer-program [-1 -1] [[1 1] [-1 -1]] [1 -3]))))
  (testing "the relaxation is feasible, the integers are not: 2x = 1"
    (is (= {:status :infeasible} (so/integer-program [1] [[2] [-2]] [1 -1]))))
  (testing "an unbounded relaxation is reported"
    (is (= {:status :unbounded} (so/integer-program [-1 -1] [[1 -1]] [1])))))

(deftest test-integer-program-against-brute-force
  (testing "100 random instances"
    (let [rng (Random. 33)]
      (is (every? agrees-with-brute-force?
                  (repeatedly 100 #(random-ip rng (+ 2 (.nextInt rng 3)) (+ 1 (.nextInt rng 3))))))))
  (testing "100 instances with a >= row, where the origin is infeasible and some have no solution"
    (let [rng (Random. 34)
          ips (vec (repeatedly 100 #(update (random-ip rng (+ 2 (.nextInt rng 2)) (+ 2 (.nextInt rng 2)))
                                            :b (fn [b] (assoc b 0 (- (inc (.nextInt rng 12))))))))]
      (is (every? agrees-with-brute-force? ips))
      (is (< 5 (count (remove brute-force-ip ips)) 95) "the sample has both kinds")))
  (testing "0/1 knapsack as an integer program = dynamic programming on 30 random instances"
    (let [rng   (Random. 42)
          trial (fn []
                  (let [n       (+ 4 (.nextInt rng 5))
                        values  (vec (repeatedly n #(+ 10 (.nextInt rng 90))))
                        weights (vec (repeatedly n #(+ 5 (.nextInt rng 45))))
                        cap     (quot (reduce + weights) 2)
                        dp      (peek (reduce (fn [best [v w]]
                                                (reduce (fn [b c] (assoc b c (max (b c) (+ v (best (- c w))))))
                                                        best (range cap (dec w) -1)))
                                              (vec (repeat (inc cap) 0)) (map vector values weights)))
                        ip      (so/integer-program (mapv - values)
                                                    (into [weights] (for [j (range n)] (assoc (vec (repeat n 0)) j 1)))
                                                    (into [cap] (repeat n 1)))]
                    (= dp (- (:value ip)))))]
      (is (every? true? (repeatedly 30 trial))))))
