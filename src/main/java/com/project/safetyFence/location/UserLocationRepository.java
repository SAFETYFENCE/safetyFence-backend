package com.project.safetyFence.location;

import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.location.domain.UserLocation;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

    /**
     * 특정 사용자의 가장 최근 위치 조회
     * @param user 사용자
     * @return 최근 위치 (Optional)
     */
    @Query("SELECT ul FROM UserLocation ul WHERE ul.user = :user ORDER BY ul.savedTime DESC LIMIT 1")
    Optional<UserLocation> findLatestByUser(@Param("user") User user);

    /**
     * 특정 지점으로부터 지정된 거리 내의 위치 기록 조회 (PostGIS ST_DWithin 사용)
     * @param targetLocation 대상 위치 (Point)
     * @param distanceInMeters 거리 (미터)
     * @return 거리 내의 위치 기록 리스트
     */
    @Query(value = "SELECT * FROM user_location ul WHERE ST_DWithin(ul.location, :targetLocation, :distance)", nativeQuery = true)
    List<UserLocation> findLocationsWithinDistance(@Param("targetLocation") Point targetLocation, @Param("distance") double distanceInMeters);

    /**
     * 특정 사용자의 특정 시간 범위 내 위치 기록 조회
     * @param user 사용자
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 시간 범위 내 위치 기록 리스트
     */
    @Query("SELECT ul FROM UserLocation ul WHERE ul.user = :user AND ul.savedTime BETWEEN :startTime AND :endTime ORDER BY ul.savedTime DESC")
    List<UserLocation> findByUserAndTimeRange(@Param("user") User user, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 특정 사용자의 특정 지점 근처 위치 기록 조회
     * @param user 사용자
     * @param targetLocation 대상 위치 (Point)
     * @param distanceInMeters 거리 (미터)
     * @return 거리 내의 위치 기록 리스트
     */
    @Query(value = "SELECT * FROM user_location ul WHERE ul.user_id = :#{#user.number} AND ST_DWithin(ul.location, :targetLocation, :distance) ORDER BY ul.saved_time DESC", nativeQuery = true)
    List<UserLocation> findUserLocationsNearPoint(@Param("user") User user, @Param("targetLocation") Point targetLocation, @Param("distance") double distanceInMeters);

    /**
     * 특정 사용자의 특정 시간 범위 내 위치 기록 조회 (시간순 정렬 - 거리 계산용)
     * @param user 사용자
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 시간 범위 내 위치 기록 리스트 (오래된 순)
     */
    @Query("SELECT ul FROM UserLocation ul WHERE ul.user = :user AND ul.savedTime BETWEEN :startTime AND :endTime ORDER BY ul.savedTime ASC")
    List<UserLocation> findByUserAndTimeRangeOrderByTimeAsc(@Param("user") User user, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
