package com.stoneiron.gamehub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 *
 * <ul>
 *     <li>/app/rooms/{code}/... : 클라이언트 → 서버 (행동 전송)</li>
 *     <li>/topic/rooms/{code} : 서버 → 방 전체 (공개 상태)</li>
 *     <li>/topic/rooms/{code}/events : 서버 → 방 전체 (그림 획, 채팅 같은 가벼운 이벤트)</li>
 *     <li>/topic/rooms/{code}/private/{token} : 서버 → 본인 (제시어, 오류 메시지)</li>
 * </ul>
 *
 * <p>허용 Origin을 따로 지정하지 않아 같은 출처(이 서버가 내려준 화면)에서만 연결할 수 있다.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
