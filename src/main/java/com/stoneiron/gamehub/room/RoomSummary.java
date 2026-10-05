package com.stoneiron.gamehub.room;

/**
 * 로비의 "열린 방" 목록에 보여줄 요약 정보.
 *
 * @param playing 게임 중이지만 중간에 들어갈 수 있는 방이면 true
 */
public record RoomSummary(String code, String gameType, String gameName, String hostNickname,
                          int playerCount, int maxPlayers, boolean playing) {
}
