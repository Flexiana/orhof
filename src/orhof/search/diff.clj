(ns orhof.search.diff
  "Backpropagation (reverse-mode automatic differentiation) on orhof.search.
   Pattern: TREE/GRAPH EXPLORER — the forward pass is a memoised fold of the
   expression graph; the backward pass is a reduce over its nodes in an order
   that visits every node after all the nodes that use it.

   An expression is a number, a keyword (a variable) or a vector
   [op & operands]. A sub-expression that is the same object in several
   places is one node of the graph: bind it once with `let`, and its value
   and its adjoint are computed once however often it is used."
  (:require [orhof.search :refer [explore hylo-eager best-first unique-by by-identity]]))

(def operations
  "The operations backprop knows: {op {:value f, :partials g}}.
     :value     applies the operation to the values of the operands
     :partials  (fn [operand-values result]) -> the derivative of the result
                with respect to each operand
   Pass an extended map as :operations to add your own.

   Example:
     (assoc operations :relu {:value    #(max 0.0 %)
                              :partials (fn [[x] _] [(if (pos? x) 1.0 0.0)])})"
  {:+    {:value +  :partials (fn [xs _] (map (constantly 1.0) xs))}
   :-    {:value -  :partials (fn [xs _] (if (next xs) (cons 1.0 (map (constantly -1.0) (rest xs))) [-1.0]))}
   :*    {:value *  :partials (fn [xs _]
                                (map-indexed (fn [i _] (reduce * 1.0 (keep-indexed #(when (not= i %1) %2) xs))) xs))}
   :/    {:value /  :partials (fn [[x y] _] [(/ 1.0 y) (- (/ x (* y y)))])}
   :sin  {:value #(Math/sin %)  :partials (fn [[x] _] [(Math/cos x)])}
   :cos  {:value #(Math/cos %)  :partials (fn [[x] _] [(- (Math/sin x))])}
   :exp  {:value #(Math/exp %)  :partials (fn [_ y] [y])}
   :log  {:value #(Math/log %)  :partials (fn [[x] _] [(/ 1.0 x)])}
   :tanh {:value #(Math/tanh %) :partials (fn [_ y] [(- 1.0 (* y y))])}})

(defn backprop
  "The value of an expression and its derivative with respect to every
   variable, at the point `env`: one forward pass and one backward pass,
   whatever the number of variables.
   Catalog: O5. Pattern: TREE/GRAPH EXPLORER (hylo-eager forward; explore +
   best-first + reduce backward).

   Expression -> {Variable R} -> {:keys [operations]} -> {:value R, :gradient {Variable R}}

   The graph may be as deep as memory allows: neither pass recurses, and
   nodes are told apart by identity, so the cost is linear in the number of
   nodes however large the expressions under them.

   Example:
     ;; d/dx and d/dy of x*y + sin x at (2, 3)
     (backprop [:+ [:* :x :y] [:sin :x]] {:x 2.0 :y 3.0})
     ;=> {:value 6.909297 :gradient {:x 2.583853 :y 2.0}}

     ;; a shared sub-expression is one node: (x*x) * (x*x)
     (let [square [:* :x :x]]
       (backprop [:* square square] {:x 1.5}))
     ;=> {:value 5.0625 :gradient {:x 13.5}}"
  {:arglists '([expression env & {:keys [operations]}])}
  [expression env & {ops :operations :or {ops operations}}]
  (let [operands (fn [e] (when (vector? e) (rest e)))
        id       (by-identity)
        ;; forward: two memoised folds over the graph
        value    (hylo-eager operands
                             (fn [e values]
                               (cond (vector? e)  (apply (:value (ops (first e))) values)
                                     (keyword? e) (env e)
                                     :else        e))
                             :memo id)
        height   (hylo-eager operands
                             (fn [_ heights] (if (seq heights) (inc (reduce max heights)) 0))
                             :memo id)
        ;; backward: highest nodes first, so a node is visited after every node that uses it;
        ;; each visit pushes the node's adjoint down to its operands
        adjoints (reduce (fn [adjoints e]
                           (if (vector? e)
                             (let [xs (map value (operands e))
                                   ds ((:partials (ops (first e))) xs (value e))
                                   a  (adjoints (id e))]
                               (reduce (fn [adjoints [operand d]]
                                         (update adjoints (id operand) (fnil + 0.0) (* a d)))
                                       adjoints (map vector (operands e) ds)))
                             adjoints))
                         {(id expression) 1.0}
                         (explore (unique-by id (best-first (comp - height))) operands expression))]
    {:value    (value expression)
     :gradient (into {} (keep (fn [variable]
                                (when-let [a (adjoints (id variable))] [variable a])))
                     (keys env))}))
