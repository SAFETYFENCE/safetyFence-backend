package com.project.safetyFence.medication.domain;

import com.project.safetyFence.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;  // 약 이름

    @Column(nullable = false)
    private String dosage;  // 복용량 (예: "1정", "10ml")

    @Column(nullable = false)
    private String purpose;  // 기능 (예: "혈압 조절", "당뇨 치료")

    @Column(nullable = false)
    private String frequency;  // 주기 (예: "하루 3회", "아침 저녁")

    // 1:N 양방향 관계 - MedicationLog
    @OneToMany(mappedBy = "medication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MedicationLog> logs = new ArrayList<>();

    public Medication(User user, String name, String dosage, String purpose, String frequency) {
        this.user = user;
        this.name = name;
        this.dosage = dosage;
        this.purpose = purpose;
        this.frequency = frequency;
    }

    // 연관관계 편의 메서드
    public void registerUser(User user) {
        this.user = user;
    }

    public void addLog(MedicationLog log) {
        logs.add(log);
        if (log.getMedication() != this) {
            log.registerMedication(this);
        }
    }

    public void removeLog(MedicationLog log) {
        logs.remove(log);
    }

    // 수정 메서드
    public void update(String name, String dosage, String purpose, String frequency) {
        this.name = name;
        this.dosage = dosage;
        this.purpose = purpose;
        this.frequency = frequency;
    }
}
