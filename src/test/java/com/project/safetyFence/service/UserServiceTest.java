package com.project.safetyFence.service;

import com.project.safetyFence.user.UserService;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;

    private static final String TEST_NUMBER = "01012345678";

    @BeforeEach
    void setUp() {
        testUser = new User(TEST_NUMBER, "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
        userRepository.save(testUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("checkExistNumber - 존재하는 번호 true 반환")
    void checkExistNumber_ExistingNumber_ReturnsTrue() {
        // when
        boolean result = userService.checkExistNumber(TEST_NUMBER);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("checkExistNumber - 존재하지 않는 번호 false 반환")
    void checkExistNumber_NonExistingNumber_ReturnsFalse() {
        // when
        boolean result = userService.checkExistNumber("01099999999");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("findByNumber - 존재하는 번호로 사용자 조회 성공")
    void findByNumber_ExistingNumber_ReturnsUser() {
        // when
        User found = userService.findByNumber(TEST_NUMBER);

        // then
        assertThat(found).isNotNull();
        assertThat(found.getNumber()).isEqualTo(TEST_NUMBER);
        assertThat(found.getName()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("findByNumber - 존재하지 않는 번호로 조회 시 null 반환")
    void findByNumber_NonExistingNumber_ReturnsNull() {
        // when
        User found = userService.findByNumber("01099999999");

        // then
        assertThat(found).isNull();
    }

    @Test
    @DisplayName("checkDuplicateNumber - 중복 번호 true 반환")
    void checkDuplicateNumber_DuplicateNumber_ReturnsTrue() {
        // when
        boolean result = userService.checkDuplicateNumber(TEST_NUMBER);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("checkDuplicateNumber - 미중복 번호 false 반환")
    void checkDuplicateNumber_UniqueNumber_ReturnsFalse() {
        // when
        boolean result = userService.checkDuplicateNumber("01099999999");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("generateAndSaveApiKey - API 키 생성 및 저장 성공")
    void generateAndSaveApiKey_Success() {
        // when
        String apiKey = userService.generateAndSaveApiKey(TEST_NUMBER);

        // then
        assertThat(apiKey).isNotNull();
        assertThat(apiKey).isNotEmpty();
        assertThat(apiKey).hasSize(32); // UUID에서 '-' 제거한 길이

        // DB에 저장 확인
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        assertThat(updatedUser.getApiKey()).isEqualTo(apiKey);
    }

    @Test
    @DisplayName("generateAndSaveApiKey - 재호출 시 새로운 API 키 생성")
    void generateAndSaveApiKey_RegenCreatesNewKey() {
        // given
        String firstApiKey = userService.generateAndSaveApiKey(TEST_NUMBER);

        // when
        String secondApiKey = userService.generateAndSaveApiKey(TEST_NUMBER);

        // then
        assertThat(secondApiKey).isNotEqualTo(firstApiKey);
    }

    @Test
    @DisplayName("findUserNumberByApiKey - 유효한 API 키로 사용자 번호 반환")
    void findUserNumberByApiKey_ValidKey_ReturnsUserNumber() {
        // given
        String apiKey = userService.generateAndSaveApiKey(TEST_NUMBER);

        // when
        String result = userService.findUserNumberByApiKey(apiKey);

        // then
        assertThat(result).isEqualTo(TEST_NUMBER);
    }

    @Test
    @DisplayName("findUserNumberByApiKey - 유효하지 않은 API 키 null 반환")
    void findUserNumberByApiKey_InvalidKey_ReturnsNull() {
        // when
        String result = userService.findUserNumberByApiKey("invalid-api-key");

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("deleteOwnAccount - 올바른 비밀번호로 계정 삭제 성공")
    void deleteOwnAccount_CorrectPassword_DeletesAccount() {
        // when
        userService.deleteOwnAccount(TEST_NUMBER, "111");
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(userRepository.existsByNumber(TEST_NUMBER)).isFalse();
    }

    @Test
    @DisplayName("deleteOwnAccount - 잘못된 비밀번호로 계정 삭제 실패")
    void deleteOwnAccount_WrongPassword_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> userService.deleteOwnAccount(TEST_NUMBER, "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다");
    }

    @Test
    @DisplayName("deleteOwnAccount - 존재하지 않는 사용자 삭제 시도 시 예외")
    void deleteOwnAccount_NonExistingUser_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> userService.deleteOwnAccount("01099999999", "111"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자를 찾을 수 없습니다");
    }
}
