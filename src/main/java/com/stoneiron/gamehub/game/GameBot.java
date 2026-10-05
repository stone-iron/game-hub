package com.stoneiron.gamehub.game;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 사람 대신 행동하는 컴퓨터 플레이어.
 *
 * <p>봇도 사람과 똑같이 {@link GameSession#handle}을 통해서만 행동한다.
 * 그래서 봇이 둔 수도 사람이 둔 수와 같은 규칙 검사를 거친다.</p>
 */
public interface GameBot {

    String playerId();

    String nickname();

    /** 지금 행동할 차례이고 생각할 시간이 지났다면 할 행동을 돌려준다. */
    Optional<BotAction> decide(GameSession session, Instant now);

    record BotAction(String type, Map<String, Object> data) {
    }
}
