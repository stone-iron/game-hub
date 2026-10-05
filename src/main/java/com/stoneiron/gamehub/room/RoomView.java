package com.stoneiron.gamehub.room;

import java.util.List;
import java.util.Map;

/**
 * 방 안의 모든 사람에게 공개해도 되는 정보. 토큰이나 정답 같은 값은 들어가지 않는다.
 *
 * @param autoStartAt 자동 시작이 예약된 시각(밀리초). 예약이 없으면 null
 */
public record RoomView(
        String code,
        String gameType,
        String gameName,
        RoomStatus status,
        String hostId,
        int minPlayers,
        int maxPlayers,
        boolean solo,
        boolean autoStart,
        Long autoStartAt,
        List<PlayerView> players,
        Map<String, Object> game,
        List<String> ranking
) {

    public record PlayerView(String id, String nickname, boolean connected, boolean host, boolean bot) {
    }
}
