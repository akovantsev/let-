(ns com.akovantsev.let-plus
  #?(:cljs (:require-macros [com.akovantsev.let-plus :refer [let+]])))


(defmulti mm       (fn [k v left right] k))
(defmethod mm :default [k v left right]
  (throw
    (ex-info (str "unknown extension key " k " for let+")
      {'k k 'v v 'left left 'right right})))


#?(:clj
   (defmacro let+ [pairs & bodies]
     (assert (-> pairs count even?))
     (let [rf# (fn f1 [res [left right]]
                 (if-not (map? left)
                   (conj res left right)
                   (let [[builtin custom] (reduce-kv
                                            (fn f2 [[b c] k v]
                                              (if (and (keyword? k) (-> k name #{"strs" "syms" "keys" "or" "as"}))
                                                [(assoc b k v) c]
                                                [b (assoc c k v)]))
                                            [{} {}] left)

                         get-pairs    (fn f3 [[k v]]
                                        (let [pairs (mm k v left right)]
                                          (assert (->> pairs count even?))
                                          (assert (->> pairs (every? #(-> % count #{2}))))))

                         custom-pairs (->> custom (mapcat get-pairs) (reduce into []))]

                     (cond-> res
                       (seq builtin) (conj builtin right)
                       (seq custom)  (into custom-pairs)))))
           bs# (reduce rf# [] (partition 2 pairs))]
       `(let ~bs# ~@bodies))))