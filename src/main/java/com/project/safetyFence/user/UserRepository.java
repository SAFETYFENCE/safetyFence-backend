package com.project.safetyFence.user;

import com.project.safetyFence.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String>{
    boolean existsByNumber(String number);
    User findByNumber(String number);
    boolean existsByLinkCode(String linkCode);
    User findByApiKey(String apiKey);
    boolean existsByApiKey(String apiKey);
    User findByLinkCode(String linkCode);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.links WHERE u.number = :number")
    User findByNumberWithLinks(@Param("number") String number);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userEvents WHERE u.number = :number")
    User findByNumberWithEvents(@Param("number") String number);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.geofences WHERE u.number = :number")
    User findByNumberWithGeofences(@Param("number") String number);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.logs WHERE u.number = :number")
    User findByNumberWithCalendarData(@Param("number") String number);

    @Query("SELECT u FROM User u " +
           "LEFT JOIN FETCH u.userAddress " +
           "LEFT JOIN FETCH u.geofences " +
           "WHERE u.number = :number")
    User findByNumberWithUserDataForMyPage(@Param("number") String number);

}
