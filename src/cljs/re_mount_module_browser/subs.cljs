(ns re-mount-module-browser.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::dep-tree
 (fn [db]
   (:dep-tree db))) 
