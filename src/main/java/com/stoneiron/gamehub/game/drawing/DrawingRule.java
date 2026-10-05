package com.stoneiron.gamehub.game.drawing;

import com.stoneiron.gamehub.game.GameRule;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.GameType;
import com.stoneiron.gamehub.game.PlayerInfo;

import java.time.Instant;
import java.util.List;
import java.util.Random;

public class DrawingRule implements GameRule {

    private final WordBank wordBank;
    private final Random random;

    public DrawingRule(WordBank wordBank, Random random) {
        this.wordBank = wordBank;
        this.random = random;
    }

    @Override
    public GameType type() {
        return GameType.DRAWING;
    }

    @Override
    public GameSession create(List<PlayerInfo> players, Instant now) {
        // 게임 중에 들어오는 사람도 차례가 오도록 최대 인원의 두 배만큼 제시어를 준비한다.
        int count = Math.max(players.size(), GameType.DRAWING.maxPlayers() * 2);
        return new DrawingSession(players, wordBank.draw(count, random), now);
    }
}
