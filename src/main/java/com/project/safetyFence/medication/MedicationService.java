package com.project.safetyFence.medication;

import com.project.safetyFence.link.LinkRepository;
import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.medication.domain.Medication;
import com.project.safetyFence.medication.domain.MedicationLog;
import com.project.safetyFence.medication.dto.*;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final UserRepository userRepository;
    private final LinkRepository linkRepository;

    // ========== 권한 검증 헬퍼 메서드 ==========

    /**
     * 약 조회/수정 권한 확인 (본인 + 보호자)
     */
    private void validateMedicationAccess(Medication medication, String requestUserNumber) {
        String medicationOwnerNumber = medication.getUser().getNumber();

        // 1) 본인인 경우
        if (medicationOwnerNumber.equals(requestUserNumber)) {
            return;
        }

        // 2) 보호자인지 확인
        List<Link> links = linkRepository.findByUserNumber(medicationOwnerNumber);
        boolean isGuardian = links.stream()
                .anyMatch(link -> link.getUser().getNumber().equals(requestUserNumber));

        if (!isGuardian) {
            throw new IllegalArgumentException("권한이 없습니다");
        }
    }

    /**
     * 약 복용 체크/해제 권한 확인 (본인만)
     */
    private void validateMedicationModifyAccess(Medication medication, String requestUserNumber) {
        if (!medication.getUser().getNumber().equals(requestUserNumber)) {
            throw new IllegalArgumentException("본인만 약 복용 체크/해제가 가능합니다");
        }
    }

    // ========== 기존 CRUD API ==========

    public MedicationListResponseDto getMedications(String userNumber, LocalDate date) {
        User user = userRepository.findByNumber(userNumber);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
        }

        List<Medication> medications = medicationRepository.findByUserNumber(userNumber);
        LocalDate checkDate = (date != null) ? date : LocalDate.now();

        // 각 약의 해당 날짜 체크 여부를 함께 조회
        List<MedicationItemDto> medicationItems = medications.stream()
                .map(medication -> {
                    // 해당 날짜 체크 여부 확인 (여러 개 있을 수 있음)
                    List<MedicationLog> logs = medicationLogRepository
                            .findByMedicationIdAndDate(medication.getId(), checkDate);
                    boolean checked = !logs.isEmpty();

                    return new MedicationItemDto(medication, checked);
                })
                .collect(Collectors.toList());

        return new MedicationListResponseDto(checkDate, medicationItems);
    }

    @Transactional
    public MedicationCreateResponseDto createMedication(String userNumber, MedicationRequestDto requestDto) {
        User user = userRepository.findByNumber(userNumber);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
        }

        Medication medication = new Medication(
                user,
                requestDto.getName(),
                requestDto.getDosage(),
                requestDto.getPurpose(),
                requestDto.getFrequency()
        );

        user.addMedication(medication);
        Medication savedMedication = medicationRepository.save(medication);

        return new MedicationCreateResponseDto(savedMedication);
    }

    @Transactional
    public MedicationDeleteResponseDto deleteMedication(String userNumber, Long medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("약 정보를 찾을 수 없습니다"));

        // 권한 확인 (본인 + 보호자)
        validateMedicationAccess(medication, userNumber);

        User user = medication.getUser();
        user.removeMedication(medication);
        medicationRepository.delete(medication);

        return new MedicationDeleteResponseDto(medicationId);
    }

    // ========== 체크 시스템 API ==========

    /**
     * 약 복용 체크 (본인만) - 여러 번 가능
     */
    @Transactional
    public MedicationCheckResponseDto checkMedication(String userNumber, Long medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("약 정보를 찾을 수 없습니다"));

        // 수정 권한 확인 (본인만)
        validateMedicationModifyAccess(medication, userNumber);

        // 현재 시간으로 로그 추가 (중복 체크 없이 계속 추가 가능)
        LocalDateTime now = LocalDateTime.now();
        MedicationLog newLog = new MedicationLog(medication, now);
        medication.addLog(newLog);
        medicationLogRepository.save(newLog);

        return MedicationCheckResponseDto.builder()
                .message("약 복용이 체크되었습니다")
                .medicationId(medicationId)
                .checkedDateTime(now)
                .build();
    }

    /**
     * 약 복용 체크 해제 (본인만) - 가장 최근 로그 삭제
     */
    @Transactional
    public MedicationUncheckResponseDto uncheckMedication(String userNumber, Long medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("약 정보를 찾을 수 없습니다"));

        // 수정 권한 확인 (본인만)
        validateMedicationModifyAccess(medication, userNumber);

        // 가장 최근 체크 로그 조회
        MedicationLog recentLog = medicationLogRepository
                .findTopByMedicationIdOrderByCheckedDateTimeDesc(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("복용 체크 기록이 없습니다"));

        // 로그 삭제
        LocalDateTime deletedDateTime = recentLog.getCheckedDateTime();
        medication.removeLog(recentLog);
        medicationLogRepository.delete(recentLog);

        return MedicationUncheckResponseDto.builder()
                .message("약 복용 체크가 해제되었습니다")
                .medicationId(medicationId)
                .uncheckedDateTime(deletedDateTime)
                .build();
    }

    /**
     * 복용 이력 조회 (본인 + 보호자)
     */
    public MedicationHistoryResponseDto getMedicationHistory(
            String userNumber,
            Long medicationId,
            LocalDate startDate,
            LocalDate endDate) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("약 정보를 찾을 수 없습니다"));

        // 조회 권한 확인 (본인 + 보호자)
        validateMedicationAccess(medication, userNumber);

        // 이력 조회
        List<MedicationLog> logs;
        if (startDate != null && endDate != null) {
            logs = medicationLogRepository.findByMedicationIdAndDateBetween(
                    medicationId, startDate, endDate
            );
        } else {
            logs = medicationLogRepository.findByMedicationId(medicationId);
        }

        // DTO 변환
        List<MedicationHistoryItemDto> historyItems = logs.stream()
                .map(MedicationHistoryItemDto::new)
                .collect(Collectors.toList());

        return MedicationHistoryResponseDto.builder()
                .medicationId(medicationId)
                .medicationName(medication.getName())
                .history(historyItems)
                .totalCheckCount(historyItems.size())
                .build();
    }

    /**
     * 피보호자들의 당일 약 복용 상태 조회 (보호자용)
     */
    public List<WardMedicationStatusDto> getWardsTodayMedicationStatus(String supporterNumber) {
        // 내가 구독한 피보호자들 조회
        User supporter = userRepository.findByNumberWithLinks(supporterNumber);
        List<Link> wardLinks = supporter.getLinks();

        List<WardMedicationStatusDto> wardStatuses = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Link link : wardLinks) {
            String wardNumber = link.getUserNumber();
            User ward = userRepository.findByNumber(wardNumber);

            // 피보호자의 약 목록 조회
            List<Medication> medications = medicationRepository.findByUserNumber(wardNumber);

            // 각 약의 오늘 복용 여부 확인
            List<MedicationItemDto> medicationItems = medications.stream()
                    .map(medication -> {
                        List<MedicationLog> logs = medicationLogRepository
                                .findByMedicationIdAndDate(medication.getId(), today);
                        boolean checked = !logs.isEmpty();
                        return new MedicationItemDto(medication, checked);
                    })
                    .collect(Collectors.toList());

            // 복용한 약 개수 계산
            int checkedCount = (int) medicationItems.stream()
                    .filter(MedicationItemDto::isCheckedToday)
                    .count();

            WardMedicationStatusDto wardStatus = WardMedicationStatusDto.builder()
                    .wardNumber(wardNumber)
                    .wardName(ward.getName())
                    .medications(medicationItems)
                    .totalMedications(medicationItems.size())
                    .checkedMedications(checkedCount)
                    .build();

            wardStatuses.add(wardStatus);
        }

        return wardStatuses;
    }
}