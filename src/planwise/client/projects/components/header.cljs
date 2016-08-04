(ns planwise.client.projects.components.header
  (:require [re-frame.core :refer [subscribe dispatch]]
            [planwise.client.utils :as utils]
            [planwise.client.components.nav :as nav]
            [planwise.client.routes :as routes]))

(defn project-tab-items [project-id]
  (let [route-params {:id project-id}]
    [#_{:item :demographics
        :href (routes/project-demographics route-params)
        :title "Demographics"}
     {:item :facilities
      :href (routes/project-facilities route-params)
      :title "Facilities"}
     {:item :transport
      :href (routes/project-transport route-params)
      :title "Transport Means"}
     #_{:item :scenarios
        :href (routes/project-scenarios route-params)
        :title "Scenarios"}]))

(defn header-section [project-id project-goal selected-tab]
  [:div.project-header
   [:h2 project-goal]
   [:nav
    [nav/ul-menu (project-tab-items project-id) selected-tab]
    [:div
     [:a
      {:href "#" :on-click (utils/with-confirm #(dispatch [:projects/delete-project project-id]) "Are you sure you want to delete this project?")}
      "Delete project"]]]])