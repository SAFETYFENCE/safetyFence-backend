package com.project.safetyFence.service;

import com.project.safetyFence.mypage.MyPageService;
import com.project.safetyFence.mypage.dto.CenterAddressUpdateRequestDto;
import com.project.safetyFence.mypage.dto.HomeAddressUpdateRequestDto;
import com.project.safetyFence.user.dto.PasswordUpdateRequestDto;
import com.project.safetyFence.user.dto.UserDataResponseDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.user.domain.UserAddress;

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
class MyPageServiceTest {

    @Autowired
    private MyPageService myPageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;

    private static final String TEST_NUMBER = "01012345678";

    @BeforeEach
    void setUp() {
        testUser = new User(TEST_NUMBER, "테스터", "111", LocalDate.of(1990, 5, 15), "test-link");

        UserAddress userAddress = new UserAddress(
                testUser,
                "06134",
                "48099",
                "서울시 강남구 테헤란로",
                "101동 1001호",
                "부산시 해운대구 센텀로"
        );
        testUser.addUserAddress(userAddress);

        userRepository.save(testUser);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("getUserData - 사용자 데이터 조회 성공")
    void getUserData_Success() {
        // when
        UserDataResponseDto result = myPageService.getUserData(TEST_NUMBER);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("테스터");
        assertThat(result.getBirth()).isEqualTo("1990-05-15");
        assertThat(result.getLinkCode()).isEqualTo("test-link");
        assertThat(result.getHomeAddress()).contains("서울시 강남구 테헤란로");
        assertThat(result.getCenterAddress()).isEqualTo("부산시 해운대구 센텀로");
    }

    @Test
    @DisplayName("updatePassword - 비밀번호 변경 성공")
    void updatePassword_Success() {
        // given
        PasswordUpdateRequestDto dto = new PasswordUpdateRequestDto("111", "222");

        // when
        myPageService.updatePassword(TEST_NUMBER, dto);
        entityManager.flush();
        entityManager.clear();

        // then
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        assertThat(updatedUser.getPassword()).isEqualTo("222");
    }

    @Test
    @DisplayName("updatePassword - 현재 비밀번호 불일치 시 예외")
    void updatePassword_WrongCurrentPassword_ThrowsException() {
        // given
        PasswordUpdateRequestDto dto = new PasswordUpdateRequestDto("wrong", "222");

        // when & then
        assertThatThrownBy(() -> myPageService.updatePassword(TEST_NUMBER, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("updateHomeAddress - 집주소 변경 성공")
    void updateHomeAddress_Success() {
        // given
        HomeAddressUpdateRequestDto dto = new HomeAddressUpdateRequestDto(
                "04524",
                "서울시 중구 세종대로",
                "시청 앞"
        );

        // when
        myPageService.updateHomeAddress(TEST_NUMBER, dto);
        entityManager.flush();
        entityManager.clear();

        // then
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        UserAddress updatedAddress = updatedUser.getUserAddress();
        assertThat(updatedAddress.getHomeAddress()).isEqualTo("04524");
        assertThat(updatedAddress.getHomeStreetAddress()).isEqualTo("서울시 중구 세종대로");
        assertThat(updatedAddress.getHomeStreetAddressDetail()).isEqualTo("시청 앞");
    }

    @Test
    @DisplayName("updateCenterAddress - 센터주소 변경 성공")
    void updateCenterAddress_Success() {
        // given
        CenterAddressUpdateRequestDto dto = new CenterAddressUpdateRequestDto(
                "12345",
                "경기도 성남시 분당구"
        );

        // when
        myPageService.updateCenterAddress(TEST_NUMBER, dto);
        entityManager.flush();
        entityManager.clear();

        // then
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        UserAddress updatedAddress = updatedUser.getUserAddress();
        assertThat(updatedAddress.getCenterAddress()).isEqualTo("12345");
        assertThat(updatedAddress.getCenterStreetAddress()).isEqualTo("경기도 성남시 분당구");
    }

    @Test
    @DisplayName("updateHomeAddress - 주소 정보 없는 사용자 예외")
    void updateHomeAddress_NoAddress_ThrowsException() {
        // given - 주소 없는 사용자
        User noAddressUser = new User("01099999999", "주소없음", "111", LocalDate.of(1990, 1, 1), "no-addr");
        userRepository.save(noAddressUser);
        entityManager.flush();

        HomeAddressUpdateRequestDto dto = new HomeAddressUpdateRequestDto("04524", "서울시", "상세");

        // when & then
        assertThatThrownBy(() -> myPageService.updateHomeAddress("01099999999", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 주소 정보가 없습니다.");
    }

    @Test
    @DisplayName("updateCenterAddress - 주소 정보 없는 사용자 예외")
    void updateCenterAddress_NoAddress_ThrowsException() {
        // given
        User noAddressUser = new User("01099999999", "주소없음", "111", LocalDate.of(1990, 1, 1), "no-addr");
        userRepository.save(noAddressUser);
        entityManager.flush();

        CenterAddressUpdateRequestDto dto = new CenterAddressUpdateRequestDto("12345", "경기도");

        // when & then
        assertThatThrownBy(() -> myPageService.updateCenterAddress("01099999999", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 주소 정보가 없습니다.");
    }
}
