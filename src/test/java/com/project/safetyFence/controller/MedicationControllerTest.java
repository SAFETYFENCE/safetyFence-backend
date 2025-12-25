package com.project.safetyFence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.safetyFence.medication.MedicationRepository;
import com.project.safetyFence.medication.domain.Medication;
import com.project.safetyFence.medication.dto.MedicationRequestDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MedicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    private User testUser;
    private User otherUser;
    private String testApiKey;
    private String otherApiKey;
    private Medication testMedication;

    @BeforeEach
    void setUp() {
        // Test user with API Key
        testUser = new User("01012345678", "tester", "password", LocalDate.now(), "test-link");
        testApiKey = "test-api-key-12345678901234567890123456789012";
        testUser.updateApiKey(testApiKey);

        // Other user for permission test
        otherUser = new User("01098765432", "other", "password", LocalDate.now(), "other-link");
        otherApiKey = "other-api-key-12345678901234567890123456789012";
        otherUser.updateApiKey(otherApiKey);

        // Save users
        userRepository.save(testUser);
        userRepository.save(otherUser);

        // Create test medication
        testMedication = new Medication(testUser, "혈압약", "1정", "혈압 조절", "하루 2회");
        testUser.addMedication(testMedication);
        medicationRepository.save(testMedication);
    }

    @Test
    @DisplayName("약 목록 조회 성공")
    void getMedications_Success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/medications")
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medications").isArray())
                .andExpect(jsonPath("$.medications[0].id").value(testMedication.getId()))
                .andExpect(jsonPath("$.medications[0].name").value("혈압약"))
                .andExpect(jsonPath("$.medications[0].dosage").value("1정"))
                .andExpect(jsonPath("$.medications[0].purpose").value("혈압 조절"))
                .andExpect(jsonPath("$.medications[0].frequency").value("하루 2회"))
                .andDo(print());
    }

    @Test
    @DisplayName("약 목록 조회 - 약이 없는 경우")
    void getMedications_Empty() throws Exception {
        // when & then
        mockMvc.perform(get("/api/medications")
                        .header("X-API-Key", otherApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medications").isArray())
                .andExpect(jsonPath("$.medications").isEmpty())
                .andDo(print());
    }

    @Test
    @DisplayName("약 목록 조회 실패 - 잘못된 API Key")
    void getMedications_Fail_InvalidApiKey() throws Exception {
        // when & then
        mockMvc.perform(get("/api/medications")
                        .header("X-API-Key", "invalid-api-key"))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("약 추가 성공")
    void createMedication_Success() throws Exception {
        // given
        MedicationRequestDto requestDto = new MedicationRequestDto();
        // Reflection을 사용하여 필드 설정 (Getter만 있는 경우)
        // 또는 생성자가 있다면 생성자 사용
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // Manual JSON 생성
        String manualJson = """
                {
                    "name": "당뇨약",
                    "dosage": "2정",
                    "purpose": "혈당 관리",
                    "frequency": "아침 저녁"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/medications")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("약이 등록되었습니다"))
                .andExpect(jsonPath("$.medication.name").value("당뇨약"))
                .andExpect(jsonPath("$.medication.dosage").value("2정"))
                .andExpect(jsonPath("$.medication.purpose").value("혈당 관리"))
                .andExpect(jsonPath("$.medication.frequency").value("아침 저녁"))
                .andDo(print());

        // Verify database
        assertThat(medicationRepository.findByUserNumber(testUser.getNumber())).hasSize(2);
    }

    @Test
    @DisplayName("약 추가 실패 - 잘못된 API Key")
    void createMedication_Fail_InvalidApiKey() throws Exception {
        // given
        String jsonRequest = """
                {
                    "name": "당뇨약",
                    "dosage": "2정",
                    "purpose": "혈당 관리",
                    "frequency": "아침 저녁"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/medications")
                        .header("X-API-Key", "invalid-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("약 삭제 성공")
    void deleteMedication_Success() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/medications/{medicationId}", testMedication.getId())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("약이 삭제되었습니다"))
                .andExpect(jsonPath("$.deletedMedicationId").value(testMedication.getId()))
                .andDo(print());

        // Verify database
        assertThat(medicationRepository.findById(testMedication.getId())).isEmpty();
    }

    @Test
    @DisplayName("약 삭제 실패 - 권한 없음 (다른 사용자의 약)")
    void deleteMedication_Fail_Unauthorized() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/medications/{medicationId}", testMedication.getId())
                        .header("X-API-Key", otherApiKey))
                .andExpect(status().isBadRequest())
                .andDo(print());

        // Verify database - 약이 삭제되지 않았는지 확인
        assertThat(medicationRepository.findById(testMedication.getId())).isPresent();
    }

    @Test
    @DisplayName("약 삭제 실패 - 존재하지 않는 약")
    void deleteMedication_Fail_NotFound() throws Exception {
        // given
        Long nonExistentId = 99999L;

        // when & then
        mockMvc.perform(delete("/api/medications/{medicationId}", nonExistentId)
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("약 삭제 실패 - 잘못된 API Key")
    void deleteMedication_Fail_InvalidApiKey() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/medications/{medicationId}", testMedication.getId())
                        .header("X-API-Key", "invalid-api-key"))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("여러 약 등록 후 목록 조회")
    void getMedications_Multiple() throws Exception {
        // given - 추가 약 등록
        Medication medication2 = new Medication(testUser, "감기약", "3정", "감기 치료", "하루 3회");
        Medication medication3 = new Medication(testUser, "소화제", "1포", "소화 개선", "식후");

        testUser.addMedication(medication2);
        testUser.addMedication(medication3);
        medicationRepository.save(medication2);
        medicationRepository.save(medication3);

        // when & then
        mockMvc.perform(get("/api/medications")
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medications").isArray())
                .andExpect(jsonPath("$.medications.length()").value(3))
                .andDo(print());
    }
}