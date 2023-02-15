(ns com.akovantsev.let-plus.test
  #?(:cljs (:require-macros [com.akovantsev.let-plus.test :refer [let+]]))
  (:require
   [clojure.set :as set]
   [com.akovantsev.let-plus :as let-plus]))

(defmethod let-plus/mm  :juxt [k v left right]
  (assert (vector? v) ":juxt must be vector")
  (->> v
    (mapcat
      (fn f1 [x]
        (cond
          (or (keyword? x) (symbol? x) (string? x))
          [[`~(symbol (name x)) (list `~x right)]]

          (map? x)
          (reduce-kv
            (fn f2 [pairs k v]
              (assert (simple-symbol? k) {'k k 'v 'v 'left left 'right right})
              (conj pairs
                (if (symbol? v)
                  [k (list `~v right)]
                  [k (list `get right `~v)])))
            [] x))))))


;; for cljs wrapping let+ in another macro so it would eval after mm is extended in js
#?(:clj (defmacro let+ [pairs & bodies] `(let-plus/let+ ~pairs ~@bodies)))


(let [res (let+ [{:juxt [set/map-invert str first {x 1} set {y "b"}] :keys [:c] :as m} {1 :a "b" 4 :c 5}]
            [x y set first map-invert m str c])]
  (assert
    (= res
      [:a
       4
       #{[:c 5] ["b" 4] [1 :a]}
       [1 :a]
       {:a 1, 4 "b", 5 :c}
       {1 :a, "b" 4, :c 5}
       "{1 :a, \"b\" 4, :c 5}"
       5])
    res))