package com.stoneiron.gamehub.room;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RoomNotifier의 WebSocket(STOMP) 구현.
 */
@Component
public class StompRoomNotifier implements RoomNotifier {

    private final SimpMessagingTemplate messaging;

    public StompRoomNotifier(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public static String roomTopic(String code) {
        return "/topic/rooms/" + code;
    }

    public static String privateTopic(String code, String token) {
        return "/topic/rooms/" + code + "/private/" + token;
    }

    @Override
    public void roomUpdated(String code, RoomView view) {
        messaging.convertAndSend(roomTopic(code), view);
    }

    @Override
    public void privateUpdated(String code, String token, Map<String, Object> privateState) {
        messaging.convertAndSend(privateTopic(code, token), (Object) Map.of("type", "private", "state", privateState));
    }

    @Override
    public void events(String code, List<Map<String, Object>> events) {
        messaging.convertAndSend(roomTopic(code) + "/events", events);
    }

    @Override
    public void roomClosed(String code) {
        messaging.convertAndSend(roomTopic(code) + "/events", List.of(Map.of("type", "closed")));
    }

    public void sendError(String code, String token, String message) {
        messaging.convertAndSend(privateTopic(code, token), (Object) Map.of("type", "error", "message", message));
    }
}
