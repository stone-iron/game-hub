package com.stoneiron.gamehub.game.omok;

import com.stoneiron.gamehub.game.GameBot;
import com.stoneiron.gamehub.game.GameSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 혼자하기에서 상대해 주는 오목 컴퓨터.
 * 바로 두면 어색하므로 차례가 온 뒤 잠깐 생각하는 시간을 둔다.
 */
public class OmokBot implements GameBot {

    static final Duration THINK_TIME = Duration.ofMillis(700);

    private final String playerId;
    private final OmokLevel level;
    private final OmokAi ai;

    public OmokBot(String playerId, OmokLevel level, Random random) {
        this.playerId = playerId;
        this.level = level;
        this.ai = new OmokAi(level, random);
    }

    @Override
    public String playerId() {
        return playerId;
    }

    @Override
    public String nickname() {
        return "컴퓨터 · " + level.displayName();
    }

    public OmokLevel level() {
        return level;
    }

    @Override
    public Optional<BotAction> decide(GameSession session, Instant now) {
        if (!(session instanceof OmokSession omok) || omok.isFinished()) {
            return Optional.empty();
        }
        if (!playerId.equals(omok.turnPlayerId())) {
            return Optional.empty();
        }
        if (Duration.between(omok.turnStartedAt(), now).compareTo(THINK_TIME) < 0) {
            return Optional.empty();
        }
        int[] move = ai.choose(omok.boardSnapshot(), omok.stoneOf(playerId));
        return Optional.of(new BotAction("place", Map.of("x", move[0], "y", move[1])));
    }
}
