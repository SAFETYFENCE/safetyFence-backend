package com.project.safetyFence.medication;

import com.project.safetyFence.medication.domain.MedicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    // 특정 날짜의 로그 조회 (여러 개 가능)
    @Query("SELECT ml FROM MedicationLog ml WHERE ml.medication.id = :medicationId " +
            "AND FUNCTION('DATE', ml.checkedDateTime) = :date ORDER BY ml.checkedDateTime DESC")
    List<MedicationLog> findByMedicationIdAndDate(
            @Param("medicationId") Long medicationId,
            @Param("date") LocalDate date
    );

    // 특정 약의 모든 로그 조회
    @Query("SELECT ml FROM MedicationLog ml WHERE ml.medication.id = :medicationId ORDER BY ml.checkedDateTime DESC")
    List<MedicationLog> findByMedicationId(@Param("medicationId") Long medicationId);

    // 날짜 범위로 로그 조회
    @Query("SELECT ml FROM MedicationLog ml WHERE ml.medication.id = :medicationId " +
            "AND FUNCTION('DATE', ml.checkedDateTime) BETWEEN :startDate AND :endDate ORDER BY ml.checkedDateTime DESC")
    List<MedicationLog> findByMedicationIdAndDateBetween(
            @Param("medicationId") Long medicationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 가장 최근 로그 조회 (uncheckMedication에서 사용)
    @Query("SELECT ml FROM MedicationLog ml WHERE ml.medication.id = :medicationId ORDER BY ml.checkedDateTime DESC LIMIT 1")
    Optional<MedicationLog> findTopByMedicationIdOrderByCheckedDateTimeDesc(@Param("medicationId") Long medicationId);
}