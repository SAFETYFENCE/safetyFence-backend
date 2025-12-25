package com.project.safetyFence.medication;

import com.project.safetyFence.medication.domain.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    @Query("SELECT m FROM Medication m WHERE m.user.number = :userNumber ORDER BY m.id DESC")
    List<Medication> findByUserNumber(@Param("userNumber") String userNumber);
}