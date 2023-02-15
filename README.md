## What

`let+` macro which lets you to extend map destructuring via multimethod.

## Install 

```clojure
;; in deps.edn
{:deps {github-akovantsev/let-plus
        {:git/url "https://github.com/akovantsev/let-plus"
         :sha     "cc5999eea658ccd0163568769dc6af1e048904b8"}}} ;; actual sha
```

## Howto

Unfortunately, `cljs` makes extending a bit more involved than just extending multimethod.
<br>(for some reference see https://github.com/clojure/test.check/commit/489d54d10ddf03b3dcc704be223c35529e9a9cd1)
<br>"Luckily" it also works for `clj`, so at least you dont need 2 different approaches.
<br>One way is to wrap `let+` in another macro (`let+` or any name you like) after all `defmethod`s are expanded "in js", and use redefined version instead:
```clojure
(ns my.cljc.ns  ;;.cljc
  #?(:cljs (:require-macros [my.cljc.ns :refer [my-let+]]))
  (:require [com.akovantsev.let-plus :as let-plus]))

;; do or import your extensions.
;; must return seq of pairs, or empty seq, or nil:
(defmethod let-plus/mm :my-key [k v left right] ...)

;; wrap `let+` into another macro, needed only for cljs, but works for clj too.
;; `defmacro` has to be done after `defmethod` but before usage.
#?(:clj (defmacro my-let+ [pairs & bodies] `(let-plus/let+ ~pairs ~@bodies)))

(my-let+ [{:my-key ...} ...]
  ...)
```

<br>`mm` dispatches by `k`, but gets entire (original) destructuring map as argument, so:
- you can use multiple keys to do things, like `:spec` and `:conf` example below
- but this means "signatures" for keys from multiple `let+` plugins might conflict.
    ```clojure
    (defmethod mm :a [k v left right] (assert (-> left :c set?)))
    (defmethod mm :b [k v left right] (assert (-> left :c list?)))
  
    #?(:clj (defmacro my-let+ [pairs & bodies] `(let-plus/let+ ~pairs ~@bodies)))
    
    (my-let+ [{:a [] :b [] :c #{}} 1] [a b c])
    Unexpected error (AssertionError) macroexpanding let+
    Assert failed: (-> left :c list?)
    
    (my-let+ [{:a [] :b [] :c ()} 1] [a b c])
    Unexpected error (AssertionError) macroexpanding let+
    Assert failed: (-> left :c set?)
    ```
    Know what you use (duh).

## Examples

### :print

Just illustrates what `mm`s args are:
```clojure
(defmethod mm :print [k v left right]
  (prn [k v left right]))  ;; prints during macro-expansion
                           ;; returns even number (0) of pairs: nil

#?(:clj (defmacro let+ [pairs & bodies] `(let-plus/let+ ~pairs ~@bodies)))

(let+ [{:print [1 2 3] :keys [a] :as m} (assoc {} :a 1)]
  [a m])

; prints:
[:print                               ;; k
 [1 2 3]                              ;; v
 {:print [1 2 3], :keys [a], :as m}   ;; left, entire destructuring map, not just :print
 (assoc {} :a 1)]                     ;; right, entire expression

=> [1 {:a 1}]
```

### :juxt

```clojure
(defmethod com.akovantsev.let-plus/mm  :juxt [k v left right]
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

#?(:clj (defmacro let+ [pairs & bodies] `(com.akovantsev.let-plus/let+ ~pairs ~@bodies)))

(let+ [{:juxt [str/join str first {x 1} set {y "b"}] :keys [:c] :as m} {1 :a "b" 4 :c 5}]
  [x y set first join m str c])

=> [:a
    4
    #{[:c 5] ["b" 4] [1 :a]}
    [1 :a]
    "[1 :a][\"b\" 4][:c 5]"
    {1 :a, "b" 4, :c 5}
    "{1 :a, \"b\" 4, :c 5}"
    5]
```

### :spec :spec! :conf :conf!

This is how you might plug https://github.com/akovantsev/slet into `let+`:

```clojure
(ns foo
  (:require
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [com.akovantsev.slet.core :as slet]
   [com.akovantsev.let-plus :as let-plus :refer [let+]]))


(defn -slet [left right validate?]
  (let [slet-form (slet/-slet validate? [left right] nil)]
    (->> slet-form second (partition 2))))

(defmethod let-plus/mm :spec  [k v left right] (-> left (select-keys [:as :keys :spec]) (-slet right false)))
(defmethod let-plus/mm :conf  [k v left right] (-> left (select-keys [:as :keys :conf]) (-slet right false)))
(defmethod let-plus/mm :spec! [k v left right] (-> left (select-keys [:as :keys :spec!]) (set/rename-keys {:spec! :spec}) (-slet right true)))
(defmethod let-plus/mm :conf! [k v left right] (-> left (select-keys [:as :keys :conf!]) (set/rename-keys {:conf! :conf}) (-slet right true)))

#?(:clj (defmacro let+ [pairs & bodies] `(let-plus/let+ ~pairs ~@bodies)))


(s/def ::a (s/cat ::one #{1} ::two #{2}))
(s/def ::foo (s/keys :req [::a]))

;; reminder:
;; :spec  does not validate `right` against :spec spec; allows only :keys declared in :spec spec at macro-expansion time
;; :conf  does not validate, or conforms `right` against :conf spec; allows only :keys declared in :conf spec + branch tags (like s/cat).
;; :spec! same as :spec but validates `right` against :spec spec first.
;; :conf! same as :conf, but conforms `right` against :conf spec first.

(let+ [{:spec  ::foo :keys [::a]   :as m}   {::a [1 2]}
       {:conf! ::a   :keys [::two] :as ac}  a]
  [m a ac two])

=> [{::a [1 2]}
    [1 2]
    {::one 1, ::two 2}
    2]


(let+ [{:spec ::foo :keys [::b]} {::a 1 ::b 2}]
  b)

=> Unexpected error (AssertionError) macroexpanding let+
Assert failed:
no key:
::b
in spec:
::foo
spec form:
(s/keys :req [::a])
spec keys:
#{::a}


(let+ [{:conf! ::a :keys [::three]} [1 2]] three)

=> Unexpected error (AssertionError) macroexpanding let+
Assert failed:
no tag:
::three
in spec:
::a
spec form:
(s/cat ::one #{1} ::two #{2})
conformed spec tags:
#{::one ::two}
```