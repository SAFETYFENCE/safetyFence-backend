package com.project.safetyFence.notification;

import com.project.safetyFence.notification.domain.DeviceToken;
import com.project.safetyFence.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
    List<DeviceToken> findByUser(User user);
    void deleteByToken(String token);
}