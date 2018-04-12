(ns re-mount-module-browser.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [re-mount-module-browser.subs :as subs]
            [re-mount-module-browser.events :as events]
            [reagent-flowgraph.core :refer [flowgraph]]))



(defn main-panel []
  (let [tree (re-frame/subscribe [::subs/dep-tree])]
   [re-com/v-box
    :height "100%" 
    :children [[re-com/button
                :label "Reload db"
                :on-click #(re-frame/dispatch [::events/reload-db])]
               [re-com/button
                :label "Re index all"
                :on-click #(re-frame/dispatch [::events/re-index-all])]
               (when-let [t @tree]
                [flowgraph t
                 :layout-width 1500
                 :layout-height 1500
                 :branch-fn :project/dependency
                 :childs-fn :project/dependency
                 :render-fn (fn [n]
                              [:div {:style {:border "1px solid black"
                                             :padding "10px"
                                             :border-radius "10px"}}
                               (if (:project/ours? n)
                                 (:project/name n)
                                 ".")])])]]))
