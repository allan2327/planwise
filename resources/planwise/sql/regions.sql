-- :name select-regions :? :*
SELECT id, country, name, admin_level
FROM regions
ORDER BY admin_level DESC, name DESC;

-- :name select-regions-with-geo-given-ids :? :*
SELECT id, country, name, admin_level, ST_AsGeoJSON(ST_Simplify(the_geom, :simplify), 15, 3) as geojson
FROM regions
WHERE id IN (:v*:ids);
