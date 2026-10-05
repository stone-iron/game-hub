package com.stoneiron.gamehub.game.omok;

import com.stoneiron.gamehub.game.GameBot;
import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.game.GameRule;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.GameType;
import com.stoneiron.gamehub.game.PlayerInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class OmokRule implements GameRule {

    private final Random random;

    public OmokRule() {
        this(new Random());
    }

    public OmokRule(Random random) {
        this.random = random;
    }

    @Override
    public GameType type() {
        return GameType.OMOK;
    }

    @Override
    public GameSession create(List<PlayerInfo> players, Instant now) {
        return new OmokSession(players, now);
    }

    @Override
    public Optional<GameBot> createBot(String playerId, String level) {
        OmokLevel omokLevel;
        try {
            omokLevel = level == null ? OmokLevel.INTERMEDIATE : OmokLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            throw new GameException("알 수 없는 난이도입니다.");
        }
        return Optional.of(new OmokBot(playerId, omokLevel, random));
    }
}
