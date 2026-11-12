(ns orhof.search.val
  "Falsification by tree search on orhof.search: the most likely way for a
   system to fail under a finite set of disturbances.
   Pattern: TREE/GRAPH EXPLORER — adaptive stress testing is Monte Carlo tree
   search over disturbance sequences; the exact answer is a shortest path.

   Systems are the maps of orhof.val.spec/make-system. The disturbance model
   is a map {disturbance probability}. A margin is a function
   state -> robustness; a negative margin is a violation.

   Both functions return
     {:falsified? Bool, :disturbances [d], :trajectory [state],
      :robustness R, :log-likelihood R}
   where :robustness is the lowest margin on the trajectory."
  (:require [orhof.search.games :as games]
            [orhof.search.graph :as graph]))

(defn- stress-game
  "Falsification as a one-player game: a move is a disturbance, and the game
   ends at the first violation or at the horizon."
  [{:keys [dynamics]} disturbances margin horizon miss-penalty]
  {:legal-moves (constantly (keys disturbances))
   :apply-move  (fn [{:keys [trajectory disturbances robustness log-likelihood] :as position} d]
                  (let [state (dynamics (peek trajectory) d)]
                    {:trajectory     (conj trajectory state)
                     :disturbances   (conj disturbances d)
                     :robustness     (min robustness (margin state))
                     :log-likelihood (+ log-likelihood (Math/log ((:model position) d)))
                     :model          (:model position)}))
   :game-over?  (fn [{:keys [disturbances robustness]}]
                  (or (neg? robustness) (= horizon (count disturbances))))
   :reward      (fn [{:keys [robustness log-likelihood]} _]
                  (if (neg? robustness)
                    log-likelihood
                    (- log-likelihood (miss-penalty robustness))))})

(defn adaptive-stress-test
  "Adaptive stress testing: searches for the most likely disturbance sequence
   that drives the system to a violation within `horizon` steps. Monte Carlo
   tree search picks each disturbance in turn.
   Catalog: V17-V20. Pattern: TREE/GRAPH EXPLORER (mcts inside iterate).

   System -> {Disturbance Prob} -> (State -> R) -> Int
   -> {:keys [budget c-explore miss-penalty]}
   -> {:falsified? Bool, :disturbances [d], :trajectory [State], :robustness R, :log-likelihood R}

   The reward of a trajectory is its log-likelihood; one that does not fail
   pays (miss-penalty robustness) on top, so near misses rank above safe runs.

   Options:
     :budget        simulations per step (default 800)
     :c-explore     exploration constant; keep it on the scale of the miss
                    penalty (default 40.0)
     :miss-penalty  robustness -> penalty (default 50 + 10 * robustness)

   The search is stochastic: it finds a failure, not always the most likely
   one. most-likely-failure gives the exact answer when it is affordable.

   Example:
     ;; a heater that switches on below 20 degrees; fail outside [18, 25]
     (def heater (orhof.val.spec/make-system
                  (fn [temp d] (+ temp (if (< temp 20.0) 2.0 0.0) d (* -0.05 (- temp 20.0))))
                  20.0))
     (def weather {-1.0 0.05, 0.0 0.9, 1.0 0.05})         ; cold snap, calm, heat wave
     (defn margin [temp] (min (- temp 18.0) (- 25.0 temp)))
     (:falsified? (adaptive-stress-test heater weather margin 8)) ;=> true"
  {:arglists '([system disturbances margin horizon & {:keys [budget c-explore miss-penalty]}])}
  [system disturbances margin horizon
   & {:keys [budget c-explore miss-penalty]
      :or   {budget 800 c-explore 40.0 miss-penalty #(+ 50.0 (* 10.0 %))}}]
  (let [game  (stress-game system disturbances margin horizon miss-penalty)
        start {:trajectory     [(:initial-state system)]
               :disturbances   []
               :robustness     (margin (:initial-state system))
               :log-likelihood 0.0
               :model          disturbances}
        step  (fn [position]
                ((:apply-move game) position
                 (:move (games/mcts game position :budget budget :c-explore c-explore))))
        final (->> (iterate step start)
                   (drop-while (complement (:game-over? game)))
                   first)]
    (-> (dissoc final :model)
        (assoc :falsified? (neg? (:robustness final))))))

(defn most-likely-failure
  "The most likely disturbance sequence that drives the system to a violation
   within `horizon` steps, exactly: a shortest path over disturbance
   sequences in which a step costs -log p. States reached by different
   sequences of the same length are searched once.
   Catalog: V17-V20 (the exact counterpart). Pattern: TREE/GRAPH EXPLORER
   (explore + best-first + unique-by).

   System -> {Disturbance Prob} -> (State -> R) -> Int
   -> {:falsified? Bool, :disturbances [d], :trajectory [State], :robustness R, :log-likelihood R}

   Returns {:falsified? false} when no sequence fails.

   Example:
     ;; heater, weather and margin as under adaptive-stress-test
     (:disturbances (most-likely-failure heater weather margin 8))
     ;=> [-1.0 1.0 1.0 1.0 1.0 1.0]     ; a cold snap switches the heater on, five heat waves do the rest"
  {:arglists '([system disturbances margin horizon])}
  [{:keys [dynamics initial-state]} disturbances margin horizon]
  (if-let [{:keys [node cost]}
           (graph/shortest-path
            (fn [{:keys [trajectory disturbances-so-far]}]
              (when (and (>= (margin (peek trajectory)) 0) (< (count disturbances-so-far) horizon))
                (for [[d p] disturbances]
                  [{:trajectory          (conj trajectory (dynamics (peek trajectory) d))
                    :disturbances-so-far (conj disturbances-so-far d)}
                   (- (Math/log p))])))
            {:trajectory [initial-state] :disturbances-so-far []}
            #(neg? (margin (peek (:trajectory %))))
            :key (fn [{:keys [trajectory disturbances-so-far]}] [(peek trajectory) (count disturbances-so-far)]))]
    {:falsified?     true
     :disturbances   (:disturbances-so-far node)
     :trajectory     (:trajectory node)
     :robustness     (reduce min (map margin (:trajectory node)))
     :log-likelihood (- cost)}
    {:falsified? false}))
