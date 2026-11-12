(ns orhof.search
  "Pattern 7, the Tree/Graph Explorer, as composable higher-order functions:
   unfold a search space and fold it in the same pass. The space is never built.

     search      top-down: a reducing function folds the nodes in strategy
                 order, and what it has folded so far steers the unfold
                 (pruning, bounds, early exit)
     explore     the unfold alone: the nodes as a reducible, seqable collection
     hylo        bottom-up: the value of a node is a fold of its children's values
     hylo-eager  the same on an explicit stack, for trees too deep for the JVM stack
     converge    the one-child case: the stopping rule `iterate` lacks

   A strategy decides which pending node comes next. Like a reducing function
   it is one function with three arities, and its frontier is plain data:

     (strategy)                 -> an empty frontier
     (strategy frontier)        -> [node frontier'], or nil when nothing is pending
     (strategy frontier nodes)  -> the frontier with the sibling `nodes` added

   depth-first and breadth-first are strategies, (best-first priority) returns
   one, unique-by and unique wrap one for graphs. Everything composes with
   what Clojure already has: successor functions with mapcat, strategy
   transformers with comp, the fold with transducers, hylo with comp and juxt.

   This namespace needs nothing but clojure.core."
  (:import (clojure.lang PersistentQueue)
           (java.util IdentityHashMap)))

;; =============================================================
;; Strategies
;; =============================================================

(defn depth-first
  "Strategy: the newest siblings first. Pre-order, like tree-seq.

   The frontier is a list of sibling seqs, so siblings keep their order and a
   lazy successor seq is only realised as far as the search backtracks into it.

   Example:
     (into [] (explore depth-first {:a [:b :c] :b [:d]} :a)) ;=> [:a :b :d :c]"
  ([] ())
  ([frames]
   (loop [frames frames]
     (when (seq frames)
       (if-let [siblings (seq (peek frames))]
         [(first siblings) (conj (pop frames) (rest siblings))]
         (recur (pop frames))))))
  ([frames nodes] (conj frames nodes)))

(defn breadth-first
  "Strategy: the oldest nodes first, level by level. The frontier is a
   PersistentQueue.

   Example:
     (into [] (explore breadth-first {:a [:b :c] :b [:d]} :a)) ;=> [:a :b :c :d]"
  ([] PersistentQueue/EMPTY)
  ([queue] (when (seq queue) [(peek queue) (pop queue)]))
  ([queue nodes] (into queue nodes)))

(defn best-first
  "Returns a strategy that takes the node with the lowest (priority node)
   first; ties leave in insertion order. Priorities are compared with
   `compare`, so numbers and vectors of numbers both work.

   (Node -> Priority) -> Strategy

   The frontier is a sorted map from priority to a queue of nodes.

   Example:
     (into [] (explore (best-first count) {} \"ccc\" \"a\" \"bb\")) ;=> [\"a\" \"bb\" \"ccc\"]"
  [priority]
  (fn
    ([] (sorted-map))
    ([buckets]
     (when-let [[p queue] (first buckets)]
       [(peek queue) (if (= 1 (count queue))
                       (dissoc buckets p)
                       (assoc buckets p (pop queue)))]))
    ([buckets nodes]
     (reduce (fn [buckets node]
               (update buckets (priority node) (fnil conj PersistentQueue/EMPTY) node))
             buckets nodes))))

(defn unique-by
  "Strategy transformer for graphs: each (key node) is taken at most once,
   so a search over a graph with cycles terminates.

   (Node -> Key) -> Strategy -> Strategy

   The frontier becomes [seen inner-frontier]. Keys are compared by value, so
   keep them small (ids, coordinates): a lookup that lands on another key
   walks both keys, which is slow for deep nested data. For nodes that are
   large immutable structures use (by-identity).

   Example:
     (def graph {:a [:b :c] :b [:d] :c [:d] :d [:a]})
     (into [] (explore (unique-by identity breadth-first) graph :a)) ;=> [:a :b :c :d]"
  [key strategy]
  (fn
    ([] [#{} (strategy)])
    ([[seen frontier]]
     (loop [frontier frontier]
       (when-let [[node frontier] (strategy frontier)]
         (let [k (key node)]
           (if (contains? seen k)
             (recur frontier)
             [node [(conj seen k) frontier]])))))
    ([[seen frontier] nodes]
     [seen (strategy frontier (remove #(contains? seen (key %)) nodes))])))

(defn unique
  "Strategy transformer: each node is taken at most once. (unique-by identity strategy).

   Strategy -> Strategy

   Example:
     (into [] (explore (unique depth-first) {:a [:b] :b [:a]} :a)) ;=> [:a :b]"
  [strategy]
  (unique-by identity strategy))

(defn by-identity
  "Returns a key function for unique-by and for the :memo option of hylo and
   hylo-eager that tells nodes apart by identity instead of value: the same
   object always gets the same key, equal but distinct objects get different
   keys. Each key is a small number, so a lookup costs the same however large
   the node is.

   () -> (Node -> Int)

   Use it when nodes are big immutable structures that share sub-structure,
   such as expression graphs. The function remembers every node it has seen;
   let it go with the search it was made for.

   Example:
     (let [id (by-identity), e [:+ :x :y]]
       [(= (id e) (id e)) (= (id e) (id [:+ :x :y]))]) ;=> [true false]"
  []
  (let [numbers (IdentityHashMap.)]
    (fn [node]
      (locking numbers
        (or (.get numbers node)
            (let [n (.size numbers)]
              (.put numbers node n)
              n))))))

;; =============================================================
;; search: unfold and fold in one loop, the fold steering the unfold
;; =============================================================

(defn search
  "Folds the space reachable from `roots` while unfolding it.

   Strategy -> (Acc -> Node -> Acc) -> Acc -> (Acc -> Node -> [Node]) -> Node... -> Acc

   (rf acc node)     -> the new acc, or (reduced acc) to stop the search
   (expand acc node) -> the child nodes. It sees what the fold knows so far,
                        so it can prune: nil cuts the branch below `node`.

   Runs in a loop, so depth is limited by memory, not by the stack.

   Example:
     ;; branch and bound: the largest sum of a subset of xs that is <= 10
     (let [xs [7 5 4 3]]
       (search depth-first
               (fn [best [_ sum]] (max best sum))
               0
               (fn [best [i sum]]                       ; prune what cannot beat `best`
                 (when (and (< i (count xs)) (> (reduce + sum (subvec xs i)) best))
                   (cond-> [[(inc i) sum]]
                     (<= (+ sum (xs i)) 10) (conj [(inc i) (+ sum (xs i))]))))
               [0 0]))
     ;=> 10"
  [strategy rf init expand & roots]
  (loop [acc init, frontier (strategy (strategy) roots)]
    (if-let [[node frontier] (strategy frontier)]
      (let [acc (rf acc node)]
        (if (reduced? acc)
          @acc
          (recur acc (strategy frontier (expand acc node)))))
      acc)))

;; =============================================================
;; explore: the unfold alone
;; =============================================================

(defn explore
  "The nodes reachable from `roots`, in strategy order, as a collection that
   is both reducible and seqable. Nothing is expanded until it is consumed, so
   the space may be infinite.

   Strategy -> (Node -> [Node]) -> Node... -> Reducible Node

   (expand node) -> the child nodes; nil or empty prunes.

   It is clojure.core/iteration whose continuation token is the frontier, so
   it works with reduce, transduce, into, sequence, eduction and the seq
   functions.

   Example:
     (take 5 (explore breadth-first (fn [n] [(* 2 n) (inc (* 2 n))]) 1)) ;=> (1 2 3 4 5)"
  [strategy expand & roots]
  (iteration strategy
             :initk (strategy (strategy) roots)
             :vf first
             :kf (fn [[node frontier]] (strategy frontier (expand node)))))

;; =============================================================
;; hylo: bottom-up, children's values fold into their parent's
;; =============================================================

(defn- lazy-map
  "map without chunking: one child at a time."
  [f coll]
  (lazy-seq
    (when-let [s (seq coll)]
      (cons (f (first s)) (lazy-map f (rest s))))))

(defn hylo
  "Returns the function node -> value defined by an unfold and a fold
   (a hylomorphism):

     (expand node)             -> the child nodes
     (fold node child-values)  -> the value of the node

   (Node -> [Node]) -> (Node -> [Value] -> Value) -> (Node -> Value)

   child-values is lazy: a child whose value `fold` never reads is never
   expanded, which is how alpha-beta and branch and bound prune.

   Options:
     :memo key-fn   remember the value of every (key-fn node), like memoize.
                    Equal keys are solved once: dynamic programming on a
                    graph without cycles. Keep keys small, or use (by-identity).

   The recursion uses the JVM stack: about 1,500 levels on the default 1 MB
   thread stack. For deeper spaces use hylo-eager.

   Example:
     ;; paths from corner to corner of a 16x16 grid
     ((hylo (fn [[x y]] (cond-> [] (< x 16) (conj [(inc x) y]) (< y 16) (conj [x (inc y)])))
            (fn [_ counts] (if (seq counts) (reduce + counts) 1N))
            :memo identity)
      [0 0])
     ;=> 601080390N"
  [expand fold & {:keys [memo]}]
  (if-not memo
    (fn value [node] (fold node (lazy-map value (expand node))))
    (let [cache (atom {})]
      (fn value [node]
        (let [k (memo node)]
          (if-let [hit (find @cache k)]
            (val hit)
            (let [v (fold node (lazy-map value (expand node)))]
              (swap! cache assoc k v)
              v)))))))

(defn hylo-eager
  "Like hylo, on an explicit stack: depth is limited by memory, not by the JVM
   stack. The price is that every child is evaluated before its parent, so
   `fold` gets all the child values (a vector) and cannot prune by not
   reading them.

   (Node -> [Node]) -> (Node -> [Value] -> Value) -> (Node -> Value)

   Whenever `fold` reads every child value, hylo and hylo-eager return the
   same result. Options:
     :memo key-fn   as in hylo. With :memo a cycle in the space throws
                    instead of looping forever.

   Example:
     ;; the length of a chain a million nodes long
     ((hylo-eager (fn [n] (when (pos? n) [(dec n)]))
                  (fn [_ lengths] (if (seq lengths) (inc (first lengths)) 0)))
      1000000)
     ;=> 1000000"
  [expand fold & {:keys [memo]}]
  (let [cache (atom {})
        key   (or memo (constantly nil))
        hit   (if memo #(find @cache %) (constantly nil))
        frame (fn [node k] [node k (seq (expand node)) []])]
    (fn [root]
      (let [k (key root)]
        (if-let [e (hit k)]
          (val e)
          (loop [stack (list (frame root k)), pending #{k}]
            (let [[node k kids values] (peek stack)
                  below (pop stack)]
              (if kids
                ;; go down into the next child, unless its value is known
                (let [kid (first kids), kk (key kid), more (next kids)]
                  (if-let [e (hit kk)]
                    (recur (conj below [node k more (conj values (val e))]) pending)
                    (do (when (and memo (contains? pending kk))
                          (throw (ex-info "hylo-eager: the space has a cycle" {:node kid})))
                        (recur (conj below [node k more values] (frame kid kk))
                               (if memo (conj pending kk) pending)))))
                ;; all children done: fold, and hand the value to the parent
                (let [v (fold node values)]
                  (when memo (swap! cache assoc k v))
                  (if-let [[pnode pk pkids pvalues] (peek below)]
                    (recur (conj (pop below) [pnode pk pkids (conj pvalues v)])
                           (if memo (disj pending k) pending))
                    v))))))))))

;; =============================================================
;; converge: the one-child case
;; =============================================================

(defn converge
  "The first x in xs for which (close? previous x) holds; the last x if xs
   ends first; nil if xs is empty. The stopping rule `iterate` lacks.

   (a -> a -> Bool) -> [a] -> a

   Example:
     (converge = (iterate #(Math/floor (/ % 2)) 100.0)) ;=> 0.0
     (converge #(< (abs (- %1 %2)) 1e-12) (iterate #(* 0.5 (+ % (/ 2.0 %))) 1.0)) ;=> 1.414213562373095"
  [close? xs]
  (let [none (Object.)
        x    (reduce (fn [prev x]
                       (if (and (not (identical? prev none)) (close? prev x))
                         (reduced x)
                         x))
                     none xs)]
    (when-not (identical? x none) x)))
