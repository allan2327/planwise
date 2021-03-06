(ns planwise.model.projects
  (:require [schema.core :as s]))

(def ProjectStats
  "Project stats cache"
  {(s/optional-key :facilities-targeted) s/Int
   (s/optional-key :facilities-total)    s/Int})

(def ProjectFilters
  "Project filters"
  {(s/optional-key :facilities) {:type [s/Int]}
   (s/optional-key :transport)  {:time [s/Int]}})

(def Project
  "A Planwise project"
  {:id         s/Int
   :goal       s/Str
   :region-id  s/Int
   :owner-id   s/Int
   :stats      (s/maybe ProjectStats)
   :filters    (s/maybe ProjectFilters)

   ;; Optional extras (read-only, for the view)
   (s/optional-key :region-name)       s/Str
   (s/optional-key :region-population) s/Int
   (s/optional-key :region-area-km2)   s/Num})
