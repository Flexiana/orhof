(ns orhof.search-test
  "The contract of orhof.search: strategies, search, explore, hylo, hylo-eager,
   converge, and the laws by which they compose."
  (:require [clojure.test :refer :all]
            [clojure.zip :as zip]
            [orhof.search :refer [search explore hylo hylo-eager converge
                                  depth-first breadth-first best-first
                                  unique unique-by by-identity]])
  (:import (java.util Random)))

(def tree {:a [:b :c] :b [:d :e] :c [:f :g]})
(def graph {:a [:b :c] :b [:d] :c [:d :e] :d [:a :f] :e [:f] :f []})   ; has a cycle
(def kids (fn [n] [(* 2 n) (inc (* 2 n))]))                           ; the infinite binary tree on 1, 2, 3, ...

;; =============================================================
;; explore
;; =============================================================

(deftest explore-generalises-iterate-and-tree-seq
  (testing "iterate is the one-child case"
    (is (= (take 5 (iterate inc 0)) (take 5 (explore depth-first (comp list inc) 0)))))
  (testing "tree-seq is the depth-first case"
    (is (= (vec (tree-seq tree tree :a)) (into [] (explore depth-first tree :a)))))
  (testing "breadth-first"
    (is (= [:a :b :c :d :e :f :g] (into [] (explore breadth-first tree :a)))))
  (testing "clojure.zip is core's own search-as-iterate; explore visits the same leaves"
    (let [nested [1 [2 3] [4 [5]]]]
      (is (= (->> (zip/vector-zip nested) (iterate zip/next) (take-while (complement zip/end?))
                  (map zip/node) (remove vector?))
             (remove vector? (explore depth-first #(when (vector? %) %) nested)))))))

(deftest explore-on-graphs
  (testing "unique makes a cyclic graph finite"
    (is (= [:a :b :d :f :c :e] (into [] (explore (unique depth-first) graph :a))))
    (is (= [:a :b :c :d :e :f] (into [] (explore (unique breadth-first) graph :a)))))
  (testing "breadth-first shortest path: the nodes are paths, unique by their last stop"
    (is (= [:a :b :d :f]
           (->> (explore (unique-by peek breadth-first)
                         (fn [path] (map #(conj path %) (graph (peek path))))
                         [:a])
                (filter #(= :f (peek %)))
                first))))
  (testing "a node budget is take"
    (is (= #{:a :b :c} (into #{} (take 3) (explore (unique breadth-first) graph :a))))))

(deftest explore-is-a-clojure-collection
  (testing "reduce with an initial value"
    (is (= 10 (reduce + 0 (explore depth-first #(when (< % 4) [(inc %)]) 1)))))
  (testing "reduce without one throws, as it does for iteration and eduction"
    (is (thrown? ClassCastException (reduce + (explore depth-first #(when (< % 4) [(inc %)]) 1)))))
  (testing "transducers"
    (is (= :e (transduce (halt-when #{:e}) conj [] (explore depth-first tree :a))))
    (is (= ["a" "b" "c" "d" "e" "f" "g"] (sequence (map name) (explore breadth-first tree :a))))
    (is (= [:d :g] (into [] (eduction (filter #{:d :g}) (explore depth-first tree :a))))))
  (testing "infinite spaces"
    (is (= [1 2 3 4 5] (take 5 (explore breadth-first kids 1))))
    (is (= [1 2 4 8 16] (take 5 (explore depth-first kids 1))))
    (is (= [1 3 7 14 15 6] (take 6 (explore (best-first #(abs (- % 11))) kids 1)))))
  (testing "several roots, nil as a node"
    (is (= [:b :c :d :e :f :g] (into [] (explore breadth-first tree :b :c))))
    (is (= [:a nil :b] (into [] (explore depth-first {:a [nil :b]} :a)))))
  (testing "best-first: ties leave in insertion order; priorities may be vectors"
    (is (= ["a" "d" "bb" "cc"] (into [] (explore (best-first count) {} "bb" "a" "cc" "d"))))
    (is (= [[0 9] [1 1] [1 2]] (into [] (explore (best-first identity) {} [1 2] [0 9] [1 1])))))
  (testing "the same value can be reduced twice"
    (let [space (explore depth-first tree :a)]
      (is (= (into [] space) (into [] space))))))

(deftest explore-expands-only-what-is-consumed
  (let [calls  (atom 0)
        expand (fn [n] (swap! calls inc) (tree n))
        space  (explore depth-first expand :a)]
    (testing "as a seq, iteration looks one expansion ahead"
      (is (= [:a 1] [(first space) @calls])))
    (testing "reduced on the goal stops expanding"
      (reset! calls 0)
      (is (= [:d 2] [(reduce (fn [_ n] (when (= n :d) (reduced n))) nil space) @calls]))))
  (testing "depth-first takes one sibling at a time from a lazy successor seq"
    (let [made   (atom 0)
          expand (fn [n]
                   (when (< n 1000)
                     (letfn [(from [i] (lazy-seq (when (< i 1000) (swap! made inc) (cons (inc n) (from (inc i))))))]
                       (from 0))))]
      ;; depth 1000, branching 1000: diving to the bottom makes 1000 children, not a million
      (is (= [1000 1000] [(first (filter #(= 1000 %) (explore depth-first expand 0))) @made])))))

;; =============================================================
;; Strategies are functions over plain data
;; =============================================================

(deftest strategies-are-plain-data
  (is (= () (depth-first)))
  (is (= '([:a :b]) (depth-first (depth-first) [:a :b])))
  (is (= [:a '((:b))] (depth-first '([:a :b]))))
  (is (= {1 ["a"] 2 ["bb" "cc"]}
         (update-vals ((best-first count) ((best-first count)) ["bb" "a" "cc"]) vec)))
  (is (= #{} (first ((unique breadth-first))))))

(defn random-first
  "A new strategy is one function: no type, no protocol."
  [^Random rng]
  (fn ([] [])
      ([v] (when (seq v)
             (let [i (.nextInt rng (count v))]
               [(v i) (into (subvec v 0 i) (subvec v (inc i)))])))
      ([v nodes] (into v nodes))))

(defn at-most
  "A new strategy transformer is one function: give up after n nodes.
   The frontier becomes [taken inner-frontier]."
  [n strategy]
  (fn ([] [0 (strategy)])
      ([[taken frontier]]
       (when (< taken n)
         (when-let [[node frontier] (strategy frontier)]
           [node [(inc taken) frontier]])))
      ([[taken frontier] nodes] [taken (strategy frontier nodes)])))

(deftest strategies-compose
  (testing "a user-defined strategy visits every node once"
    (is (= [:a :b :c :d :e :f]
           (sort (into [] (explore (unique (random-first (Random. 3))) graph :a))))))
  (testing "transformers compose with comp"
    (is (= [:a :b :c :d]
           (into [] (explore ((comp (partial at-most 4) unique) breadth-first) graph :a)))))
  (testing "order matters, as with transducers: a budget inside unique counts the duplicates too"
    (is (= 6 (count (into [] (explore (at-most 6 (unique breadth-first)) graph :a)))))
    (is (= 5 (count (into [] (explore (unique (at-most 6 breadth-first)) graph :a))))))
  (testing "unique is idempotent"
    (is (= (into [] (explore (unique depth-first) graph :a))
           (into [] (explore (unique (unique depth-first)) graph :a))))))

;; =============================================================
;; Successor functions compose like any functions
;; =============================================================

(defn then [f g] (fn [node] (mapcat g (f node))))                  ; Kleisli composition
(defn pruning [prune? expand] (fn [node] (remove prune? (expand node))))
(defn to-depth [limit expand]
  (fn [[node depth]] (when (< depth limit) (map #(vector % (inc depth)) (expand node)))))

(deftest successor-functions-compose
  (testing "then: two steps at a time visits the nodes at even depth"
    (is (= [1 4 5 6 7] (into [] (take 5) (explore breadth-first (then kids kids) 1)))))
  (testing "decorators stack"
    (is (= [1 2 4 8 5 10 11]
           (into [] (map first)
                 (explore depth-first (->> kids (pruning #(zero? (mod % 3))) (to-depth 3)) [1 0])))))
  (testing "iterative deepening is `some` over depth limits"
    (let [iterative-deepening
          (fn [expand goal? root]
            (some (fn [limit]
                    (first (filter (comp goal? first) (explore depth-first (to-depth limit expand) [root 0]))))
                  (range)))]
      (is (= [37 5] (iterative-deepening kids #{37} 1)))))
  (testing "beam search is `iterate` over the beam"
    (let [beam-search (fn [expand score width root]
                        (iterate (fn [beam] (take width (sort-by score (mapcat expand beam)))) [root]))]
      (is (= [127 126] (nth (beam-search kids #(abs (- % 127)) 2 1) 6))))))

;; =============================================================
;; search
;; =============================================================

(defn knapsack
  "0/1 knapsack by branch and bound; the bound is the fractional relaxation."
  [strategy-of items capacity visited]
  (let [items    (vec (sort-by #(/ (:value %) (:weight %)) > items))
        bound    (fn [{:keys [i value room]}]
                   (loop [i i, value value, room room]
                     (if-let [{v :value w :weight} (get items i)]
                       (if (<= w room) (recur (inc i) (+ value v) (- room w)) (+ value (* v (/ room w))))
                       value)))
        children (fn [{:keys [i value room]}]
                   (when-let [{v :value w :weight} (get items i)]
                     (cond-> []
                       (<= w room) (conj {:i (inc i) :value (+ value v) :room (- room w)})
                       true        (conj {:i (inc i) :value value :room room}))))]
    (:value (search (strategy-of bound)
                    (fn [best node] (swap! visited inc) (if (> (:value node) (:value best)) node best))
                    {:value 0}
                    (fn [best node] (when (> (bound node) (:value best)) (children node)))
                    {:i 0 :value 0 :room capacity}))))

(defn knapsack-dp [items capacity]
  (peek (reduce (fn [best {:keys [value weight]}]
                  (reduce (fn [b c] (assoc b c (max (b c) (+ value (best (- c weight))))))
                          best (range capacity (dec weight) -1)))
                (vec (repeat (inc capacity) 0))
                items)))

(defn random-items [^Random rng n]
  (vec (repeatedly n #(hash-map :value (+ 10 (.nextInt rng 90)) :weight (+ 5 (.nextInt rng 45))))))

(deftest search-is-branch-and-bound
  (let [rng      (Random. 42)
        by-bound (fn [bound] (best-first (comp - bound)))]
    (testing "best-first branch and bound agrees with dynamic programming on 100 random knapsacks"
      (is (every? true?
                  (repeatedly 100 #(let [items (random-items rng (+ 5 (.nextInt rng 20)))
                                         cap   (quot (reduce + (map :weight items)) 2)]
                                     (= (knapsack by-bound items cap (atom 0)) (knapsack-dp items cap)))))))
    (testing "the strategy changes the work, not the answer"
      (let [items (random-items (Random. 7) 22)
            cap   (quot (reduce + (map :weight items)) 2)
            run   (fn [strategy-of] (let [n (atom 0)] [(knapsack strategy-of items cap n) @n]))
            [v-best n-best] (run by-bound)
            [v-dfs n-dfs]   (run (constantly depth-first))
            [v-bfs n-bfs]   (run (constantly breadth-first))]
        (is (= v-best v-dfs v-bfs (knapsack-dp items cap)))
        (is (<= n-best n-dfs))
        (is (< (* 10 n-best) n-bfs))))))

(deftest search-contract
  (testing "reduced stops the search; the space may be infinite"
    (is (= 37 (search breadth-first (fn [_ n] (when (= n 37) (reduced n))) nil (fn [_ n] (kids n)) 1))))
  (testing "the reducing function may be a transduced one"
    (is (= [1 2 3 4 5] (search breadth-first ((take 5) conj) [] (fn [_ n] (kids n)) 1))))
  (testing "transduce over explore = search with the transduced reducing function"
    (let [xf      (comp (map first) (filter odd?) (map inc) (take 7))
          expand  (to-depth 6 kids)
          outside (transduce xf conj [] (explore breadth-first expand [1 0]))
          rf      (xf conj)
          inside  (rf (search breadth-first rf [] (fn [_ node] (expand node)) [1 0]))]
      (is (= outside inside))))
  (testing "search loops: a chain 200,000 deep is no problem"
    (is (= 200001 (search depth-first (fn [k _] (inc k)) 0 (fn [_ n] (when (< n 200000) [(inc n)])) 0)))))

(defn dijkstra [graph start]
  (into {} (map (juxt :at :cost))
        (explore (unique-by :at (best-first :cost))
                 (fn [{:keys [at cost]}] (for [[to w] (graph at)] {:at to :cost (+ cost w)}))
                 {:at start :cost 0})))

(defn floyd-warshall [graph n]
  (let [inf Long/MAX_VALUE
        d0  (into {} (for [i (range n) j (range n)] [[i j] (if (= i j) 0 (get-in graph [i j] inf))]))]
    (reduce (fn [d k]
              (into {} (for [i (range n) j (range n)]
                         [[i j] (let [a (d [i k]) b (d [k j])]
                                  (if (or (= a inf) (= b inf)) (d [i j]) (min (d [i j]) (+ a b))))])))
            d0 (range n))))

(deftest dijkstra-is-best-first-explore
  (is (= {:a 0 :b 7 :c 9 :f 11 :d 20 :e 20}
         (dijkstra {:a {:b 7 :c 9 :f 14} :b {:a 7 :c 10 :d 15} :c {:a 9 :b 10 :d 11 :f 2}
                    :d {:b 15 :c 11 :e 6} :e {:d 6 :f 9} :f {:a 14 :c 2 :e 9}}
                   :a)))
  (testing "agrees with Floyd-Warshall on 40 random digraphs"
    (let [rng (Random. 1)
          ok? (fn []
                (let [n     20
                      graph (into {} (for [i (range n)]
                                       [i (into {} (for [j (range n)
                                                         :when (and (not= i j) (< (.nextDouble rng) 0.15))]
                                                     [j (inc (.nextInt rng 20))]))]))
                      fw    (floyd-warshall graph n)
                      dj    (dijkstra graph 0)]
                  (every? (fn [j] (= (get dj j Long/MAX_VALUE) (fw [0 j]))) (range n))))]
      (is (every? true? (repeatedly 40 ok?))))))

(deftest backtracking-is-depth-first-explore
  (let [queens (fn [n]
                 (letfn [(safe? [qs col]
                           (let [row (count qs)]
                             (every? (fn [[r c]] (and (not= c col) (not= (- row r) (abs (- col c)))))
                                     (map-indexed vector qs))))
                         (place [qs]
                           (when (< (count qs) n)
                             (for [col (range n) :when (safe? qs col)] (conj qs col))))]
                   (eduction (filter #(= n (count %))) (explore depth-first place []))))]
    (is (= 92 (count (seq (queens 8)))))
    (is (= [0 4 7 5 2 6 1 3] (first (queens 8))))))

;; =============================================================
;; hylo
;; =============================================================

(defn random-tree [^Random rng depth]
  {:label    (.nextInt rng 10)
   :children (if (zero? depth) [] (vec (repeatedly (.nextInt rng 4) #(random-tree rng (dec depth)))))})

(defn cata
  "A fold over a tree that already exists."
  [fold tree]
  (fold tree (map #(cata fold %) (:children tree))))

(def sum-of   (fn [node values] (reduce + (:label node) values)))
(def height   (fn [_ values] (if (seq values) (inc (reduce max values)) 0)))
(def rebuild  (fn [node subtrees] {:label (:label node) :children (vec subtrees)}))

(deftest hylo-laws
  (let [rng   (Random. 3)
        trees (repeatedly 100 #(random-tree rng 5))]
    (testing "folding while unfolding = building the tree, then folding it"
      (is (every? (fn [t] (= ((hylo :children sum-of) t) (cata sum-of ((hylo :children rebuild) t)))) trees)))
    (testing "two folds in one pass = two passes (tupling)"
      (is (every? (fn [t]
                    (= ((hylo :children (fn [node pairs]
                                          [(sum-of node (map first pairs)) (height node (map second pairs))]))
                        t)
                       [((hylo :children sum-of) t) ((hylo :children height) t)]))
                  trees)))
    (testing "hylo-eager = hylo whenever the fold reads every child"
      (is (every? (fn [t]
                    (and (= ((hylo :children sum-of) t) ((hylo-eager :children sum-of) t))
                         (= ((hylo :children rebuild) t) ((hylo-eager :children rebuild) t))
                         (= ((hylo :children height) t) ((hylo-eager :children height) t))))
                  trees)))))

(deftest hylo-returns-a-function
  (let [depth-of (hylo kids (fn [n depths] (if (< n 16) (inc (reduce max depths)) 0)))]
    (is (= [4 2 0] (map depth-of [1 4 16])))
    (is (= 4 ((comp inc depth-of) 2)))
    (is (= [1 8] ((juxt depth-of identity) 8)))))

(deftest hylo-children-are-lazy
  (testing "and/or search stops at the first child that decides the answer"
    (let [expanded (atom 0)
          ;; a full binary tree of depth 12 whose leaves are all true: the or-node needs one child
          wins?    (hylo (fn [[d]] (swap! expanded inc) (when (pos? d) [[(dec d)] [(dec d)]]))
                         (fn [_ answers] (if (seq answers) (boolean (some true? answers)) true)))]
      (is (true? (wins? [12])))
      (is (= 13 @expanded))))
  (testing "hylo-eager evaluates every child"
    (let [expanded (atom 0)
          wins?    (hylo-eager (fn [[d]] (swap! expanded inc) (when (pos? d) [[(dec d)] [(dec d)]]))
                               (fn [_ answers] (if (seq answers) (boolean (some true? answers)) true)))]
      (is (true? (wins? [12])))
      (is (= 8191 @expanded)))))

(defn lattice [n]
  (fn [[x y]] (cond-> [] (< x n) (conj [(inc x) y]) (< y n) (conj [x (inc y)]))))

(def count-paths (fn [_ counts] (if (seq counts) (reduce + counts) 1N)))

(defn edit-distance
  "Levenshtein distance as a memoised fold over index pairs."
  [hylo-fn a b]
  ((hylo-fn (fn [[i j]] (when (and (pos? i) (pos? j)) [[(dec i) j] [i (dec j)] [(dec i) (dec j)]]))
            (fn [[i j] [delete insert substitute]]
              (cond (zero? i) j
                    (zero? j) i
                    :else (min (inc delete) (inc insert)
                               (+ substitute (if (= (nth a (dec i)) (nth b (dec j))) 0 1)))))
            :memo identity)
   [(count a) (count b)]))

(defn edit-distance-rows
  "The textbook two-row dynamic program, as an oracle."
  [a b]
  (peek (reduce (fn [prev [i ca]]
                  (reduce (fn [row [j cb]]
                            (conj row (min (inc (prev (inc j))) (inc (peek row))
                                           (+ (prev j) (if (= ca cb) 0 1)))))
                          [(inc i)] (map-indexed vector b)))
                (vec (range (inc (count b))))
                (map-indexed vector a))))

(defn random-string [^Random rng n]
  (apply str (repeatedly n #(char (+ 97 (.nextInt rng 4))))))

(deftest memo-is-dynamic-programming
  (testing "paths through a 16x16 grid = C(32,16), with 289 nodes solved instead of 601 million paths walked"
    (is (= 601080390N ((hylo (lattice 16) count-paths :memo identity) [0 0])))
    (is (= 601080390N ((hylo-eager (lattice 16) count-paths :memo identity) [0 0]))))
  (testing "each key is solved once, and the returned function keeps its cache"
    (let [solved (atom 0)
          paths  (hylo-eager (lattice 16) (fn [node counts] (swap! solved inc) (count-paths node counts))
                             :memo identity)]
      (paths [0 0])
      (paths [3 5])
      (is (= 289 @solved))))
  (testing "nil is a value like any other"
    (let [calls (atom 0)
          f     (hylo-eager (constantly nil) (fn [_ _] (swap! calls inc) nil) :memo identity)]
      (is (nil? (f :a)))
      (is (nil? (f :a)))
      (is (= 1 @calls))))
  (testing "edit distance: hylo, hylo-eager and the two-row table agree"
    (let [rng (Random. 1)]
      (is (every? true?
                  (repeatedly 30 #(let [a (random-string rng (.nextInt rng 40))
                                        b (random-string rng (.nextInt rng 40))]
                                    (= (edit-distance hylo a b)
                                       (edit-distance hylo-eager a b)
                                       (edit-distance-rows a b)))))))))

(deftest stack-depth
  (let [chain  (fn [n] (when (pos? n) [(dec n)]))
        length (fn [_ lengths] (if (seq lengths) (inc (first lengths)) 0))]
    (testing "hylo recurses on the JVM stack: fine at depth 500"
      (is (= 500 ((hylo chain length) 500))))
    (testing "hylo-eager does not recurse"
      (is (= 100000 ((hylo-eager chain length) 100000)))
      (is (= 100000 ((hylo-eager chain length :memo identity) 100000))))
    (testing "edit distance of two 200-letter strings: 40,000 subproblems, the same answer as the table"
      (let [rng (Random. 2)
            a   (random-string rng 200)
            b   (random-string rng 200)]
        (is (= (edit-distance-rows a b) (edit-distance hylo-eager a b)))))
    (testing "with :memo, a cycle throws instead of looping forever"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cycle"
                            ((hylo-eager {:a [:b] :b [:c] :c [:a]} (fn [_ vs] (count vs)) :memo identity) :a))))))

(deftest identity-keys
  (testing "the same object gets the same key; an equal copy gets another"
    (let [id (by-identity)
          e  [:+ :x :y]]
      (is (= (id e) (id e)))
      (is (not= (id e) (id (into [] e))))
      (is (= (id nil) (id nil)))))
  (testing "deep nested nodes: unique-by identity pays for a deep = on colliding keys, by-identity does not"
    (let [chain   (reduce (fn [e _] [:+ e :x]) :x (range 20000))        ; 20,000 levels deep
          operands (fn [e] (when (vector? e) (rest e)))
          visited (count (into [] (explore (unique-by (by-identity) depth-first) operands chain)))]
      ;; 20,000 [:+ _ :x] nodes and the single interned :x
      (is (= 20001 visited))))
  (testing "hylo-eager with identity keys folds a 100,000-deep shared structure"
    (let [chain  (reduce (fn [e _] [:+ e e]) 1 (range 100000))           ; a DAG: 2^100000 paths, 100,001 nodes
          height ((hylo-eager (fn [e] (when (vector? e) (rest e)))
                              (fn [_ hs] (if (seq hs) (inc (reduce max hs)) 0))
                              :memo (by-identity))
                  chain)]
      (is (= 100000 height)))))

;; =============================================================
;; converge
;; =============================================================

(deftest converge-is-the-stopping-rule-of-iterate
  (testing "Babylonian square root"
    (is (< (abs (- (Math/sqrt 2.0)
                   (converge #(< (abs (- %1 %2)) 1e-12) (iterate #(* 0.5 (+ % (/ 2.0 %))) 1.0))))
           1e-12)))
  (testing "a fixed point"
    (is (= 0.0 (converge = (iterate #(Math/floor (/ % 2)) 100.0)))))
  (testing "with a budget it returns the last iterate"
    (is (= 4 (converge = (take 5 (iterate inc 0))))))
  (testing "nothing to converge on"
    (is (nil? (converge = [])))
    (is (= 1 (converge = [1])))))
