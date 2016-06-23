\set on_error_stop on

-- new function template
/* create or replace function f */ 
/* returns void */
/* begin */
/* end; */
/* $$ language plpgsql; */

drop table if exists facilities_polygons;

create table facilities_polygons (
        facility_id integer not null,
        threshold   integer not null,
        the_geom    geometry(polygon, 4326)
);

-- find closest node to a point
drop function closest_node(geometry(point, 4326));
create or replace function closest_node (original geometry(point, 4326))
returns integer as $$
declare
	closest_node integer;
begin
  select w.id
  from ways_vertices_pgr w
  order by w.the_geom <-> original limit 1
  into closest_node;
  
  return closest_node;
end;
$$ language plpgsql;

-- generates the isochrone polygons for all the facilities and thresholds
drop function calculate_isochrones(integer, integer, integer);
create or replace function calculate_isochrones(threshold_start integer, threshold_finish integer, threshold_jump integer)
returns void as $$
declare
  from_cost integer;
  to_cost integer;
  f_row facilities%rowtype;
begin
  create temporary table if not exists edges_agg_cost (
    gid integer not null,
    agg_cost double precision
  );

  -- Process only facilities with 'hospital' in their name
  for f_row in select * from facilities f where name ilike '%hospital%' loop
    insert into edges_agg_cost (
      select w.gid, e.agg_cost
      from pgr_drivingdistance(
        'select gid as id, source, target, cost_s as cost from ways', 
        closest_node(f_row.the_geom), 
        threshold_finish * 60, 
        false) e
      join ways w on e.edge = w.gid
    );

    from_cost := 0;
    to_cost   := threshold_start * 60;
    while to_cost <= threshold_finish * 60 loop
      insert into facilities_polygons (
        select f_row.id, to_cost, st_union(buffers.the_geom)
        from (
          select wb.the_geom
          from edges_agg_cost eac
          join ways_buffers wb on wb.ways_gid = eac.gid
          where agg_cost >= from_cost and agg_cost < to_cost
          union
          select the_geom
          from facilities_polygons
          where facility_id = f_row.id and threshold = (to_cost-threshold_jump * 60)
        ) as buffers
      );
      -- Uncomment the following code to use the faster (but inaccurate) alpha
      -- shape method
      -- insert into facilities_polygons (
      --        select f_row.id, to_cost, st_buffer(st_setsrid(pgr_pointsaspolygon('select id::integer, lon::float as x, lat::float as y from ways_nodes where gid in (select gid from edges_agg_cost where agg_cost < ' || to_cost || ')'),4326), 0.004));
      from_cost := to_cost;
      to_cost   := to_cost + threshold_jump * 60;
    end loop;

    delete from edges_agg_cost;
  end loop;

  -- Cannot drop the temp table when running the alpha shape algorithm because
  -- pgr holds a reference to it
  -- drop table edges_agg_cost;

end;
$$ language plpgsql;

-- cache the buffers of the ways
drop function cache_ways_buffers(float);
create or replace function cache_ways_buffers(buffer_radius_in_meters float)
returns void as $$
begin
  create temporary table if not exists ways_buffers (
    ways_gid integer not null,
    the_geom geometry
  );
  truncate table ways_buffers;
  insert into ways_buffers (
    select gid, ST_Buffer(ST_GeogFromWKB(the_geom), buffer_radius_in_meters)::geometry
    from ways
  );
end;
$$ language plpgsql;
