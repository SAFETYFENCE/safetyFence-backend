package com.project.safetyFence.medication.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"medication_id", "checked_date"}
    )
)
public class MedicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(nullable = false)
    private LocalDate checkedDate;  // 체크한 날짜 (하루에 하나만)

    public MedicationLog(Medication medication, LocalDate checkedDate) {
        this.medication = medication;
        this.checkedDate = checkedDate;
    }

    // 연관관계 편의 메서드
    public void registerMedication(Medication medication) {
        this.medication = medication;
    }
}