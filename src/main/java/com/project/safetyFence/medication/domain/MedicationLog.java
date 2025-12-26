package com.project.safetyFence.medication.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class MedicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(nullable = false)
    private LocalDateTime checkedDateTime;  // 체크한 날짜 + 시간 (여러 번 가능)

    public MedicationLog(Medication medication, LocalDateTime checkedDateTime) {
        this.medication = medication;
        this.checkedDateTime = checkedDateTime;
    }

    // 연관관계 편의 메서드
    public void registerMedication(Medication medication) {
        this.medication = medication;
    }
}