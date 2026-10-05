package com.stoneiron.gamehub.game.crossword;

import com.stoneiron.gamehub.game.GameRule;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.GameType;
import com.stoneiron.gamehub.game.PlayerInfo;

import java.time.Instant;
import java.util.List;
import java.util.Random;

public class CrosswordRule implements GameRule {

    private final PuzzleBank puzzleBank;
    private final Random random;

    public CrosswordRule(PuzzleBank puzzleBank, Random random) {
        this.puzzleBank = puzzleBank;
        this.random = random;
    }

    @Override
    public GameType type() {
        return GameType.CROSSWORD;
    }

    @Override
    public GameSession create(List<PlayerInfo> players, Instant now) {
        return new CrosswordSession(puzzleBank.pick(random), players, now);
    }
}
