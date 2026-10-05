package com.stoneiron.gamehub.room;

import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.game.GameType;
import com.stoneiron.gamehub.game.crossword.CrosswordRule;
import com.stoneiron.gamehub.game.crossword.PuzzleBank;
import com.stoneiron.gamehub.game.drawing.DrawingRule;
import com.stoneiron.gamehub.game.drawing.WordBank;
import com.stoneiron.gamehub.game.omok.OmokRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceTest {

    /** 테스트에서 시간을 마음대로 움직이기 위한 시계 */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-05T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void plusSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }

    /** 보낸 메시지를 모아 두는 가짜 전송기 */
    static class RecordingNotifier implements RoomNotifier {
        final List<RoomView> views = Collections.synchronizedList(new ArrayList<>());
        final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void roomUpdated(String code, RoomView view) {
            views.add(view);
        }

        @Override
        public void privateUpdated(String code, String token, Map<String, Object> privateState) {
        }

        @Override
        public void events(String code, List<Map<String, Object>> events) {
            this.events.addAll(events);
        }

        @Override
        public void roomClosed(String code) {
        }
    }

    private MutableClock clock;
    private RecordingNotifier notifier;
    private RoomService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        notifier = new RecordingNotifier();
        service = new RoomService(
                List.of(new OmokRule(new Random(1)),
                        new DrawingRule(new WordBank(), new Random(1)),
                        new CrosswordRule(new PuzzleBank(List.of(PuzzleBank.everyday())), new Random(1))),
                notifier, clock, new RoomCodeGenerator());
    }

    @Test
    @DisplayName("6자리 코드로 방을 만들고, 대소문자 구분 없이 입장할 수 있다")
    void createAndJoinByCode() {
        JoinResult host = service.create(GameType.OMOK, "방장");
        assertEquals(6, host.code().length());

        JoinResult guest = service.join(host.code().toLowerCase(), "손님");
        assertEquals(host.code(), guest.code());
        assertEquals(2, service.view(host.code(), host.token()).players().size());
    }

    @Test
    @DisplayName("정원이 차면 입장할 수 없고 열린 방 목록에서도 빠진다")
    void fullRoomRejected() {
        JoinResult host = service.create(GameType.OMOK, "방장");
        service.join(host.code(), "손님");

        assertThrows(GameException.class, () -> service.join(host.code(), "셋째"));
        assertTrue(service.openRooms().isEmpty());
    }

    @Test
    @DisplayName("방장만, 최소 인원이 모였을 때만 시작할 수 있다")
    void startRules() {
        JoinResult host = service.create(GameType.OMOK, "방장");
        assertThrows(GameException.class, () -> service.start(host.code(), host.token()));

        JoinResult guest = service.join(host.code(), "손님");
        assertThrows(GameException.class, () -> service.start(host.code(), guest.token()));

        service.start(host.code(), host.token());
        assertEquals(RoomStatus.PLAYING, service.view(host.code(), host.token()).status());
    }

    @Test
    @DisplayName("연결이 끊겨도 제한 시간 안에 같은 토큰으로 돌아오면 자리가 유지된다")
    void reconnectWithinGrace() {
        JoinResult host = service.create(GameType.OMOK, "방장");
        JoinResult guest = service.join(host.code(), "손님");
        service.connect(host.code(), host.token(), "s1");
        service.connect(host.code(), guest.token(), "s2");
        service.start(host.code(), host.token());

        service.disconnect("s2");
        clock.plusSeconds(30);
        service.tickAll();
        service.connect(host.code(), guest.token(), "s3");

        RoomView view = service.view(host.code(), guest.token());
        assertEquals(RoomStatus.PLAYING, view.status());
        assertTrue(view.players().stream().allMatch(RoomView.PlayerView::connected));
    }

    @Test
    @DisplayName("게임 중 오래 돌아오지 않으면 기권 처리되고 상대가 이긴다")
    void abandonAfterGrace() {
        JoinResult host = service.create(GameType.OMOK, "방장");
        JoinResult guest = service.join(host.code(), "손님");
        service.connect(host.code(), host.token(), "s1");
        service.connect(host.code(), guest.token(), "s2");
        service.start(host.code(), host.token());

        service.disconnect("s2");
        clock.plusSeconds(RoomService.PLAYING_RECONNECT_GRACE.toSeconds());
        service.tickAll();

        RoomView view = service.view(host.code(), host.token());
        assertEquals(RoomStatus.FINISHED, view.status());
        assertEquals(host.playerId(), view.ranking().get(0));
    }

    @Test
    @DisplayName("마지막 사람이 나가면 방이 사라진다")
    void emptyRoomRemoved() {
        JoinResult host = service.create(GameType.CROSSWORD, "혼자");
        service.leave(host.code(), host.token());
        assertEquals(0, service.roomCount());
    }

    @Test
    @DisplayName("같은 단어에 동시에 정답이 몰려도 정확히 한 명만 차지한다")
    void concurrentAnswersOnlyOneClaims() throws Exception {
        JoinResult host = service.create(GameType.CROSSWORD, "p0");
        List<JoinResult> players = new ArrayList<>(List.of(host));
        for (int i = 1; i < 4; i++) {
            players.add(service.join(host.code(), "p" + i));
        }
        service.start(host.code(), host.token());

        int threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            JoinResult player = players.get(i % players.size());
            futures.add(pool.submit(() -> {
                ready.await();
                try {
                    service.action(host.code(), player.token(), "answer", Map.of("wordId", "a1", "text", "고구마"));
                    success.incrementAndGet();
                } catch (GameException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        ready.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, success.get());
        assertEquals(threads - 1, rejected.get());
    }

    @Test
    @DisplayName("다른 사람에게 보내는 방 정보에는 토큰과 제시어가 들어가지 않는다")
    void publicViewsNeverLeakSecrets() {
        JoinResult host = service.create(GameType.DRAWING, "그림1");
        JoinResult guest = service.join(host.code(), "그림2");
        service.start(host.code(), host.token());

        String word = (String) service.privateState(host.code(), host.token()).get("word");
        assertNotNull(word);
        assertTrue(service.privateState(host.code(), guest.token()).isEmpty());

        for (RoomView view : notifier.views) {
            String text = view.toString();
            assertFalse(text.contains(host.token()));
            assertFalse(text.contains(guest.token()));
            assertFalse(text.contains(word));
        }
    }

    @Test
    @DisplayName("오목 혼자하기: 컴퓨터가 자리에 앉고, 생각할 시간이 지나면 스스로 둔다")
    void soloOmokBotPlays() {
        JoinResult me = service.createSolo(GameType.OMOK, "나", "ADVANCED");
        RoomView view = service.view(me.code(), me.token());
        assertEquals(RoomStatus.PLAYING, view.status());
        assertTrue(view.solo());
        assertTrue(view.players().get(1).bot());
        assertEquals("컴퓨터 · 고급", view.players().get(1).nickname());

        service.connect(me.code(), me.token(), "s1");
        service.action(me.code(), me.token(), "place", Map.of("x", 7, "y", 7));
        service.tickAll();
        assertEquals(1, service.view(me.code(), me.token()).game().get("moveCount"));

        clock.plusSeconds(1);
        service.tickAll();
        RoomView afterBot = service.view(me.code(), me.token());
        assertEquals(2, afterBot.game().get("moveCount"));
        assertEquals(me.playerId(), afterBot.game().get("turnPlayerId"));
    }

    @Test
    @DisplayName("혼자하기 방은 목록에 나오지 않고 다른 사람이 들어올 수 없으며, 내가 나가면 닫힌다")
    void soloRoomIsPrivate() {
        JoinResult me = service.createSolo(GameType.OMOK, "나", "BEGINNER");
        assertTrue(service.openRooms().isEmpty());
        assertThrows(GameException.class, () -> service.join(me.code(), "손님"));

        service.leave(me.code(), me.token());
        assertEquals(0, service.roomCount());
    }

    @Test
    @DisplayName("컴퓨터 상대가 없는 그림맞추기는 혼자할 수 없고, 실패해도 방이 남지 않는다")
    void drawingCannotBeSolo() {
        assertThrows(GameException.class, () -> service.createSolo(GameType.DRAWING, "나", null));
        assertThrows(GameException.class, () -> service.createSolo(GameType.OMOK, "나", "GOD"));
        assertEquals(0, service.roomCount());
    }

    @Test
    @DisplayName("십자격돌 혼자하기는 바로 시작된다")
    void soloCrossword() {
        JoinResult me = service.createSolo(GameType.CROSSWORD, "나", null);
        assertEquals(RoomStatus.PLAYING, service.view(me.code(), me.token()).status());
    }

    @Test
    @DisplayName("그림맞추기는 두 명이 접속하면 잠시 뒤 자동으로 시작하고, 게임 중에도 들어올 수 있다")
    void drawingAutoStartAndJoinWhilePlaying() {
        JoinResult a = service.create(GameType.DRAWING, "에이");
        JoinResult b = service.join(a.code(), "비");
        service.connect(a.code(), a.token(), "s1");
        service.tickAll();
        assertEquals(RoomStatus.WAITING, service.view(a.code(), a.token()).status());

        service.connect(a.code(), b.token(), "s2");
        service.tickAll();
        assertNotNull(service.view(a.code(), a.token()).autoStartAt());

        clock.plusSeconds(RoomService.AUTO_START_DELAY.toSeconds());
        service.tickAll();
        assertEquals(RoomStatus.PLAYING, service.view(a.code(), a.token()).status());
        assertTrue(service.openRooms().get(0).playing());

        JoinResult c = service.join(a.code(), "씨");
        RoomView view = service.view(a.code(), c.token());
        assertEquals(3, view.players().size());
        assertEquals(3, view.game().get("totalRounds"));
    }

    @Test
    @DisplayName("대기실에서도 채팅할 수 있고, 들어오고 나갈 때 안내 메시지가 나간다")
    void chatAndSystemMessages() {
        JoinResult a = service.create(GameType.DRAWING, "에이");
        JoinResult b = service.join(a.code(), "비");
        service.chat(a.code(), a.token(), "안녕하세요");
        service.leave(a.code(), b.token());

        List<Object> types = notifier.events.stream().map(e -> e.get("type")).map(Object.class::cast).toList();
        assertTrue(types.contains("chat"));
        assertTrue(notifier.events.stream().anyMatch(e -> "비님이 들어왔어요.".equals(e.get("text"))));
        assertTrue(notifier.events.stream().anyMatch(e -> "비님이 나갔어요.".equals(e.get("text"))));
        assertThrows(GameException.class, () -> service.chat(a.code(), a.token(), "   "));
    }
}
