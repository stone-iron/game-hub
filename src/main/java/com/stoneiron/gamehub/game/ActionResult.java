package com.stoneiron.gamehub.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 게임 세션이 요청을 처리한 결과.
 *
 * <p>stateChanged가 true이면 방 전체 상태를 다시 보내고,
 * events는 그림 획처럼 상태 전체를 다시 보내기에는 잦은 변경을 가볍게 전달할 때 쓴다.</p>
 */
public record ActionResult(boolean stateChanged, List<Map<String, Object>> events) {

    public static ActionResult none() {
        return new ActionResult(false, List.of());
    }

    public static ActionResult changed() {
        return new ActionResult(true, List.of());
    }

    public static ActionResult changed(List<Map<String, Object>> events) {
        return new ActionResult(true, List.copyOf(events));
    }

    public static ActionResult eventsOnly(List<Map<String, Object>> events) {
        return new ActionResult(false, List.copyOf(events));
    }

    /** 두 결과를 합친다. 타이머 처리처럼 여러 단계가 한 번에 일어날 때 사용한다. */
    public ActionResult merge(ActionResult other) {
        List<Map<String, Object>> merged = new ArrayList<>(events);
        merged.addAll(other.events);
        return new ActionResult(stateChanged || other.stateChanged, List.copyOf(merged));
    }
}
