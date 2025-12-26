package com.project.safetyFence.calendar;

import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.log.domain.Log;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.calendar.domain.UserEvent;
import com.project.safetyFence.calendar.dto.EventDataRequestDto;
import com.project.safetyFence.geofence.dto.GeofenceItemDto;
import com.project.safetyFence.log.dto.LogItemDto;
import com.project.safetyFence.calendar.dto.OneDayResponseDto;
import com.project.safetyFence.calendar.dto.UserEventItemDto;
import com.project.safetyFence.calendar.UserEventRepository;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.medication.MedicationRepository;
import com.project.safetyFence.medication.MedicationLogRepository;
import com.project.safetyFence.medication.domain.Medication;
import com.project.safetyFence.medication.domain.MedicationLog;
import com.project.safetyFence.medication.dto.MedicationItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final UserRepository userRepository;
    private final UserEventRepository userEventRepository;
    private final MedicationRepository medicationRepository;
    private final MedicationLogRepository medicationLogRepository;


    @Transactional(readOnly = true)
    public List<OneDayResponseDto> getCalendarData(String userNumber) {
        User user = userRepository.findByNumberWithCalendarData(userNumber);

        // 날짜별로 OneDayResponseDto를 저장하는 Map (날짜 순서대로 정렬)
        Map<String, OneDayResponseDto> dateMap = new TreeMap<>();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // 1. Log 데이터 처리
        for (Log log : user.getLogs()) {
            String date = log.getArriveTime().toLocalDate().format(dateFormatter);
            String arriveTime = log.getArriveTime().format(timeFormatter);

            // 해당 날짜의 DTO가 없으면 생성
            OneDayResponseDto dayDto = dateMap.computeIfAbsent(date, OneDayResponseDto::new);

            // Log 아이템 추가
            LogItemDto logItem = new LogItemDto(
                log.getId(),
                log.getLocation(),
                log.getLocationAddress(),
                arriveTime
            );
            dayDto.addLog(logItem);
        }

        // 2. Geofence 데이터 처리 (일시적 geofence만 - startTime이 있는 것)
        for (Geofence geofence : user.getGeofences()) {
            if (geofence.getType() == 1 && geofence.getStartTime() != null) {
                String date = geofence.getStartTime().toLocalDate().format(dateFormatter);

                // 해당 날짜의 DTO가 없으면 생성
                OneDayResponseDto dayDto = dateMap.computeIfAbsent(date, OneDayResponseDto::new);

                // Geofence 아이템 추가
                GeofenceItemDto geofenceItem = new GeofenceItemDto(
                    geofence.getId(),
                    geofence.getName(),
                    geofence.getAddress(),
                    geofence.getStartTime(),
                    geofence.getEndTime()
                );
                dayDto.addGeofence(geofenceItem);
            }
        }

        // 3. UserEvent 데이터 처리
        for (UserEvent event : user.getUserEvents()) {
            String date = event.getEventDate().format(dateFormatter);
            String eventStartTime = event.getStartTime() != null ?
                event.getStartTime().format(timeFormatter) : null;

            // 해당 날짜의 DTO가 없으면 생성
            OneDayResponseDto dayDto = dateMap.computeIfAbsent(date, OneDayResponseDto::new);

            // UserEvent 아이템 추가
            UserEventItemDto eventItem = new UserEventItemDto(
                event.getId(),
                event.getEvent(),
                eventStartTime
            );
            dayDto.addUserEvent(eventItem);
        }

        // 4. Medication 데이터 처리
        List<Medication> medications = medicationRepository.findByUserNumber(userNumber);

        // 각 날짜에 대해 모든 약의 복용 여부 확인
        for (Map.Entry<String, OneDayResponseDto> entry : dateMap.entrySet()) {
            LocalDate date = LocalDate.parse(entry.getKey(), dateFormatter);
            OneDayResponseDto dayDto = entry.getValue();

            // 각 약에 대해 해당 날짜에 복용했는지 확인
            for (Medication medication : medications) {
                List<MedicationLog> logs = medicationLogRepository
                    .findByMedicationIdAndDate(medication.getId(), date);
                boolean checked = !logs.isEmpty();

                MedicationItemDto medicationItem = new MedicationItemDto(medication, checked);
                dayDto.addMedication(medicationItem);
            }
        }

        // Map의 값들을 리스트로 변환 (날짜순으로 정렬됨)
        return new ArrayList<>(dateMap.values());
    }

    @Transactional
    public void addEvent(String userNumber, EventDataRequestDto eventDataRequestDto) {
        User user = userRepository.findByNumber(userNumber);

        String event = eventDataRequestDto.getEvent();
        String eventDate = eventDataRequestDto.getEventDate(); // "2024-10-22" 형태
        String startTime = eventDataRequestDto.getStartTime(); // "14:30" 형태

        // 문자열을 LocalDate로 변환
        LocalDate date = LocalDate.parse(eventDate);
        LocalTime time = LocalTime.parse(startTime);

        UserEvent userEvent = new UserEvent(user, event, date, time);

        user.addUserEvent(userEvent);
        userRepository.save(user);
    }

    @Transactional
    public void deleteEvent(String userNumber, Long eventId) {
        User user = userRepository.findByNumberWithEvents(userNumber);

        UserEvent userEventToDelete = user.getUserEvents().stream()
                .filter(event -> event.getId().equals(eventId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        user.removeEvent(userEventToDelete);
    }
}
