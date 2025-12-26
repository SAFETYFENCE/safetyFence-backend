package com.project.safetyFence.medication;

import com.project.safetyFence.medication.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    // 복용 약 리스트 출력 -> 사용자 및 보호자
    @GetMapping("/api/medications")
    public ResponseEntity<MedicationListResponseDto> getMedications(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        MedicationListResponseDto response = medicationService.getMedications(userNumber, date);
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

    // 약 복용 체크
    @PostMapping("/api/medications/{medicationId}/check")
    public ResponseEntity<MedicationCheckResponseDto> checkMedication(
            @PathVariable Long medicationId,
            HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        MedicationCheckResponseDto response = medicationService.checkMedication(userNumber, medicationId);
        return ResponseEntity.ok(response);
    }

    // 약 복용 체크 해제
    @DeleteMapping("/api/medications/{medicationId}/check")
    public ResponseEntity<MedicationUncheckResponseDto> uncheckMedication(
            @PathVariable Long medicationId,
            HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        MedicationUncheckResponseDto response = medicationService.uncheckMedication(userNumber, medicationId);
        return ResponseEntity.ok(response);
    }

    // 복용 이력 조회
    @GetMapping("/api/medications/{medicationId}/history")
    public ResponseEntity<MedicationHistoryResponseDto> getMedicationHistory(
            @PathVariable Long medicationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        MedicationHistoryResponseDto response = medicationService.getMedicationHistory(
                userNumber, medicationId, startDate, endDate
        );
        return ResponseEntity.ok(response);
    }

    // 피보호자들의 당일 약 복용 상태 조회 (보호자용)
    @GetMapping("/api/medications/wards-today")
    public ResponseEntity<List<WardMedicationStatusDto>> getWardsTodayMedicationStatus(
            HttpServletRequest request) {
        String userNumber = (String) request.getAttribute("userNumber");
        List<WardMedicationStatusDto> response = medicationService.getWardsTodayMedicationStatus(userNumber);
        return ResponseEntity.ok(response);
    }
}