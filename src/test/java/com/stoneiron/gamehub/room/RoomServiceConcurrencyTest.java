package com.stoneiron.gamehub.room;

import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.game.GameType;
import com.stoneiron.gamehub.game.crossword.CrosswordRule;
import com.stoneiron.gamehub.game.crossword.Puzzle;
import com.stoneiron.gamehub.game.crossword.PuzzleBank;
import com.stoneiron.gamehub.game.drawing.DrawingRule;
import com.stoneiron.gamehub.game.drawing.WordBank;
import com.stoneiron.gamehub.game.omok.OmokRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 방 단위 락이 많은 동시 요청 속에서도 게임 상태를 일관되게 지키는지 검증한다.
 * 모든 동시 요청은 CountDownLatch로 같은 순간에 출발시킨다.
 */
class RoomServiceConcurrencyTest {

    /** 테스트에서 시간을 마음대로 움직이기 위한 시계 */
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-05T00:00:00Z");

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

    /** 보낸 메시지를 모아 두는 가짜 전송기. sendDelayMs만큼 전송에 시간이 걸리는 것처럼 동작한다. */
    static class RecordingNotifier implements RoomNotifier {
        final long sendDelayMs;
        final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<>());
        final List<String> publicMessages = Collections.synchronizedList(new ArrayList<>());

        RecordingNotifier(long sendDelayMs) {
            this.sendDelayMs = sendDelayMs;
        }

        @Override
        public void roomUpdated(String code, RoomView view) {
            publicMessages.add(view.toString());
            simulateSend();
        }

        @Override
        public void privateUpdated(String code, String token, Map<String, Object> privateState) {
        }

        @Override
        public void events(String code, List<Map<String, Object>> events) {
            this.events.addAll(events);
            events.forEach(event -> publicMessages.add(String.valueOf(event)));
            simulateSend();
        }

        @Override
        public void roomClosed(String code) {
        }

        private void simulateSend() {
            if (sendDelayMs <= 0) {
                return;
            }
            try {
                Thread.sleep(sendDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static RoomService newService(MutableClock clock, RoomNotifier notifier) {
        return new RoomService(
                List.of(new OmokRule(new Random(1)),
                        new DrawingRule(new WordBank(), new Random(1)),
                        new CrosswordRule(new PuzzleBank(List.of(PuzzleBank.everyday())), new Random(1))),
                notifier, clock, new RoomCodeGenerator());
    }

    private static RoomService newService() {
        return newService(new MutableClock(), new RecordingNotifier(0));
    }

    /** count개의 작업을 모두 준비시킨 뒤 같은 순간에 출발시키고, 끝날 때까지 기다린다. */
    private static void runConcurrently(int count, IntConsumer task) throws Exception {
        int threads = Math.min(count, 200);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger readyCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    if (readyCount.getAndIncrement() < threads) {
                        ready.countDown();
                    }
                    start.await();
                    task.accept(index);
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    @Test
    @Timeout(120)
    @DisplayName("십자격돌: 4명이 모든 단어에 동시에 정답을 보내도 단어마다 한 명만 차지하고 점수가 어긋나지 않는다")
    void crosswordEachWordClaimedOnce() throws Exception {
        int rooms = 50;
        int answersPerPlayer = 25;
        List<Puzzle.Word> words = PuzzleBank.everyday().words();
        int answersPerWord = answersPerPlayer * 4;

        for (int r = 0; r < rooms; r++) {
            RecordingNotifier notifier = new RecordingNotifier(0);
            RoomService service = newService(new MutableClock(), notifier);
            JoinResult host = service.create(GameType.CROSSWORD, "p0");
            List<JoinResult> players = new ArrayList<>(List.of(host));
            for (int i = 1; i < 4; i++) {
                players.add(service.join(host.code(), "p" + i));
            }
            service.start(host.code(), host.token());
            notifier.events.clear();

            Map<String, AtomicInteger> winsPerWord = new ConcurrentHashMap<>();
            AtomicInteger unexpected = new AtomicInteger();
            runConcurrently(words.size() * answersPerWord, i -> {
                Puzzle.Word word = words.get(i / answersPerWord);
                JoinResult player = players.get(i % 4);
                try {
                    service.action(host.code(), player.token(), "answer",
                            Map.of("wordId", word.id(), "text", word.answer()));
                    winsPerWord.computeIfAbsent(word.id(), k -> new AtomicInteger()).incrementAndGet();
                } catch (GameException expected) {
                    // 이미 풀린 단어
                } catch (RuntimeException e) {
                    unexpected.incrementAndGet();
                }
            });

            assertEquals(0, unexpected.get(), "규칙 위반이 아닌 예외가 발생하면 안 된다");
            winsPerWord.forEach((wordId, wins) -> assertEquals(1, wins.get(), wordId + "를 두 명 이상이 차지했다"));

            List<Map<String, Object>> claimed = notifier.events.stream()
                    .filter(e -> "claimed".equals(e.get("type")))
                    .toList();
            assertEquals(winsPerWord.size(), claimed.size(), "성공 응답 수와 정답 이벤트 수가 같아야 한다");

            int eventPoints = claimed.stream().mapToInt(e -> intValue(e.get("points"))).sum();
            @SuppressWarnings("unchecked")
            Map<String, Object> scores = (Map<String, Object>) service.view(host.code(), host.token()).game().get("scores");
            int scoreSum = scores.values().stream().mapToInt(RoomServiceConcurrencyTest::intValue).sum();
            assertEquals(eventPoints, scoreSum, "획득 점수 합계와 점수판 합계가 같아야 한다");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("오목: 두 사람이 같은 칸에 동시에 100번씩 두어도 돌은 하나만 놓인다")
    void omokSameCellOnlyOneStone() throws Exception {
        for (int r = 0; r < 100; r++) {
            RoomService service = newService();
            JoinResult black = service.create(GameType.OMOK, "흑");
            JoinResult white = service.join(black.code(), "백");
            service.start(black.code(), black.token());

            AtomicInteger placed = new AtomicInteger();
            runConcurrently(200, i -> {
                JoinResult player = i % 2 == 0 ? black : white;
                try {
                    service.action(black.code(), player.token(), "place", Map.of("x", 7, "y", 7));
                    placed.incrementAndGet();
                } catch (GameException expected) {
                    // 상대 차례이거나 이미 돌이 놓인 자리
                }
            });

            assertEquals(1, placed.get());
            assertEquals(1, intValue(service.view(black.code(), black.token()).game().get("moveCount")));
        }
    }

    @Test
    @Timeout(120)
    @DisplayName("오목: 무작위 칸에 동시에 착수가 몰려도 차례가 교대로 지켜지고 판과 응답이 일치한다")
    @SuppressWarnings("unchecked")
    void omokRandomMovesKeepTurnOrder() throws Exception {
        for (int r = 0; r < 100; r++) {
            RoomService service = newService();
            JoinResult black = service.create(GameType.OMOK, "흑");
            JoinResult white = service.join(black.code(), "백");
            service.start(black.code(), black.token());

            AtomicInteger blackPlaced = new AtomicInteger();
            AtomicInteger whitePlaced = new AtomicInteger();
            long seed = r;
            runConcurrently(600, i -> {
                Random random = new Random(seed * 10_000 + i);
                boolean isBlack = i % 2 == 0;
                JoinResult player = isBlack ? black : white;
                try {
                    service.action(black.code(), player.token(), "place",
                            Map.of("x", random.nextInt(15), "y", random.nextInt(15)));
                    (isBlack ? blackPlaced : whitePlaced).incrementAndGet();
                } catch (GameException expected) {
                    // 상대 차례, 이미 놓인 자리, 끝난 게임
                }
            });

            Map<String, Object> game = service.view(black.code(), black.token()).game();
            int blackStones = 0;
            int whiteStones = 0;
            for (List<Integer> row : (List<List<Integer>>) game.get("board")) {
                for (int cell : row) {
                    if (cell == 1) blackStones++;
                    if (cell == 2) whiteStones++;
                }
            }
            assertEquals(blackPlaced.get(), blackStones, "흑의 성공 응답 수와 판의 흑돌 수");
            assertEquals(whitePlaced.get(), whiteStones, "백의 성공 응답 수와 판의 백돌 수");
            assertEquals(blackStones + whiteStones, intValue(game.get("moveCount")));
            assertTrue(blackStones == whiteStones || blackStones == whiteStones + 1, "흑과 백은 번갈아 두어야 한다");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("정원 경쟁: 100명이 동시에 들어와도 오목방은 2명, 십자격돌방은 4명을 넘지 않는다")
    void concurrentJoinsNeverExceedCapacity() throws Exception {
        for (int r = 0; r < 100; r++) {
            RoomService service = newService();
            JoinResult omok = service.create(GameType.OMOK, "방장");
            JoinResult crossword = service.create(GameType.CROSSWORD, "방장");

            AtomicInteger omokJoined = new AtomicInteger();
            AtomicInteger crosswordJoined = new AtomicInteger();
            runConcurrently(200, i -> {
                try {
                    if (i % 2 == 0) {
                        service.join(omok.code(), "o" + i);
                        omokJoined.incrementAndGet();
                    } else {
                        service.join(crossword.code(), "c" + i);
                        crosswordJoined.incrementAndGet();
                    }
                } catch (GameException expected) {
                    // 방이 가득 참
                }
            });

            assertEquals(1, omokJoined.get());
            assertEquals(3, crosswordJoined.get());
            assertEquals(2, service.view(omok.code(), omok.token()).players().size());
            assertEquals(4, service.view(crossword.code(), crossword.token()).players().size());
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("그림맞추기: 같은 정답을 동시에 여러 번 보내도 한 사람은 한 번만 점수를 받는다")
    void drawingCorrectGuessScoredOnce() throws Exception {
        for (int r = 0; r < 100; r++) {
            RecordingNotifier notifier = new RecordingNotifier(0);
            RoomService service = newService(new MutableClock(), notifier);
            JoinResult host = service.create(GameType.DRAWING, "a");
            List<JoinResult> players = new ArrayList<>(List.of(host));
            for (int i = 1; i < 5; i++) {
                players.add(service.join(host.code(), "p" + i));
            }
            service.start(host.code(), host.token());

            JoinResult drawer = null;
            String word = null;
            for (JoinResult player : players) {
                Object privateWord = service.privateState(host.code(), player.token()).get("word");
                if (privateWord != null) {
                    drawer = player;
                    word = (String) privateWord;
                }
            }
            assertNotNull(word, "그리는 사람에게만 제시어가 보여야 한다");

            JoinResult theDrawer = drawer;
            String answer = word;
            List<JoinResult> guessers = players.stream().filter(p -> p != theDrawer).toList();
            notifier.events.clear();
            runConcurrently(guessers.size() * 50, i -> {
                try {
                    service.action(host.code(), guessers.get(i % guessers.size()).token(), "guess", Map.of("text", answer));
                } catch (GameException expected) {
                    // 이미 맞힌 사람
                }
            });

            Map<Object, Long> correctPerPlayer = new HashMap<>();
            notifier.events.stream()
                    .filter(e -> "correct".equals(e.get("type")))
                    .forEach(e -> correctPerPlayer.merge(e.get("playerId"), 1L, Long::sum));
            assertEquals(guessers.size(), correctPerPlayer.size(), "맞히는 사람 모두 한 번씩 정답 처리");
            correctPerPlayer.values().forEach(count -> assertEquals(1L, count, "한 사람이 두 번 이상 점수를 받았다"));
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("재접속: 게임 중인 방 200개에서 동시에 끊겼다가 같은 토큰으로 돌아오면 자리와 판, 차례가 그대로다")
    void concurrentReconnectRestoresSeatAndBoard() throws Exception {
        int rooms = 200;
        MutableClock clock = new MutableClock();
        RoomService service = newService(clock, new RecordingNotifier(0));
        List<JoinResult[]> pairs = new ArrayList<>();
        Map<String, String> boardBefore = new HashMap<>();
        for (int r = 0; r < rooms; r++) {
            JoinResult black = service.create(GameType.OMOK, "흑");
            JoinResult white = service.join(black.code(), "백");
            service.connect(black.code(), black.token(), "black-" + r);
            service.connect(black.code(), white.token(), "white-" + r);
            service.start(black.code(), black.token());
            service.action(black.code(), black.token(), "place", Map.of("x", 7, "y", 7));
            service.action(black.code(), white.token(), "place", Map.of("x", 8, "y", 8));
            service.action(black.code(), black.token(), "place", Map.of("x", 6, "y", 6));
            pairs.add(new JoinResult[]{black, white});
            boardBefore.put(black.code(), String.valueOf(service.view(black.code(), black.token()).game().get("board")));
        }

        // 모든 방의 백이 동시에 끊기고(새로고침), 50초 동안 타이머가 돈 뒤 동시에 같은 토큰으로 돌아온다.
        runConcurrently(rooms, i -> service.disconnect("white-" + i));
        clock.plusSeconds(50);
        service.tickAll();
        runConcurrently(rooms, i -> {
            JoinResult[] pair = pairs.get(i);
            service.connect(pair[0].code(), pair[1].token(), "white-reconnected-" + i);
        });

        for (JoinResult[] pair : pairs) {
            JoinResult black = pair[0];
            JoinResult white = pair[1];
            RoomView view = service.view(black.code(), white.token());
            assertEquals(RoomStatus.PLAYING, view.status());
            assertTrue(view.players().stream().anyMatch(p -> p.id().equals(white.playerId()) && p.connected()),
                    "같은 자리로 돌아와야 한다");
            assertEquals(boardBefore.get(black.code()), String.valueOf(view.game().get("board")), "판이 그대로여야 한다");
            assertEquals(white.playerId(), view.game().get("turnPlayerId"), "백의 차례가 유지되어야 한다");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("방 단위 락: 같은 방 요청은 순서대로, 서로 다른 방 요청은 동시에 처리된다")
    void differentRoomsRunInParallel() throws Exception {
        int requests = 100;
        long sendDelayMs = 5; // 상태 전송에 걸리는 시간을 가정

        RoomService oneRoomService = newService(new MutableClock(), new RecordingNotifier(sendDelayMs));
        JoinResult only = oneRoomService.create(GameType.CROSSWORD, "p0");
        long t0 = System.nanoTime();
        runConcurrently(requests, i -> oneRoomService.chat(only.code(), only.token(), "안녕" + i));
        long sameRoomMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        RoomService manyRoomService = newService(new MutableClock(), new RecordingNotifier(sendDelayMs));
        List<JoinResult> hosts = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            hosts.add(manyRoomService.create(GameType.CROSSWORD, "p0"));
        }
        long t1 = System.nanoTime();
        runConcurrently(requests, i -> manyRoomService.chat(hosts.get(i).code(), hosts.get(i).token(), "안녕" + i));
        long manyRoomsMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1);

        // 같은 방은 직렬 처리되므로 최소 (요청 수 × 전송 시간)이 걸린다.
        assertTrue(sameRoomMs >= requests * sendDelayMs, "같은 방 요청은 하나씩 처리되어야 한다: " + sameRoomMs + "ms");
        // 서로 다른 방은 서로를 기다리지 않는다.
        assertTrue(manyRoomsMs * 3 < sameRoomMs,
                "서로 다른 방은 병렬로 처리되어야 한다: 같은 방 " + sameRoomMs + "ms, 다른 방 " + manyRoomsMs + "ms");
    }

    @Test
    @Timeout(60)
    @DisplayName("비공개 정보: 동시 채팅 중 정답을 그대로 입력해도 다른 사람에게 제시어와 토큰이 노출되지 않는다")
    void secretsNeverLeakUnderConcurrentChat() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RoomService service = newService(new MutableClock(), notifier);
        JoinResult host = service.create(GameType.DRAWING, "a");
        List<JoinResult> players = new ArrayList<>(List.of(host));
        for (int i = 1; i < 10; i++) {
            players.add(service.join(host.code(), "p" + i));
        }
        service.start(host.code(), host.token());

        String word = null;
        for (JoinResult player : players) {
            Object privateWord = service.privateState(host.code(), player.token()).get("word");
            if (privateWord != null) {
                word = (String) privateWord;
            }
        }
        assertNotNull(word);
        String answer = word;
        notifier.publicMessages.clear();

        runConcurrently(1_000, i -> {
            try {
                service.chat(host.code(), players.get(i % 10).token(), i % 2 == 0 ? answer : "틀린답" + i);
            } catch (GameException expected) {
                // 이미 맞힌 사람이 정답을 다시 말하는 경우
            }
        });

        List<String> messages = List.copyOf(notifier.publicMessages);
        assertFalse(messages.isEmpty());
        for (String message : messages) {
            assertFalse(message.contains(answer), "공개 메시지에 제시어가 들어가면 안 된다");
            for (JoinResult player : players) {
                assertFalse(message.contains(player.token()), "공개 메시지에 토큰이 들어가면 안 된다");
            }
        }
    }
}
