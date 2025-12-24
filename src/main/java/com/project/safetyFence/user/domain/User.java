package com.project.safetyFence.user.domain;

import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.log.domain.Log;
import com.project.safetyFence.calendar.domain.UserEvent;
import com.project.safetyFence.medication.domain.Medication;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDate birth;

    @Column(nullable = false)
    private String linkCode;

    @Column(unique = true, length = 64)
    private String apiKey;

    // 1:1 양방향 관계 - UserAddress
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserAddress userAddress;

    // 1:N 양방향 관계 - UserLocation
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserLocation> userLocations = new ArrayList<>();

    // 1:N 양방향 관계 - Log
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Log> logs = new ArrayList<>();

    // 1:N 양방향 관계 - Link
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Link> links = new ArrayList<>();

    // 1:N 양방향 관계 - Geofence
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Geofence> geofences = new ArrayList<>();

    // 1:N 양방향 관계 - userEvent
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserEvent> userEvents = new ArrayList<>();

    // 1:N 양방향 관계 - Medication
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Medication> medications = new ArrayList<>();

    public User(String number, String name, String password, LocalDate birth, String linkCode) {
        this.number = number;
        this.name = name;
        this.password = password;
        this.birth = birth;
        this.linkCode = linkCode;
    }

    // UserAddress 연관관계 편의 메서드
    public void addUserAddress(UserAddress userAddress) {
        this.userAddress = userAddress;
        if (userAddress != null && userAddress.getUser() != this) {
            userAddress.registerUser(this);
        }
    }

    // UserLocation 연관관계 편의 메서드
    public void addUserLocation(UserLocation userLocation) {
        userLocations.add(userLocation);
        if (userLocation.getUser() != this) {
            userLocation.registerUser(this);
        }
    }

    public void removeUserLocation(UserLocation userLocation) {
        userLocations.remove(userLocation);
    }

    // Log 연관관계 편의 메서드
    public void addLog(Log log) {
        logs.add(log);
        if (log.getUser() != this) {
            log.registerUser(this);
        }
    }

    // Link 연관관계 편의 메서드
    public void addLink(Link link) {
        links.add(link);
        if (link.getUser() != this) {
            link.registerUser(this);
        }
    }

    // Geofence 연관관계 편의 메서드
    public void addGeofence(Geofence geofence) {
        geofences.add(geofence);
        if (geofence.getUser() != this) {
            geofence.registerUser(this);
        }
    }

    public void removeGeofence(Geofence geofence) {
        geofences.remove(geofence);
        // 컬렉션에서만 제거 - orphanRemoval = true가 자동으로 DB 삭제 처리
    }

    // UserEvent 연관관계 편의 메서드
    public void addUserEvent(UserEvent userEvent) {
        userEvents.add(userEvent);
        if (userEvent.getUser() != this) {
            userEvent.registerUser(this);
        }
    }

    public void removeEvent(UserEvent userEvent) {
        userEvents.remove(userEvent);
    }

    // Medication 연관관계 편의 메서드
    public void addMedication(Medication medication) {
        medications.add(medication);
        if (medication.getUser() != this) {
            medication.registerUser(this);
        }
    }

    public void removeMedication(Medication medication) {
        medications.remove(medication);
    }

    public void removeLink(Link link) {
        links.remove(link);
    }

    // API Key 설정 메서드
    public void updateApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    // 비밀번호 변경 메서드
    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

}
