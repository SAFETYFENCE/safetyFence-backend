package com.project.safetyFence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.safetyFence.location.LocationCacheService;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.mypage.dto.NumberRequestDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.user.domain.UserAddress;
import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.location.UserLocationRepository;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLocationRepository userLocationRepository;

    @Autowired
    private LocationCacheService locationCacheService;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private String testApiKey;

    private static final String TEST_NUMBER = "01012345678";

    @BeforeEach
    void setUp() {
        testUser = new User(TEST_NUMBER, "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
        testApiKey = "test-api-key-location-controller-test1234";
        testUser.updateApiKey(testApiKey);

        UserAddress address = new UserAddress(testUser, "06134", null, "서울시 강남구", "101동", null);
        testUser.addUserAddress(address);

        userRepository.save(testUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("getDailyDistance - 이동거리 조회 성공")
    void getDailyDistance_Success() throws Exception {
        // when & then
        mockMvc.perform(post("/location/daily-distance")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userNumber").value(TEST_NUMBER))
                .andExpect(jsonPath("$.distanceMeters").isNumber());
    }

    @Test
    @DisplayName("getDailyDistance - 보호자가 이용자 번호로 조회")
    void getDailyDistance_WithTargetNumber_Success() throws Exception {
        // given
        NumberRequestDto dto = new NumberRequestDto(TEST_NUMBER);

        // when & then
        mockMvc.perform(post("/location/daily-distance")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userNumber").value(TEST_NUMBER));
    }

    @Test
    @DisplayName("getDailyDistanceByDate - 특정 날짜 이동거리 조회 성공")
    void getDailyDistanceByDate_Success() throws Exception {
        // when & then
        mockMvc.perform(post("/location/daily-distance/2024-12-25")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userNumber").value(TEST_NUMBER));
    }

    @Test
    @DisplayName("getDailyDistanceByDate - 잘못된 날짜 형식 400 반환")
    void getDailyDistanceByDate_InvalidDateFormat_ReturnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(post("/location/daily-distance/invalid-date")
                        .header("X-API-Key", testApiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("getLastLocation - 캐시에 위치 있을 때 조회 성공")
    void getLastLocation_WithCachedLocation_ReturnsOk() throws Exception {
        // given - 캐시에 위치 저장
        LocationUpdateDto locationDto = new LocationUpdateDto(TEST_NUMBER, 37.4979, 127.0276, System.currentTimeMillis());
        locationCacheService.updateLocation(TEST_NUMBER, locationDto);

        // when & then
        mockMvc.perform(get("/location/last/" + TEST_NUMBER)
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.4979))
                .andExpect(jsonPath("$.longitude").value(127.0276));
    }

    @Test
    @DisplayName("getLastLocation - 위치 없을 때 404 반환")
    void getLastLocation_NoLocation_ReturnsNotFound() throws Exception {
        // when & then
        mockMvc.perform(get("/location/last/01099999999")
                        .header("X-API-Key", testApiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API 키 없이 요청 시 401 반환")
    void request_WithoutApiKey_ReturnsUnauthorized() throws Exception {
        // when & then
        mockMvc.perform(post("/location/daily-distance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
