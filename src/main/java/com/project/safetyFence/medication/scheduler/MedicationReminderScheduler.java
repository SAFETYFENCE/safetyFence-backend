package com.project.safetyFence.medication.scheduler;

import com.project.safetyFence.medication.MedicationRepository;
import com.project.safetyFence.notification.NotificationService;
import com.project.safetyFence.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 약 복용 알림 스케줄러
 * 하루 3번 (아침 8시, 점심 12시, 저녁 7시) 약이 등록된 사용자에게 알림 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationReminderScheduler {

    private final MedicationRepository medicationRepository;
    private final NotificationService notificationService;

    /**
     * 아침 8시 약 복용 알림
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendMorningReminder() {
        log.info("🌅 아침 약 복용 알림 스케줄러 시작");
        sendMedicationReminders("아침");
    }

    /**
     * 점심 12시 약 복용 알림
     */
    @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendNoonReminder() {
        log.info("☀️ 점심 약 복용 알림 스케줄러 시작");
        sendMedicationReminders("점심");
    }

    /**
     * 저녁 7시 약 복용 알림
     */
    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendEveningReminder() {
        log.info("🌙 저녁 약 복용 알림 스케줄러 시작");
        sendMedicationReminders("저녁");
    }

    /**
     * 약이 등록된 모든 사용자에게 알림 전송
     * @param reminderTime 알림 시간대 (아침/점심/저녁)
     */
    private void sendMedicationReminders(String reminderTime) {
        List<User> usersWithMedications = medicationRepository.findAllUsersWithMedications();

        if (usersWithMedications.isEmpty()) {
            log.info("ℹ️ 약이 등록된 사용자가 없습니다.");
            return;
        }

        log.info("📢 {} 약 복용 알림 전송 시작: {}명의 사용자", reminderTime, usersWithMedications.size());

        int successCount = 0;
        for (User user : usersWithMedications) {
            try {
                notificationService.sendMedicationReminderNotification(user, reminderTime);
                successCount++;
            } catch (Exception e) {
                log.error("❌ 약 복용 알림 전송 실패: userNumber={}, error={}",
                        user.getNumber(), e.getMessage());
            }
        }

        log.info("✅ {} 약 복용 알림 전송 완료: {}/{}명 성공",
                reminderTime, successCount, usersWithMedications.size());
    }
}
