package com.project.safetyFence.websocket;

import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.link.LinkRepository;
import com.project.safetyFence.location.UserLocationRepository;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.location.LocationCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실시간 위치 공유 시스템 E2E 테스트
 * 실제 사용 시나리오를 기반으로 한 전체 흐름 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("실시간 위치 공유 시스템 E2E 테스트")
class LocationSharingE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private UserLocationRepository userLocationRepository;

    @Autowired
    private LocationCacheService cacheService;

    private String wsUrl;
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        wsUrl = "ws://localhost:" + port + "/ws";

        // React Native용 네이티브 WebSocket 클라이언트 (SockJS 제거)
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

        stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // User를 먼저 조회한 후 삭제하면 Cascade가 제대로 작동함
        userRepository.findAll().forEach(user -> userRepository.delete(user));
    }

    @Test
    @DisplayName("E2E 시나리오 1: 친구 위치 실시간 추적")
    void E2E_시나리오1_친구_위치_실시간_추적() throws Exception {
        /*
         * 시나리오:
         * 1. 사용자 A와 B가 앱에 로그인
         * 2. A가 B를 친구로 추가 (Link)
         * 3. A가 B의 위치를 지도에서 보기 위해 구독
         * 4. B가 이동하면서 2초마다 위치 전송
         * 5. A의 지도에서 B의 위치가 실시간으로 업데이트됨
         */

        // Given: 사용자 및 Link 설정
        User userA = createUser("alice", "앨리스", "LINK_A");
        User userB = createUser("bob", "밥", "LINK_B");

        Link linkAtoB = new Link(userA, "bob", "친구");
        userA.addLink(linkAtoB);
        linkRepository.save(linkAtoB);

        BlockingQueue<LocationUpdateDto> aliceQueue = new LinkedBlockingQueue<>();

        // When: Alice와 Bob 로그인 (WebSocket 연결)
        StompSession aliceSession = connectUser("alice");
        StompSession bobSession = connectUser("bob");

        // Alice가 Bob의 위치 구독
        aliceSession.subscribe(
                "/topic/location/bob",
                createFrameHandler(aliceQueue)
        );

        // 구독 완료 대기
        Thread.sleep(500);

        // Bob이 이동하면서 위치 전송 (5회)
        double[] latitudes = {37.5665, 37.5670, 37.5675, 37.5680, 37.5685};
        double[] longitudes = {126.9780, 126.9785, 126.9790, 126.9795, 126.9800};

        for (int i = 0; i < 5; i++) {
            LocationUpdateDto location = new LocationUpdateDto(latitudes[i], longitudes[i]);
            bobSession.send("/app/location", location);
            Thread.sleep(2000); // 2초 간격
        }

        // Then: Alice가 Bob의 위치를 5번 모두 수신
        for (int i = 0; i < 5; i++) {
            LocationUpdateDto received = aliceQueue.poll(3, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.getUserNumber()).isEqualTo("bob");
            assertThat(received.getLatitude()).isEqualTo(latitudes[i]);
            assertThat(received.getLongitude()).isEqualTo(longitudes[i]);
        }

        // 캐시에 Bob의 최신 위치 저장 확인
        LocationUpdateDto cachedLocation = cacheService.getLatestLocation("bob");
        assertThat(cachedLocation).isNotNull();
        assertThat(cachedLocation.getLatitude()).isEqualTo(37.5685); // 마지막 위치

        aliceSession.disconnect();
        bobSession.disconnect();
    }

    @Test
    @DisplayName("E2E 시나리오 2: 여러 친구 중 한 명 선택하여 추적")
    void E2E_시나리오2_여러_친구_중_한_명_선택하여_추적() throws Exception {
        /*
         * 시나리오:
         * 1. Alice가 Bob, Charlie, David를 친구로 추가
         * 2. Alice가 처음에 Bob의 위치 구독
         * 3. Bob이 위치 전송 → Alice 수신
         * 4. Alice가 Bob 구독 해제하고 Charlie 구독
         * 5. Charlie가 위치 전송 → Alice 수신
         * 6. Bob이 위치 전송해도 Alice는 수신 안 함
         */

        // Given
        User alice = createUser("alice", "앨리스", "LINK_A");
        User bob = createUser("bob", "밥", "LINK_B");
        User charlie = createUser("charlie", "찰리", "LINK_C");
        User david = createUser("david", "데이빗", "LINK_D");

        linkRepository.save(new Link(alice, "bob", "친구"));
        linkRepository.save(new Link(alice, "charlie", "가족"));
        linkRepository.save(new Link(alice, "david", "동료"));

        BlockingQueue<LocationUpdateDto> aliceQueue = new LinkedBlockingQueue<>();

        StompSession aliceSession = connectUser("alice");
        StompSession bobSession = connectUser("bob");
        StompSession charlieSession = connectUser("charlie");

        // When: Alice가 Bob 구독
        StompSession.Subscription bobSubscription = aliceSession.subscribe(
                "/topic/location/bob",
                createFrameHandler(aliceQueue)
        );

        // 구독 완료 대기
        Thread.sleep(500);

        // Bob 위치 전송
        bobSession.send("/app/location", new LocationUpdateDto(37.111, 127.111));
        LocationUpdateDto receivedFromBob = aliceQueue.poll(3, TimeUnit.SECONDS);
        assertThat(receivedFromBob).isNotNull();
        assertThat(receivedFromBob.getUserNumber()).isEqualTo("bob");

        // Alice가 Bob 구독 해제하고 Charlie 구독
        bobSubscription.unsubscribe();
        aliceSession.subscribe("/topic/location/charlie", createFrameHandler(aliceQueue));

        // 구독 완료 대기
        Thread.sleep(500);

        // Charlie 위치 전송
        charlieSession.send("/app/location", new LocationUpdateDto(37.222, 127.222));
        LocationUpdateDto receivedFromCharlie = aliceQueue.poll(3, TimeUnit.SECONDS);
        assertThat(receivedFromCharlie).isNotNull();
        assertThat(receivedFromCharlie.getUserNumber()).isEqualTo("charlie");

        // Bob이 다시 위치 전송 (Alice는 수신 안 함)
        bobSession.send("/app/location", new LocationUpdateDto(37.333, 127.333));
        LocationUpdateDto shouldBeNull = aliceQueue.poll(2, TimeUnit.SECONDS);
        assertThat(shouldBeNull).isNull(); // Alice는 Bob 구독 해제했으므로 수신 안 함

        aliceSession.disconnect();
        bobSession.disconnect();
        charlieSession.disconnect();
    }

    @Test
    @DisplayName("E2E 시나리오 3: 구독 시 캐시된 최신 위치 즉시 수신")
    void E2E_시나리오3_구독_시_캐시된_최신_위치_즉시_수신() throws Exception {
        /*
         * 시나리오:
         * 1. Bob이 먼저 로그인하여 위치 전송
         * 2. Bob의 위치가 캐시에 저장됨
         * 3. Alice가 나중에 로그인하여 Bob 구독
         * 4. Alice는 구독 즉시 Bob의 최신 위치 수신 (캐시에서)
         */

        // Given
        User alice = createUser("alice", "앨리스", "LINK_A");
        User bob = createUser("bob", "밥", "LINK_B");
        linkRepository.save(new Link(alice, "bob", "친구"));

        StompSession bobSession = connectUser("bob");

        // Bob이 먼저 위치 전송
        LocationUpdateDto bobLocation = new LocationUpdateDto(37.123, 127.456);
        bobSession.send("/app/location", bobLocation);

        // 캐시 저장 대기
        await().atMost(2, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   LocationUpdateDto cached = cacheService.getLatestLocation("bob");
                   assertThat(cached).isNotNull();
               });

        // When: Alice가 나중에 로그인하고 Bob 구독
        BlockingQueue<LocationUpdateDto> aliceQueue = new LinkedBlockingQueue<>();
        StompSession aliceSession = connectUser("alice");

        // Bob의 위치 구독 (@SubscribeMapping이 자동으로 최신 위치 반환)
        aliceSession.subscribe("/topic/location/bob", createFrameHandler(aliceQueue));

        // 구독 완료 및 @SubscribeMapping 응답 대기
        Thread.sleep(500);

        // Then: 구독 즉시 캐시된 위치 수신 (@SubscribeMapping에서 반환)
        LocationUpdateDto receivedFromCache = aliceQueue.poll(3, TimeUnit.SECONDS);
        assertThat(receivedFromCache).isNotNull();
        assertThat(receivedFromCache.getLatitude()).isEqualTo(37.123);
        assertThat(receivedFromCache.getLongitude()).isEqualTo(127.456);

        aliceSession.disconnect();
        bobSession.disconnect();
    }

    @Test
    @DisplayName("E2E 시나리오 4: 100m 이상 이동 시 DB 저장 확인")
    void E2E_시나리오4_100m_이상_이동_시_DB_저장_확인() throws Exception {
        /*
         * 시나리오:
         * 1. Bob이 위치 전송 (첫 번째 위치 → DB 저장)
         * 2. Bob이 50m 이동 → DB 저장 안 됨
         * 3. Bob이 100m 이상 이동 → DB 저장됨
         */

        // Given
        User bob = createUser("bob", "밥", "LINK_B");
        StompSession bobSession = connectUser("bob");

        // When: 첫 위치 전송 (서울시청)
        bobSession.send("/app/location", new LocationUpdateDto(37.5665, 126.9780));
        Thread.sleep(1000);

        // Then: 첫 위치는 DB에 저장됨
        await().atMost(3, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   List<UserLocation> locations = userLocationRepository.findAll();
                   assertThat(locations).hasSize(1);
               });

        // When: 50m 이동 (저장 조건 미충족)
        bobSession.send("/app/location", new LocationUpdateDto(37.5670, 126.9780));
        Thread.sleep(1000);

        // Then: 여전히 1개 (저장 안 됨)
        List<UserLocation> afterSmallMove = userLocationRepository.findAll();
        assertThat(afterSmallMove).hasSize(1);

        // When: 150m 이상 이동 (광화문)
        bobSession.send("/app/location", new LocationUpdateDto(37.5758, 126.9768));
        Thread.sleep(2000);

        // Then: 2개로 증가 (저장됨)
        await().atMost(5, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   List<UserLocation> locations = userLocationRepository.findAll();
                   assertThat(locations).hasSize(2);
               });

        bobSession.disconnect();
    }

    @Test
    @DisplayName("E2E 시나리오 5: 연결 해제 시 캐시 삭제 확인")
    void E2E_시나리오5_연결_해제_시_캐시_삭제_확인() throws Exception {
        /*
         * 시나리오:
         * 1. Bob이 로그인하여 위치 전송
         * 2. Bob의 위치가 캐시에 저장됨
         * 3. Bob이 로그아웃 (WebSocket 연결 해제)
         * 4. 캐시에서 Bob의 위치 삭제 확인
         */

        // Given
        User bob = createUser("bob", "밥", "LINK_B");
        StompSession bobSession = connectUser("bob");

        // Bob 위치 전송
        bobSession.send("/app/location", new LocationUpdateDto(37.123, 127.456));

        // 캐시 저장 확인
        await().atMost(2, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   LocationUpdateDto cached = cacheService.getLatestLocation("bob");
                   assertThat(cached).isNotNull();
               });

        // When: Bob 연결 해제
        bobSession.disconnect();

        // Then: 캐시에서 삭제됨
        await().atMost(3, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   LocationUpdateDto cached = cacheService.getLatestLocation("bob");
                   assertThat(cached).isNull();
               });
    }

    @Test
    @DisplayName("E2E 시나리오 6: 권한 없는 사용자의 구독 시도 차단")
    void E2E_시나리오6_권한_없는_사용자의_구독_시도_차단() throws Exception {
        /*
         * 시나리오:
         * 1. Alice와 Bob은 서로 Link가 아님
         * 2. Alice가 Bob의 위치 구독 시도
         * 3. WebSocketAuthInterceptor가 SUBSCRIBE 명령을 차단 (return null)
         * 4. Bob이 위치 전송해도 Alice는 수신 못 함
         */

        // Given: Link 없음
        User alice = createUser("alice", "앨리스", "LINK_A");
        User bob = createUser("bob", "밥", "LINK_B");

        BlockingQueue<LocationUpdateDto> aliceQueue = new LinkedBlockingQueue<>();
        StompSession aliceSession = connectUser("alice");
        StompSession bobSession = connectUser("bob");

        // When: Alice가 Bob 구독 시도 (권한 없음)
        // WebSocketAuthInterceptor가 SUBSCRIBE 명령을 차단하지만
        // 클라이언트에서는 구독 객체가 생성됨 (서버에서 조용히 차단)
        aliceSession.subscribe("/topic/location/bob", createFrameHandler(aliceQueue));
        Thread.sleep(1000); // 구독 처리 대기

        // Bob이 위치 전송
        bobSession.send("/app/location", new LocationUpdateDto(37.123, 127.456));
        Thread.sleep(500);

        // Then: Alice는 수신 못 함 (WebSocketAuthInterceptor가 구독 차단)
        LocationUpdateDto received = aliceQueue.poll(2, TimeUnit.SECONDS);
        assertThat(received).isNull();

        // 권한 검증 로직:
        // 1차 방어: WebSocketAuthInterceptor가 SUBSCRIBE 명령 차단 (return null)
        // 2차 방어: @SubscribeMapping에서도 AccessDeniedException 발생 (도달하지 않음)

        aliceSession.disconnect();
        bobSession.disconnect();
    }

    @Test
    @DisplayName("E2E 시나리오 7: 다중 사용자 동시 위치 공유")
    void E2E_시나리오7_다중_사용자_동시_위치_공유() throws Exception {
        /*
         * 시나리오:
         * 1. Alice, Bob, Charlie 3명이 서로 친구
         * 2. Alice가 Bob과 Charlie의 위치 동시 구독
         * 3. Bob과 Charlie가 동시에 위치 전송
         * 4. Alice가 두 사람의 위치를 모두 수신
         */

        // Given
        User alice = createUser("alice", "앨리스", "LINK_A");
        User bob = createUser("bob", "밥", "LINK_B");
        User charlie = createUser("charlie", "찰리", "LINK_C");

        Link linkToBob = new Link(alice, "bob", "친구");
        alice.addLink(linkToBob);
        linkRepository.save(linkToBob);

        Link linkToCharlie = new Link(alice, "charlie", "친구");
        alice.addLink(linkToCharlie);
        linkRepository.save(linkToCharlie);

        BlockingQueue<LocationUpdateDto> aliceQueueForBob = new LinkedBlockingQueue<>();
        BlockingQueue<LocationUpdateDto> aliceQueueForCharlie = new LinkedBlockingQueue<>();

        StompSession aliceSession = connectUser("alice");
        StompSession bobSession = connectUser("bob");
        StompSession charlieSession = connectUser("charlie");

        // When: Alice가 Bob과 Charlie 동시 구독
        aliceSession.subscribe("/topic/location/bob", createFrameHandler(aliceQueueForBob));
        aliceSession.subscribe("/topic/location/charlie", createFrameHandler(aliceQueueForCharlie));

        // 구독 완료 대기
        Thread.sleep(500);

        // Bob과 Charlie가 동시에 위치 전송
        bobSession.send("/app/location", new LocationUpdateDto(37.111, 127.111));
        charlieSession.send("/app/location", new LocationUpdateDto(37.222, 127.222));

        // Then: Alice가 두 위치 모두 수신
        LocationUpdateDto bobLocation = aliceQueueForBob.poll(3, TimeUnit.SECONDS);
        LocationUpdateDto charlieLocation = aliceQueueForCharlie.poll(3, TimeUnit.SECONDS);

        assertThat(bobLocation).isNotNull();
        assertThat(bobLocation.getUserNumber()).isEqualTo("bob");
        assertThat(bobLocation.getLatitude()).isEqualTo(37.111);

        assertThat(charlieLocation).isNotNull();
        assertThat(charlieLocation.getUserNumber()).isEqualTo("charlie");
        assertThat(charlieLocation.getLatitude()).isEqualTo(37.222);

        aliceSession.disconnect();
        bobSession.disconnect();
        charlieSession.disconnect();
    }

    // === 헬퍼 메서드 ===

    private User createUser(String number, String name, String linkCode) {
        User user = new User(number, name, "password", LocalDate.now(), linkCode);
        return userRepository.save(user);
    }

    private StompSession connectUser(String userNumber) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("userNumber", userNumber);

        return stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler createFrameHandler(BlockingQueue<LocationUpdateDto> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LocationUpdateDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((LocationUpdateDto) payload);
            }
        };
    }
}
