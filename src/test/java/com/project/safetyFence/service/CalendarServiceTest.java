package com.project.safetyFence.service;

import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.calendar.domain.UserEvent;
import com.project.safetyFence.calendar.dto.EventDataRequestDto;
import com.project.safetyFence.calendar.UserEventRepository;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.calendar.CalendarService;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CalendarServiceTest {

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserEventRepository userEventRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;

    private static final String TEST_NUMBER = "01012345678";

    @BeforeEach
    void setUp() {
        // Test user
        testUser = new User(TEST_NUMBER, "tester", "password", LocalDate.of(1990, 1, 1), "test-link");
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("addEvent - successfully adds event to user")
    void addEvent_Success() {
        // given
        EventDataRequestDto requestDto = new EventDataRequestDto(
                "Meeting with client",
                "2024-10-25",
                "14:30",
                null
        );

        int initialEventCount = testUser.getUserEvents().size();

        // when
        calendarService.addEvent(TEST_NUMBER, requestDto);

        // then
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        assertThat(updatedUser.getUserEvents()).hasSize(initialEventCount + 1);

        UserEvent addedEvent = updatedUser.getUserEvents().get(0);
        assertThat(addedEvent.getEvent()).isEqualTo("Meeting with client");
        assertThat(addedEvent.getEventDate()).isEqualTo(LocalDate.of(2024, 10, 25));
        assertThat(addedEvent.getStartTime().toString()).isEqualTo("14:30");
        assertThat(addedEvent.getUser()).isEqualTo(updatedUser);
    }

    @Test
    @DisplayName("addEvent - adds multiple events to same user")
    void addEvent_MultipleEvents_Success() {
        // given
        EventDataRequestDto event1 = new EventDataRequestDto(
                "Morning Meeting",
                "2024-10-25",
                "09:00",
                null
        );

        EventDataRequestDto event2 = new EventDataRequestDto(
                "Lunch Appointment",
                "2024-10-25",
                "12:30",
                null
        );

        EventDataRequestDto event3 = new EventDataRequestDto(
                "Evening Conference",
                "2024-10-26",
                "18:00",
                null
        );

        // when
        calendarService.addEvent(TEST_NUMBER, event1);
        calendarService.addEvent(TEST_NUMBER, event2);
        calendarService.addEvent(TEST_NUMBER, event3);

        // then
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        assertThat(updatedUser.getUserEvents()).hasSize(3);

        assertThat(updatedUser.getUserEvents())
                .extracting("event")
                .containsExactlyInAnyOrder("Morning Meeting", "Lunch Appointment", "Evening Conference");
    }

    @Test
    @DisplayName("addEvent - maintains bidirectional relationship")
    void addEvent_MaintainsBidirectionalRelationship() {
        // given
        EventDataRequestDto requestDto = new EventDataRequestDto(
                "Team Building",
                "2024-11-01",
                "10:00",
                null
        );

        // when
        calendarService.addEvent(TEST_NUMBER, requestDto);

        // then
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        UserEvent addedEvent = updatedUser.getUserEvents().get(0);

        // Verify bidirectional relationship
        assertThat(addedEvent.getUser()).isEqualTo(updatedUser);
        assertThat(updatedUser.getUserEvents()).contains(addedEvent);
    }

    @Test
    @DisplayName("addEvent - handles different date formats correctly")
    void addEvent_DifferentDates_Success() {
        // given
        EventDataRequestDto requestDto = new EventDataRequestDto(
                "New Year Event",
                "2025-01-01",
                "00:00",
                null
        );

        // when
        calendarService.addEvent(TEST_NUMBER, requestDto);

        // then
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        UserEvent addedEvent = updatedUser.getUserEvents().get(0);

        assertThat(addedEvent.getEventDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(addedEvent.getStartTime().toString()).isEqualTo("00:00");
    }

    @Test
    @DisplayName("addEvent - data persists across multiple calls")
    void addEvent_DataPersistence() {
        // given
        EventDataRequestDto requestDto = new EventDataRequestDto(
                "Persistent Event",
                "2024-12-25",
                "15:00",
                null
        );

        // when
        calendarService.addEvent(TEST_NUMBER, requestDto);

        // then - first retrieval
        User user1 = userRepository.findByNumber(TEST_NUMBER);
        assertThat(user1.getUserEvents()).hasSize(1);

        // then - second retrieval (verify persistence)
        User user2 = userRepository.findByNumber(TEST_NUMBER);
        assertThat(user2.getUserEvents()).hasSize(1);
        assertThat(user2.getUserEvents().get(0).getEvent()).isEqualTo("Persistent Event");
    }

    @Test
    @DisplayName("deleteEvent - successfully deletes event from database")
    void deleteEvent_Success() {
        // given - add event first
        EventDataRequestDto requestDto = new EventDataRequestDto(
                "Event to Delete",
                "2024-11-01",
                "10:00",
                null
        );
        calendarService.addEvent(TEST_NUMBER, requestDto);

        User user = userRepository.findByNumber(TEST_NUMBER);
        Long eventId = user.getUserEvents().get(0).getId();

        // when
        calendarService.deleteEvent(TEST_NUMBER, eventId);
        entityManager.flush();
        entityManager.clear();

        // then - verify deleted from database
        assertThat(userEventRepository.findById(eventId)).isEmpty();
    }

    @Test
    @DisplayName("deleteEvent - only deletes specified event from database")
    void deleteEvent_DeletesOnlySpecifiedEvent() {
        // given - add multiple events
        calendarService.addEvent(TEST_NUMBER, new EventDataRequestDto("Event 1", "2024-11-01", "10:00", null));
        calendarService.addEvent(TEST_NUMBER, new EventDataRequestDto("Event 2", "2024-11-02", "11:00", null));
        calendarService.addEvent(TEST_NUMBER, new EventDataRequestDto("Event 3", "2024-11-03", "12:00", null));

        User user = userRepository.findByNumber(TEST_NUMBER);
        Long eventId1 = user.getUserEvents().get(0).getId();
        Long eventId2 = user.getUserEvents().get(1).getId();
        Long eventId3 = user.getUserEvents().get(2).getId();

        // when - delete middle event
        calendarService.deleteEvent(TEST_NUMBER, eventId2);
        entityManager.flush();
        entityManager.clear();

        // then - verify only event2 is deleted from database
        assertThat(userEventRepository.findById(eventId1)).isPresent();
        assertThat(userEventRepository.findById(eventId2)).isEmpty();
        assertThat(userEventRepository.findById(eventId3)).isPresent();
    }

    @Test
    @DisplayName("deleteEvent - User 컬렉션도 검증 (orphanRemoval 테스트)")
    void deleteEvent_VerifyUserCollectionSync() {
        // given
        calendarService.addEvent(TEST_NUMBER, new EventDataRequestDto("Event to Delete", "2024-11-01", "10:00", null));

        User user = userRepository.findByNumber(TEST_NUMBER);
        Long eventId = user.getUserEvents().get(0).getId();
        assertThat(user.getUserEvents()).hasSize(1);  // 삭제 전: 1개

        // when
        calendarService.deleteEvent(TEST_NUMBER, eventId);
        entityManager.flush();  // orphanRemoval을 즉시 DB에 반영
        entityManager.clear();  // 영속성 컨텍스트 초기화 (캐시 제거)

        // then - DB는 삭제됨 ✅
        assertThat(userEventRepository.findById(eventId)).isEmpty();

        // then - User 컬렉션도 확인 (새로 조회)
        User updatedUser = userRepository.findByNumber(TEST_NUMBER);
        assertThat(updatedUser.getUserEvents()).hasSize(0);  // orphanRemoval 작동!
    }
}
