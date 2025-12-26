package com.project.safetyFence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.safetyFence.link.LinkRepository;
import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.medication.MedicationLogRepository;
import com.project.safetyFence.medication.MedicationRepository;
import com.project.safetyFence.medication.domain.Medication;
import com.project.safetyFence.medication.domain.MedicationLog;
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
import java.time.LocalDateTime;

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

    @Autowired
    private MedicationLogRepository medicationLogRepository;

    @Autowired
    private LinkRepository linkRepository;

    private User testUser;
    private User otherUser;
    private User guardianUser;
    private String testApiKey;
    private String otherApiKey;
    private String guardianApiKey;
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

        // Guardian user
        guardianUser = new User("01011112222", "guardian", "password", LocalDate.now(), "guardian-link");
        guardianApiKey = "guardian-api-key-1234567890123456789012345";
        guardianUser.updateApiKey(guardianApiKey);

        // Save users
        userRepository.save(testUser);
        userRepository.save(otherUser);
        userRepository.save(guardianUser);

        // Create link (guardian -> testUser)
        Link link = new Link(guardianUser, testUser.getNumber(), "보호자");
        linkRepository.save(link);

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

    // ========== 날짜별 조회 테스트 ==========

    @Test
    @DisplayName("약 목록 조회 - 오늘 체크 여부 포함")
    void getMedications_WithCheckStatus() throws Exception {
        // given - 오늘 체크
        MedicationLog todayLog = new MedicationLog(testMedication, LocalDate.now().atTime(9, 0));
        testMedication.addLog(todayLog);
        medicationLogRepository.save(todayLog);

        // when & then
        mockMvc.perform(get("/api/medications")
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.medications[0].checkedToday").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("약 목록 조회 - 특정 날짜 체크 여부")
    void getMedications_WithSpecificDate() throws Exception {
        // given - 3일 전 체크
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        MedicationLog pastLog = new MedicationLog(testMedication, threeDaysAgo.atTime(9, 0));
        testMedication.addLog(pastLog);
        medicationLogRepository.save(pastLog);

        // when & then
        mockMvc.perform(get("/api/medications")
                        .param("date", threeDaysAgo.toString())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkDate").value(threeDaysAgo.toString()))
                .andExpect(jsonPath("$.medications[0].checkedToday").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("약 목록 조회 - 체크 안한 날짜")
    void getMedications_WithUncheckedDate() throws Exception {
        // given - 어제 체크했지만 오늘은 안함
        LocalDate yesterday = LocalDate.now().minusDays(1);
        MedicationLog yesterdayLog = new MedicationLog(testMedication, yesterday.atTime(9, 0));
        testMedication.addLog(yesterdayLog);
        medicationLogRepository.save(yesterdayLog);

        // when & then - 오늘 조회
        mockMvc.perform(get("/api/medications")
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.medications[0].checkedToday").value(false))
                .andDo(print());
    }

    // ========== 약 복용 체크 테스트 ==========

    @Test
    @DisplayName("약 복용 체크 성공")
    void checkMedication_Success() throws Exception {
        // when & then
        mockMvc.perform(post("/api/medications/{medicationId}/check", testMedication.getId())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("약 복용이 체크되었습니다"))
                .andExpect(jsonPath("$.medicationId").value(testMedication.getId()))
                .andExpect(jsonPath("$.checkedDateTime").exists())
                .andDo(print());

        // Verify database
        assertThat(medicationLogRepository.findByMedicationIdAndDate(
                testMedication.getId(), LocalDate.now())).isNotEmpty();
    }

    @Test
    @DisplayName("약 복용 여러 번 체크 가능")
    void checkMedication_MultipleChecks() throws Exception {
        // given - 이미 체크됨
        MedicationLog firstLog = new MedicationLog(testMedication, LocalDate.now().atTime(9, 0));
        testMedication.addLog(firstLog);
        medicationLogRepository.save(firstLog);

        // when & then - 두 번째 체크도 성공
        mockMvc.perform(post("/api/medications/{medicationId}/check", testMedication.getId())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("약 복용이 체크되었습니다"))
                .andExpect(jsonPath("$.checkedDateTime").exists())
                .andDo(print());

        // Verify database - 2개의 로그 존재
        assertThat(medicationLogRepository.findByMedicationIdAndDate(
                testMedication.getId(), LocalDate.now())).hasSize(2);
    }

    @Test
    @DisplayName("약 복용 체크 실패 - 보호자 시도")
    void checkMedication_Fail_Guardian() throws Exception {
        // when & then
        mockMvc.perform(post("/api/medications/{medicationId}/check", testMedication.getId())
                        .header("X-API-Key", guardianApiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인만 약 복용 체크/해제가 가능합니다"))
                .andDo(print());
    }

    @Test
    @DisplayName("약 복용 체크 실패 - 권한 없음")
    void checkMedication_Fail_Unauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/api/medications/{medicationId}/check", testMedication.getId())
                        .header("X-API-Key", otherApiKey))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("약 복용 체크 실패 - 존재하지 않는 약")
    void checkMedication_Fail_NotFound() throws Exception {
        // when & then
        mockMvc.perform(post("/api/medications/{medicationId}/check", 99999L)
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    // ========== 약 복용 체크 해제 테스트 ==========

    @Test
    @DisplayName("약 복용 체크 해제 성공")
    void uncheckMedication_Success() throws Exception {
        // given - 오늘 체크됨
        MedicationLog todayLog = new MedicationLog(testMedication, LocalDate.now().atTime(9, 0));
        testMedication.addLog(todayLog);
        medicationLogRepository.save(todayLog);

        // when & then
        mockMvc.perform(delete("/api/medications/{medicationId}/check", testMedication.getId())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("약 복용 체크가 해제되었습니다"))
                .andExpect(jsonPath("$.medicationId").value(testMedication.getId()))
                .andExpect(jsonPath("$.uncheckedDateTime").exists())
                .andDo(print());

        // Verify database - 가장 최근 로그 삭제됨
        assertThat(medicationLogRepository.findByMedicationIdAndDate(
                testMedication.getId(), LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("약 복용 체크 해제 실패 - 체크 기록 없음")
    void uncheckMedication_Fail_NoRecord() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/medications/{medicationId}/check", testMedication.getId())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("복용 체크 기록이 없습니다"))
                .andDo(print());
    }

    @Test
    @DisplayName("약 복용 체크 해제 실패 - 보호자 시도")
    void uncheckMedication_Fail_Guardian() throws Exception {
        // given - 오늘 체크됨
        MedicationLog todayLog = new MedicationLog(testMedication, LocalDate.now().atTime(9, 0));
        testMedication.addLog(todayLog);
        medicationLogRepository.save(todayLog);

        // when & then
        mockMvc.perform(delete("/api/medications/{medicationId}/check", testMedication.getId())
                        .header("X-API-Key", guardianApiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인만 약 복용 체크/해제가 가능합니다"))
                .andDo(print());
    }

    // ========== 복용 이력 조회 테스트 ==========

    @Test
    @DisplayName("복용 이력 조회 성공 - 본인")
    void getMedicationHistory_Success_Owner() throws Exception {
        // given - 3일치 이력
        MedicationLog log1 = new MedicationLog(testMedication, LocalDate.now().minusDays(2).atTime(9, 0));
        MedicationLog log2 = new MedicationLog(testMedication, LocalDate.now().minusDays(1).atTime(9, 0));
        MedicationLog log3 = new MedicationLog(testMedication, LocalDate.now().atTime(9, 0));
        medicationLogRepository.save(log1);
        medicationLogRepository.save(log2);
        medicationLogRepository.save(log3);

        // when & then
        mockMvc.perform(get("/api/medications/{medicationId}/history", testMedication.getId())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicationId").value(testMedication.getId()))
                .andExpect(jsonPath("$.medicationName").value("혈압약"))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.totalCheckCount").value(3))
                .andDo(print());
    }

    @Test
    @DisplayName("복용 이력 조회 성공 - 보호자")
    void getMedicationHistory_Success_Guardian() throws Exception {
        // given
        MedicationLog log = new MedicationLog(testMedication, LocalDate.now().atTime(9, 0));
        medicationLogRepository.save(log);

        // when & then
        mockMvc.perform(get("/api/medications/{medicationId}/history", testMedication.getId())
                        .header("X-API-Key", guardianApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicationId").value(testMedication.getId()))
                .andExpect(jsonPath("$.history").isArray())
                .andDo(print());
    }

    @Test
    @DisplayName("복용 이력 조회 - 기간 필터링")
    void getMedicationHistory_WithDateRange() throws Exception {
        // given - 5일치 이력
        for (int i = 0; i < 5; i++) {
            MedicationLog log = new MedicationLog(testMedication, LocalDate.now().minusDays(i).atTime(9, 0));
            medicationLogRepository.save(log);
        }

        LocalDate startDate = LocalDate.now().minusDays(2);
        LocalDate endDate = LocalDate.now();

        // when & then - 최근 3일만 조회
        mockMvc.perform(get("/api/medications/{medicationId}/history", testMedication.getId())
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.totalCheckCount").value(3))
                .andDo(print());
    }

    @Test
    @DisplayName("복용 이력 조회 실패 - 권한 없음")
    void getMedicationHistory_Fail_Unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/api/medications/{medicationId}/history", testMedication.getId())
                        .header("X-API-Key", otherApiKey))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("복용 이력 조회 - 이력 없음")
    void getMedicationHistory_Empty() throws Exception {
        // when & then
        mockMvc.perform(get("/api/medications/{medicationId}/history", testMedication.getId())
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history").isEmpty())
                .andExpect(jsonPath("$.totalCheckCount").value(0))
                .andDo(print());
    }
}