package com.stoneiron.gamehub.game;

/**
 * 규칙 위반이나 잘못된 요청을 사용자에게 알려야 할 때 던지는 예외.
 * 메시지는 그대로 화면에 표시되므로 사용자가 이해할 수 있는 문장으로 작성한다.
 */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }
}
