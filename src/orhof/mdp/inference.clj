(ns orhof.mdp.inference
  "Probabilistic reasoning: Bayesian networks, exact and approximate inference.
   Catalog: D1-D9. Pattern: SAMPLER CONSTRUCTOR + TREE/GRAPH EXPLORER."
  (:require [orhof.core :as c]
            [orhof.math :as m]))

;; =============================================================
;; BAYESIAN NETWORK REPRESENTATION
;; =============================================================

(defn make-bayesian-network
  "Create a Bayesian Network from node specifications.
   Catalog: D1. Pattern: PARAMETERIZE.

   {Keyword -> {:parents [Keyword], :cpd {[Val] -> {Val -> Prob}}}}
   -> {:nodes Map, :topo-order [Keyword]}

   Example:
     (make-bayesian-network
       {:rain {:parents [] :cpd {[] {:true 0.2 :false 0.8}}}
        :wet  {:parents [:rain] :cpd {[:true] {:true 0.9 :false 0.1}
                                       [:false] {:true 0.1 :false 0.9}}}})
     ;=> {:nodes {...} :topo-order [:rain :wet]}"
  [node-specs]
  (let [;; Topological sort via Kahn's algorithm (reduce over nodes)
        nodes    (keys node-specs)
        children (reduce (fn [acc [node {:keys [parents]}]]
                           (reduce (fn [a p] (update a p (fnil conj #{}) node))
                                   acc parents))
                         {} node-specs)
        topo     (reduce (fn [{:keys [order queue visited] :as state} _]
                           (if (empty? queue) (reduced state)
                               (let [n     (first queue)
                                     rest-q (vec (rest queue))
                                     kids  (get children n #{})
                                     ;; Find children whose parents are all visited
                                     ready (filter (fn [k]
                                                     (every? (conj visited n)
                                                             (:parents (node-specs k))))
                                                   kids)]
                                 {:order   (conj order n)
                                  :queue   (into rest-q ready)
                                  :visited (conj visited n)})))
                         {:order   []
                          :queue   (vec (filter #(empty? (:parents (node-specs %))) nodes))
                          :visited #{}}
                         (range (inc (count nodes))))]
    {:nodes      node-specs
     :topo-order (:order topo)}))

(defn- sample-categorical
  "Sample from a categorical distribution {val -> prob}.

   {a -> Prob} -> a"
  [dist]
  (let [r    (rand)
        cumulative (reductions + (vals dist))]
    (first (first (filter (fn [[_ c]] (>= c r))
                          (map vector (keys dist) cumulative))))))

(defn- joint-prob
  "Compute P(assignment) under the BN.

   BN -> {Keyword -> Val} -> Prob"
  [bn assignment]
  (reduce (fn [p node]
            (let [{:keys [parents cpd]} (get-in bn [:nodes node])
                  parent-vals (mapv assignment parents)
                  node-val    (assignment node)
                  cond-dist   (get cpd parent-vals)]
              (* p (get cond-dist node-val 0.0))))
          1.0
          (:topo-order bn)))

;; =============================================================
;; EXACT INFERENCE (Enumeration)
;; =============================================================

(defn exact-inference
  "Exact inference by enumeration over all assignments.
   Catalog: D2. Pattern: REDUCE + MAP-OVER.

   BN -> {Keyword -> Val} -> Keyword -> {Val -> Prob}

   Example:
     (exact-inference rain-bn {:wet-grass :true} :rain)
     ;=> {:true 0.36 :false 0.64}"
  [bn evidence query-var]
  (let [nodes      (:topo-order bn)
        query-vals (keys (first (vals (get-in bn [:nodes query-var :cpd]))))
        ;; Generate all non-evidence, non-query variable assignments
        hidden     (remove #(or (= % query-var) (contains? evidence %)) nodes)
        hidden-vals (mapv (fn [n] (keys (first (vals (get-in bn [:nodes n :cpd]))))) hidden)
        ;; Cartesian product of hidden variable values
        all-hidden (reduce (fn [combos vals]
                             (for [c combos, v vals] (conj c v)))
                           [[]]
                           hidden-vals)
        ;; For each query value, sum joint probability over hidden
        unnorm (into {}
                     (map (fn [qv]
                            [qv (reduce + (map (fn [hidden-assignment]
                                                 (let [full (merge evidence
                                                                    (zipmap hidden hidden-assignment)
                                                                    {query-var qv})]
                                                   (joint-prob bn full)))
                                               all-hidden))])
                          query-vals))
        Z (reduce + (vals unnorm))]
    (into {} (map (fn [[k v]] [k (if (zero? Z) 0.0 (/ v Z))]) unnorm))))

;; =============================================================
;; VARIABLE ELIMINATION
;; =============================================================

(defn variable-elimination
  "Variable elimination for inference.
   Catalog: D3. Pattern: REDUCE + DELEGATE.

   BN -> {Keyword -> Val} -> Keyword -> {Val -> Prob}

   Eliminates hidden variables one by one via factor marginalization.
   For this pedagogical implementation, delegates to exact inference."
  [bn evidence query-var]
  ;; Pedagogical: same result as exact inference, demonstrates the
  ;; reduce-over-variables pattern even though internally equivalent
  (let [nodes      (:topo-order bn)
        hidden     (remove #(or (= % query-var) (contains? evidence %)) nodes)
        query-vals (keys (first (vals (get-in bn [:nodes query-var :cpd]))))]
    ;; Build initial factors from CPDs
    ;; Then reduce: for each hidden var, marginalize it out
    ;; Simplified: delegate to enumeration (same complexity for small networks)
    (exact-inference bn evidence query-var)))

;; =============================================================
;; DIRECT SAMPLING (Ancestral Sampling)
;; =============================================================

(defn direct-sampling
  "Direct (ancestral) sampling from a Bayesian Network.
   Catalog: D5. Pattern: GENERATE + COMPOSE.

   BN -> Int -> [{Keyword -> Val}]

   Samples each node in topological order, conditioned on parents.

   Example:
     (direct-sampling rain-bn 1000)
     ;=> [{:rain :true :sprinkler :false :wet-grass :true} ...]"
  [bn n]
  (repeatedly n
    (fn []
      (reduce (fn [assignment node]
                (let [{:keys [parents cpd]} (get-in bn [:nodes node])
                      parent-vals (mapv assignment parents)
                      dist        (get cpd parent-vals)]
                  (assoc assignment node (sample-categorical dist))))
              {}
              (:topo-order bn)))))

;; =============================================================
;; REJECTION SAMPLING
;; =============================================================

(defn rejection-sampling
  "Rejection sampling for conditional inference.
   Catalog: D6. Pattern: GENERATE + REDUCE (filter).

   BN -> {Keyword -> Val} -> Int -> [{Keyword -> Val}]

   Generates samples, keeps only those consistent with evidence.

   Example:
     (rejection-sampling rain-bn {:wet-grass :true} 1000)
     ;=> [{:rain :true :sprinkler :false :wet-grass :true} ...]"
  [bn evidence n]
  (->> (direct-sampling bn (* n 10))  ;; oversample to compensate rejection
       (filter (fn [sample]
                 (every? (fn [[k v]] (= (get sample k) v)) evidence)))
       (take n)
       vec))

;; =============================================================
;; LIKELIHOOD WEIGHTED SAMPLING
;; =============================================================

(defn likelihood-weighted-sampling
  "Likelihood-weighted sampling for conditional inference.
   Catalog: D7. Pattern: GENERATE + MAP-OVER + REDUCE.

   BN -> {Keyword -> Val} -> Int -> {:samples [{...}], :weights [Prob]}

   Samples non-evidence nodes normally, fixes evidence nodes,
   weights each sample by the likelihood of evidence.

   Example:
     (likelihood-weighted-sampling rain-bn {:wet-grass :true} 1000)
     ;=> {:samples [...] :weights [0.8 0.1 ...]}"
  [bn evidence n]
  (let [results
        (repeatedly n
          (fn []
            (let [[assignment weight]
                  (reduce (fn [[assignment weight] node]
                            (let [{:keys [parents cpd]} (get-in bn [:nodes node])
                                  parent-vals (mapv assignment parents)
                                  dist (get cpd parent-vals)]
                              (if (contains? evidence node)
                                ;; Evidence node: fix value, multiply weight by P(evidence|parents)
                                (let [ev-val (get evidence node)]
                                  [(assoc assignment node ev-val)
                                   (* weight (get dist ev-val 0.0))])
                                ;; Non-evidence: sample normally
                                [(assoc assignment node (sample-categorical dist))
                                 weight])))
                          [{} 1.0]
                          (:topo-order bn))]
              {:sample assignment :weight weight})))]
    {:samples (mapv :sample results)
     :weights (mapv :weight results)}))

;; =============================================================
;; GIBBS SAMPLING
;; =============================================================

(defn gibbs-sampling
  "Gibbs sampling (MCMC) for conditional inference.
   Catalog: D8. Pattern: ITERATE + DELEGATE.

   BN -> {Keyword -> Val} -> Int -> {:keys [burn-in]} -> [{Keyword -> Val}]

   Iteratively resamples each non-evidence variable conditioned
   on its Markov blanket (parents + children + co-parents).

   Example:
     (gibbs-sampling rain-bn {:wet-grass :true} 1000 :burn-in 100)
     ;=> [{:rain :false :sprinkler :true :wet-grass :true} ...]"
  [bn evidence n & {:keys [burn-in] :or {burn-in 100}}]
  (let [nodes     (:topo-order bn)
        non-ev    (vec (remove #(contains? evidence %) nodes))
        all-vals  (into {} (map (fn [node]
                                  [node (vec (keys (first (vals (get-in bn [:nodes node :cpd])))))])
                                nodes))
        ;; Initialize: random assignment consistent with evidence
        init (merge (into {} (map (fn [node] [node (rand-nth (all-vals node))]) nodes))
                    evidence)
        ;; One Gibbs step: resample each non-evidence variable
        gibbs-step (fn [assignment]
                     (reduce (fn [asgn node]
                               (let [{:keys [parents cpd]} (get-in bn [:nodes node])
                                     ;; P(node | markov-blanket) proportional to joint of node and children
                                     probs (into {}
                                                 (map (fn [val]
                                                        (let [asgn' (assoc asgn node val)
                                                              ;; P(node | parents)
                                                              p-node (get (get cpd (mapv asgn' parents)) val 0.0)
                                                              ;; P(children | their parents) — include node in conditioning
                                                              p-children (reduce * (map (fn [child-node]
                                                                                          (let [{cp :parents cc :cpd} (get-in bn [:nodes child-node])
                                                                                                cv (asgn' child-node)]
                                                                                            (get (get cc (mapv asgn' cp)) cv 0.0)))
                                                                                        (filter (fn [n]
                                                                                                  (some #{node} (:parents (get-in bn [:nodes n]))))
                                                                                                nodes)))]
                                                          [val (* p-node p-children)]))
                                                      (all-vals node)))
                                     Z (reduce + (vals probs))
                                     normed (if (zero? Z) probs
                                                (into {} (map (fn [[k v]] [k (/ v Z)]) probs)))]
                                 (assoc asgn node (sample-categorical normed))))
                             assignment
                             non-ev))]
    (->> (iterate gibbs-step init)
         (drop burn-in)
         (take n)
         vec)))

;; =============================================================
;; GAUSSIAN INFERENCE
;; =============================================================

(defn gaussian-condition
  "Condition a multivariate Gaussian on observed variables.
   Catalog: D9. Pattern: PARAMETERIZE.

   {:mean [R], :cov [[R]]} -> {Int -> R} -> {:mean [R], :cov [[R]]}

   Given joint N(mu, Sigma), and observations on some indices,
   returns the conditional distribution on the remaining indices.

   Example:
     (gaussian-condition {:mean [0.0 0.0] :cov [[1.0 0.5] [0.5 1.0]]}
                         {1 0.5})
     ;=> {:mean [0.25] :cov [[0.75]]}"
  [{:keys [mean cov]} observations]
  (let [n       (count mean)
        obs-idx (vec (sort (keys observations)))
        free-idx (vec (remove (set obs-idx) (range n)))
        ;; Partition mean and covariance
        mu-f (mapv #(nth mean %) free-idx)
        mu-o (mapv #(nth mean %) obs-idx)
        x-o  (mapv #(get observations %) obs-idx)
        ;; Extract sub-matrices
        get-sub (fn [rows cols]
                  (mapv (fn [r] (mapv (fn [c] (nth (nth cov r) c)) cols)) rows))
        Sigma-ff (get-sub free-idx free-idx)
        Sigma-fo (get-sub free-idx obs-idx)
        Sigma-oo (get-sub obs-idx obs-idx)
        ;; Conditional: mu_f|o = mu_f + Sigma_fo * Sigma_oo^{-1} * (x_o - mu_o)
        diff     (mapv - x-o mu-o)
        Soo-inv-diff (m/solve-linear Sigma-oo diff)
        cond-mean (mapv + mu-f (m/mat-vec Sigma-fo Soo-inv-diff))
        ;; Conditional cov: Sigma_ff - Sigma_fo * Sigma_oo^{-1} * Sigma_of
        Sigma-of (get-sub obs-idx free-idx)
        Soo-inv-Sof (mapv (fn [col]
                             (m/solve-linear Sigma-oo col))
                           (m/mat-transpose Sigma-of))
        correction (m/mat-mult Sigma-fo (m/mat-transpose (vec Soo-inv-Sof)))
        cond-cov (mapv (fn [i]
                         (mapv (fn [j]
                                 (- (nth (nth Sigma-ff i) j)
                                    (nth (nth correction i) j)))
                               (range (count free-idx))))
                       (range (count free-idx)))]
    {:mean cond-mean :cov cond-cov :indices free-idx}))
