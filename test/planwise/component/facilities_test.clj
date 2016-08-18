(ns planwise.component.facilities-test
  (:require [planwise.component.facilities :as facilities]
            [planwise.test-system :refer [test-system with-system]]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all])
  (:import [org.postgis PGgeometry]))

(defn make-point [lat lon]
  (PGgeometry. (str "SRID=4326;POINT(" lon " " lat ")")))
(defn sample-polygon []
  (PGgeometry. (str "SRID=4326;POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))")))

(def fixture-data
  [[:facilities
    [{:id 1 :name "Facility A" :type_id 1 :lat -3   :lon 42 :the_geom (make-point -3 42)}
     {:id 2 :name "Facility B" :type_id 1 :lat -3.5 :lon 42 :the_geom (make-point -3.5 42)}]]
   [:facilities_polygons
    [{:id 1 :facility_id 1 :threshold 900 :method "alpha-shape" :the_geom (sample-polygon)}]]])

(def new-facilities
  [{:id 3 :name "New facility" :type-id 1 :lat 4 :lon 10 :type "hospital"}])

(defn system []
  (into
   (test-system {:fixtures {:data fixture-data}})
   {:facilities (component/using (facilities/facilities-service {}) [:db])}))

(deftest list-facilities
  (with-system (system)
    (let [service (:facilities system)
          facilities (facilities/list-facilities service)]
      (is (= 2 (count facilities)))
      (is (= #{:id :name :lat :lon} (-> facilities first keys set))))))

(deftest list-facilities-with-isochrones
  (with-system (system)
    (let [service (:facilities system)
          facilities (facilities/list-with-isochrones service {:threshold 900})]
      (is (= 2 (count facilities)))
      (is (= #{:id :name :lat :lon :isochrone :polygon-id :area :population} (-> facilities first keys set))))))

(deftest insert-facility
  (with-system (system)
    (let [service (:facilities system)]
      (is (= 1 (facilities/insert-facilities! service new-facilities)))
      (is (= 3 (count (facilities/list-facilities service)))))))

(deftest destroy-facilities
  (with-system (system)
    (let [service (:facilities system)]
      (facilities/destroy-facilities! service)
      (is (= 0 (count (facilities/list-facilities service)))))))

(deftest list-isochrones-in-bbox
  (with-system (system)
    (let [service (:facilities system)
          facilities (facilities/isochrones-in-bbox service {:threshold 900} {:bbox [0.0 0.0 2.0 2.0]})]
      (is (= 1 (count facilities)))
      (let [[facility] facilities]
        (is (= 1 (:id facility)))
        (is (= 1 (:polygon-id facility)))
        (is (:isochrone facility))))))

(deftest list-isochrones-in-bbox-excluding-ids
  (with-system (system)
    (let [service (:facilities system)
          facilities (facilities/isochrones-in-bbox service {:threshold 900} {:bbox [0.0 0.0 2.0 2.0], :excluding [1]})]
      (is (= 1 (count facilities)))
      (let [[facility] facilities]
        (is (= 1 (:id facility)))
        (is (= 1 (:polygon-id facility)))
        (is (nil? (:isochrone facility)))))))
