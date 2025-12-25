package com.project.safetyFence.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.link.LinkRepository;
import com.project.safetyFence.location.UserLocationRepository;
import com.project.safetyFence.user.UserRepository;
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

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * WebSocket 통합 테스트
 * 실제 WebSocket 연결, 메시지 송수신, 권한 검증 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("WebSocket 통합 테스트")
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private UserLocationRepository userLocationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String wsUrl;
    private WebSocketStompClient stompClient;
    private BlockingQueue<LocationUpdateDto> blockingQueue;

    private User userA;
    private User userB;
    private User userC;

    @BeforeEach
    void setUp() {
        // 이전 테스트 데이터 정리 (테스트 격리 보장)
        userLocationRepository.deleteAll();
        linkRepository.deleteAll();
        userRepository.deleteAll();

        wsUrl = "ws://localhost:" + port + "/ws";

        // WebSocket 클라이언트 설정 (네이티브 WebSocket 사용)
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

        stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        blockingQueue = new LinkedBlockingQueue<>();

        // 테스트 데이터 생성
        createTestUsers();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // 비동기 위치 저장 작업 완료 대기
        Thread.sleep(1000);

        // 테스트 데이터 정리 (외래 키 제약 때문에 삭제 순서 중요)
        userLocationRepository.deleteAll(); // 1. UserLocation 먼저 삭제
        linkRepository.deleteAll();         // 2. Link 삭제
        userRepository.deleteAll();         // 3. User 마지막에 삭제
    }

    private void createTestUsers() {
        userA = new User("userA", "사용자A", "password", LocalDate.now(), "LINKA");
        userB = new User("userB", "사용자B", "password", LocalDate.now(), "LINKB");
        userC = new User("userC", "사용자C", "password", LocalDate.now(), "LINKC");

        userRepository.save(userA);
        userRepository.save(userB);
        userRepository.save(userC);

        // A가 B를 Link로 등록
        Link linkAtoB = new Link(userA, "userB", "친구");
        userA.addLink(linkAtoB);
        linkRepository.save(linkAtoB);

        // B가 C를 Link로 등록
        Link linkBtoC = new Link(userB, "userC", "가족");
        userB.addLink(linkBtoC);
        linkRepository.save(linkBtoC);
    }

    @Test
    @DisplayName("시나리오 1: WebSocket 연결 성공")
    void 시나리오1_WebSocket_연결_성공() throws Exception {
        // Given
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("userNumber", "userA");

        // When
        StompSession session = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // Then
        assertThat(session.isConnected()).isTrue();

        session.disconnect();
    }

    @Test
    @DisplayName("시나리오 2: userNumber 없이 연결 시도 - 실패")
    void 시나리오2_userNumber_없이_연결_시도_실패() {
        // Given
        StompHeaders connectHeaders = new StompHeaders();
        // userNumber 헤더 없음

        // When & Then
        boolean exceptionOccurred = false;
        try {
            StompSession session = stompClient.connectAsync(
                    wsUrl,
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            ).get(5, TimeUnit.SECONDS);

            // 연결이 성공하면 안 됨
            fail("userNumber 없이 연결이 성공하면 안 됩니다");
        } catch (Exception e) {
            exceptionOccurred = true;
            // 예외 발생 확인 (메시지 내용은 래핑될 수 있으므로 예외 발생 여부만 확인)
            assertThat(e).isNotNull();
        }

        assertThat(exceptionOccurred).isTrue();
    }

    @Test
    @DisplayName("시나리오 3: 권한 있는 사용자 구독 성공 - A가 B 구독")
    void 시나리오3_권한_있는_사용자_구독_성공() throws Exception {
        // Given
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("userNumber", "userA");

        StompSession session = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // When: A가 B의 위치 구독
        StompSession.Subscription subscription = session.subscribe(
                "/topic/location/userB",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return LocationUpdateDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        blockingQueue.add((LocationUpdateDto) payload);
                    }
                }
        );

        // Then: 구독 성공 (예외 없음)
        assertThat(subscription).isNotNull();

        session.disconnect();
    }

    @Test
    @DisplayName("시나리오 4: 권한 없는 사용자 구독 실패 - A가 C 구독 시도")
    void 시나리오4_권한_없는_사용자_구독_실패() throws Exception {
        // Given
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("userNumber", "userA");

        StompSession session = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // When: A가 C의 위치 구독 시도 (A는 C를 Link로 등록하지 않음)
        // WebSocketAuthInterceptor가 SUBSCRIBE 명령을 차단 (return null)
        session.subscribe(
                "/topic/location/userC",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return LocationUpdateDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        blockingQueue.add((LocationUpdateDto) payload);
                    }
                }
        );

        // 구독 처리 대기
        Thread.sleep(1000);

        // Then: 구독이 서버에서 차단되어 메시지를 받을 수 없음
        // (WebSocketAuthInterceptor가 return null로 SUBSCRIBE 명령 차단)
        assertThat(blockingQueue).isEmpty();

        session.disconnect();
    }

    @Test
    @DisplayName("시나리오 5: 위치 전송 및 구독자 수신")
    void 시나리오5_위치_전송_및_구독자_수신() throws Exception {
        // Given: 두 개의 세션 생성
        // Session 1: userA (구독자)
        StompHeaders connectHeadersA = new StompHeaders();
        connectHeadersA.add("userNumber", "userA");
        StompSession sessionA = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersA,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // Session 2: userB (위치 전송자)
        StompHeaders connectHeadersB = new StompHeaders();
        connectHeadersB.add("userNumber", "userB");
        StompSession sessionB = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersB,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // A가 B의 위치 구독
        sessionA.subscribe(
                "/topic/location/userB",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return LocationUpdateDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        blockingQueue.add((LocationUpdateDto) payload);
                    }
                }
        );

        // 구독 완료 대기
        Thread.sleep(500);

        // When: B가 위치 전송
        LocationUpdateDto locationDto = new LocationUpdateDto(37.123456, 127.123456);
        sessionB.send("/app/location", locationDto);

        // Then: A가 B의 위치 수신
        LocationUpdateDto receivedLocation = blockingQueue.poll(5, TimeUnit.SECONDS);

        assertThat(receivedLocation).isNotNull();
        assertThat(receivedLocation.getUserNumber()).isEqualTo("userB");
        assertThat(receivedLocation.getLatitude()).isEqualTo(37.123456);
        assertThat(receivedLocation.getLongitude()).isEqualTo(127.123456);

        sessionA.disconnect();
        sessionB.disconnect();
    }

    @Test
    @DisplayName("시나리오 6: 여러 구독자에게 동시 전송")
    void 시나리오6_여러_구독자에게_동시_전송() throws Exception {
        // Given
        // A와 C가 B를 Link로 등록
        Link linkCtoB = new Link(userC, "userB", "동료");
        userC.addLink(linkCtoB);
        linkRepository.save(linkCtoB);

        BlockingQueue<LocationUpdateDto> queueA = new LinkedBlockingQueue<>();
        BlockingQueue<LocationUpdateDto> queueC = new LinkedBlockingQueue<>();

        // Session A
        StompHeaders connectHeadersA = new StompHeaders();
        connectHeadersA.add("userNumber", "userA");
        StompSession sessionA = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersA,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // Session C
        StompHeaders connectHeadersC = new StompHeaders();
        connectHeadersC.add("userNumber", "userC");
        StompSession sessionC = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersC,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // Session B
        StompHeaders connectHeadersB = new StompHeaders();
        connectHeadersB.add("userNumber", "userB");
        StompSession sessionB = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersB,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // A와 C가 B 구독
        sessionA.subscribe("/topic/location/userB", createFrameHandler(queueA));
        sessionC.subscribe("/topic/location/userB", createFrameHandler(queueC));

        // 구독 완료 대기
        Thread.sleep(500);

        // When: B가 위치 전송
        LocationUpdateDto locationDto = new LocationUpdateDto(37.555555, 127.777777);
        sessionB.send("/app/location", locationDto);

        // Then: A와 C 모두 수신
        LocationUpdateDto receivedByA = queueA.poll(5, TimeUnit.SECONDS);
        LocationUpdateDto receivedByC = queueC.poll(5, TimeUnit.SECONDS);

        assertThat(receivedByA).isNotNull();
        assertThat(receivedByC).isNotNull();
        assertThat(receivedByA.getLatitude()).isEqualTo(37.555555);
        assertThat(receivedByC.getLatitude()).isEqualTo(37.555555);

        sessionA.disconnect();
        sessionB.disconnect();
        sessionC.disconnect();
    }

    @Test
    @DisplayName("시나리오 7: 구독 취소 후 메시지 수신 안 됨")
    void 시나리오7_구독_취소_후_메시지_수신_안_됨() throws Exception {
        // Given
        StompHeaders connectHeadersA = new StompHeaders();
        connectHeadersA.add("userNumber", "userA");
        StompSession sessionA = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersA,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        StompHeaders connectHeadersB = new StompHeaders();
        connectHeadersB.add("userNumber", "userB");
        StompSession sessionB = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersB,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        // A가 B 구독
        StompSession.Subscription subscription = sessionA.subscribe(
                "/topic/location/userB",
                createFrameHandler(blockingQueue)
        );

        // When: 구독 취소
        subscription.unsubscribe();

        // B가 위치 전송
        LocationUpdateDto locationDto = new LocationUpdateDto(37.123, 127.456);
        sessionB.send("/app/location", locationDto);

        // Then: A는 수신하지 않음
        LocationUpdateDto receivedLocation = blockingQueue.poll(2, TimeUnit.SECONDS);
        assertThat(receivedLocation).isNull();

        sessionA.disconnect();
        sessionB.disconnect();
    }

    @Test
    @DisplayName("시나리오 8: 연속 위치 업데이트")
    void 시나리오8_연속_위치_업데이트() throws Exception {
        // Given
        StompHeaders connectHeadersA = new StompHeaders();
        connectHeadersA.add("userNumber", "userA");
        StompSession sessionA = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersA,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        StompHeaders connectHeadersB = new StompHeaders();
        connectHeadersB.add("userNumber", "userB");
        StompSession sessionB = stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeadersB,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        sessionA.subscribe("/topic/location/userB", createFrameHandler(blockingQueue));

        // 구독 완료 대기
        Thread.sleep(500);

        // When: B가 연속으로 위치 전송 (이동 시뮬레이션)
        for (int i = 0; i < 5; i++) {
            LocationUpdateDto locationDto = new LocationUpdateDto(
                    37.123 + (i * 0.001),
                    127.456 + (i * 0.001)
            );
            sessionB.send("/app/location", locationDto);
            Thread.sleep(500); // 0.5초 간격
        }

        // Then: 5개 모두 수신
        for (int i = 0; i < 5; i++) {
            LocationUpdateDto received = blockingQueue.poll(2, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.getUserNumber()).isEqualTo("userB");
        }

        sessionA.disconnect();
        sessionB.disconnect();
    }

    // 헬퍼 메서드
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
