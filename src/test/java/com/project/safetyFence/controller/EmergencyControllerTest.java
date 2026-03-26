package com.project.safetyFence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.safetyFence.notification.dto.EmergencyRequestDto;
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
class EmergencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private String testApiKey;

    private static final String TEST_NUMBER = "01012345678";

    @BeforeEach
    void setUp() {
        testUser = new User(TEST_NUMBER, "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
        testApiKey = "test-api-key-emergency-controller-test12";
        testUser.updateApiKey(testApiKey);

        UserAddress address = new UserAddress(testUser, "06134", null, "서울시 강남구", "101동", null);
        testUser.addUserAddress(address);

        userRepository.save(testUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("sendEmergencyAlert - 유효한 사용자 번호로 긴급 알림 전송")
    void sendEmergencyAlert_ValidUser_ReturnsOk() throws Exception {
        // given
        EmergencyRequestDto dto = new EmergencyRequestDto(TEST_NUMBER);

        // when & then
        mockMvc.perform(post("/notification/emergency")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sendEmergencyAlert - 존재하지 않는 사용자 번호로 요청 시 400 반환")
    void sendEmergencyAlert_InvalidUser_ReturnsBadRequest() throws Exception {
        // given
        EmergencyRequestDto dto = new EmergencyRequestDto("01099999999");

        // when & then
        mockMvc.perform(post("/notification/emergency")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sendEmergencyAlert - userNumber 없이 요청 시 400 반환")
    void sendEmergencyAlert_EmptyUserNumber_ReturnsBadRequest() throws Exception {
        // given
        EmergencyRequestDto dto = new EmergencyRequestDto("");

        // when & then
        mockMvc.perform(post("/notification/emergency")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sendEmergencyAlert - null userNumber 요청 시 400 반환")
    void sendEmergencyAlert_NullUserNumber_ReturnsBadRequest() throws Exception {
        // given
        EmergencyRequestDto dto = new EmergencyRequestDto(null);

        // when & then
        mockMvc.perform(post("/notification/emergency")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
