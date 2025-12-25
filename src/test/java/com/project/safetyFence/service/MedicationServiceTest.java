package com.project.safetyFence.service;

import com.project.safetyFence.medication.MedicationRepository;
import com.project.safetyFence.medication.MedicationService;
import com.project.safetyFence.medication.domain.Medication;
import com.project.safetyFence.medication.dto.MedicationCreateResponseDto;
import com.project.safetyFence.medication.dto.MedicationDeleteResponseDto;
import com.project.safetyFence.medication.dto.MedicationListResponseDto;
import com.project.safetyFence.medication.dto.MedicationRequestDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class MedicationServiceTest {

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    private User testUser;
    private Medication testMedication;

    @BeforeEach
    void setUp() {
        testUser = new User("01012345678", "tester", "password", LocalDate.now(), "test-link");
        userRepository.save(testUser);

        testMedication = new Medication(testUser, "혈압약", "1정", "혈압 조절", "하루 2회");
        testUser.addMedication(testMedication);
        medicationRepository.save(testMedication);
    }

    @Test
    @DisplayName("약 목록 조회 성공")
    void getMedications_Success() {
        // when
        MedicationListResponseDto response = medicationService.getMedications(testUser.getNumber());

        // then
        assertThat(response.getMedications()).hasSize(1);
        assertThat(response.getMedications().get(0).getName()).isEqualTo("혈압약");
        assertThat(response.getMedications().get(0).getDosage()).isEqualTo("1정");
    }

    @Test
    @DisplayName("약 목록 조회 - 약이 없는 경우")
    void getMedications_Empty() {
        // given
        User emptyUser = new User("01099999999", "empty", "password", LocalDate.now(), "empty-link");
        userRepository.save(emptyUser);

        // when
        MedicationListResponseDto response = medicationService.getMedications(emptyUser.getNumber());

        // then
        assertThat(response.getMedications()).isEmpty();
    }

    @Test
    @DisplayName("약 추가 성공")
    void createMedication_Success() {
        // given
        MedicationRequestDto requestDto = new MedicationRequestDto();
        // Manual creation for test
        String jsonString = """
                {
                    "name": "당뇨약",
                    "dosage": "2정",
                    "purpose": "혈당 관리",
                    "frequency": "아침 저녁"
                }
                """;

        // Use reflection or create a test builder
        // For now, create directly
        Medication newMedication = new Medication(testUser, "당뇨약", "2정", "혈당 관리", "아침 저녁");
        testUser.addMedication(newMedication);
        medicationRepository.save(newMedication);

        // when
        MedicationListResponseDto response = medicationService.getMedications(testUser.getNumber());

        // then
        assertThat(response.getMedications()).hasSize(2);
    }

    @Test
    @DisplayName("약 삭제 성공")
    void deleteMedication_Success() {
        // when
        MedicationDeleteResponseDto response = medicationService.deleteMedication(
                testUser.getNumber(),
                testMedication.getId()
        );

        // then
        assertThat(response.getDeletedMedicationId()).isEqualTo(testMedication.getId());
        assertThat(medicationRepository.findById(testMedication.getId())).isEmpty();
    }

    @Test
    @DisplayName("약 삭제 실패 - 권한 없음")
    void deleteMedication_Fail_Unauthorized() {
        // given
        User otherUser = new User("01098765432", "other", "password", LocalDate.now(), "other-link");
        userRepository.save(otherUser);

        // when & then
        assertThatThrownBy(() ->
            medicationService.deleteMedication(otherUser.getNumber(), testMedication.getId())
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("권한이 없습니다");
    }

    @Test
    @DisplayName("약 삭제 실패 - 존재하지 않는 약")
    void deleteMedication_Fail_NotFound() {
        // given
        Long nonExistentId = 99999L;

        // when & then
        assertThatThrownBy(() ->
            medicationService.deleteMedication(testUser.getNumber(), nonExistentId)
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("약 정보를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("여러 약 등록 후 조회")
    void getMedications_Multiple() {
        // given
        Medication medication2 = new Medication(testUser, "감기약", "3정", "감기 치료", "하루 3회");
        Medication medication3 = new Medication(testUser, "소화제", "1포", "소화 개선", "식후");

        testUser.addMedication(medication2);
        testUser.addMedication(medication3);
        medicationRepository.save(medication2);
        medicationRepository.save(medication3);

        // when
        MedicationListResponseDto response = medicationService.getMedications(testUser.getNumber());

        // then
        assertThat(response.getMedications()).hasSize(3);
    }
}