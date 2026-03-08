package com.project.safetyFence.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private String testApiKey;

    @BeforeEach
    void setUp() {
        User testUser = new User("01012345678", "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
        testApiKey = "test-api-key-log-controller-test12345678";
        testUser.updateApiKey(testApiKey);

        UserAddress address = new UserAddress(testUser, "06134", null, "서울시 강남구", "101동", null);
        testUser.addUserAddress(address);

        userRepository.save(testUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("getLogs - 로그 목록 조회 성공")
    void getLogs_ReturnsOk() throws Exception {
        // when & then
        mockMvc.perform(get("/logs")
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("getLogs - 로그 없을 때 빈 배열 반환")
    void getLogs_NoLogs_ReturnsEmptyArray() throws Exception {
        // when & then
        mockMvc.perform(get("/logs")
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("getLogs - API 키 없이 요청 시 401 반환")
    void getLogs_WithoutApiKey_ReturnsUnauthorized() throws Exception {
        // when & then
        mockMvc.perform(get("/logs"))
                .andExpect(status().isUnauthorized());
    }
}
