(ns nextjournal.log
  (:refer-clojure :exclude [case])
  #?(:cljs (:require-macros [nextjournal.log]))
  (:require [lambdaisland.glogi :as glogi]
            #?(:clj [io.pedestal.log :as pedestal])))


#?(:clj
   (do

     (defmacro case [& {:keys [cljs clj]}]
       `(if (:ns ~'&env) ~cljs ~clj))

     (defmacro finest [& keyvals] ;; goog.log
       (case :clj  (#'pedestal/log-expr &form :trace keyvals)
             :cljs (#'glogi/log-expr &form :finest keyvals)))

     (defmacro finer [& keyvals] ;; goog.log
       (case :clj  (#'pedestal/log-expr &form :trace keyvals)
             :cljs (#'glogi/log-expr &form :finer keyvals)))

     (defmacro trace [& keyvals]
       (case :clj  (#'pedestal/log-expr &form :trace keyvals)
             :cljs (#'glogi/log-expr &form :trace keyvals)))

     (defmacro fine [& keyvals] ;; goog.log
       (case :clj  (#'pedestal/log-expr &form :debug keyvals)
             :cljs (#'glogi/log-expr &form :fine keyvals)))

     (defmacro debug [& keyvals]
       (case :clj  (#'pedestal/log-expr &form :debug keyvals)
             :cljs (#'glogi/log-expr &form :debug keyvals)))

     (defmacro config [& keyvals] ;; goog.log
       (case :clj  (#'pedestal/log-expr &form :info keyvals)
             :cljs (#'glogi/log-expr &form :config keyvals)))

     (defmacro info [& keyvals]
       (case :clj  (#'pedestal/log-expr &form :info keyvals)
             :cljs (#'glogi/log-expr &form :info keyvals)))

     (defmacro warn [& keyvals]
       (case :clj  (#'pedestal/log-expr &form :warn keyvals)
             :cljs (#'glogi/log-expr &form :warn keyvals)))

     (defmacro error [& keyvals]
       (case :clj  (#'pedestal/log-expr &form :error keyvals)
             :cljs (#'glogi/log-expr &form :error keyvals)))

     (defmacro spy [expr]
       (case :clj `(pedestal/spy ~expr)
             :cljs `(glogi/spy ~expr)))

     (defmacro with-context [ctx-map & body]
       `(pedestal/with-context ~ctx-map ~@body))

     (def format-name pedestal/format-name)
     (def counter pedestal/counter)
     (def gauge pedestal/gauge)
     (def histogram pedestal/histogram)
     (def meter pedestal/meter)))
