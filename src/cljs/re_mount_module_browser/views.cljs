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
            [dorothy.core :as dorothy]
            [goog.string :as gstring]))       
 
(defn all-projects [& {:keys [on-change selected-id]}]
  (let [all @(re-frame/subscribe [::subs/all-projects])]
    [:div
     [ui/select-field {:floating-label-text "Projects"
                       :value (or selected-id (:db/id (first all)))
                       :on-change (fn [ev idx val] (on-change val))}
      (for [{:keys [:project/name :db/id]} all]
        ^{:key id}
        [ui/menu-item {:value id
                       :primary-text name}])]]))

(defn all-project-namespaces [& {:keys [on-change selected-id]}]
  (let [all @(re-frame/subscribe [::subs/all-project-namespaces])]
    [:div
     [ui/select-field {:floating-label-text "Namespaces"
                       :value (or selected-id (:db/id (first all)))
                       :on-change (fn [ev idx val] (on-change val))}
      (for [{:keys [:namespace/name :db/id]} all]
        ^{:key id}
        [ui/menu-item {:value id
                       :primary-text name}])]]))

(defn dependency-explorer []
  (let [edges (re-frame/subscribe [::subs/projecs-dependencies-edges])
        selected-project-id (re-frame/subscribe [::subs/selected-project-id])
        graphviz (atom nil)
        redraw-graph (fn []
                       (let [eds @edges
                             all-nodes (into #{} (mapcat identity eds))]
                         (-> @graphviz
                             (.renderDot (->> (map (fn [n] [n {:shape :rectangle :fontname :helvetica :fontsize 10}]) all-nodes)
                                              (into eds)
                                              dorothy/digraph
                                              dorothy/dot)))))]
    (r/create-class
     {:component-did-mount (fn []
                             (reset! graphviz (-> (.select js/d3 "#dependency-graph")
                                                  .graphviz
                                                  (.transition (fn []
                                                                 (-> js/d3
                                                                     (.transition "main")
                                                                     (.ease (.-easeLinear js/d3))
                                                                     (.duration 800))))))
                             (redraw-graph))
      :component-did-update redraw-graph
      :reagent-render (fn []
                        [:div.dependency-explorer {:style {:margin 10}}
                         [all-projects
                          :on-change #(re-frame/dispatch [::events/select-project %])
                          :selected-id @selected-project-id]
                         [:div.tree-panel 
                          [:div#dependency-graph]]])})))

(defn namespace-explorer []
  (let [edges (re-frame/subscribe [::subs/project-namespaces-edges])
        selected-project-id (re-frame/subscribe [::subs/selected-project-id])
        selected-namespace-id (re-frame/subscribe [::subs/selected-namespace-id])
        graphviz (atom nil)
        redraw-graph (fn []
                       (let [eds @edges
                             all-nodes (into #{} (mapcat identity eds))]
                         (-> @graphviz
                             (.renderDot (->> all-nodes
                                              (map (fn [{:keys [:namespace/name :mount-state]}]
                                                     [name (cond-> {:shape :rectangle
                                                                    :fontname :helvetica
                                                                    :fontsize 10}
                                                             mount-state (assoc :shape :record
                                                                                :label (gstring/format "{%s|State: %s\\l}"
                                                                                                       (str name)
                                                                                                       (:mount.feature/name (first mount-state)))
                                                                                :color :red))]))
                                              (into (map (fn [[n1 n2]]
                                                           [(:namespace/name n1)
                                                            (:namespace/name n2)])
                                                         eds))
                                              dorothy/digraph
                                              dorothy/dot)))))]
    (r/create-class
     {:component-did-mount (fn []
                             (reset! graphviz (-> (.select js/d3 "#namespaces-graph")
                                                  .graphviz
                                                  (.transition (fn []
                                                                 (-> js/d3
                                                                     (.transition "main")
                                                                     (.ease (.-easeLinear js/d3))
                                                                     (.duration 800))))))
                             (redraw-graph))
      :component-did-update redraw-graph
      :reagent-render (fn []
                        [:div.dependency-explorer {:style {:margin 10}}
                         [all-projects
                          :on-change #(re-frame/dispatch [::events/select-project %])
                          :selected-id @selected-project-id]
                         [all-project-namespaces
                          :on-change #(re-frame/dispatch [::events/select-namespace %])
                          :selected-id @selected-namespace-id]
                         [:div.tree-panel 
                          [:div#namespaces-graph]]])})))

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
