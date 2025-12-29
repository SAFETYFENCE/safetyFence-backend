package com.project.safetyFence.notification;

import com.google.firebase.messaging.*;
import com.project.safetyFence.link.LinkRepository;
import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.notification.domain.DeviceToken;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final LinkRepository linkRepository;
    private final UserRepository userRepository;

    /**
     * 어르신을 구독하는 모든 보호자에게 알림 전송
     * @param elderUser 어르신 (이용자)
     * @param title 알림 제목
     * @param body 알림 내용
     */
    @Transactional(readOnly = true)
    public void sendNotificationToSupporters(User elderUser, String title, String body) {
        // 어르신 번호가 link.user_number(피보호자)인 링크들을 조회해 보호자 목록을 얻는다.
        List<Link> links = linkRepository.findByUserNumber(elderUser.getNumber());

        if (links.isEmpty()) {
            log.info("ℹ️ 보호자가 없어 알림 전송 생략: 어르신={}", elderUser.getNumber());
            return;
        }

        log.info("🔔 {} 명의 보호자에게 알림 전송 시작: 어르신={}", links.size(), elderUser.getNumber());

        for (Link link : links) {
            User supporter = link.getUser();
            sendNotificationToUser(supporter.getNumber(), title, body, elderUser.getNumber());
        }
    }

    /**
     * 특정 사용자에게 알림 전송
     */
    private void sendNotificationToUser(String userNumber, String title, String body, String elderNumber) {
        User user = userRepository.findByNumber(userNumber);

        if (user == null) {
            log.warn("⚠️ 사용자를 찾을 수 없음: userNumber={}", userNumber);
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUser(user);

        if (tokens.isEmpty()) {
            log.warn("⚠️ 디바이스 토큰이 없음: userNumber={}", userNumber);
            return;
        }

        for (DeviceToken deviceToken : tokens) {
            sendFCMNotification(deviceToken.getToken(), title, body, elderNumber);
        }
    }

    /**
     * FCM으로 알림 전송
     */
    private void sendFCMNotification(String token, String title, String body, String elderNumber) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("elderNumber", elderNumber)
                    .putData("type", "geofence")
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setChannelId("geofence_notifications")
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ FCM 알림 전송 성공: token={}, response={}",
                    token.substring(0, Math.min(20, token.length())) + "...", response);

        } catch (FirebaseMessagingException e) {
            log.error("❌ FCM 알림 전송 실패: token={}, error={}",
                    token.substring(0, Math.min(20, token.length())) + "...", e.getMessage());
        }
    }

    /**
     * 디바이스 토큰 저장 또는 업데이트
     */
    @Transactional
    public void saveOrUpdateToken(String userNumber, String token, String deviceType) {
        User user = userRepository.findByNumber(userNumber);

        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userNumber);
        }

        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
                .orElse(new DeviceToken(user, token, deviceType));

        if (deviceToken.getId() != null) {
            deviceToken.updateToken(token);
        }

        deviceTokenRepository.save(deviceToken);
        log.info("✅ 디바이스 토큰 저장: userNumber={}, deviceType={}", userNumber, deviceType);
    }

    /**
     * 디바이스 토큰 삭제 (로그아웃 시)
     */
    @Transactional
    public void deleteToken(String token) {
        deviceTokenRepository.deleteByToken(token);
        log.info("✅ 디바이스 토큰 삭제: token={}", token.substring(0, Math.min(20, token.length())) + "...");
    }

    /**
     * 긴급 알림 전송
     * 이용자의 모든 보호자에게 긴급 상황 알림 전송
     * @param elderUser 긴급 버튼을 누른 이용자
     */
    @Transactional(readOnly = true)
    public void sendEmergencyAlert(User elderUser) {
        String title = "🚨 긴급 알림";
        String body = String.format("%s님이 긴급 버튼을 클릭하셨어요. 확인 부탁드려요!", elderUser.getName());

        log.info("🚨 긴급 알림 전송 시작: 이용자={}, 이름={}", elderUser.getNumber(), elderUser.getName());

        // 긴급 알림용 전송 (type="emergency")
        sendEmergencyNotificationToSupporters(elderUser, title, body);

        log.info("✅ 긴급 알림 전송 완료: 이용자={}", elderUser.getNumber());
    }

    /**
     * 긴급 알림을 보호자들에게 전송 (type="emergency")
     */
    private void sendEmergencyNotificationToSupporters(User elderUser, String title, String body) {
        List<Link> links = linkRepository.findByUserNumber(elderUser.getNumber());

        if (links.isEmpty()) {
            log.info("ℹ️ 보호자가 없어 긴급 알림 전송 생략: 어르신={}", elderUser.getNumber());
            return;
        }

        log.info("🚨 {} 명의 보호자에게 긴급 알림 전송 시작: 어르신={}", links.size(), elderUser.getNumber());

        for (Link link : links) {
            User supporter = link.getUser();
            List<DeviceToken> tokens = deviceTokenRepository.findByUser(supporter);

            for (DeviceToken deviceToken : tokens) {
                sendEmergencyFCM(deviceToken.getToken(), title, body, elderUser.getNumber());
            }
        }
    }

    /**
     * 긴급 알림 FCM 전송 (type="emergency")
     */
    private void sendEmergencyFCM(String token, String title, String body, String elderNumber) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("elderNumber", elderNumber)
                    .putData("type", "emergency")  // ⭐ 긴급 알림 타입
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setChannelId("emergency_notifications")  // ⭐ 긴급 알림 채널
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ 긴급 FCM 알림 전송 성공: token={}, response={}",
                    token.substring(0, Math.min(20, token.length())) + "...", response);

        } catch (FirebaseMessagingException e) {
            log.error("❌ 긴급 FCM 알림 전송 실패: token={}, error={}",
                    token.substring(0, Math.min(20, token.length())) + "...", e.getMessage());
        }
    }

    /**
     * 약 추가 알림 전송
     * 보호자가 피보호자의 약을 추가했을 때 피보호자에게 알림 전송
     */
    @Transactional
    public void sendMedicationAddedNotification(String supporterNumber, String wardNumber, String medicationName) {
        // 권한 검증
        boolean isAuthorized = linkRepository.existsByUser_NumberAndUserNumber(supporterNumber, wardNumber);
        if (!isAuthorized) {
            throw new IllegalArgumentException("권한이 없습니다");
        }

        // 피보호자의 토큰 조회
        User ward = userRepository.findByNumber(wardNumber);
        if (ward == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUser(ward);
        if (tokens.isEmpty()) {
            log.info("알림 전송 스킵: 토큰 없음 - userNumber={}", wardNumber);
            return;
        }

        // FCM 메시지 전송
        String title = "약 추가 알림";
        String body = String.format("보호자님이 약 '%s'을(를) 추가했습니다", medicationName);

        for (DeviceToken deviceToken : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("type", "medication_added")
                        .putData("medicationName", medicationName)
                        .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                        .setAndroidConfig(AndroidConfig.builder()
                                .setNotification(AndroidNotification.builder()
                                        .setChannelId("ward_updates")
                                        .setPriority(AndroidNotification.Priority.DEFAULT)
                                        .build())
                                .build())
                        .setApnsConfig(ApnsConfig.builder()
                                .setAps(Aps.builder()
                                        .setSound("default")
                                        .setBadge(1)
                                        .build())
                                .build())
                        .build();

                FirebaseMessaging.getInstance().send(message);
                log.info("약 추가 알림 전송 성공: ward={}, medication={}", wardNumber, medicationName);

            } catch (FirebaseMessagingException e) {
                log.error("FCM 전송 실패: {}", e.getMessage());
                // 토큰 만료 시 삭제
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                        e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    deviceTokenRepository.deleteByToken(deviceToken.getToken());
                    log.info("만료된 토큰 삭제: {}", deviceToken.getToken());
                }
            }
        }
    }

    /**
     * 일정 추가 알림 전송
     * 보호자가 피보호자의 일정을 추가했을 때 피보호자에게 알림 전송
     */

    @Transactional
    public void sendEventAddedNotification(String supporterNumber, String wardNumber,
                                          String eventTitle, String eventDate, String eventTime) {
        // 권한 검증
        boolean isAuthorized = linkRepository.existsByUser_NumberAndUserNumber(supporterNumber, wardNumber);
        if (!isAuthorized) {
            throw new IllegalArgumentException("권한이 없습니다");
        }

        // 피보호자의 토큰 조회
        User ward = userRepository.findByNumber(wardNumber);
        if (ward == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUser(ward);
        if (tokens.isEmpty()) {
            log.info("알림 전송 스킵: 토큰 없음 - userNumber={}", wardNumber);
            return;
        }

        // FCM 메시지 전송
        String title = "일정 추가 알림";
        String body = String.format("보호자님이 일정 '%s'을(를) %s %s에 추가했습니다",
                                   eventTitle, eventDate, eventTime);

        for (DeviceToken deviceToken : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("type", "event_added")
                        .putData("eventTitle", eventTitle)
                        .putData("eventDate", eventDate)
                        .putData("eventTime", eventTime)
                        .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                        .setAndroidConfig(AndroidConfig.builder()
                                .setNotification(AndroidNotification.builder()
                                        .setChannelId("ward_updates")
                                        .setPriority(AndroidNotification.Priority.DEFAULT)
                                        .build())
                                .build())
                        .setApnsConfig(ApnsConfig.builder()
                                .setAps(Aps.builder()
                                        .setSound("default")
                                        .setBadge(1)
                                        .build())
                                .build())
                        .build();

                FirebaseMessaging.getInstance().send(message);
                log.info("일정 추가 알림 전송 성공: ward={}, event={}", wardNumber, eventTitle);

            } catch (FirebaseMessagingException e) {
                log.error("FCM 전송 실패: {}", e.getMessage());
                // 토큰 만료 시 삭제
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                        e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                    deviceTokenRepository.deleteByToken(deviceToken.getToken());
                    log.info("만료된 토큰 삭제: {}", deviceToken.getToken());
                }
            }
        }
    }
}
