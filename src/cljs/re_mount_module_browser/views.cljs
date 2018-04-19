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
    [:div.dependency-explorer
     [:h4 "Dependency explorer"]
     [all-projects
      :on-change #(re-frame/dispatch [::events/select-project %])
      :selected-id selected-project-id]
     [:div.tree-panel 
      (when-let [t @tree]
        [flowgraph t
         :layout-width 15500
         :layout-height 1500
         :branch-fn :project/dependency
         :childs-fn :project/dependency
         :line-styles {:stroke-width 1
                       :stroke :orange}
         :render-fn (fn [n]
                      [:div.node {}
                       (if (str/includes? (:project/name n) "/")
                         (let [[ns name] (str/split (:project/name n) #"/")]
                           [:div [:span {:style {:color "#999"}}(str ns "/")] [:b name]])
                         [:b (:project/name n)])])])]]))

(defn header []
  [:div.header {}
   [:button.btn.btn-danger {:on-click #(re-frame/dispatch [::events/re-index-all])
                            :style {:margin-left 5}}
    "Re index all"]])

(defn menu []
  (let [selected-tab @(re-frame/subscribe [::subs/selected-tab-id])]
    [re-com/vertical-bar-tabs
    :model selected-tab
    :on-change #(re-frame/dispatch [::events/select-tab %])
    :style {:grid-column "menu-col-start/body-col-start"
            :grid-row "body-row-start/body-row-end"}
    :tabs [{:id :tab-dependencies :label "Dependencies"}
           {:id :tab-events :label "Events"}
           {:id :tab-subscriptions :label "Subscriptions"}
           {:id :tab-effects :label "Effects"}
           {:id :tab-coeffects :label "CoEffects"}
           {:id :tab-specs :label "Specs"}
           {:id :tab-smart :label "Smart Contracts"}]]))

(defn open-file-link [path line child]
  [:a {:href (str "/open-file?path=" path "&line=" line "#line-tag")
       :target "_blank"} child])

(defn feature-explorer [type]
  (let [features @(re-frame/subscribe [::subs/features type])] 
    [:div.feature-explorer {}
     (for [[p namespaces-map] features]
       ^{:key p}
       [:div.feature-bubble {}
        [:h4  (str p)]
        [:div
         (for [[n feats] namespaces-map]
           ^{:key n} 
           [:div 
            [:h6 (str n)]
            [:div 
             (for [f feats]
               (let [[ns name] (str/split (:feature/name f) #"/")
                     line (:feature/line f)]
                 ^{:key (str f)}
                 [open-file-link
                  (:namespace/path f)
                  line
                  [:div [:span {:style {:color "#999"}}(str ns "/")] [:b name]]]))]])]])]))



(defn smart-contracts-explorer []
  (let [smart-contracts @(re-frame/subscribe [::subs/smart-contracts])]
    [:div {}
     (for [[pname contracts] smart-contracts]
       ^{:key pname}
       [:div
        [:h4 pname]
        (for [{:keys [:smart-contract/path]} contracts]
          ^{:key path}
          [:div [open-file-link path 0 path]])])]))

(defn specs-explorer []
  (let [all-specs @(re-frame/subscribe [::subs/specs type])] 
    [:div.spec-explorer {} 
     (for [[p namespaces-map] all-specs]
       ^{:key p}
       [:div.spec-bubble {} 
        [:h4  (str p)]
        [:div
         (for [[n specs] namespaces-map]
           ^{:key n} 
           [:div 
            [:h6 (str n)]
            [:div 
             (for [s specs]
               (let [[ns name] (str/split (:spec/name s) #"/")
                     line (:spec/line s)]
                 ^{:key (str s)}
                 [open-file-link
                  (:namespace/path s)
                  line
                  [:div [:span {:style {:color "#999"}}(str ns "/")] [:b name]]]))]])]])]))

(defn main-panel []
  [:div.main-panel {}
   [header]
   [menu]
   (case @(re-frame/subscribe [::subs/selected-tab-id])
     :tab-dependencies [dependency-explorer]
     :tab-events [feature-explorer :event]
     :tab-subscriptions [feature-explorer :subscription]
     :tab-effects [feature-explorer :fx]
     :tab-coeffects [feature-explorer :cofx]
     :tab-specs [specs-explorer]
     :tab-smart [smart-contracts-explorer])])
