(ns orhof.mdp.online
  "Online planning methods: MCTS, forward search, sparse sampling,
   A*, rollout, receding horizon.
   Pattern: TREE/GRAPH EXPLORER — parameterized by select, expand, simulate."
  (:require [orhof.core :as c]
            [orhof.mdp.planning :as plan]))

;; =============================================================
;; MCTS (existing, kept from Phase 0)
;; =============================================================

(defn mcts
  "Monte Carlo Tree Search.
   Catalog: D36. Pattern: TREE/GRAPH EXPLORER.

   MDP -> State -> {:keys [budget c-explore]} -> Action

   Uses UCB1 for selection, random rollout for simulation.
   Returns the best action from the root state.

   Example:
     (mcts mdp :start :budget 1000)
     ;=> :go"
  [mdp root-state & {:keys [budget simulate-fn c-explore]
                      :or   {budget 1000 c-explore 1.41}}]
  (let [{:keys [actions transition reward gamma]} mdp
        simulate-fn (or simulate-fn
                        (fn [s depth]
                          (reduce (fn [total d]
                                    (if (empty? (actions s))
                                      (reduced total)
                                      (let [a (rand-nth (vec (actions s)))
                                            r (reward s a)
                                            transitions (transition s a)
                                            s' (if (empty? transitions) s
                                                   (let [roll (rand)
                                                         cumulative (reductions + (vals transitions))]
                                                     (first (first (filter (fn [[_ c]] (>= c roll))
                                                                           (map vector (keys transitions) cumulative))))))]
                                        (+ total (* (Math/pow gamma d) r)))))
                                  0.0
                                  (range depth 20))))
        make-node (fn [state] (atom {:state state :visits 0 :value 0.0 :children {}}))]
    (let [root (make-node root-state)]
      (dotimes [_ budget]
        (let [path (loop [node root, path [root]]
                     (let [n @node
                           avail (vec (actions (:state n)))
                           unexpanded (remove #(contains? (:children n) %) avail)]
                       (if (or (seq unexpanded) (empty? avail))
                         path
                         (let [total-visits (:visits n)
                               best-a (apply max-key
                                             (fn [a]
                                               (let [child @(get (:children n) a)]
                                                 (+ (/ (:value child) (max 1 (:visits child)))
                                                    (* c-explore
                                                       (Math/sqrt (/ (Math/log (max 1 total-visits))
                                                                     (max 1 (:visits child))))))))
                                             avail)
                               child-node (get (:children n) best-a)]
                           (recur child-node (conj path child-node))))))
              leaf @(last path)
              avail (vec (actions (:state leaf)))
              unexpanded (remove #(contains? (:children leaf) %) avail)]
          (when (seq unexpanded)
            (let [a (first unexpanded)
                  transitions (transition (:state leaf) a)
                  s' (if (empty? transitions) (:state leaf) (first (keys transitions)))
                  child (make-node s')]
              (swap! (last path) assoc-in [:children a] child)
              (let [r (simulate-fn s' 0)]
                (doseq [node path]
                  (swap! node update :visits inc)
                  (swap! node update :value + r)))))))
      (let [root-data @root children (:children root-data)]
        (when (seq children)
          (apply max-key (fn [a] (:visits @(get children a))) (keys children)))))))

;; =============================================================
;; FORWARD SEARCH
;; =============================================================

(defn forward-search
  "Exhaustive forward search to a fixed depth.
   Catalog: D33. Pattern: TREE/GRAPH EXPLORER (recursive).

   MDP -> State -> {:keys [depth]} -> Action

   Evaluates all action sequences up to depth, returns best first action.

   Example:
     (forward-search mdp :start :depth 3)
     ;=> :right"
  [mdp state & {:keys [depth] :or {depth 3}}]
  (let [{:keys [actions transition reward gamma]} mdp
        ;; Recursive value computation via reduce over actions
        value-at (fn value-at [s d]
                   (if (zero? d) 0.0
                       (apply max
                              (map (fn [a]
                                     (+ (reward s a)
                                        (* gamma
                                           (reduce + (map (fn [[s' p]]
                                                            (* p (value-at s' (dec d))))
                                                          (transition s a))))))
                                   (actions s)))))]
    ;; Return best action from root
    (apply max-key
           (fn [a]
             (+ (reward state a)
                (* gamma
                   (reduce + (map (fn [[s' p]]
                                    (* p (value-at s' (dec depth))))
                                  (transition state a))))))
           (actions state))))

;; =============================================================
;; SPARSE SAMPLING
;; =============================================================

(defn sparse-sampling
  "Sparse sampling — sample-based forward search.
   Catalog: D35. Pattern: TREE/GRAPH EXPLORER + GENERATE.

   MDP -> State -> {:keys [depth n-samples]} -> Action

   Like forward search but samples transitions instead of
   enumerating all successors.

   Example:
     (sparse-sampling mdp :start :depth 2 :n-samples 10)
     ;=> :down"
  [mdp state & {:keys [depth n-samples] :or {depth 3 n-samples 10}}]
  (let [{:keys [actions transition reward gamma]} mdp
        sample-next (fn [s a]
                      (let [dist (transition s a)
                            r (rand)
                            cumul (reductions + (vals dist))]
                        (first (first (filter (fn [[_ c]] (>= c r))
                                              (map vector (keys dist) cumul))))))
        value-at (fn value-at [s d]
                   (if (zero? d) 0.0
                       (apply max
                              (map (fn [a]
                                     (let [samples (repeatedly n-samples #(sample-next s a))
                                           values (map (fn [s'] (+ (reward s a) (* gamma (value-at s' (dec d)))))
                                                       samples)]
                                       (/ (reduce + values) (double n-samples))))
                                   (actions s)))))]
    (apply max-key (fn [a]
                     (let [samples (repeatedly n-samples #(sample-next state a))]
                       (/ (reduce + (map (fn [s'] (+ (reward state a) (* gamma (value-at s' (dec depth)))))
                                         samples))
                          (double n-samples))))
           (actions state))))

;; =============================================================
;; ROLLOUT WITH HEURISTIC
;; =============================================================

(defn rollout-with-heuristic
  "Rollout with a heuristic policy.
   Catalog: D32. Pattern: COMPOSE + GENERATE + REDUCE.

   MDP -> State -> (State -> [Action]) -> Opts -> Action

   Simulates n rollouts with the heuristic policy from each action,
   returns the action with highest average return.

   Example:
     (rollout-with-heuristic mdp :start random-policy :n-rollouts 50 :horizon 20)
     ;=> :right"
  [mdp state heuristic-policy & {:keys [n-rollouts horizon]
                                   :or   {n-rollouts 50 horizon 20}}]
  (let [{:keys [actions transition reward gamma]} mdp
        sample-next (fn [s a]
                      (let [dist (transition s a)]
                        (first (first (filter (fn [[_ c]] (>= c (rand)))
                                              (map vector (keys dist) (reductions + (vals dist))))))))
        simulate (fn [s0 a0]
                   (:total
                    (reduce (fn [{:keys [s total]} step]
                              (let [a (if (zero? step) a0 (rand-nth (vec (heuristic-policy s))))
                                    r (reward s a)
                                    s' (sample-next s a)]
                                {:s s' :total (+ total (* (Math/pow gamma step) r))}))
                            {:s s0 :total 0.0}
                            (range horizon))))]
    (apply max-key
           (fn [a]
             (/ (reduce + (repeatedly n-rollouts #(simulate state a)))
                (double n-rollouts)))
           (actions state))))

;; =============================================================
;; A* / HEURISTIC SEARCH
;; =============================================================

(defn a-star
  "A* search algorithm.
   Catalog: D37. Pattern: TREE/GRAPH EXPLORER + DELEGATE.

   State -> (State -> [State]) -> (State -> State -> R) -> (State -> R) -> (State -> Bool)
   -> {:node State, :cost R, :path [State]}

   expand-fn: node -> [successors]
   cost-fn: (from, to) -> edge cost
   heuristic: node -> estimated cost to goal
   goal?: node -> boolean

   Example:
     (a-star :a expand-fn cost-fn heuristic #(= % :goal))
     ;=> {:node :goal :cost 5.0 :path [:a :b :goal]}"
  [start expand-fn cost-fn heuristic goal?]
  (reduce (fn [{:keys [open closed g-scores came-from]} _]
            (if (empty? open)
              (reduced {:node nil :cost Double/MAX_VALUE :path []})
              (let [;; Select node with lowest f = g + h
                    current (apply min-key (fn [n] (+ (get g-scores n Double/MAX_VALUE)
                                                       (heuristic n)))
                                   open)]
                (if (goal? current)
                  ;; Reconstruct path
                  (let [path (reduce (fn [path _]
                                       (let [prev (get came-from (first path))]
                                         (if prev (cons prev path) (reduced path))))
                                     (list current)
                                     (range (count g-scores)))]
                    (reduced {:node current :cost (get g-scores current) :path (vec path)}))
                  ;; Expand current
                  (let [neighbors (expand-fn current)
                        open'     (disj open current)
                        closed'   (conj closed current)
                        [open'' g' cf']
                        (reduce (fn [[o g cf] neighbor]
                                  (if (contains? closed' neighbor)
                                    [o g cf]
                                    (let [tentative-g (+ (get g-scores current 0.0) (cost-fn current neighbor))]
                                      (if (< tentative-g (get g neighbor Double/MAX_VALUE))
                                        [(conj o neighbor) (assoc g neighbor tentative-g) (assoc cf neighbor current)]
                                        [o g cf]))))
                                [open' g-scores came-from]
                                neighbors)]
                    {:open open'' :closed closed' :g-scores g' :came-from cf'})))))
          {:open #{start} :closed #{} :g-scores {start 0.0} :came-from {}}
          (range 10000)))

;; =============================================================
;; RECEDING HORIZON
;; =============================================================

(defn receding-horizon
  "Receding horizon planning — solve finite-horizon, take first action.
   Catalog: D31. Pattern: COMPOSE.

   MDP -> State -> {:keys [horizon]} -> Action

   Wraps forward-search with a horizon.

   Example:
     (receding-horizon mdp :start :horizon 5)
     ;=> :right"
  [mdp state & {:keys [horizon] :or {horizon 5}}]
  (forward-search mdp state :depth (min horizon 4)))

;; =============================================================
;; OPEN-LOOP PLANNING
;; =============================================================

(defn open-loop-planning
  "Open-loop planning — optimize over action sequences.
   Catalog: D39. Pattern: ITERATE + GENERATE.

   MDP -> State -> {:keys [horizon n-samples]} -> Action

   Evaluates random action sequences, returns first action of best.

   Example:
     (open-loop-planning mdp :start :horizon 5 :n-samples 100)
     ;=> :down"
  [mdp state & {:keys [horizon n-samples] :or {horizon 5 n-samples 200}}]
  (let [{:keys [actions transition reward gamma]} mdp
        avail (vec (actions state))
        sample-next (fn [s a]
                      (let [dist (transition s a)]
                        (first (first (filter (fn [[_ c]] (>= c (rand)))
                                              (map vector (keys dist) (reductions + (vals dist))))))))
        evaluate-seq (fn [action-seq]
                       (reduce (fn [{:keys [state total step]} a]
                                 (let [r (reward state a)
                                       s' (sample-next state a)]
                                   {:state s' :total (+ total (* (Math/pow gamma step) r))
                                    :step (inc step)}))
                               {:state state :total 0.0 :step 0}
                               action-seq))
        ;; Generate random action sequences and evaluate
        best (apply max-key
                     (fn [seq] (:total (evaluate-seq seq)))
                     (repeatedly n-samples
                                 (fn [] (repeatedly horizon #(rand-nth avail)))))]
    (first best)))
