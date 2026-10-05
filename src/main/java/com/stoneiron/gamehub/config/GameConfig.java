package com.stoneiron.gamehub.config;

import com.stoneiron.gamehub.game.GameRule;
import com.stoneiron.gamehub.game.crossword.CrosswordRule;
import com.stoneiron.gamehub.game.crossword.PuzzleBank;
import com.stoneiron.gamehub.game.drawing.DrawingRule;
import com.stoneiron.gamehub.game.drawing.WordBank;
import com.stoneiron.gamehub.game.omok.OmokRule;
import com.stoneiron.gamehub.room.RoomCodeGenerator;
import com.stoneiron.gamehub.room.RoomNotifier;
import com.stoneiron.gamehub.room.RoomService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;

/**
 * 게임 규칙과 RoomService를 조립한다.
 * 게임 규칙 클래스는 Spring에 의존하지 않도록 여기서만 빈으로 등록한다.
 * 새 게임을 추가하면 아래에 GameRule 빈 하나만 더하면 된다.
 */
@Configuration
public class GameConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public GameRule omokRule() {
        return new OmokRule();
    }

    @Bean
    public GameRule drawingRule() {
        return new DrawingRule(new WordBank(), new SecureRandom());
    }

    @Bean
    public GameRule crosswordRule() {
        return new CrosswordRule(new PuzzleBank(), new SecureRandom());
    }

    @Bean
    public RoomService roomService(List<GameRule> rules, RoomNotifier notifier, Clock clock) {
        return new RoomService(rules, notifier, clock, new RoomCodeGenerator());
    }
}
