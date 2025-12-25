package com.project.safetyFence.notification.domain;

import com.project.safetyFence.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_number", referencedColumnName = "number", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(nullable = false)
    private String deviceType; // "android" or "ios"

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public DeviceToken(User user, String token, String deviceType) {
        this.user = user;
        this.token = token;
        this.deviceType = deviceType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateToken(String newToken) {
        this.token = newToken;
        this.updatedAt = LocalDateTime.now();
    }
}