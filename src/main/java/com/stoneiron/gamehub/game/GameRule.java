package com.stoneiron.gamehub.game;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 게임 종류별로 새 판을 만들어 주는 규칙.
 *
 * <p>방 관리, 입장 코드, 재접속, 타이머 같은 공통 기능은 RoomService가 맡고,
 * 게임마다 다른 것은 이 인터페이스의 구현체뿐이다.
 * 새 게임을 추가할 때는 GameRule과 GameSession 구현체만 만들면 된다.</p>
 */
public interface GameRule {

    GameType type();

    GameSession create(List<PlayerInfo> players, Instant now);

    /**
     * 혼자하기에서 상대해 줄 컴퓨터를 만든다. 컴퓨터 상대가 없는 게임(혼자 푸는 게임 포함)은 비워 둔다.
     *
     * @param level 게임마다 정한 난이도 이름. 없으면 기본 난이도
     */
    default Optional<GameBot> createBot(String playerId, String level) {
        return Optional.empty();
    }
}
