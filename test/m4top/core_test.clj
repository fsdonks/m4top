(ns m4top.core-test
  (:require [clojure.test :refer :all]
            [marathon.ces.testing]
            [marathon.ui]
            #_
            [chazel.core]
            [taa.capacity-test]
            )
  #_
  (:import [chazel.core Ctask]))

(println [:testing :m4top.core-test])
#_#_
(run-tests 'marathon.ces.testing)
(require 'taa.test-hook)

(run-tests 'taa.capacity-test)
