(ns orhof.search.graph
  "Graph search on orhof.search: shortest paths and reachability.
   Pattern: TREE/GRAPH EXPLORER — explore with a strategy; unique-by makes
   the tree a graph."
  (:require [orhof.search :refer [explore best-first breadth-first unique unique-by]]))

(defn shortest-path
  "Cheapest path from `start` to a node satisfying `goal?`: Dijkstra's
   algorithm, or A* when given a heuristic.
   Catalog: D37. Pattern: TREE/GRAPH EXPLORER (explore + best-first + unique-by).

   (Node -> {Node Cost}) -> Node -> (Node -> Bool) -> {:node Node, :cost R, :path [Node]}

   neighbors: node -> {node' cost} or [[node' cost] ...], costs >= 0
   Options:
     :heuristic  node -> an estimate of the remaining cost that never
                 overestimates and is consistent (default: 0, Dijkstra)
     :key        node -> what makes two nodes the same state (default: identity)

   Returns nil when no goal can be reached. The graph may be infinite.

   Example:
     (shortest-path {:a {:b 7 :c 9} :b {:d 15} :c {:d 11} :d {}} :a #{:d})
     ;=> {:node :d :cost 20 :path [:a :c :d]}"
  [neighbors start goal? & {:keys [heuristic key] :or {heuristic (constantly 0) key identity}}]
  (->> (explore (unique-by (comp key :node) (best-first #(+ (:cost %) (heuristic (:node %)))))
                (fn [{:keys [node cost path]}]
                  (for [[node' step-cost] (neighbors node)]
                    {:node node' :cost (+ cost step-cost) :path (conj path node')}))
                {:node start :cost 0 :path [start]})
       (filter (comp goal? :node))
       first))

(defn reachable
  "The set of nodes that can be reached from `roots`.
   Catalog: V48. Pattern: TREE/GRAPH EXPLORER (explore + breadth-first + unique).

   (Node -> [Node]) -> Node... -> #{Node}

   Example:
     (reachable {:a [:b] :b [:a :c] :d [:a]} :a) ;=> #{:a :b :c}"
  [successors & roots]
  (into #{} (apply explore (unique breadth-first) successors roots)))
