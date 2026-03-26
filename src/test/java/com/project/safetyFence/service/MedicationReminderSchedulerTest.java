package com.project.safetyFence.service;

import com.project.safetyFence.medication.MedicationRepository;
import com.project.safetyFence.medication.domain.Medication;
import com.project.safetyFence.medication.scheduler.MedicationReminderScheduler;
import com.project.safetyFence.notification.NotificationService;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicationReminderSchedulerTest {

    @InjectMocks
    private MedicationReminderScheduler scheduler;

    @Mock
    private MedicationRepository medicationRepository;

    @Mock
    private NotificationService notificationService;

    @Test
    @DisplayName("sendMorningReminder - 약이 등록된 사용자에게 아침 알림 전송")
    void sendMorningReminder_SendsToUsersWithMedications() {
        // given
        User user1 = new User("01011111111", "사용자1", "111", LocalDate.of(1950, 1, 1), "link1");
        User user2 = new User("01022222222", "사용자2", "111", LocalDate.of(1955, 3, 15), "link2");
        when(medicationRepository.findAllUsersWithMedications()).thenReturn(List.of(user1, user2));

        // when
        scheduler.sendMorningReminder();

        // then
        verify(notificationService, times(1)).sendMedicationReminderNotification(user1, "아침");
        verify(notificationService, times(1)).sendMedicationReminderNotification(user2, "아침");
    }

    @Test
    @DisplayName("sendNoonReminder - 점심 알림 전송")
    void sendNoonReminder_SendsNoonNotification() {
        // given
        User user = new User("01011111111", "사용자", "111", LocalDate.of(1950, 1, 1), "link1");
        when(medicationRepository.findAllUsersWithMedications()).thenReturn(List.of(user));

        // when
        scheduler.sendNoonReminder();

        // then
        verify(notificationService, times(1)).sendMedicationReminderNotification(user, "점심");
    }

    @Test
    @DisplayName("sendEveningReminder - 저녁 알림 전송")
    void sendEveningReminder_SendsEveningNotification() {
        // given
        User user = new User("01011111111", "사용자", "111", LocalDate.of(1950, 1, 1), "link1");
        when(medicationRepository.findAllUsersWithMedications()).thenReturn(List.of(user));

        // when
        scheduler.sendEveningReminder();

        // then
        verify(notificationService, times(1)).sendMedicationReminderNotification(user, "저녁");
    }

    @Test
    @DisplayName("약이 등록된 사용자가 없으면 알림 전송 안함")
    void sendReminder_NoUsers_SkipsNotification() {
        // given
        when(medicationRepository.findAllUsersWithMedications()).thenReturn(Collections.emptyList());

        // when
        scheduler.sendMorningReminder();

        // then
        verify(notificationService, never()).sendMedicationReminderNotification(any(), any());
    }

    @Test
    @DisplayName("개별 사용자 알림 전송 실패해도 나머지 사용자에게 계속 전송")
    void sendReminder_PartialFailure_ContinuesWithOthers() {
        // given
        User user1 = new User("01011111111", "사용자1", "111", LocalDate.of(1950, 1, 1), "link1");
        User user2 = new User("01022222222", "사용자2", "111", LocalDate.of(1955, 3, 15), "link2");
        User user3 = new User("01033333333", "사용자3", "111", LocalDate.of(1960, 7, 20), "link3");
        when(medicationRepository.findAllUsersWithMedications()).thenReturn(List.of(user1, user2, user3));

        // user2 알림 전송 시 예외 발생
        doNothing().when(notificationService).sendMedicationReminderNotification(user1, "아침");
        doThrow(new RuntimeException("FCM 전송 실패"))
                .when(notificationService).sendMedicationReminderNotification(user2, "아침");
        doNothing().when(notificationService).sendMedicationReminderNotification(user3, "아침");

        // when
        scheduler.sendMorningReminder();

        // then - user2 실패해도 user3까지 전송 시도
        verify(notificationService, times(1)).sendMedicationReminderNotification(user1, "아침");
        verify(notificationService, times(1)).sendMedicationReminderNotification(user2, "아침");
        verify(notificationService, times(1)).sendMedicationReminderNotification(user3, "아침");
    }
}
