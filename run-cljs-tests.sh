#!/bin/bash
rm -rd cljs-test-runner-out && clojure -M:cljs -r com\.akovantsev\.let-plus\.test.* -x node
