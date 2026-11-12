(ns orhof.search.learn
  "Tree learners on orhof.search: decision trees, random forests, regression
   trees and gradient boosting.
   Pattern: TREE/GRAPH EXPLORER + MODEL BUILDER — growing a tree is an unfold
   (split the rows) whose fold is the constructor, so hylo returns the tree
   itself. A forest is `repeatedly` over that; boosting is `iterate` over it.

   Rows are maps. Trees are plain data:
     classification  {:attribute a, :branches {value subtree}, :default label} | {:label label}
     regression      {:feature f, :threshold t, :left subtree, :right subtree}  | {:value mean}"
  (:require [orhof.search :refer [hylo]]))

;; =============================================================
;; DECISION TREES (ID3)
;; =============================================================

(defn- entropy [labels]
  (let [n (count labels)]
    (- (reduce + (map (fn [k] (let [p (/ (double k) n)] (* p (/ (Math/log p) (Math/log 2.0)))))
                      (vals (frequencies labels)))))))

(defn- majority [rows target]
  (key (apply max-key val (frequencies (map target rows)))))

(defn- best-attribute
  "The split with the highest information gain, or nil when there is nothing to gain."
  [rows attributes target]
  (let [before (entropy (map target rows))
        gain   (fn [attribute]
                 (let [parts (group-by attribute rows)]
                   {:attribute attribute
                    :parts     parts
                    :gain      (- before
                                  (reduce + (map (fn [part]
                                                   (* (/ (double (count part)) (count rows))
                                                      (entropy (map target part))))
                                                 (vals parts))))}))]
    (when (and (pos? before) (seq attributes))
      (let [best (apply max-key :gain (map gain attributes))]
        (when (pos? (:gain best)) best)))))

(defn decision-tree
  "Grows a classification tree by ID3: split on the attribute with the
   highest information gain until the rows agree or nothing is left to gain.
   Pattern: TREE/GRAPH EXPLORER (hylo; the fold is the constructor).

   [Row] -> [Attribute] -> Attribute -> {:keys [choose]} -> Tree

   rows: maps; attributes: the keys to split on; target: the key of the label
   Options:
     :choose  attributes -> the attributes to consider at this split
              (default: all of them; random-forest draws a random few)

   Example:
     (def tree (decision-tree [{:sky :sun :wind :calm :play :yes}
                               {:sky :sun :wind :gale :play :no}
                               {:sky :rain :wind :calm :play :no}
                               {:sky :rain :wind :gale :play :no}]
                              [:sky :wind] :play))
     (classify tree {:sky :sun :wind :calm}) ;=> :yes"
  [rows attributes target & {:keys [choose] :or {choose identity}}]
  (let [node (fn [rows attributes]
               {:rows rows :attributes attributes
                :split (best-attribute rows (choose attributes) target)})]
    ((hylo (fn [{:keys [attributes split]}]
             (when split
               (map #(node % (remove #{(:attribute split)} attributes)) (vals (:parts split)))))
           (fn [{:keys [rows split]} subtrees]
             (if split
               {:attribute (:attribute split)
                :branches  (zipmap (keys (:parts split)) subtrees)
                :default   (majority rows target)}
               {:label (majority rows target)})))
     (node rows attributes))))

(defn classify
  "The label a decision tree gives a row. A value the tree has not seen falls
   back to the majority label at that node.

   Tree -> Row -> Label

   Example:
     (classify tree {:sky :fog :wind :calm}) ;=> :no     ; tree as under decision-tree"
  [tree row]
  (if-let [attribute (:attribute tree)]
    (recur (get (:branches tree) (row attribute) {:label (:default tree)}) row)
    (:label tree)))

;; =============================================================
;; RANDOM FOREST
;; =============================================================

(defn- shuffled
  "A random permutation, through clojure.core/rand."
  [coll]
  (map second (sort-by first (map (fn [x] [(rand) x]) coll))))

(defn random-forest
  "Grows decision trees on bootstrap samples of the rows, each split choosing
   among a few randomly drawn attributes.
   Pattern: GENERATE (repeatedly) + TREE/GRAPH EXPLORER (decision-tree).

   [Row] -> [Attribute] -> Attribute -> {:keys [n-trees n-features]} -> [Tree]

   Options:
     :n-trees     number of trees (default 25)
     :n-features  attributes drawn at each split (default: floor of the
                  square root of the number of attributes, at least 1)

   Example:
     (def forest (random-forest rows [:a :b :c :d] :label :n-trees 40))
     (classify-forest forest {:a 1 :b 0 :c 2 :d 1}) ;=> :yes"
  [rows attributes target & {:keys [n-trees n-features] :or {n-trees 25}}]
  (let [rows       (vec rows)
        n          (count rows)
        n-features (or n-features (max 1 (int (Math/sqrt (count attributes)))))
        draw       (fn [attributes] (take n-features (shuffled attributes)))]
    (vec (repeatedly n-trees
                     #(decision-tree (vec (repeatedly n (fn [] (rows (int (rand n))))))
                                     attributes target :choose draw)))))

(defn classify-forest
  "The label most trees of a forest give a row.

   [Tree] -> Row -> Label

   Example:
     (classify-forest forest row)"
  [forest row]
  (key (apply max-key val (frequencies (map #(classify % row) forest)))))

;; =============================================================
;; REGRESSION TREES AND GRADIENT BOOSTING
;; =============================================================

(defn- best-threshold
  "The (feature, threshold) that most reduces the squared error, or nil.
   One sorted scan per feature, with running sums."
  [rows features target min-rows]
  (let [sse   (fn [n sum squares] (- squares (/ (* sum sum) n)))
        n     (count rows)
        total (reduce + (map target rows))
        total-squares (reduce + (map #(let [y (target %)] (* y y)) rows))
        scan  (fn [feature]
                (let [sorted (sort-by feature rows)]
                  (loop [[row & more] sorted, k 1, sum 0.0, squares 0.0, best nil]
                    (if (seq more)
                      (let [y       (target row)
                            sum     (+ sum y)
                            squares (+ squares (* y y))
                            x       (feature row)
                            x'      (feature (first more))
                            error   (+ (sse k sum squares)
                                       (sse (- n k) (- total sum) (- total-squares squares)))]
                        (recur more (inc k) sum squares
                               (if (and (< x x') (>= k min-rows) (>= (- n k) min-rows)
                                        (or (nil? best) (< error (:error best))))
                                 {:feature feature :threshold (/ (+ x x') 2.0) :error error}
                                 best)))
                      best))))
        candidates (keep scan features)]
    (when (seq candidates)
      (let [best (apply min-key :error candidates)]
        (when (< (:error best) (- (sse n total total-squares) 1e-12)) best)))))

(defn regression-tree
  "Grows a regression tree (CART): split on the feature and threshold that
   most reduce the squared error, down to a maximum depth.
   Pattern: TREE/GRAPH EXPLORER (hylo; the fold is the constructor).

   [Row] -> [Feature] -> Feature -> {:keys [max-depth min-rows]} -> Tree

   Options:
     :max-depth  deepest split (default 3)
     :min-rows   fewest rows on either side of a split (default 1)

   Example:
     (def tree (regression-tree [{:x 1.0 :y 5.0} {:x 2.0 :y 5.0} {:x 3.0 :y 9.0}] [:x] :y))
     (predict tree {:x 2.9}) ;=> 9.0"
  [rows features target & {:keys [max-depth min-rows] :or {max-depth 3 min-rows 1}}]
  (let [node (fn [rows depth]
               {:rows rows :depth depth
                :split (when (< depth max-depth) (best-threshold rows features target min-rows))})]
    ((hylo (fn [{:keys [rows depth split]}]
             (when-let [{:keys [feature threshold]} split]
               (let [{left true right false} (group-by #(<= (feature %) threshold) rows)]
                 [(node left (inc depth)) (node right (inc depth))])))
           (fn [{:keys [rows split]} [left right]]
             (if split
               (assoc (select-keys split [:feature :threshold]) :left left :right right)
               {:value (/ (reduce + (map target rows)) (count rows))})))
     (node rows 0))))

(defn predict
  "The value a regression tree gives a row.

   Tree -> Row -> R

   Example:
     (predict tree {:x 1.5}) ;=> 5.0     ; tree as under regression-tree"
  [tree row]
  (if-let [feature (:feature tree)]
    (recur (if (<= (feature row) (:threshold tree)) (:left tree) (:right tree)) row)
    (:value tree)))

(defn gradient-boosting
  "Gradient boosting for squared error: each round fits a small regression
   tree to the residuals of the model so far and adds a fraction of it.
   Pattern: ITERATE (the round) + TREE/GRAPH EXPLORER (regression-tree).

   [Row] -> [Feature] -> Feature -> {:keys [n-rounds lr max-depth]}
   -> {:base R, :lr R, :trees [Tree]}

   Options:
     :n-rounds   number of trees (default 50)
     :lr         learning rate, the fraction of each tree that is added (default 0.1)
     :max-depth  depth of each tree (default 2)

   Example:
     (def model (gradient-boosting rows [:x] :y :n-rounds 200))
     (predict-boosted model {:x 1.5})"
  [rows features target & {:keys [n-rounds lr max-depth] :or {n-rounds 50 lr 0.1 max-depth 2}}]
  (let [rows  (vec rows)
        base  (/ (reduce + (map target rows)) (count rows))
        round (fn [{:keys [trees fitted]}]
                (let [residuals (mapv (fn [row y] (assoc row ::residual (- (target row) y))) rows fitted)
                      tree      (regression-tree residuals features ::residual :max-depth max-depth)]
                  {:trees  (conj trees tree)
                   :fitted (mapv (fn [row y] (+ y (* lr (predict tree row)))) rows fitted)}))]
    {:base  base
     :lr    lr
     :trees (:trees (nth (iterate round {:trees [] :fitted (vec (repeat (count rows) base))}) n-rounds))}))

(defn predict-boosted
  "The value a boosted model gives a row: the base value plus the learning
   rate times the sum of its trees.

   {:base R, :lr R, :trees [Tree]} -> Row -> R

   Example:
     (predict-boosted model {:x 1.5})     ; model as under gradient-boosting"
  {:arglists '([model row])}
  [{:keys [base lr trees]} row]
  (reduce + base (map #(* lr (predict % row)) trees)))
