(ns orhof.search.mdp
  "Online planning on orhof.search: forward search, branch and bound, sparse
   sampling, and planning in belief space for POMDPs.
   Pattern: TREE/GRAPH EXPLORER — each is a fold of the lookahead tree, in
   which decision nodes (pick an action) alternate with chance nodes (nature
   picks an outcome). Forward search and sparse sampling read every child, so
   they run on hylo-eager and no depth is too deep; branch and bound skips
   children, so it runs on hylo.

   MDPs are the maps of orhof.mdp.planning/make-mdp. Each function returns
   {:action a, :value v}: the best action at the state and its value."
  (:require [orhof.search :refer [hylo hylo-eager]]
            [orhof.search.graph :as graph]
            [orhof.mdp.planning :as plan]))

(defn- lookahead-tree
  "Successor function of the lookahead tree. A decision node {:s :d} has one
   chance node per action from `actions-of`; a chance node {:s :d :a :outcomes}
   has one decision node per outcome [state' probability]."
  [actions-of outcomes-of]
  (fn [{:keys [s d a outcomes] :as node}]
    (cond
      (contains? node :a) (map (fn [[s' _]] {:s s' :d (dec d)}) outcomes)
      (pos? d)            (map (fn [a] {:s s :d d :a a :outcomes (seq (outcomes-of s a))}) (actions-of s)))))

(defn- node-key
  "Equal states at equal depth are one subproblem."
  [{:keys [s d a] :as node}]
  (if (contains? node :a) [s d a] [s d]))

(defn- expectation
  "Fold of a chance node: reward plus the discounted expected value of the outcomes."
  [reward gamma]
  (fn [{:keys [s a outcomes]} values]
    {:action a
     :value  (+ (reward s a)
                (* gamma (reduce + (map (fn [[_ p] v] (* p (:value v))) outcomes values))))}))

(defn forward-search
  "Exhaustive lookahead to a fixed depth, solving equal states at equal depth once.
   Catalog: D33. Pattern: TREE/GRAPH EXPLORER (hylo-eager with :memo).

   MDP -> State -> {:keys [depth leaf]} -> {:action Action, :value R}

   Options:
     :depth  number of steps to look ahead (default 3)
     :leaf   state -> estimate of its value, used at the horizon (default 0).
             With the converged values of value iteration the result is exact
             at any depth.

   A state without actions is a leaf.

   Example:
     ;; mdp: a 4x4 grid, -1 a step until the corner [3 3], gamma 0.9
     (forward-search mdp [0 0] :depth 4)
     ;=> {:action :right :value -3.439}"
  {:arglists '([mdp state & {:keys [depth leaf]}])}
  [{:keys [actions transition reward gamma]} state
   & {:keys [depth leaf] :or {depth 3 leaf (constantly 0.0)}}]
  (let [chance (expectation reward gamma)]
    ((hylo-eager (lookahead-tree actions transition)
                 (fn [{:keys [s] :as node} values]
                   (cond
                     (contains? node :a) (chance node values)
                     (seq values)        (apply max-key :value values)
                     :else               {:value (leaf s)}))
                 :memo node-key)
     {:s state :d depth})))

(defn branch-and-bound
  "Forward search that tries the actions of a state in order of their upper
   bound and stops at the first one that cannot beat the best found so far.
   The same answer as forward-search with :leaf lower, from fewer evaluations.
   Pattern: TREE/GRAPH EXPLORER (hylo; the unread children are never expanded).

   MDP -> State -> (State -> R) -> (State -> Action -> R) -> {:keys [depth]}
   -> {:action Action, :value R}

   (lower s)    <= the value of s; used at the horizon
   (upper s a)  >= the value of taking a in s

   Example:
     ;; mdp as under forward-search: no state is worth less than -10 or more than 0
     (branch-and-bound mdp [0 0] (fn [s] (if (= s [3 3]) 0.0 -10.0)) (constantly 0.0) :depth 6)
     ;=> {:action :down :value -4.68559}"
  {:arglists '([mdp state lower upper & {:keys [depth]}])}
  [{:keys [actions transition reward gamma]} state lower upper
   & {:keys [depth] :or {depth 3}}]
  (let [chance (expectation reward gamma)
        ranked (memoize (fn [s] (sort-by #(- (upper s %)) (actions s))))]
    ((hylo (lookahead-tree ranked transition)
           (fn [{:keys [s d] :as node} values]
             (cond
               (contains? node :a) (chance node values)
               (and (pos? d) (seq (ranked s)))
               (loop [as (ranked s), qs values, best {:value Double/NEGATIVE_INFINITY}]
                 (if (or (empty? as) (< (upper s (first as)) (:value best)))
                   best
                   (recur (rest as) (rest qs)
                          (let [q (first qs)] (if (> (:value q) (:value best)) q best)))))
               :else {:value (lower s)}))
           :memo node-key)
     {:s state :d depth})))

(defn- sample
  "One outcome of the distribution {x probability}, through clojure.core/rand."
  [distribution]
  (let [u (rand)]
    (loop [[[x p] & more] (seq distribution), acc 0.0]
      (let [acc (+ acc p)]
        (if (or (< u acc) (empty? more)) x (recur more acc))))))

(defn sparse-sampling
  "Forward search on a fixed number of sampled outcomes per action instead of
   all of them, so the cost does not depend on the number of states.
   Catalog: D35. Pattern: TREE/GRAPH EXPLORER + GENERATE (hylo-eager over a sampled tree).

   MDP -> State -> {:keys [depth n-samples leaf]} -> {:action Action, :value R}

   Options:
     :depth      number of steps to look ahead (default 3)
     :n-samples  sampled outcomes per action (default 10)
     :leaf       state -> estimate of its value at the horizon (default 0)

   Example:
     (sparse-sampling mdp [0 0] :depth 2 :n-samples 20)     ; mdp as under forward-search
     ;=> {:action :right :value -1.9}"
  {:arglists '([mdp state & {:keys [depth n-samples leaf]}])}
  [{:keys [actions transition reward gamma]} state
   & {:keys [depth n-samples leaf] :or {depth 3 n-samples 10 leaf (constantly 0.0)}}]
  (let [draws (fn [s a]                                   ; each sampled outcome has weight 1/n
                (let [distribution (transition s a)]
                  (repeatedly n-samples (fn [] [(sample distribution) (/ 1.0 n-samples)]))))
        chance (expectation reward gamma)]
    ((hylo-eager (lookahead-tree actions draws)
                 (fn [{:keys [s] :as node} values]
                   (cond
                     (contains? node :a) (chance node values)
                     (seq values)        (apply max-key :value values)
                     :else               {:value (leaf s)})))
     {:s state :d depth})))

(defn belief-mdp
  "The MDP whose states are the beliefs of a POMDP, so that every MDP
   algorithm plans under partial observability unchanged. A belief is a map
   {state probability}.
   Catalog: D71 (the same problem alpha vectors solve). Pattern: TRANSFORM-PROBLEM.

   POMDP -> MDP

   The POMDP is a map:
     :actions      state -> [action]            (the same for every state of a belief)
     :transition   state, action -> {state' probability}
     :observation  action, state' -> {observation probability}
     :reward       state, action -> reward
     :gamma        discount factor

   A step of the belief MDP is one Bayes update per possible observation.

   Example:
     ;; tiger: a tiger behind one of two doors; listening is right 85% of the time
     (:action (forward-search (belief-mdp tiger) {:left 0.5 :right 0.5} :depth 4))  ;=> :listen
     (:action (forward-search (belief-mdp tiger) {:left 0.99 :right 0.01} :depth 4)) ;=> :open-right"
  {:arglists '([pomdp])}
  [{:keys [actions transition observation reward gamma]}]
  (plan/make-mdp
   nil
   (fn [belief] (actions (key (first belief))))
   (fn [belief a]
     (let [joint (apply merge-with (partial merge-with +)
                        (for [[s p] belief, [s' q] (transition s a), [o r] (observation a s')]
                          {o {s' (* p q r)}}))]
       ;; one posterior belief per observation, weighted by the probability of seeing it
       (reduce (fn [successors [_ weights]]
                 (let [weights (into {} (filter (comp pos? val)) weights)
                       z       (reduce + (vals weights))]
                   (if (pos? z)
                     (update successors (update-vals weights #(/ % z)) (fnil + 0.0) z)
                     successors)))
               {} joint)))
   (fn [belief a] (reduce + (map (fn [[s p]] (* p (reward s a))) belief)))
   gamma))

(defn reachable-states
  "Every state the MDP can reach from `state`: the :states that planning from
   there needs.
   Pattern: TREE/GRAPH EXPLORER (explore + unique).

   MDP -> State -> #{State}

   Example:
     (plan/value-iteration (assoc mdp :states (reachable-states mdp start)))"
  {:arglists '([mdp state])}
  [{:keys [actions transition]} state]
  (graph/reachable (fn [s] (for [a (actions s), [s' p] (transition s a) :when (pos? p)] s'))
                   state))
