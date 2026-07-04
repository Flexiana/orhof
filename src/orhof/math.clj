(ns orhof.math
  "Vector and matrix operations for the HOF algorithms library.
   Pure Clojure implementations operating on standard vectors and
   nested vectors (for matrices). No external dependencies.")

;; =============================================================
;; Vector Operations
;; =============================================================

(defn v+
  "Vector addition.

   [R] -> [R] -> [R]

   Example:
     (v+ [1 2] [3 4]) ;=> [4 6]"
  [a b]
  (mapv + a b))

(defn v-
  "Vector subtraction.

   [R] -> [R] -> [R]

   Example:
     (v- [5 3] [1 2]) ;=> [4 1]"
  [a b]
  (mapv - a b))

(defn v*
  "Scalar-vector multiplication.

   R -> [R] -> [R]

   Example:
     (v* 3 [1 2 4]) ;=> [3 6 12]"
  [s v]
  (mapv #(* s %) v))

(defn dot
  "Dot product of two vectors.

   [R] -> [R] -> R

   Example:
     (dot [1 2 3] [4 5 6]) ;=> 32"
  [a b]
  (reduce + (map * a b)))

(defn vnorm
  "Euclidean norm (L2) of a vector.

   [R] -> R

   Example:
     (vnorm [3 4]) ;=> 5.0"
  [v]
  (Math/sqrt (reduce + (map #(* % %) v))))

(defn v-zero
  "Zero vector of dimension n.

   Int -> [R]

   Example:
     (v-zero 3) ;=> [0.0 0.0 0.0]"
  [n]
  (vec (repeat n 0.0)))

(defn v-ones
  "Ones vector of dimension n.

   Int -> [R]

   Example:
     (v-ones 3) ;=> [1.0 1.0 1.0]"
  [n]
  (vec (repeat n 1.0)))

;; =============================================================
;; Matrix Operations
;; Matrices are nested vectors: [[row0] [row1] ...]
;; =============================================================

(defn mat-vec
  "Matrix-vector multiplication: A * x.

   [[R]] -> [R] -> [R]

   Example:
     (mat-vec [[1 0] [0 1]] [3 4]) ;=> [3 4]"
  [A x]
  (mapv (fn [row] (dot row x)) A))

(defn mat-transpose
  "Transpose of a matrix.

   [[R]] -> [[R]]

   Example:
     (mat-transpose [[1 2] [3 4]]) ;=> [[1 3] [2 4]]"
  [A]
  (apply mapv vector A))

(defn mat-mult
  "Matrix-matrix multiplication: A * B.

   [[R]] -> [[R]] -> [[R]]

   Example:
     (mat-mult [[1 2] [3 4]] [[5 6] [7 8]]) ;=> [[19 22] [43 50]]"
  [A B]
  (let [BT (mat-transpose B)]
    (mapv (fn [row-a]
            (mapv (fn [col-b] (dot row-a col-b)) BT))
          A)))

(defn mat-add
  "Matrix addition.

   [[R]] -> [[R]] -> [[R]]

   Example:
     (mat-add [[1 2] [3 4]] [[5 6] [7 8]]) ;=> [[6 8] [10 12]]"
  [A B]
  (mapv (fn [ra rb] (mapv + ra rb)) A B))

(defn mat-scale
  "Scalar-matrix multiplication.

   R -> [[R]] -> [[R]]

   Example:
     (mat-scale 2 [[1 2] [3 4]]) ;=> [[2 4] [6 8]]"
  [s A]
  (mapv (fn [row] (mapv #(* s %) row)) A))

(defn identity-matrix
  "n x n identity matrix.

   Int -> [[R]]

   Example:
     (identity-matrix 2) ;=> [[1.0 0.0] [0.0 1.0]]"
  [n]
  (vec (for [i (range n)]
         (vec (for [j (range n)]
                (if (= i j) 1.0 0.0))))))

(defn outer-product
  "Outer product of two vectors: a * b^T.

   [R] -> [R] -> [[R]]

   Example:
     (outer-product [1 2] [3 4]) ;=> [[3 4] [6 8]]"
  [a b]
  (mapv (fn [ai] (mapv #(* ai %) b)) a))

(defn mat-row
  "Get row i from matrix A.

   [[R]] -> Int -> [R]

   Example:
     (mat-row [[10 20] [30 40]] 1) ;=> [30 40]"
  [A i]
  (nth A i))

(defn mat-col
  "Get column j from matrix A.

   [[R]] -> Int -> [R]

   Example:
     (mat-col [[10 20] [30 40]] 0) ;=> [10 30]"
  [A j]
  (mapv #(nth % j) A))

(defn mat-diag
  "Extract the diagonal of a square matrix.

   [[R]] -> [R]

   Example:
     (mat-diag [[1 2] [3 4]]) ;=> [1 4]"
  [A]
  (mapv (fn [i] (nth (nth A i) i)) (range (count A))))

(defn diag-matrix
  "Create a diagonal matrix from a vector.

   [R] -> [[R]]

   Example:
     (diag-matrix [3 5]) ;=> [[3 0.0] [0.0 5]]"
  [v]
  (let [n (count v)]
    (vec (for [i (range n)]
           (vec (for [j (range n)]
                  (if (= i j) (nth v i) 0.0)))))))

;; =============================================================
;; Linear Algebra
;; =============================================================

(defn solve-linear
  "Solve Ax = b via Gaussian elimination with partial pivoting.
   Returns x.

   [[R]] -> [R] -> [R]

   Example:
     (solve-linear [[2 1] [1 3]] [5 10]) ;=> [1.0 3.0]"
  [A b]
  (let [n (count b)
        ;; Augmented matrix [A|b]
        aug (mapv (fn [i] (conj (vec (nth A i)) (nth b i))) (range n))]
    ;; Forward elimination
    (let [aug (reduce
               (fn [aug k]
                 ;; Partial pivoting: find max in column k
                 (let [pivot-row (apply max-key
                                        (fn [i] (Math/abs (nth (nth aug i) k)))
                                        (range k n))
                       aug (if (= pivot-row k) aug
                               (let [tmp (nth aug k)]
                                 (-> aug
                                     (assoc k (nth aug pivot-row))
                                     (assoc pivot-row tmp))))]
                   ;; Eliminate below pivot
                   (reduce (fn [aug i]
                             (let [factor (/ (double (nth (nth aug i) k))
                                            (let [d (nth (nth aug k) k)]
                                              (if (zero? d) 1e-300 (double d))))
                                   new-row (mapv (fn [j]
                                                   (- (nth (nth aug i) j)
                                                      (* factor (nth (nth aug k) j))))
                                                 (range (inc n)))]
                               (assoc aug i new-row)))
                           aug
                           (range (inc k) n))))
               aug
               (range n))]
      ;; Back substitution
      (let [x (double-array n)]
        (doseq [i (reverse (range n))]
          (let [sum (reduce (fn [acc j] (+ acc (* (nth (nth aug i) j) (aget x j))))
                            0.0
                            (range (inc i) n))
                denom (nth (nth aug i) i)]
            (aset x i (if (zero? denom) 0.0
                           (/ (- (nth (nth aug i) n) sum) denom)))))
        (vec x)))))

(defn cholesky
  "Cholesky decomposition of a positive-definite matrix A.
   Returns lower triangular L such that A = L * L^T.

   [[R]] -> [[R]]

   Example:
     (cholesky [[4 2] [2 3]]) ;=> [[2.0 0.0] [1.0 1.4142135623730951]]"
  [A]
  (let [n (count A)
        L (to-array-2d (vec (repeat n (vec (repeat n 0.0)))))]
    (doseq [i (range n)
            j (range (inc i))]
      (let [sum (reduce (fn [acc k] (+ acc (* (aget L i k) (aget L j k))))
                        0.0
                        (range j))]
        (if (= i j)
          (aset L i j (Math/sqrt (max 0.0 (- (nth (nth A i) i) sum))))
          (let [denom (aget L j j)]
            (aset L i j (if (zero? denom) 0.0
                            (/ (- (nth (nth A i) j) sum) denom)))))))
    (vec (map vec (partition n (vec (for [i (range n) j (range n)] (aget L i j))))))))
