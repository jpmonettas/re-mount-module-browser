(ns re-mount-module-browser.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [re-mount-module-browser.subs :as subs]
            [re-mount-module-browser.events :as events]
            [reagent-flowgraph.core :refer [flowgraph]]
            [clojure.string :as str]))

(defn all-projects [& {:keys [on-change selected-id]}]
  (let [all (re-frame/subscribe [::subs/all-projects])]
    [:div  
     [:label "Projects:"]
     [re-com/single-dropdown
      :width "350px"
      :choices all
      :model selected-id
      :id-fn :db/id :label-fn :project/name
      :on-change #(on-change %)]]))

(defn dependency-explorer []
  (let [tree (re-frame/subscribe [::subs/dependency-tree])
        selected-project-id (re-frame/subscribe [::subs/selected-project-id])]
    [:div {}
     [:h4 "Dependency explorer"]
     [all-projects
      :on-change #(re-frame/dispatch [::events/select-project %])
      :selected-id selected-project-id]
     [:div {:style {:padding-top "30px"}}
      (when-let [t @tree]
        [flowgraph t
         :layout-width 15500
         :layout-height 1500
         :branch-fn :project/dependency
         :childs-fn :project/dependency
         :line-styles {:stroke-width 1
                       :stroke :orange}
         :render-fn (fn [n]
                      [:div {:style {:border "1px solid black"
                                     :padding "10px"
                                     :border-radius "10px"
                                     :font-size (if (:project/ours? n) "12px" "8px")}}
                       (if (str/includes? (:project/name n) "/")
                         (let [[ns name] (str/split (:project/name n) #"/")]
                           [:div [:span {:style {:color "#999"}}(str ns "/")] [:b name]])
                         [:b (:project/name n)])])])]]))

(defn header []
  [:div {:style {:background-color :grey :padding 5}}
   [:button.btn.btn-warning {:on-click #(re-frame/dispatch [::events/reload-db])}
    "Reload db"]
   [:button.btn.btn-danger {:on-click #(re-frame/dispatch [::events/re-index-all])
                            :style {:margin-left 5}}
    "Re index all"]])

(defn main-panel []
  [:div
   [header]
   [dependency-explorer]])
