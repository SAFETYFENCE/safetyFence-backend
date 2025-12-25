package com.project.safetyFence.medication;

import com.project.safetyFence.medication.dto.MedicationCreateResponseDto;
import com.project.safetyFence.medication.dto.MedicationDeleteResponseDto;
import com.project.safetyFence.medication.dto.MedicationListResponseDto;
import com.project.safetyFence.medication.dto.MedicationRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    // 복용 약 리스트 출력
    @GetMapping("/api/medications")
    public ResponseEntity<MedicationListResponseDto> getMedications(HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        MedicationListResponseDto response = medicationService.getMedications(userNumber);
        return ResponseEntity.ok(response);
    }

    // 약 추가 기능
    @PostMapping("/api/medications")
    public ResponseEntity<MedicationCreateResponseDto> createMedication(
            @RequestBody MedicationRequestDto requestDto,
            HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        MedicationCreateResponseDto response = medicationService.createMedication(userNumber, requestDto);
        return ResponseEntity.ok(response);
    }

    // 약 삭제 기능
    @DeleteMapping("/api/medications/{medicationId}")
    public ResponseEntity<MedicationDeleteResponseDto> deleteMedication(
            @PathVariable Long medicationId,
            HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        MedicationDeleteResponseDto response = medicationService.deleteMedication(userNumber, medicationId);
        return ResponseEntity.ok(response);
    }
}