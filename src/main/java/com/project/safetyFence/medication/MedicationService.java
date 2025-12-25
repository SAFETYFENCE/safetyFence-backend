package com.project.safetyFence.medication;

import com.project.safetyFence.medication.domain.Medication;
import com.project.safetyFence.medication.dto.*;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;

    public MedicationListResponseDto getMedications(String userNumber) {
        List<Medication> medications = medicationRepository.findByUserNumber(userNumber);

        List<MedicationItemDto> medicationDtos = medications.stream()
                .map(MedicationItemDto::new)
                .collect(Collectors.toList());

        return new MedicationListResponseDto(medicationDtos);
    }

    @Transactional
    public MedicationCreateResponseDto createMedication(String userNumber, MedicationRequestDto requestDto) {
        User user = userRepository.findById(userNumber)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

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

        // 권한 확인: 본인 약만 삭제 가능
        if (!medication.getUser().getNumber().equals(userNumber)) {
            throw new IllegalArgumentException("권한이 없습니다");
        }

        // 양방향 관계 정리
        User user = medication.getUser();
        user.removeMedication(medication);

        // 명시적 삭제
        medicationRepository.delete(medication);

        return new MedicationDeleteResponseDto(medicationId);
    }
}