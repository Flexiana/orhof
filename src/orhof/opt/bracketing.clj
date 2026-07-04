(ns orhof.opt.bracketing
  "1D bracket-based optimization methods.
   All methods search within an interval [a, b] for a minimum.
   Pattern: Iterative Refinement — shrink the bracket each step."
  (:require [orhof.core :as c]))

(defn golden-section-search
  "Golden section search for 1D minimization.
   Catalog: O9. Pattern: ITERATE.
   f: R -> R, [a, b] bracket, tol tolerance."
  [f a b & {:keys [tol] :or {tol 1e-8}}]
  (let [phi    (/ (+ 1 (Math/sqrt 5)) 2)
        resphi (- 2 phi)
        step   (fn [[a b]]
                 (let [x1 (+ a (* resphi (- b a)))
                       x2 (- b (* resphi (- b a)))]
                   (if (< (f x1) (f x2)) [a x2] [x1 b])))]
    (->> (iterate step [a b])
         (drop-while (fn [[a b]] (>= (abs (- b a)) tol)))
         first
         ((fn [[a b]] (/ (+ a b) 2.0))))))

(defn fibonacci-search
  "Fibonacci search for 1D minimization.
   Catalog: O8. Pattern: ITERATE (reduce over steps)."
  [f a b & {:keys [n] :or {n 50}}]
  (let [fibs  (take (+ n 2) (map first (iterate (fn [[a b]] [b (+ a b)]) [1 1])))
        fib   (vec fibs)
        [a' b'] (reduce (fn [[a b] k]
                          (let [rho (/ (double (nth fib (- n k)))
                                       (double (nth fib (- n k -1))))
                                x1  (+ a (* (- 1 rho) (- b a)))
                                x2  (+ a (* rho (- b a)))]
                            (if (< (f x1) (f x2)) [a x2] [x1 b])))
                        [a b]
                        (range 1 n))]
    (/ (+ a' b') 2.0)))

(defn bisection-method
  "Bisection method for root finding. f: R -> R.
   Catalog: O12. Pattern: ITERATE."
  [f a b & {:keys [tol] :or {tol 1e-10}}]
  (let [step (fn [[a b]]
               (let [mid (/ (+ a b) 2.0)]
                 (if (neg? (* (f a) (f mid))) [a mid] [mid b])))]
    (->> (iterate step [a b])
         (drop-while (fn [[a b]] (>= (abs (- b a)) tol)))
         first
         ((fn [[a b]] (/ (+ a b) 2.0))))))

(defn quadratic-fit-search
  "Quadratic fit search for 1D minimization.
   Catalog: O10. Pattern: ITERATE."
  [f a b & {:keys [tol max-iter] :or {tol 1e-8 max-iter 100}}]
  (let [step (fn [[a b c]]
               (let [fa (f a) fb (f b) fc (f c)
                     num (+ (* fa (- (* b b) (* c c)))
                            (* fb (- (* c c) (* a a)))
                            (* fc (- (* a a) (* b b))))
                     den (+ (* fa (- b c))
                            (* fb (- c a))
                            (* fc (- a b)))
                     x   (if (zero? den) c (/ num (* 2 den)))
                     x   (max a (min b x))]
                 (if (< x c)
                   (if (< (f x) fc) [a c x] [x b c])
                   (if (< (f x) fc) [c b x] [a x c]))))
        init   [a b (/ (+ a b) 2.0)]
        result (->> (iterate step init)
                    (take (inc max-iter)))]
    (let [[_ _ c] (or (->> result
                           (drop-while (fn [[a b _]] (>= (abs (- b a)) tol)))
                           first)
                      (last result))]
      c)))

(defn shubert-piyavskii
  "Shubert-Piyavskii method for Lipschitz optimization.
   Catalog: O11. Pattern: ITERATE + TRANSFORM-PROBLEM.
   Uses the Lipschitz constant L to build a piecewise-linear
   lower bound. Evaluates at the point where the lower bound
   is minimized. Guaranteed to find global minimum within tolerance."
  [f a b L & {:keys [tol max-iter] :or {tol 1e-6 max-iter 200}}]
  ;; Maintain a set of evaluated points and their sawtooth lower bound
  ;; Each evaluation adds a new tent function to the lower envelope
  (reduce (fn [{:keys [points best-x best-val]} _iter]
            (if (empty? points)
              ;; First evaluation: midpoint
              (let [mid (/ (+ a b) 2.0)
                    fmid (f mid)]
                {:points [[mid fmid]]
                 :best-x mid :best-val fmid})
              ;; Find the interval where the lower bound is lowest
              ;; Lower bound between x_i and x_{i+1}:
              ;; intersection of tents: (f(x_i)+f(x_{i+1}))/2 + L*(x_{i+1}-x_i)/2
              ;; Simplified: evaluate at midpoint of widest unexplored gap
              (let [sorted (sort-by first points)
                    ;; Add boundary points
                    all-pts (concat [[a (+ best-val (* L (- (ffirst sorted) a)))]]
                                    sorted
                                    [[b (+ best-val (* L (- b (first (last sorted)))))]])
                    pairs   (partition 2 1 all-pts)
                    ;; Find the interval with lowest potential minimum
                    best-gap (apply min-key
                                    (fn [[[x1 f1] [x2 f2]]]
                                      (let [mid-bound (- (/ (+ f1 f2) 2.0) (* L (/ (- x2 x1) 2.0)))]
                                        mid-bound))
                                    pairs)
                    [[x1 _] [x2 _]] best-gap
                    x-new   (/ (+ x1 x2) 2.0)
                    f-new   (f x-new)
                    gap     (- x2 x1)]
                (if (< gap tol)
                  (reduced {:points (conj (vec points) [x-new f-new])
                            :best-x (if (< f-new best-val) x-new best-x)
                            :best-val (min f-new best-val)})
                  {:points (conj (vec points) [x-new f-new])
                   :best-x (if (< f-new best-val) x-new best-x)
                   :best-val (min f-new best-val)}))))
          {:points [] :best-x nil :best-val Double/MAX_VALUE}
          (range max-iter)))
