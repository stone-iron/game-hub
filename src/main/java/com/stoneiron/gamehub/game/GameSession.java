package com.stoneiron.gamehub.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 진행 중인 한 판의 게임.
 *
 * <p>모든 규칙 검사와 승패 판정은 이 클래스 안에서만 이루어진다.
 * 클라이언트는 "여기에 두겠다", "이 단어가 정답이다" 같은 의도만 보내고,
 * 결과는 항상 서버가 결정한다.</p>
 *
 * <p>구현체는 스레드 안전하지 않다. 호출하는 쪽(RoomService)이 방 단위 락을 잡은 상태에서만 호출한다.
 * 덕분에 게임 규칙 코드는 동시성을 신경 쓰지 않고 규칙에만 집중할 수 있다.</p>
 */
public interface GameSession {

    /** 플레이어의 행동을 처리한다. 규칙에 어긋나면 {@link GameException}을 던진다. */
    ActionResult handle(String playerId, String type, Map<String, Object> data, Instant now);

    /** 주기적으로 호출된다. 제한 시간 처리처럼 시간이 흐르면 일어나는 일을 담당한다. */
    default ActionResult tick(Instant now) {
        return ActionResult.none();
    }

    /**
     * 게임이 진행 중일 때 새 플레이어가 들어왔을 때 호출된다.
     * 중간 입장을 지원하지 않는 게임은 기본 구현대로 거절한다.
     */
    default ActionResult onPlayerJoined(PlayerInfo player, Instant now) {
        throw new GameException("이미 게임이 진행 중인 방입니다.");
    }

    /** 플레이어가 게임 도중 방을 나갔을 때 호출된다. */
    default ActionResult onPlayerLeft(String playerId, Instant now) {
        return ActionResult.none();
    }

    boolean isFinished();

    /** 모든 플레이어에게 공개해도 되는 상태. 정답처럼 숨겨야 할 값은 절대 넣지 않는다. */
    Map<String, Object> publicState();

    /** 특정 플레이어에게만 보내는 상태. 예) 그림맞추기 출제자에게만 보이는 제시어 */
    default Map<String, Object> privateState(String playerId) {
        return Map.of();
    }

    /** 게임이 끝났을 때의 최종 순위. 1등부터 순서대로 플레이어 ID를 담는다. */
    List<String> ranking();
}
