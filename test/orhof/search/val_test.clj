(ns orhof.search.val-test
  "Adaptive stress testing and its exact counterpart on the thermostat,
   against brute force over every disturbance sequence."
  (:require [clojure.test :refer :all]
            [orhof.search.val :as sv]
            [orhof.val.spec :as spec]
            [orhof.test-util :as tu]))

(use-fixtures :each tu/with-seeded-rand)   ; deterministic rand for the tree search

;; A heater that switches on below 20 degrees and leaks heat towards 20.
(def heater
  (spec/make-system (fn [temp d] (+ temp (if (< temp 20.0) 2.0 0.0) d (* -0.05 (- temp 20.0))))
                    20.0))
(def weather {-1.0 0.05, 0.0 0.9, 1.0 0.05})                  ; cold snap, calm, heat wave
(defn margin [temp] (min (- temp 18.0) (- 25.0 temp)))        ; stay within [18, 25]

(def horizon 8)
(def best-log-likelihood (* 6 (Math/log 0.05)))               ; six rare disturbances: the cheapest way to fail

(defn fails? [disturbances]
  (neg? ((spec/always margin) (spec/rollout heater disturbances))))

(defn near? [x y] (< (abs (- x y)) 1e-9))

(defn brute-force
  "The oracle: extend every sequence step by step, merging equal positions."
  [system disturbances margin horizon]
  (let [extend (fn [paths]
                 (for [{:keys [state log-p failed?] :as path} paths, [d p] disturbances]
                   (if failed?
                     path
                     (let [s ((:dynamics system) state d)]
                       {:state s :log-p (+ log-p (Math/log p)) :failed? (neg? (margin s))}))))]
    (->> (nth (iterate (comp distinct extend) [{:state (:initial-state system) :log-p 0.0 :failed? false}]) horizon)
         (filter :failed?)
         (map :log-p)
         (reduce max))))

(defn failure-probability
  "The exact probability that random disturbances cause a failure within the horizon."
  [system disturbances margin horizon]
  (->> (nth (iterate (fn [paths]
                       (->> (for [{:keys [state p failed?]} paths, [d q] disturbances]
                              (if failed?
                                {:state state :p (* p q) :failed? true}
                                (let [s ((:dynamics system) state d)]
                                  {:state s :p (* p q) :failed? (neg? (margin s))})))
                            (group-by (juxt :state :failed?))
                            (map (fn [[[state failed?] group]]
                                   {:state state :failed? failed? :p (reduce + (map :p group))}))))
                     [{:state (:initial-state system) :p 1.0 :failed? false}])
            horizon)
       (filter :failed?)
       (map :p)
       (reduce +)))

(deftest test-most-likely-failure
  (let [exact (sv/most-likely-failure heater weather margin horizon)]
    (testing "its disturbances do make the system fail"
      (is (:falsified? exact))
      (is (fails? (:disturbances exact)))
      (is (neg? (:robustness exact)))
      (is (= (spec/rollout heater (:disturbances exact)) (:trajectory exact))))
    (testing "with log-likelihood 6 ln 0.05, the same as brute force over all 3^8 sequences"
      (is (near? best-log-likelihood (:log-likelihood exact)))
      (is (near? (brute-force heater weather margin horizon) (:log-likelihood exact))))
    (testing "a system that cannot fail"
      (is (= {:falsified? false}
             (sv/most-likely-failure heater {0.0 1.0} margin horizon))))
    (testing "a horizon too short to fail"
      (is (= {:falsified? false} (sv/most-likely-failure heater weather margin 5))))))

(deftest test-adaptive-stress-test
  (let [runs (mapv (fn [seed]
                     (tu/with-seeded-rand seed #(sv/adaptive-stress-test heater weather margin horizon)))
                   (range 1 7))]
    (testing "finds a failure from every one of 6 seeds"
      (is (every? :falsified? runs))
      (is (every? #(fails? (:disturbances %)) runs))
      (is (every? #(= (:trajectory %) (spec/rollout heater (:disturbances %))) runs)))
    (testing "each within 0.25 of the best log-likelihood: at most two wasted calm steps"
      (is (every? #(< (- best-log-likelihood (:log-likelihood %)) 0.25) runs)))
    (testing "random sampling would need about a million trajectories per failure; the search used 6,400 simulations"
      (is (< 1e-6 (failure-probability heater weather margin horizon) 1e-5)))))
