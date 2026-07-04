#!/usr/bin/env clojure
;; Generate API documentation from namespace docstrings
;; Run: clojure -M generate_docs.clj

(require '[clojure.string :as str])

(def namespaces
  '[orhof.core orhof.math orhof.diff
    orhof.opt.bracketing orhof.opt.descent orhof.opt.second-order
    orhof.opt.direct orhof.opt.stochastic orhof.opt.population
    orhof.opt.constrained orhof.opt.linear orhof.opt.surrogate
    orhof.opt.multiobjective
    orhof.mdp.planning orhof.mdp.online orhof.mdp.policy
    orhof.mdp.beliefs orhof.mdp.games orhof.mdp.inference
    orhof.mdp.learning orhof.mdp.approx
    orhof.val.spec orhof.val.falsify orhof.val.sampling
    orhof.val.reach orhof.val.explain])

(doseq [ns-sym namespaces]
  (require ns-sym))

(defn format-var-doc [ns-sym var-sym]
  (let [v (ns-resolve (find-ns ns-sym) var-sym)
        m (meta v)
        doc (or (:doc m) "")
        arglists (:arglists m)]
    (str "### `" (name var-sym) "`\n\n"
         (when arglists
           (str "```clojure\n"
                (str/join "\n" (map #(str "(" (name var-sym) " " (str/join " " %) ")") arglists))
                "\n```\n\n"))
         (when (seq doc)
           (str doc "\n\n"))
         "---\n\n")))

(defn format-ns-doc [ns-sym]
  (let [ns-obj (find-ns ns-sym)
        ns-doc (or (:doc (meta ns-obj)) "")
        publics (sort (keys (ns-publics ns-obj)))]
    (str "## `" (name ns-sym) "`\n\n"
         (when (seq ns-doc) (str ns-doc "\n\n"))
         (str/join "" (map #(format-var-doc ns-sym %) publics))
         "\n")))

(let [header "# HOF Algorithms Library — API Reference\n\n"
      intro (str "Auto-generated documentation for all public functions.\n"
                 "Each entry shows the function signature, type information, and usage examples.\n\n"
                 "**Library stats:**\n"
                 (str "- " (count namespaces) " namespaces\n")
                 (str "- " (reduce + (map #(count (ns-publics (find-ns %))) namespaces)) " public functions\n\n")
                 "---\n\n")
      toc (str "## Table of Contents\n\n"
               (str/join "\n" (map (fn [ns-sym]
                                     (let [n (count (ns-publics (find-ns ns-sym)))]
                                       (str "- [`" (name ns-sym) "`](#" (str/replace (name ns-sym) "." "") ") (" n " functions)")))
                                   namespaces))
               "\n\n---\n\n")
      body (str/join "" (map format-ns-doc namespaces))
      content (str header intro toc body)]
  (spit "docs/API.md" content)
  (println "Generated docs/API.md with" (count namespaces) "namespaces"))
