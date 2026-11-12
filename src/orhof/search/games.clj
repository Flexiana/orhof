(ns orhof.search.games
  "Game-tree search on orhof.search: minimax, alpha-beta, expectiminimax and
   Monte Carlo tree search.
   Pattern: TREE/GRAPH EXPLORER — the minimax family is hylo; MCTS is iterate
   over two explore walks and a reduce.

   A game is a map of functions, its rules:
     :game-over?   state -> boolean
     :legal-moves  state -> [move]
     :apply-move   state, move -> state
     :evaluate     state -> score for the maximising player    (minimax family)
     :kind         state -> :max, :min or :chance              (expectiminimax)
     :outcomes     state -> {state' probability}               (expectiminimax)
     :to-move      state -> player                             (mcts; default: one player)
     :reward       final state, player -> number               (mcts)"
  (:require [orhof.search :refer [explore hylo depth-first]]))

;; =============================================================
;; MINIMAX FAMILY
;; =============================================================

(defn- plies
  "Successor function of a depth-limited game tree. Nodes are [state depth max?]."
  [{:keys [game-over? legal-moves apply-move]}]
  (fn [[state depth max?]]
    (when-not (or (zero? depth) (game-over? state))
      (map (fn [move] [(apply-move state move) (dec depth) (not max?)])
           (legal-moves state)))))

(defn minimax
  "Minimax value of `state`, searching `depth` plies.
   Pattern: TREE/GRAPH EXPLORER (hylo). The fold is max or min.

   Rules -> State -> Int -> Bool -> R

   Example:
     ;; nim: take 1 or 2 sticks; whoever takes the last one wins.
     ;; A state is [sticks player-to-move], the players are 1 and -1.
     (def nim {:game-over?  (fn [[sticks _]] (zero? sticks))
               :legal-moves (fn [[sticks _]] (filter #(<= % sticks) [1 2]))
               :apply-move  (fn [[sticks player] n] [(- sticks n) (- player)])
               :evaluate    (fn [[_ player]] (- player))})     ; the one who just moved won
     (minimax nim [4 1] 10 true) ;=> 1
     (minimax nim [3 1] 10 true) ;=> -1"
  {:arglists '([rules state depth maximizing?])}
  [{:keys [evaluate] :as rules} state depth maximizing?]
  ((hylo (plies rules)
         (fn [[state _ max?] values]
           (if (seq values)
             (reduce (if max? max min) values)
             (evaluate state))))
   [state depth maximizing?]))

(defn alpha-beta
  "Minimax value of `state` with alpha-beta pruning: the same value as
   minimax from a fraction of the nodes.
   Pattern: TREE/GRAPH EXPLORER (hylo). The value of a node is a function of
   the window, so a child is searched only when its parent calls it, with the
   window narrowed so far; `reduced` is the cutoff.

   Rules -> State -> Int -> R -> R -> Bool -> R

   Example:
     (alpha-beta nim [4 1] 10 -1 1 true) ;=> 1     ; nim as under minimax"
  {:arglists '([rules state depth alpha beta maximizing?])}
  [{:keys [evaluate] :as rules} state depth alpha beta maximizing?]
  (((hylo (plies rules)
          (fn [[state _ max?] children]
            (fn [alpha beta]
              (cond
                (empty? children) (evaluate state)
                max? (reduce (fn [a child]
                               (let [a (max a (child a beta))]
                                 (if (>= a beta) (reduced a) a)))
                             alpha children)
                :else (reduce (fn [b child]
                                (let [b (min b (child alpha b))]
                                  (if (<= b alpha) (reduced b) b)))
                              beta children)))))
    [state depth maximizing?])
   alpha beta))

(defn expectiminimax
  "Minimax with chance nodes: the value of a chance node is the expectation
   over its outcomes.
   Pattern: TREE/GRAPH EXPLORER (hylo). The fold is max, min or a weighted sum.

   Rules -> State -> Int -> R

   The rules also give (:kind state) -> :max, :min or :chance and, at a chance
   node, (:outcomes state) -> {state' probability}.

   Example:
     ;; a coin flip between a sure 1 and a choice of 4 or 0
     (def flip {:kind :chance
                :outcomes {{:value 1} 0.5
                           {:kind :max :moves [{:value 4} {:value 0}]} 0.5}})
     (expectiminimax {:game-over? :value :evaluate :value :kind :kind :outcomes :outcomes
                      :legal-moves :moves :apply-move (fn [_ child] child)}
                     flip 5)
     ;=> 2.5"
  {:arglists '([rules state depth])}
  [{:keys [game-over? evaluate legal-moves apply-move kind outcomes]} state depth]
  (:value
   ((hylo (fn [{:keys [state depth]}]
            (when-not (or (zero? depth) (game-over? state))
              (if (= :chance (kind state))
                (map (fn [[state' p]] {:state state' :depth (dec depth) :p p}) (outcomes state))
                (map (fn [move] {:state (apply-move state move) :depth (dec depth)}) (legal-moves state)))))
          ;; a child of a chance node carries its probability up with its value
          (fn [{:keys [state p]} children]
            {:p p
             :value (if (empty? children)
                      (evaluate state)
                      (case (kind state)
                        :max    (reduce max (map :value children))
                        :min    (reduce min (map :value children))
                        :chance (reduce + (map (fn [{:keys [p value]}] (* p value)) children))))}))
    {:state state :depth depth})))

;; =============================================================
;; MONTE CARLO TREE SEARCH
;; =============================================================

(defn- pick
  "A random element of the vector v. All randomness goes through clojure.core/rand."
  [v]
  (v (int (rand (count v)))))

(defn- mcts-step
  "Returns (fn [stats root] -> stats'), one simulation: select and expand
   (a walk down the best children), simulate (a random playout, or :evaluate)
   and backpropagate (a fold over the path)."
  [{:keys [legal-moves apply-move game-over? to-move reward] :or {to-move (constantly :agent)}}
   {:keys [c-explore evaluate prior] :or {c-explore (Math/sqrt 2.0)}}]
  (let [children (fn [s] (map #(apply-move s %) (legal-moves s)))]
    (fn [stats root]
      (let [visits (fn [s] (:n (stats s) 0))
            score  (if prior
                     (fn [parent]                           ; PUCT: the prior weighs the exploration term
                       (let [p (zipmap (children parent) (map (prior parent) (legal-moves parent)))]
                         (fn [child]
                           (let [{:keys [n w] :or {n 0 w 0.0}} (stats child)]
                             (+ (if (pos? n) (/ w n) 0.0)
                                (* c-explore (p child) (/ (Math/sqrt (visits parent)) (inc n))))))))
                     (fn [parent]                           ; UCB1
                       (fn [child]
                         (let [{:keys [n w]} (stats child)]
                           (+ (/ w n) (* c-explore (Math/sqrt (/ (Math/log (visits parent)) n))))))))
            ;; 1. select, 2. expand: down the best children to the first unvisited or final state
            select (fn [s]
                     (when (and (pos? (visits s)) (not (game-over? s)))
                       (let [kids    (children s)
                             untried (when-not prior (seq (remove #(pos? (visits %)) kids)))]
                         [(if untried
                            (pick (vec untried))
                            (apply max-key (score s) kids))])))
            path   (into [] (explore depth-first select root))
            leaf   (peek path)
            ;; 3. simulate: a random playout to the end, or the supplied evaluation
            value  (if evaluate
                     (fn [player] (evaluate leaf player))
                     (let [random-move (fn [s]
                                         (when-not (game-over? s)
                                           [(apply-move s (pick (vec (legal-moves s))))]))
                           final       (reduce (fn [_ s] s) leaf (explore depth-first random-move leaf))]
                       (fn [player] (reward final player))))]
        ;; 4. backpropagate: credit each state on the path to the player who moved into it
        (reduce (fn [stats [parent child]]
                  (update stats child (fn [{:keys [n w] :or {n 0 w 0.0}}]
                                        {:n (inc n) :w (+ w (value (to-move parent)))})))
                (update stats root (fn [{:keys [n] :or {n 0} :as node}] (assoc node :n (inc n))))
                (partition 2 1 path))))))

(defn mcts-statistics
  "The statistics of Monte Carlo tree search after `budget` simulations from
   `state`: {state {:n visits, :w total reward}}, the reward being that of
   the player who moved into the state.
   Catalog: D36. Pattern: TREE/GRAPH EXPLORER (iterate over one simulation).

   Rules -> State -> {:keys [budget c-explore evaluate prior]} -> {State {:n Int, :w R}}

   Options as for mcts.

   Example:
     (:n ((mcts-statistics nim' [4 1] :budget 200) [4 1])) ;=> 200     ; nim' as under mcts"
  {:arglists '([rules state & {:keys [budget c-explore evaluate prior]}])}
  [rules state & {:keys [budget] :or {budget 1000} :as options}]
  (let [step (mcts-step rules options)]
    (nth (iterate #(step % state) {}) budget)))

(defn mcts
  "Monte Carlo tree search: run `budget` simulations from `state` and play
   the most visited move.
   Catalog: D36. Pattern: TREE/GRAPH EXPLORER + GENERATE.

   Rules -> State -> {:keys [budget c-explore evaluate prior]}
   -> {:move Move, :visits Int, :value R}

   The rules need :legal-moves, :apply-move, :game-over? and :reward, plus
   :to-move when there is more than one player. :value is the mean reward of
   the chosen move for the player to move. Returns nil if the game is over.

   Options:
     :budget     number of simulations (default 1000)
     :c-explore  exploration constant (default sqrt 2)
     :evaluate   (fn [state player] -> reward) used instead of random playouts
     :prior      (fn [state] -> (fn [move] -> probability)); switches the
                 selection rule from UCB1 to PUCT, as in AlphaZero

   Example:
     ;; nim as under minimax, plus who moves and who won
     (def nim' (assoc nim
                      :to-move (fn [[_ player]] player)
                      :reward  (fn [[_ player] p] (if (= p (- player)) 1.0 0.0))))
     (:move (mcts nim' [4 1] :budget 500)) ;=> 1"
  {:arglists '([rules state & {:keys [budget c-explore evaluate prior]}])}
  [{:keys [legal-moves apply-move game-over?] :as rules} state & {:as options}]
  (when-let [moves (and (not (game-over? state)) (seq (legal-moves state)))]
    (let [stats (mcts-statistics rules state (or options {}))
          move  (apply max-key #(:n (stats (apply-move state %)) 0) moves)
          {:keys [n w] :or {n 0 w 0.0}} (stats (apply-move state move))]
      {:move move :visits n :value (if (pos? n) (/ w n) 0.0)})))
