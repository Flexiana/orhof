(ns orhof.search.games-test
  "Minimax, alpha-beta, expectiminimax and MCTS against independent oracles."
  (:require [clojure.test :refer :all]
            [orhof.search :refer [explore hylo breadth-first unique]]
            [orhof.search.games :as g]
            [orhof.test-util :as tu])
  (:import (java.util Random)))

(use-fixtures :each tu/with-seeded-rand)   ; deterministic rand for MCTS playouts

;; =============================================================
;; Tic-tac-toe
;; =============================================================

(def lines [[0 1 2] [3 4 5] [6 7 8] [0 3 6] [1 4 7] [2 5 8] [0 4 8] [2 4 6]])
(defn winner [b] (some (fn [[i j k]] (and (b i) (= (b i) (b j) (b k)) (b i))) lines))
(defn to-move [b] (if (= (count (filter #{:x} b)) (count (filter #{:o} b))) :x :o))
(def empty-board (vec (repeat 9 nil)))
(defn board [s] (mapv {\x :x \o :o \. nil} (remove #{\space} s)))

(def ttt
  {:game-over?  #(boolean (or (winner %) (every? some? %)))
   :evaluate    #(case (winner %) :x 1 :o -1 0)
   :legal-moves #(filter (fn [i] (nil? (% i))) (range 9))
   :apply-move  #(assoc %1 %2 (to-move %1))
   :to-move     to-move
   :reward      (fn [b player] (let [w (winner b)] (cond (nil? w) 0.5 (= w player) 1.0 :else 0.0)))})

(defn counting
  "The rules, counting visited nodes: every node calls :evaluate or :legal-moves, never both."
  [rules calls]
  (let [tick (fn [f] (fn [s] (swap! calls inc) (f s)))]
    (-> rules (update :evaluate tick) (update :legal-moves tick))))

(defn book-alpha-beta
  "Alpha-beta the usual way, two loops with early exit: the oracle for value and node count."
  [rules state depth alpha beta max?]
  (if (or (zero? depth) ((:game-over? rules) state))
    ((:evaluate rules) state)
    (let [moves ((:legal-moves rules) state)]
      (if max?
        (loop [ms moves, a alpha]
          (if (empty? ms)
            a
            (let [a' (max a (book-alpha-beta rules ((:apply-move rules) state (first ms)) (dec depth) a beta false))]
              (if (>= a' beta) a' (recur (rest ms) a')))))
        (loop [ms moves, b beta]
          (if (empty? ms)
            b
            (let [b' (min b (book-alpha-beta rules ((:apply-move rules) state (first ms)) (dec depth) alpha b true))]
              (if (<= b' alpha) b' (recur (rest ms) b')))))))))

(deftest test-minimax
  (let [calls (atom 0)]
    (testing "tic-tac-toe is a draw; the full game tree has 549,946 nodes"
      (is (= 0 (g/minimax (counting ttt calls) empty-board 9 true)))
      (is (= 549946 @calls))))
  (testing "depth-limited search falls back to :evaluate"
    (is (= 0 (g/minimax ttt empty-board 2 true))))
  (testing "a won position"
    (is (= 1 (g/minimax ttt (board "xx. oo. ...") 9 true)))
    (is (= -1 (g/minimax ttt (board "xx. oo. x..") 9 false)))))

(deftest test-alpha-beta
  (testing "same value as minimax, and exactly the nodes the two-loop version visits"
    (let [calls (atom 0)
          value (g/alpha-beta (counting ttt calls) empty-board 9 -10 10 true)
          mine  @calls
          _     (reset! calls 0)
          book  (book-alpha-beta (counting ttt calls) empty-board 9 -10 10 true)]
      (is (= 0 value book))
      (is (= 18297 mine @calls))))
  (testing "agrees with minimax on all 72 positions two plies in"
    (is (every? (fn [b]
                  (= (g/minimax ttt b 9 true) (g/alpha-beta ttt b 9 -10 10 true)))
                (for [i (range 9) j (range 9) :when (not= i j)]
                  (-> empty-board (assoc i :x) (assoc j :o)))))))

;; =============================================================
;; Expectiminimax
;; =============================================================

(defn random-tree [^Random rng depth]
  (if (or (zero? depth) (< (.nextDouble rng) 0.15))
    {:value (- (.nextInt rng 21) 10)}
    (let [kind ([:max :min :chance] (.nextInt rng 3))
          kids (vec (repeatedly (+ 2 (.nextInt rng 2)) #(random-tree rng (dec depth))))]
      (if (= kind :chance)
        (let [ws (repeatedly (count kids) #(inc (.nextInt rng 5))), z (reduce + ws)]
          {:kind :chance :children kids :probs (mapv #(/ % z) ws)})
        {:kind kind :children kids}))))

(defn direct
  "The oracle: plain recursion over a tree that already exists."
  [t]
  (case (:kind t)
    nil     (:value t)
    :max    (apply max (map direct (:children t)))
    :min    (apply min (map direct (:children t)))
    :chance (reduce + (map * (:probs t) (map direct (:children t))))))

(def tree-rules
  {:game-over?  #(contains? % :value)
   :evaluate    :value
   :legal-moves #(range (count (:children %)))
   :apply-move  #(nth (:children %1) %2)
   :kind        :kind
   ;; pairs, not a map: two equal subtrees must stay two outcomes
   :outcomes    #(map vector (:children %) (:probs %))})

(deftest test-expectiminimax
  (testing "equals plain recursion on 200 random trees (exact, in ratios)"
    (let [rng (Random. 11)]
      (is (every? (fn [t] (= (direct t) (g/expectiminimax tree-rules t 99)))
                  (repeatedly 200 #(random-tree rng 5))))))
  (testing "the docstring example"
    (let [flip {:kind :chance
                :outcomes {{:value 1} 0.5
                           {:kind :max :moves [{:value 4} {:value 0}]} 0.5}}]
      (is (= 2.5 (g/expectiminimax {:game-over? :value :evaluate :value :kind :kind :outcomes :outcomes
                                    :legal-moves :moves :apply-move (fn [_ child] child)}
                                   flip 5))))))

;; =============================================================
;; Monte Carlo tree search
;; =============================================================

(def perfect-value
  "The value of a board for the player to move: 1 win, 0 draw, -1 loss.
   Negamax as a memoised hylo: an oracle, and an evaluator for MCTS."
  (hylo (fn [b] (when-not ((:game-over? ttt) b) (map #((:apply-move ttt) b %) ((:legal-moves ttt) b))))
        (fn [b values]
          (if (seq values)
            (- (reduce min values))
            (let [w (winner b)] (cond (nil? w) 0 (= w (to-move b)) 1 :else -1))))
        :memo identity))

(defn optimal-moves [b]
  (let [values (into {} (map (fn [m] [m (- (perfect-value ((:apply-move ttt) b m)))]) ((:legal-moves ttt) b)))
        best   (apply max (vals values))]
    (set (keep (fn [[m v]] (when (= v best) m)) values))))

(defn play-out [x-player o-player]
  (loop [b empty-board]
    (if ((:game-over? ttt) b)
      (or (winner b) :draw)
      (recur ((:apply-move ttt) b ((if (= :x (to-move b)) x-player o-player) b))))))

(defn random-player [b]
  (let [moves (vec ((:legal-moves ttt) b))]
    (moves (int (rand (count moves))))))

(def positions
  "60 tic-tac-toe positions with two to five stones, spread over the game."
  (delay (->> (explore (unique breadth-first)
                       (fn [b] (when-not ((:game-over? ttt) b) (map #((:apply-move ttt) b %) ((:legal-moves ttt) b))))
                       empty-board)
              (remove (:game-over? ttt))
              (filter #(<= 2 (count (remove nil? %)) 5))
              (take-nth 37)
              (take 60)
              vec)))

(deftest test-mcts-tactics
  (testing "takes the win"
    (is (= 2 (:move (g/mcts ttt (board "xx. oo. ...") :budget 1000)))))
  (testing "blocks the loss"
    (is (= 5 (:move (g/mcts ttt (board "x.. oo. x..") :budget 1000)))))
  (testing "returns the visit count and the mean reward of its move"
    (let [{:keys [visits value]} (g/mcts ttt (board "xx. oo. ...") :budget 1000)]
      (is (> visits 500))
      (is (> value 0.9))))
  (testing "no moves, no answer"
    (is (nil? (g/mcts ttt (board "xxx oo. ...") :budget 10))))
  (testing "the statistics count every simulation at the root"
    (is (= 200 (:n ((g/mcts-statistics ttt empty-board :budget 200) empty-board))))))

(deftest test-mcts-against-a-random-player
  (let [mcts-player (fn [b] (:move (g/mcts ttt b :budget 400)))
        as-x        (frequencies (repeatedly 15 #(play-out mcts-player random-player)))
        as-o        (frequencies (repeatedly 15 #(play-out random-player mcts-player)))]
    (testing "400 simulations a move: no loss in 30 games"
      (is (= 0 (get as-x :o 0)))
      (is (= 0 (get as-o :x 0))))
    (testing "and it wins most of them"
      (is (> (+ (get as-x :x 0) (get as-o :o 0)) 20)))))

(deftest test-mcts-against-the-perfect-player
  (let [hits (fn [choose] (count (filter #(contains? (optimal-moves %) (choose %)) @positions)))]
    (testing "the oracle values the empty board as a draw"
      (is (= 0 (perfect-value empty-board))))
    (testing "random playouts, 500 simulations: an optimal move on at least 57 of 60 positions"
      (is (>= (hits (fn [b] (:move (g/mcts ttt b :budget 500)))) 57)))
    (testing "AlphaZero style, 60 simulations: a value function instead of playouts, a prior, PUCT"
      (let [evaluate (fn [b player]
                       (let [v (perfect-value b)]
                         (/ (+ 1.0 (if (= player (to-move b)) v (- v))) 2.0)))
            uniform  (fn [b] (let [n (count ((:legal-moves ttt) b))] (constantly (/ 1.0 n))))]
        (is (>= (hits (fn [b] (:move (g/mcts ttt b :budget 60 :c-explore 1.5 :evaluate evaluate :prior uniform))))
                57))))))
