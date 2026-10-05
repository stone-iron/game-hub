package com.stoneiron.gamehub.room;

import java.util.List;
import java.util.Map;

/**
 * 방에서 일어난 변화를 클라이언트에게 전달하는 통로.
 *
 * <p>RoomService는 전달 방식(WebSocket 등)을 모르고 이 인터페이스에만 의존한다.
 * 덕분에 테스트에서는 메시지를 모아 두는 가짜 구현으로 바꿔 끼울 수 있다.</p>
 */
public interface RoomNotifier {

    /** 방 전체에 공개 상태를 보낸다. */
    void roomUpdated(String code, RoomView view);

    /** 특정 플레이어에게만 비공개 상태를 보낸다. 주소에 토큰이 들어가므로 본인만 구독할 수 있다. */
    void privateUpdated(String code, String token, Map<String, Object> privateState);

    /** 그림 획, 채팅처럼 상태 전체를 다시 보낼 필요가 없는 가벼운 이벤트를 보낸다. */
    void events(String code, List<Map<String, Object>> events);

    /** 방이 사라졌음을 알린다. */
    void roomClosed(String code);
}
