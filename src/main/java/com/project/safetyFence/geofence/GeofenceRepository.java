package com.project.safetyFence.geofence;

import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.user.domain.User;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GeofenceRepository extends JpaRepository<Geofence, Long> {

    List<Geofence> findByUser(User user);

    List<Geofence> findByTypeAndEndTimeBefore(int type, LocalDateTime endTime);

    /**
     * 특정 위치로부터 지정된 거리 내의 지오펜스 조회 (PostGIS ST_DWithin 사용)
     * @param userLocation 사용자 위치 (Point)
     * @param distanceInMeters 거리 (미터)
     * @return 거리 내의 지오펜스 리스트
     */
    @Query(value = "SELECT * FROM geofence g WHERE ST_DWithin(g.location, :userLocation, :distance)", nativeQuery = true)
    List<Geofence> findGeofencesWithinDistance(@Param("userLocation") Point userLocation, @Param("distance") double distanceInMeters);

    /**
     * 특정 사용자의 지오펜스 중 지정된 거리 내의 지오펜스 조회
     * @param user 사용자
     * @param userLocation 사용자 위치 (Point)
     * @param distanceInMeters 거리 (미터)
     * @return 거리 내의 지오펜스 리스트
     */
    @Query(value = "SELECT * FROM geofence g WHERE g.user_id = :#{#user.number} AND ST_DWithin(g.location, :userLocation, :distance)", nativeQuery = true)
    List<Geofence> findUserGeofencesWithinDistance(@Param("user") User user, @Param("userLocation") Point userLocation, @Param("distance") double distanceInMeters);

    /**
     * 두 지점 간의 거리 계산 (PostGIS ST_Distance 사용, 미터 단위)
     * @param point1 첫 번째 지점
     * @param point2 두 번째 지점
     * @return 거리 (미터)
     */
    @Query(value = "SELECT ST_Distance(:point1::geography, :point2::geography)", nativeQuery = true)
    Double calculateDistance(@Param("point1") Point point1, @Param("point2") Point point2);
}
