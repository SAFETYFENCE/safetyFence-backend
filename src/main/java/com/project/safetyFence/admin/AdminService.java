package com.project.safetyFence.admin;

import com.project.safetyFence.admin.dto.AdminLinkResponseDto;
import com.project.safetyFence.admin.dto.StatisticsResponseDto;
import com.project.safetyFence.admin.dto.UserResponseDto;
import com.project.safetyFence.calendar.UserEventRepository;
import com.project.safetyFence.geofence.GeofenceRepository;
import com.project.safetyFence.link.LinkRepository;
import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.log.LogRepository;
import com.project.safetyFence.medication.MedicationRepository;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final LinkRepository linkRepository;
    private final GeofenceRepository geofenceRepository;
    private final MedicationRepository medicationRepository;
    private final UserEventRepository userEventRepository;
    private final LogRepository logRepository;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 8;

    /**
     * 전체 사용자 목록 조회
     */
    public List<UserResponseDto> getAllUsers() {
        List<User> users = userRepository.findAll();

        return users.stream()
                .map(user -> {
                    // 각 사용자의 통계 정보 계산
                    int geofenceCount = user.getGeofences().size();
                    int medicationCount = user.getMedications().size();
                    int eventCount = user.getUserEvents().size();

                    // 연결된 보호자/피보호자 수 계산
                    int linkedGuardiansCount = linkRepository.findByUserNumber(user.getNumber()).size();
                    int linkedWardsCount = user.getLinks().size();

                    return new UserResponseDto(
                            user.getNumber(),
                            user.getName(),
                            user.getBirth(),
                            user.getLinkCode(),
                            geofenceCount,
                            medicationCount,
                            eventCount,
                            linkedGuardiansCount,
                            linkedWardsCount
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 사용자 삭제 (연관된 모든 데이터 cascade 삭제)
     */
    @Transactional
    public void deleteUser(String userNumber) {
        User user = userRepository.findByNumber(userNumber);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userNumber);
        }

        log.info("🗑️ 사용자 삭제 시작: {} ({})", user.getName(), userNumber);

        // JPA cascade 설정으로 자동 삭제:
        // - UserAddress (OneToOne, cascade ALL)
        // - UserLocation (OneToMany, cascade ALL)
        // - Log (OneToMany, cascade ALL)
        // - Link (OneToMany, cascade ALL)
        // - Geofence (OneToMany, cascade ALL)
        // - UserEvent (OneToMany, cascade ALL)
        // - Medication (OneToMany, cascade ALL)

        userRepository.delete(user);
        log.info("✅ 사용자 및 연관 데이터 삭제 완료: {}", userNumber);
    }

    /**
     * 비밀번호 초기화 (랜덤 8자리 생성)
     */
    @Transactional
    public String resetPassword(String userNumber) {
        User user = userRepository.findByNumber(userNumber);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userNumber);
        }

        String newPassword = generateRandomPassword();
        user.updatePassword(newPassword);

        log.info("🔑 비밀번호 초기화: {} ({})", user.getName(), userNumber);
        return newPassword;
    }

    /**
     * 전체 링크 목록 조회
     */
    public List<AdminLinkResponseDto> getAllLinks() {
        List<Link> links = linkRepository.findAll();

        return links.stream()
                .map(link -> {
                    User ward = link.getUser();  // 피보호자
                    User guardian = userRepository.findByNumber(link.getUserNumber());  // 보호자

                    return new AdminLinkResponseDto(
                            link.getId(),
                            ward.getNumber(),
                            ward.getName(),
                            guardian != null ? guardian.getNumber() : link.getUserNumber(),
                            guardian != null ? guardian.getName() : "알 수 없음",
                            link.getRelation(),
                            link.getIsPrimary()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 링크 삭제
     */
    @Transactional
    public void deleteLink(Long linkId) {
        Link link = linkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("링크를 찾을 수 없습니다: " + linkId));

        log.info("🗑️ 링크 삭제: {} - {}", link.getUser().getName(), link.getUserNumber());
        linkRepository.delete(link);
    }

    /**
     * 시스템 통계 조회
     */
    public StatisticsResponseDto getStatistics() {
        long totalUsers = userRepository.count();
        long totalLinks = linkRepository.count();
        long totalGeofences = geofenceRepository.count();

        // 현재 활성 중인 임시 지오펜스 수 (type=1 && endTime > now)
        long activeGeofences = geofenceRepository.findByTypeAndEndTimeBefore(1, LocalDateTime.now()).size();

        long totalMedications = medicationRepository.count();
        long totalEvents = userEventRepository.count();
        long totalLogs = logRepository.count();

        return new StatisticsResponseDto(
                totalUsers,
                totalLinks,
                totalGeofences,
                activeGeofences,
                totalMedications,
                totalEvents,
                totalLogs
        );
    }

    /**
     * 만료된 지오펜스 삭제 (type=1 && endTime < now)
     */
    @Transactional
    public int deleteExpiredGeofences() {
        // type=1: 임시 지오펜스, endTime이 현재 시간보다 이전인 것들
        List<com.project.safetyFence.geofence.domain.Geofence> expiredGeofences =
                geofenceRepository.findByTypeAndEndTimeBefore(1, LocalDateTime.now());

        int count = expiredGeofences.size();
        geofenceRepository.deleteAll(expiredGeofences);

        log.info("🧹 만료된 지오펜스 삭제 완료: {}개", count);
        return count;
    }

    /**
     * 오래된 로그 정리 (monthsOld개월 이전 로그 삭제)
     */
    @Transactional
    public int deleteOldLogs(int monthsOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(monthsOld);

        List<com.project.safetyFence.log.domain.Log> allLogs = logRepository.findAll();
        List<com.project.safetyFence.log.domain.Log> oldLogs = allLogs.stream()
                .filter(log -> log.getArriveTime().isBefore(cutoffDate))
                .collect(Collectors.toList());

        int count = oldLogs.size();
        logRepository.deleteAll(oldLogs);

        log.info("🧹 오래된 로그 정리 완료: {}개월 이전 {}개 삭제", monthsOld, count);
        return count;
    }

    /**
     * 랜덤 비밀번호 생성 (8자리, 영문 대소문자 + 숫자)
     */
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = random.nextInt(CHARACTERS.length());
            password.append(CHARACTERS.charAt(index));
        }

        return password.toString();
    }
}
