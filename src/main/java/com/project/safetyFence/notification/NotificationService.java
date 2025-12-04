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
}
