package com.stoneiron.gamehub.room;

import com.stoneiron.gamehub.game.GameType;
import com.stoneiron.gamehub.member.AuthController;
import com.stoneiron.gamehub.member.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 방 만들기·입장·나가기·조회 API. 게임 중 행동은 WebSocket({@link RoomSocketController})으로 주고받는다.
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private static final String AUTH = "Authorization";

    private final RoomService roomService;
    private final AuthService authService;

    public RoomController(RoomService roomService, AuthService authService) {
        this.roomService = roomService;
        this.authService = authService;
    }

    /** 로그인했으면 회원 닉네임으로, 아니면 입력한 닉네임(회원 닉네임과 겹치지 않아야 함)으로 참가한다. */
    private String nickname(String authorization, String requested) {
        return authService.resolvePlayNickname(AuthController.bearer(authorization), requested);
    }

    public record GameInfo(String type, String name, int minPlayers, int maxPlayers,
                           boolean soloPlayable, boolean autoStart) {
    }

    public record CreateRoomRequest(@NotNull GameType gameType,
                                    @NotBlank @Size(max = 12) String nickname) {
    }

    public record SoloRequest(@NotNull GameType gameType,
                              @NotBlank @Size(max = 12) String nickname,
                              @Size(max = 20) String level) {
    }

    public record JoinRoomRequest(@NotBlank @Size(max = 12) String nickname) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    @GetMapping("/games")
    public List<GameInfo> games() {
        return Arrays.stream(GameType.values())
                .map(t -> new GameInfo(t.name(), t.displayName(), t.minPlayers(), t.maxPlayers(),
                        t.soloPlayable(), t.autoStart()))
                .toList();
    }

    @GetMapping("/rooms")
    public List<RoomSummary> openRooms() {
        return roomService.openRooms();
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResult create(@RequestHeader(value = AUTH, required = false) String authorization,
                             @Valid @RequestBody CreateRoomRequest request) {
        return roomService.create(request.gameType(), nickname(authorization, request.nickname()));
    }

    /** 혼자하기: 컴퓨터 상대(오목) 또는 혼자 풀기(십자격돌) 방을 만들고 바로 시작한다. */
    @PostMapping("/rooms/solo")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinResult createSolo(@RequestHeader(value = AUTH, required = false) String authorization,
                                 @Valid @RequestBody SoloRequest request) {
        return roomService.createSolo(request.gameType(), nickname(authorization, request.nickname()), request.level());
    }

    @PostMapping("/rooms/{code}/join")
    public JoinResult join(@RequestHeader(value = AUTH, required = false) String authorization,
                           @PathVariable String code, @Valid @RequestBody JoinRoomRequest request) {
        return roomService.join(code, nickname(authorization, request.nickname()));
    }

    /** 새로고침 후 저장해 둔 토큰이 아직 유효한지 확인한다. */
    @GetMapping("/rooms/{code}")
    public RoomView view(@PathVariable String code, @RequestParam String token) {
        return roomService.view(code, token);
    }

    @PostMapping("/rooms/{code}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable String code, @Valid @RequestBody TokenRequest request) {
        roomService.leave(code, request.token());
    }
}
