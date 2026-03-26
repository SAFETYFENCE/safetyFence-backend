package com.project.safetyFence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.safetyFence.location.dto.BatteryUpdateDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.user.domain.UserAddress;

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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BatteryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private String testApiKey;

    @BeforeEach
    void setUp() {
        User testUser = new User("01012345678", "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
        testApiKey = "test-api-key-battery-controller-test1234";
        testUser.updateApiKey(testApiKey);

        UserAddress address = new UserAddress(testUser, "06134", null, "서울시 강남구", "101동", null);
        testUser.addUserAddress(address);

        userRepository.save(testUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("updateBattery - 유효한 배터리 레벨 업데이트 성공")
    void updateBattery_ValidLevel_ReturnsOk() throws Exception {
        // given
        BatteryUpdateDto dto = new BatteryUpdateDto(85);

        // when & then
        mockMvc.perform(post("/battery")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateBattery - 배터리 레벨 0% 경계값 성공")
    void updateBattery_ZeroLevel_ReturnsOk() throws Exception {
        // given
        BatteryUpdateDto dto = new BatteryUpdateDto(0);

        // when & then
        mockMvc.perform(post("/battery")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateBattery - 배터리 레벨 100% 경계값 성공")
    void updateBattery_FullLevel_ReturnsOk() throws Exception {
        // given
        BatteryUpdateDto dto = new BatteryUpdateDto(100);

        // when & then
        mockMvc.perform(post("/battery")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateBattery - 배터리 레벨 음수 시 400 반환")
    void updateBattery_NegativeLevel_ReturnsBadRequest() throws Exception {
        // given
        BatteryUpdateDto dto = new BatteryUpdateDto(-1);

        // when & then
        mockMvc.perform(post("/battery")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updateBattery - 배터리 레벨 101 이상 시 400 반환")
    void updateBattery_OverMaxLevel_ReturnsBadRequest() throws Exception {
        // given
        BatteryUpdateDto dto = new BatteryUpdateDto(101);

        // when & then
        mockMvc.perform(post("/battery")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updateBattery - 배터리 레벨 null 시 400 반환")
    void updateBattery_NullLevel_ReturnsBadRequest() throws Exception {
        // given
        BatteryUpdateDto dto = new BatteryUpdateDto(null, null, null);

        // when & then
        mockMvc.perform(post("/battery")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updateBattery - API 키 없이 요청 시 401 반환")
    void updateBattery_WithoutApiKey_ReturnsUnauthorized() throws Exception {
        // given
        BatteryUpdateDto dto = new BatteryUpdateDto(50);

        // when & then
        mockMvc.perform(post("/battery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }
}
