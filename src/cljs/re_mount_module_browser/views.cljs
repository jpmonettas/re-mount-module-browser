(ns re-mount-module-browser.views
  (:require [cljsjs.material-ui]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [re-com.core :as re-com]
            [re-mount-module-browser.subs :as subs]
            [re-mount-module-browser.events :as events]
            [reagent-flowgraph.core :refer [flowgraph]]
            [clojure.string :as str]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            cljsjs.d3
            [dorothy.core :as dorothy]))       
 
(defn all-projects [& {:keys [on-change selected-id]}]
  (let [all @(re-frame/subscribe [::subs/all-projects])]
    [:div
     [ui/select-field {:floating-label-text "Projects"
                       :value selected-id
                       :on-change (fn [ev idx val] (on-change val))}
      (for [{:keys [:project/name :db/id]} all]
        ^{:key id}
        [ui/menu-item {:value id
                       :primary-text name}])]]))

(defn dependency-explorer []
  (let [edges (re-frame/subscribe [::subs/projecs-dependencies-edges])
        selected-project-id (re-frame/subscribe [::subs/selected-project-id])
        redraw-graph (fn []
                       (let [eds @edges
                             all-nodes (into #{} (mapcat identity eds))]
                        (-> (.select js/d3 "#dependency-graph")
                            (.graphviz)
                            (.renderDot (dorothy/dot (dorothy/digraph (into eds
                                                                            (map (fn [n] [n {:shape :box}]) all-nodes)) ))))))]
    (r/create-class
     {:component-did-mount redraw-graph
      :component-did-update redraw-graph
      :reagent-render (fn []
                        [:div.dependency-explorer {:style {:margin 10}}
                         [all-projects
                          :on-change #(re-frame/dispatch [::events/select-project %])
                          :selected-id @selected-project-id]
                         [:div.tree-panel 
                          [:div#dependency-graph]]])})))

(defn namespace-explorer []
  [:div "Comming Soon"]
  #_(let [edges (re-frame/subscribe [::subs/projecs-dependencies-edges])
        selected-project-id (re-frame/subscribe [::subs/selected-project-id])
        redraw-graph (fn []
                       (let [eds @edges
                             all-nodes (into #{} (mapcat identity eds))]
                        (-> (.select js/d3 "#dependency-graph")
                            (.graphviz)
                            (.renderDot (dorothy/dot (dorothy/digraph (into eds
                                                                            (map (fn [n] [n {:shape :box}]) all-nodes)) ))))))]
    (r/create-class
     {:component-did-mount redraw-graph
      :component-did-update redraw-graph
      :reagent-render (fn []
                        [:div.dependency-explorer {:style {:margin 10}}
                         [all-projects
                          :on-change #(re-frame/dispatch [::events/select-project %])
                          :selected-id @selected-project-id]
                         [:div.tree-panel 
                          [:div#dependency-graph]]])})))

;; (def t (d/pull @db-conn 
;;                  '[:namespace/name :namespace/ours? {:mount.feature/_namespace [:mount.feature/name]} {:namespace/require 100}]
;;                  226
;;                  ))
;;   (defn only-ours [tree]
;;     (update tree :namespace/require (fn [deps]
;;                                       (->> deps
;;                                            (filter :namespace/ours?)
;;                                            (map only-ours)))))
;;   (only-ours t)

;;   (defn state-tree [ns-tree]
;;   (let [sub-states ()
;;         node-mount-feats (-> ns-tree :mount.feature/_namespace)]
;;     (if (not-empty node-mount-feats)
;;       (map (fn [{:keys [:mount.feature/name]}
;;                 {:state-name name
;;                  :childs sub-states}])
;;            node-mount-feats)))
;;   )
(defn mount-state-explorer []
  [:div "Comming Soon"]
  #_(let [edges (re-frame/subscribe [::subs/projecs-dependencies-edges])
          selected-project-id (re-frame/subscribe [::subs/selected-project-id])
          redraw-graph (fn []
                         (let [eds @edges
                               all-nodes (into #{} (mapcat identity eds))]
                           (-> (.select js/d3 "#dependency-graph")
                               (.graphviz)
                               (.renderDot (dorothy/dot (dorothy/digraph (into eds
                                                                               (map (fn [n] [n {:shape :box}]) all-nodes)) ))))))]
      (r/create-class
       {:component-did-mount redraw-graph
        :component-did-update redraw-graph
        :reagent-render (fn []
                          [:div.dependency-explorer {:style {:margin 10}}
                           [all-projects
                            :on-change #(re-frame/dispatch [::events/select-project %])
                            :selected-id @selected-project-id]
                           [:div.tree-panel 
                            [:div#dependency-graph]]])})))
 
(defn header []
  [ui/app-bar
   {:title "Explorer"
    :class-name "header"
    :icon-element-right (r/as-element
                         [ui/raised-button
                          {:secondary true
                           :label "Refresh index"
                           :on-click #(re-frame/dispatch [::events/re-index-all])}])}])

(defn link [full-name path line]
  (let [full-name (str full-name)
        name-style {:font-weight :bold
                    :color (color :blue200)}]
   [:a {:href (str "/open-file?path=" path "&line=" line "#line-tag") :target "_blank"} 
    (if (str/index-of full-name "/")
      (let [[_ ns name] (str/split full-name #"(.+)/(.+)")]
        [:div {:style {:font-size 12}}
         [:span {:style {:color "#bbb"}}
          (str ns "/")]
         [:span.name {:style name-style} name]])
      [:div
       [:span.name {:style name-style} full-name]])]))

(defn feature-explorer [type]
  (let [features @(re-frame/subscribe [::subs/features type])] 
    [ui/grid-list {:cols 3
                   :padding 20
                   :style {:margin 20}
                   :cell-height "auto"}
     (for [[p namespaces-map] features]
       ^{:key p}
       [ui/paper {:style {:padding 10
                          :height "100%"}}
        [:h4 {:style {:color (color :blueGrey600)}} (str p)]
        [:div
         (for [[n feats] namespaces-map]
           ^{:key n} 
           [:div 
            [:h6 {:style {:color (color :blueGrey500)}} (str n)]
            [:div 
             (for [f feats]
               ^{:key (str f)}
               [link (:re-frame.feature/name f) (:namespace/path f) (:re-frame.feature/line f)])]])]])]))

(defn specs-explorer []
  (let [all-specs @(re-frame/subscribe [::subs/specs type])] 
    [ui/grid-list {:cols 3
                   :padding 20
                   :style {:margin 20}
                   :cell-height "auto"}
     (for [[p namespaces-map] all-specs]
       ^{:key p}
       [ui/paper {:style {:padding 10
                          :height "100%"}} 
        [:h4 {:style {:color (color :blueGrey600)}} (str p)]
        [:div
         (for [[n specs] namespaces-map]
           ^{:key n} 
           [:div 
            [:h6 {:style {:color (color :blueGrey500)}} (str n)]
            [:div 
             (for [s specs]
               ^{:key (str s)}
               [link (:spec/name s) (:namespace/path s) (:spec/line s)])]])]])]))

(defn tabs []
  (let [selected-tab @(re-frame/subscribe [::subs/selected-tab-id])]
    [ui/tabs {:value selected-tab} 
    [ui/tab {:label "Dependencies"
             :on-active #(re-frame/dispatch [::events/select-tab "tab-dependencies"])
             :value "tab-dependencies"}
     [dependency-explorer]]
     [ui/tab {:label "Namespaces"
              :on-active #(re-frame/dispatch [::events/select-tab "tab-namespaces"])
              :value "tab-namespaces"}
      [namespace-explorer]]
     [ui/tab {:label "Mount State"
              :on-active #(re-frame/dispatch [::events/select-tab "tab-mount"])
              :value "tab-mount"}
      [mount-state-explorer]]
    [ui/tab {:label "Events"
             :on-active #(re-frame/dispatch [::events/select-tab "tab-events"])
             :value "tab-events"}
     [feature-explorer :event]]
    [ui/tab {:label "Subscriptions" 
             :on-active #(re-frame/dispatch [::events/select-tab "tab-subscriptions"])
             :value "tab-subscriptions"}
     [feature-explorer :subscription]]
    [ui/tab {:label "Effects"
             :on-active #(re-frame/dispatch [::events/select-tab "tab-effects"])
             :value "tab-effects"} 
     [feature-explorer :fx]]
    [ui/tab {:label "Coeffects"
             :on-active #(re-frame/dispatch [::events/select-tab "tab-coeffects"])
             :value "tab-coeffects"}
     [feature-explorer :cofx]]
    [ui/tab {:label "Specs"
             :on-active #(re-frame/dispatch [::events/select-tab "tab-specs"])
             :value "tab-specs"}
     [specs-explorer]]]))

(defn main-panel []
  [ui/mui-theme-provider
   {:mui-theme (get-mui-theme (aget js/MaterialUIStyles "lightBaseTheme"))}
   [:div.main-panel 
    [header]
    [tabs]]])
