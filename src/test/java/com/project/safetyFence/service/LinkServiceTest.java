package com.project.safetyFence.service;

import com.project.safetyFence.common.exception.CustomException;
import com.project.safetyFence.common.exception.ErrorResult;
import com.project.safetyFence.link.LinkService;
import com.project.safetyFence.link.LinkRepository;
import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.link.dto.LinkRequestDto;
import com.project.safetyFence.link.dto.LinkResponseDto;
import com.project.safetyFence.link.dto.SupporterResponseDto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LinkServiceTest {

    @Autowired
    private LinkService linkService;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User supporter;
    private User ward;

    private static final String SUPPORTER_NUMBER = "01011111111";
    private static final String WARD_NUMBER = "01022222222";

    @BeforeEach
    void setUp() {
        supporter = new User(SUPPORTER_NUMBER, "보호자", "111", LocalDate.of(1970, 1, 1), "sup-link");
        ward = new User(WARD_NUMBER, "피보호자", "111", LocalDate.of(1950, 1, 1), "ward-link");
        userRepository.save(supporter);
        userRepository.save(ward);
        entityManager.flush();
    }

    @Test
    @DisplayName("addLinkUser - 링크 코드로 사용자 추가 성공")
    void addLinkUser_Success() {
        // given
        LinkRequestDto dto = new LinkRequestDto(ward.getLinkCode(), "자녀");

        // when
        linkService.addLinkUser(SUPPORTER_NUMBER, dto);
        entityManager.flush();
        entityManager.clear();

        // then
        User updatedSupporter = userRepository.findByNumberWithLinks(SUPPORTER_NUMBER);
        assertThat(updatedSupporter.getLinks()).hasSize(1);
        assertThat(updatedSupporter.getLinks().get(0).getUserNumber()).isEqualTo(WARD_NUMBER);
        assertThat(updatedSupporter.getLinks().get(0).getRelation()).isEqualTo("자녀");
    }

    @Test
    @DisplayName("addLinkUser - 존재하지 않는 링크 코드 예외")
    void addLinkUser_InvalidLinkCode_ThrowsException() {
        // given
        LinkRequestDto dto = new LinkRequestDto("invalid-code", "자녀");

        // when & then
        assertThatThrownBy(() -> linkService.addLinkUser(SUPPORTER_NUMBER, dto))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorResult", ErrorResult.LINK_CODE_NOT_EXIST);
    }

    @Test
    @DisplayName("addLinkUser - 자기 자신 추가 방지")
    void addLinkUser_SelfLink_ThrowsException() {
        // given
        LinkRequestDto dto = new LinkRequestDto(supporter.getLinkCode(), "본인");

        // when & then
        assertThatThrownBy(() -> linkService.addLinkUser(SUPPORTER_NUMBER, dto))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorResult", ErrorResult.CANNOT_ADD_SELF_AS_LINK);
    }

    @Test
    @DisplayName("addLinkUser - 중복 추가 방지")
    void addLinkUser_DuplicateLink_ThrowsException() {
        // given
        LinkRequestDto dto = new LinkRequestDto(ward.getLinkCode(), "자녀");
        linkService.addLinkUser(SUPPORTER_NUMBER, dto);
        entityManager.flush();

        // when & then
        assertThatThrownBy(() -> linkService.addLinkUser(SUPPORTER_NUMBER, dto))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorResult", ErrorResult.LINK_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("getUserLink - 링크 목록 조회 성공")
    void getUserLink_Success() {
        // given
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();
        entityManager.clear();

        // when
        List<LinkResponseDto> result = linkService.getUserLink(SUPPORTER_NUMBER);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserNumber()).isEqualTo(WARD_NUMBER);
    }

    @Test
    @DisplayName("getUserLink - 링크 없을 때 빈 리스트 반환")
    void getUserLink_NoLinks_ReturnsEmptyList() {
        // when
        List<LinkResponseDto> result = linkService.getUserLink(SUPPORTER_NUMBER);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteLinkUser - 링크 삭제 성공")
    void deleteLinkUser_Success() {
        // given
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();
        entityManager.clear();

        // when
        linkService.deleteLinkUser(SUPPORTER_NUMBER, WARD_NUMBER);
        entityManager.flush();
        entityManager.clear();

        // then
        User updatedSupporter = userRepository.findByNumberWithLinks(SUPPORTER_NUMBER);
        assertThat(updatedSupporter.getLinks()).isEmpty();
    }

    @Test
    @DisplayName("deleteLinkUser - 존재하지 않는 링크 삭제 시 예외")
    void deleteLinkUser_NotFound_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> linkService.deleteLinkUser(SUPPORTER_NUMBER, "01099999999"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorResult", ErrorResult.LINK_NOT_FOUND);
    }

    @Test
    @DisplayName("hasLink - 링크 존재 true 반환")
    void hasLink_ExistingLink_ReturnsTrue() {
        // given
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();

        // when
        boolean result = linkService.hasLink(SUPPORTER_NUMBER, WARD_NUMBER);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasLink - 링크 미존재 false 반환")
    void hasLink_NoLink_ReturnsFalse() {
        // when
        boolean result = linkService.hasLink(SUPPORTER_NUMBER, WARD_NUMBER);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getMySupporters - 나를 구독하는 보호자 목록 조회")
    void getMySupporters_Success() {
        // given - supporter가 ward를 구독
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();
        entityManager.clear();

        // when - ward 입장에서 보호자 조회
        List<SupporterResponseDto> result = linkService.getMySupporters(WARD_NUMBER);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSupporterNumber()).isEqualTo(SUPPORTER_NUMBER);
        assertThat(result.get(0).getSupporterName()).isEqualTo("보호자");
        assertThat(result.get(0).getIsPrimary()).isFalse();
    }

    @Test
    @DisplayName("getMySupporters - 보호자가 없을 때 빈 리스트 반환")
    void getMySupporters_NoSupporters_ReturnsEmptyList() {
        // when
        List<SupporterResponseDto> result = linkService.getMySupporters(WARD_NUMBER);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("setPrimarySupporter - 대표 보호자 설정 성공")
    void setPrimarySupporter_Success() {
        // given
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();
        entityManager.clear();

        Link savedLink = linkRepository.findByUserNumber(WARD_NUMBER).get(0);

        // when
        linkService.setPrimarySupporter(WARD_NUMBER, savedLink.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        Link updatedLink = linkRepository.findById(savedLink.getId()).orElseThrow();
        assertThat(updatedLink.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("setPrimarySupporter - 기존 대표 보호자 해제 후 새로운 대표 설정")
    void setPrimarySupporter_ReplacesExisting() {
        // given - 2명의 보호자
        User supporter2 = new User("01033333333", "보호자2", "111", LocalDate.of(1975, 1, 1), "sup2-link");
        userRepository.save(supporter2);

        Link link1 = new Link(supporter, WARD_NUMBER, "자녀");
        Link link2 = new Link(supporter2, WARD_NUMBER, "배우자");
        supporter.addLink(link1);
        supporter2.addLink(link2);
        userRepository.save(supporter);
        userRepository.save(supporter2);
        entityManager.flush();
        entityManager.clear();

        List<Link> links = linkRepository.findByUserNumber(WARD_NUMBER);
        Link firstLink = links.get(0);
        Link secondLink = links.get(1);

        // 첫 번째를 대표로 설정
        linkService.setPrimarySupporter(WARD_NUMBER, firstLink.getId());
        entityManager.flush();
        entityManager.clear();

        // when - 두 번째를 대표로 변경
        linkService.setPrimarySupporter(WARD_NUMBER, secondLink.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        Link updatedFirst = linkRepository.findById(firstLink.getId()).orElseThrow();
        Link updatedSecond = linkRepository.findById(secondLink.getId()).orElseThrow();
        assertThat(updatedFirst.getIsPrimary()).isFalse();
        assertThat(updatedSecond.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("setPrimarySupporter - 권한 없는 사용자 예외")
    void setPrimarySupporter_UnauthorizedAccess_ThrowsException() {
        // given
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();
        entityManager.clear();

        Link savedLink = linkRepository.findByUserNumber(WARD_NUMBER).get(0);

        // when & then - 다른 사용자가 대표 보호자 설정 시도
        assertThatThrownBy(() -> linkService.setPrimarySupporter("01099999999", savedLink.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorResult", ErrorResult.UNAUTHORIZED_ACCESS);
    }

    @Test
    @DisplayName("setPrimarySupporter - 존재하지 않는 링크 예외")
    void setPrimarySupporter_LinkNotFound_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> linkService.setPrimarySupporter(WARD_NUMBER, 999999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorResult", ErrorResult.LINK_NOT_FOUND);
    }

    @Test
    @DisplayName("getPrimarySupporter - 대표 보호자 조회 성공")
    void getPrimarySupporter_Success() {
        // given
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();
        entityManager.clear();

        Link savedLink = linkRepository.findByUserNumber(WARD_NUMBER).get(0);
        linkService.setPrimarySupporter(WARD_NUMBER, savedLink.getId());
        entityManager.flush();
        entityManager.clear();

        // when
        SupporterResponseDto result = linkService.getPrimarySupporter(WARD_NUMBER);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSupporterNumber()).isEqualTo(SUPPORTER_NUMBER);
        assertThat(result.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("getPrimarySupporter - 대표 보호자 미설정 시 예외")
    void getPrimarySupporter_NotSet_ThrowsException() {
        // given - 보호자는 있지만 대표 미설정
        Link link = new Link(supporter, WARD_NUMBER, "자녀");
        supporter.addLink(link);
        userRepository.save(supporter);
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> linkService.getPrimarySupporter(WARD_NUMBER))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorResult", ErrorResult.PRIMARY_SUPPORTER_NOT_FOUND);
    }
}
