package com.project.safetyFence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.user.domain.UserAddress;
import com.project.safetyFence.geofence.dto.FenceInRequestDto;
import com.project.safetyFence.geofence.dto.GeofenceDeleteRequestDto;
import com.project.safetyFence.geofence.GeofenceRepository;
import com.project.safetyFence.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GeofenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeofenceRepository geofenceRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private User guardianUser;
    private Geofence permanentGeofence;
    private Geofence temporaryGeofence;
    private String testApiKey;
    private String guardianApiKey;

    @BeforeEach
    void setUp() {
        // Test user with API Key
        testUser = new User("01012345678", "tester", "password", LocalDate.now(), "test-link");
        testApiKey = "test-api-key-12345678901234567890123456789012";
        testUser.updateApiKey(testApiKey);

        UserAddress userAddress = new UserAddress(testUser, "서울", "부산", "서울시 강남구", "상세주소1", "부산시 해운대구");
        testUser.addUserAddress(userAddress);

        // Guardian user with API Key (보호자)
        guardianUser = new User("01098765432", "guardian", "password", LocalDate.now(), "guardian-link");
        guardianApiKey = "guardian-api-key-12345678901234567890123456";
        guardianUser.updateApiKey(guardianApiKey);

        UserAddress guardianAddress = new UserAddress(guardianUser, "서울", "인천", "서울시 종로구", "상세주소2", "인천시 남동구");
        guardianUser.addUserAddress(guardianAddress);

        // Permanent geofences
        Geofence homeGeofence = new Geofence(testUser, "집", "서울시 노원구 노원로 564",
                new BigDecimal("37.123"), new BigDecimal("127.123"), 0, 100);
        Geofence centerGeofence = new Geofence(testUser, "센터", "서울시 노원구 노원로 18길 41",
                new BigDecimal("35.456"), new BigDecimal("129.456"), 0, 100);

        // Permanent geofence for userFenceIn test
        permanentGeofence = new Geofence(testUser, "Test Permanent", "Seoul Test Address",
                new BigDecimal("37.555"), new BigDecimal("127.555"), 0, 5);

        // Temporary geofence for userFenceIn test
        temporaryGeofence = new Geofence(testUser, "Test Temporary", "Seoul Temp Address",
                new BigDecimal("37.666"), new BigDecimal("127.666"), 1,
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), 0);

        // Setup bidirectional relationships BEFORE saving
        testUser.addGeofence(homeGeofence);
        testUser.addGeofence(centerGeofence);
        testUser.addGeofence(permanentGeofence);
        testUser.addGeofence(temporaryGeofence);

        // Save users (cascade will save geofences)
        testUser = userRepository.save(testUser);
        guardianUser = userRepository.save(guardianUser);
        entityManager.flush(); // ID 할당을 위해 즉시 DB 반영

        // Reload to get managed entities with IDs
        testUser = userRepository.findByNumberWithGeofences(testUser.getNumber());
        List<Geofence> geofences = testUser.getGeofences();
        permanentGeofence = geofences.stream()
                .filter(g -> "Test Permanent".equals(g.getName()))
                .findFirst()
                .orElseThrow();
        temporaryGeofence = geofences.stream()
                .filter(g -> "Test Temporary".equals(g.getName()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("사용자 번호로 지오펜스 목록 조회 API 테스트")
    void findGeofenceList() throws Exception {
        // when & then
        mockMvc.perform(post("/geofence/list")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].address").exists())
                .andExpect(jsonPath("$[0].latitude").exists())
                .andExpect(jsonPath("$[0].longitude").exists())
                .andDo(print());
    }

    @Test
    @DisplayName("userFenceIn - permanent geofence success")
    void userFenceIn_PermanentGeofence_Success() throws Exception {
        // given
        int initialMaxSequence = permanentGeofence.getMaxSequence();
        FenceInRequestDto requestDto = new FenceInRequestDto(permanentGeofence.getId());
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(post("/geofence/userFenceIn")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(content().string("사용자의 진입이 성공적으로 감지되었습니다."))
                .andDo(print());

        // Verify maxSequence decreased
        Geofence updated = geofenceRepository.findById(permanentGeofence.getId())
                .orElseThrow();
        assertThat(updated.getMaxSequence()).isEqualTo(initialMaxSequence - 1);
    }

    @Test
    @DisplayName("userFenceIn - temporary geofence deleted")
    void userFenceIn_TemporaryGeofence_Deleted() throws Exception {
        // given
        Long temporaryGeofenceId = temporaryGeofence.getId();
        FenceInRequestDto requestDto = new FenceInRequestDto(temporaryGeofenceId);
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(post("/geofence/userFenceIn")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(content().string("사용자의 진입이 성공적으로 감지되었습니다."))
                .andDo(print());

        entityManager.flush();
        entityManager.clear();

        // Verify temporary geofence was deleted
        assertThat(geofenceRepository.findById(temporaryGeofenceId)).isEmpty();
    }

    @Test
    @DisplayName("userFenceIn - multiple entries decrease sequence")
    void userFenceIn_MultipleEntries_DecreaseSequence() throws Exception {
        // given
        int initialMaxSequence = permanentGeofence.getMaxSequence();
        FenceInRequestDto requestDto = new FenceInRequestDto(permanentGeofence.getId());
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when - enter 3 times
        mockMvc.perform(post("/geofence/userFenceIn")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk());

        mockMvc.perform(post("/geofence/userFenceIn")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk());

        mockMvc.perform(post("/geofence/userFenceIn")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk());

        // then
        Geofence updated = geofenceRepository.findById(permanentGeofence.getId())
                .orElseThrow();
        assertThat(updated.getMaxSequence()).isEqualTo(initialMaxSequence - 3);
    }

    @Test
    @DisplayName("deleteFence - 지오펜스 삭제 성공")
    void deleteFence_Success() throws Exception {
        // given
        Long geofenceId = permanentGeofence.getId();
        GeofenceDeleteRequestDto requestDto = new GeofenceDeleteRequestDto(geofenceId, null);
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(delete("/geofence/deleteFence")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(content().string("지오펜스가 성공적으로 삭제되었습니다."))
                .andDo(print());

        entityManager.flush();
        entityManager.clear();

        // Verify geofence was deleted from database
        assertThat(geofenceRepository.findById(geofenceId)).isEmpty();
    }

    @Test
    @DisplayName("deleteFence - 삭제 후 데이터베이스에서 실제로 제거 확인")
    void deleteFence_VerifyDatabaseDeletion() throws Exception {
        // given
        Long geofenceId = temporaryGeofence.getId();
        GeofenceDeleteRequestDto requestDto = new GeofenceDeleteRequestDto(geofenceId, null);
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when
        mockMvc.perform(delete("/geofence/deleteFence")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        // then - verify deleted from database
        assertThat(geofenceRepository.findById(geofenceId)).isEmpty();
        assertThat(geofenceRepository.findAll()).hasSize(3); // 4개 중 1개 삭제
    }

    @Test
    @DisplayName("deleteFence - API Key 없이 요청 시 401 에러")
    void deleteFence_NoApiKey_Unauthorized() throws Exception {
        // given
        GeofenceDeleteRequestDto requestDto = new GeofenceDeleteRequestDto(permanentGeofence.getId(), null);
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(delete("/geofence/deleteFence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("deleteFence - 잘못된 API Key로 요청 시 401 에러")
    void deleteFence_InvalidApiKey_Unauthorized() throws Exception {
        // given
        GeofenceDeleteRequestDto requestDto = new GeofenceDeleteRequestDto(permanentGeofence.getId(), null);
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(delete("/geofence/deleteFence")
                        .header("X-API-Key", "invalid-api-key-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("deleteFence - 여러 지오펜스 순차 삭제")
    void deleteFence_MultipleDeletes() throws Exception {
        // given
        Long id1 = permanentGeofence.getId();
        Long id2 = temporaryGeofence.getId();

        // when - delete first geofence
        mockMvc.perform(delete("/geofence/deleteFence")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GeofenceDeleteRequestDto(id1, null))))
                .andExpect(status().isOk());

        // when - delete second geofence
        mockMvc.perform(delete("/geofence/deleteFence")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GeofenceDeleteRequestDto(id2, null))))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        // then - verify both deleted
        assertThat(geofenceRepository.findById(id1)).isEmpty();
        assertThat(geofenceRepository.findById(id2)).isEmpty();
        assertThat(geofenceRepository.findAll()).hasSize(2); // 4개 중 2개 삭제
    }

    // ============= 보호자 기능 테스트 =============

    @Test
    @DisplayName("findGeofenceList - 보호자가 NumberRequestDto로 피보호자의 지오펜스 조회")
    void findGeofenceList_Guardian_WithNumberRequestDto() throws Exception {
        // given
        com.project.safetyFence.mypage.dto.NumberRequestDto requestDto =
                new com.project.safetyFence.mypage.dto.NumberRequestDto(testUser.getNumber());
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(post("/geofence/list")
                        .header("X-API-Key", guardianApiKey) // 보호자 API Key 사용
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(4)) // testUser의 4개 지오펜스
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].address").exists())
                .andDo(print());
    }

    @Test
    @DisplayName("findGeofenceList - NumberRequestDto 없이 본인 지오펜스 조회")
    void findGeofenceList_WithoutNumberRequestDto_Self() throws Exception {
        // when & then - NumberRequestDto 없이 요청하면 본인 지오펜스 조회
        mockMvc.perform(post("/geofence/list")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(4))
                .andDo(print());
    }

    @Test
    @DisplayName("findGeofenceList - 보호자가 자신의 지오펜스 조회 (지오펜스 없음)")
    void findGeofenceList_Guardian_NoGeofences() throws Exception {
        // when & then - guardianUser는 지오펜스가 없음
        mockMvc.perform(post("/geofence/list")
                        .header("X-API-Key", guardianApiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0))
                .andDo(print());
    }

    @Test
    @DisplayName("deleteFence - 보호자가 NumberRequestDto로 피보호자의 지오펜스 삭제")
    void deleteFence_Guardian_WithNumberRequestDto() throws Exception {
        // given
        Long geofenceId = permanentGeofence.getId();
        GeofenceDeleteRequestDto requestDto = new GeofenceDeleteRequestDto(
                geofenceId,
                testUser.getNumber() // 피보호자 번호
        );
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(delete("/geofence/deleteFence")
                        .header("X-API-Key", guardianApiKey) // 보호자 API Key 사용
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(content().string("지오펜스가 성공적으로 삭제되었습니다."))
                .andDo(print());

        entityManager.flush();
        entityManager.clear();

        // Verify geofence was deleted
        assertThat(geofenceRepository.findById(geofenceId)).isEmpty();
    }
}