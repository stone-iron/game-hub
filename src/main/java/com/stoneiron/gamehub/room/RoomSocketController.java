package com.stoneiron.gamehub.room;

import com.stoneiron.gamehub.game.GameException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 게임 중 주고받는 WebSocket 메시지를 처리한다.
 * 모든 요청에 토큰을 담아 보내고, 서버는 토큰으로 누가 보낸 요청인지 확인한다.
 * 규칙 위반은 보낸 사람에게만 오류 메시지로 돌려준다.
 */
@Controller
public class RoomSocketController {

    private final RoomService roomService;
    private final StompRoomNotifier notifier;

    public RoomSocketController(RoomService roomService, StompRoomNotifier notifier) {
        this.roomService = roomService;
        this.notifier = notifier;
    }

    public record SocketRequest(String token, String type, Map<String, Object> data) {
    }

    @MessageMapping("/rooms/{code}/hello")
    public void hello(@DestinationVariable String code, @Payload SocketRequest request,
                      @Header("simpSessionId") String sessionId) {
        run(code, request, () -> roomService.connect(code, request.token(), sessionId));
    }

    @MessageMapping("/rooms/{code}/start")
    public void start(@DestinationVariable String code, @Payload SocketRequest request) {
        run(code, request, () -> roomService.start(code, request.token()));
    }

    @MessageMapping("/rooms/{code}/action")
    public void action(@DestinationVariable String code, @Payload SocketRequest request) {
        run(code, request, () -> roomService.action(code, request.token(), request.type(), request.data()));
    }

    @MessageMapping("/rooms/{code}/chat")
    public void chat(@DestinationVariable String code, @Payload SocketRequest request) {
        run(code, request, () -> {
            Object text = request.data() == null ? null : request.data().get("text");
            roomService.chat(code, request.token(), text == null ? null : text.toString());
        });
    }

    @MessageMapping("/rooms/{code}/reset")
    public void reset(@DestinationVariable String code, @Payload SocketRequest request) {
        run(code, request, () -> roomService.reset(code, request.token()));
    }

    private void run(String code, SocketRequest request, Runnable work) {
        if (request == null || request.token() == null) {
            return;
        }
        try {
            work.run();
        } catch (GameException e) {
            notifier.sendError(code.toUpperCase(), request.token(), e.getMessage());
        }
    }
}
