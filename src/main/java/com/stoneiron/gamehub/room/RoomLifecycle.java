package com.stoneiron.gamehub.room;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 시간 흐름과 연결 끊김을 RoomService에 전달한다.
 */
@Component
public class RoomLifecycle {

    private final RoomService roomService;

    public RoomLifecycle(RoomService roomService) {
        this.roomService = roomService;
    }

    /** 제한 시간, 연결이 끊긴 사람 정리를 0.5초마다 확인한다. */
    @Scheduled(fixedDelay = 500)
    public void tick() {
        roomService.tickAll();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        roomService.disconnect(event.getSessionId());
    }
}
