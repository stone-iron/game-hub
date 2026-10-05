/* 겜허브 클라이언트
 * 서버가 모든 규칙과 판정을 결정하고, 이 화면은 서버가 보내 준 상태를 그리고 사용자의 의도만 전송한다.
 * 사용자 입력(닉네임, 채팅)은 항상 textContent로 넣어 스크립트가 실행되지 않게 한다.
 */
(() => {
    'use strict';

    const GAMES = {
        OMOK: {
            icon: '⚫', desc: '친구보다 먼저 다섯 돌을 이어요.',
            guide: '흑이 먼저 두고 번갈아 한 수씩 둬요. 가로·세로·대각선으로 다섯 개 이상 먼저 이으면 이겨요. 한 수에 60초가 지나면 상대가 이겨요.',
        },
        DRAWING: {
            icon: '🎨', desc: '한 명이 그리고, 나머지는 채팅으로 맞혀요.',
            guide: '두 명이 모이면 자동으로 시작해요. 들어온 순서대로 돌아가며 제시어를 그리고, 나머지는 채팅으로 정답을 입력해요. 빨리 맞힐수록 점수가 커요. 게임 중에도 들어올 수 있어요.',
        },
        CROSSWORD: {
            icon: '✚', desc: '십자말을 풀고, 먼저 맞힌 사람이 단어를 차지해요.',
            guide: '7×7 판의 빈칸을 누르면 문제가 나와요. 정답을 맞히면 교차하는 글자가 공개돼요. 오답을 내면 2초 동안 답을 낼 수 없어요. 혼자 풀 수도 있어요.',
        },
    };
    const LEVELS = [
        { id: 'BEGINNER', name: '초급', desc: '줄을 만들고 뻔한 위협은 막아요. 대각선은 잘 놓쳐요.' },
        { id: 'INTERMEDIATE', name: '중급', desc: '대부분의 위협을 막아요. 띄운 모양은 잘 못 읽어요.' },
        { id: 'ADVANCED', name: '고급', desc: '한 칸 띄운 모양까지 읽는 가장 센 컴퓨터예요.' },
    ];
    const COLORS = ['#222222', '#ffffff', '#e8a7a7', '#f2c9a1', '#f3e3a4', '#b7d9a8', '#a8cbe6', '#c4b0e0',
        '#d9541e', '#2f6fd6', '#2f8f5b', '#8b5a2b'];
    const WIDTHS = [2, 4, 8, 14, 24];
    const TOOLS = [
        { id: 'pen', name: '펜', icon: '✏️' },
        { id: 'eraser', name: '지우개', icon: '🧽' },
        { id: 'fill', name: '채우기', icon: '🪣' },
        { id: 'line', name: '직선', icon: '╱' },
        { id: 'rect', name: '사각형', icon: '▢' },
        { id: 'circle', name: '원', icon: '◯' },
    ];
    const STORAGE_KEY = 'game-hub.session';
    const NICK_KEY = 'game-hub.nickname';
    const AUTH_KEY = 'game-hub.auth';

    const $ = (sel, root = document) => root.querySelector(sel);
    const el = (tag, props = {}, ...children) => {
        const node = document.createElement(tag);
        for (const [key, value] of Object.entries(props)) {
            if (key === 'class') node.className = value;
            else if (key === 'text') node.textContent = value;
            else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
            else if (key === 'dataset') Object.assign(node.dataset, value);
            else if (value !== undefined && value !== null && value !== false) node.setAttribute(key, value === true ? '' : value);
        }
        for (const child of children.flat()) {
            if (child === null || child === undefined || child === false) continue;
            node.append(child instanceof Node ? child : document.createTextNode(String(child)));
        }
        return node;
    };

    const state = {
        games: [],       // 서버가 알려 준 게임 목록
        modalGame: null, // 게임 선택 창에 열린 게임
        soloGame: null,
        soloLevel: 'INTERMEDIATE',
        me: null,        // { code, playerId, token, gameType }
        room: null,      // 서버가 보내 준 RoomView
        priv: {},        // 본인에게만 온 비공개 상태
        client: null,
        stageKey: null,
        game: null,      // 현재 화면에 올라간 게임 렌더러
        roomsTimer: null,
        auth: null,      // 로그인했으면 { token, member: { id, username, nickname } }
    };

    // ------------------------------------------------------------ 저장소 (사생활 모드 등에서 실패해도 동작)
    const storage = {
        get(key) { try { return JSON.parse(localStorage.getItem(key)); } catch { return null; } },
        set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 무시 */ } },
        remove(key) { try { localStorage.removeItem(key); } catch { /* 무시 */ } },
    };

    // ------------------------------------------------------------ 공통 UI
    let toastTimer;
    function toast(message) {
        const box = $('#toast');
        box.textContent = message;
        box.classList.remove('hidden');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => box.classList.add('hidden'), 2600);
    }

    async function api(method, url, body) {
        const headers = body ? { 'Content-Type': 'application/json' } : {};
        if (state.auth) headers.Authorization = `Bearer ${state.auth.token}`;
        let res;
        try {
            res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
        } catch {
            throw Object.assign(new Error('서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.'), { status: 0 });
        }
        if (res.status === 204) return null;
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw Object.assign(new Error(data.message || '요청을 처리하지 못했습니다.'), { status: res.status });
        return data;
    }

    function playerColor(playerId) {
        const index = state.room ? state.room.players.findIndex(p => p.id === playerId) : -1;
        return `var(--p${Math.max(0, index) % 8})`;
    }

    function nicknameOf(playerId) {
        const p = state.room && state.room.players.find(x => x.id === playerId);
        return p ? p.nickname : '나간 사람';
    }

    function show(viewId) {
        for (const v of document.querySelectorAll('.view')) v.classList.toggle('hidden', v.id !== viewId);
    }

    // ------------------------------------------------------------ 로비
    function getNickname() {
        if (state.auth) return state.auth.member.nickname;
        const value = $('#nickname').value.trim();
        if (!value) {
            toast('닉네임을 먼저 입력해 주세요.');
            $('#nickname').focus();
            return null;
        }
        storage.set(NICK_KEY, value);
        return value;
    }

    function playersLabel(g) {
        return g.minPlayers === g.maxPlayers ? `${g.minPlayers}명` : `${g.minPlayers}~${g.maxPlayers}명`;
    }

    function renderGameCards() {
        const box = $('#game-cards');
        box.replaceChildren();
        api('GET', '/api/games').then(games => {
            state.games = games;
            for (const g of games) {
                const info = GAMES[g.type] || { icon: '🎲', desc: '' };
                box.append(el('button', { class: 'game-card', type: 'button', onclick: () => openGameModal(g.type) },
                    el('div', { class: 'icon', text: info.icon }),
                    el('h3', { text: g.name }),
                    el('p', { text: info.desc }),
                    el('div', { class: 'meta', text: `${playersLabel(g)}${g.soloPlayable ? ' · 혼자하기 가능' : ''}` }),
                    el('span', { class: 'btn primary', text: '시작하기' }),
                ));
            }
        }).catch(e => toast(e.message));
    }

    // ------------------------------------------------------------ 게임 선택 창
    function openGameModal(type) {
        const g = state.games.find(x => x.type === type);
        if (!g) return;
        const info = GAMES[type] || {};
        state.modalGame = type;
        $('#modal-icon').textContent = info.icon || '🎲';
        $('#modal-title').textContent = g.name;
        $('#modal-desc').textContent = info.desc || '';
        $('#modal-meta').textContent = `${playersLabel(g)} · 무료`;
        $('#modal-guide').textContent = info.guide || '';
        $('#modal-room-name').textContent = g.name;
        $('#modal-solo').classList.toggle('hidden', !g.soloPlayable);
        $('.modal-actions').classList.toggle('single', !g.soloPlayable);
        $('#game-modal').classList.remove('hidden');
        loadModalRooms();
    }

    function closeGameModal() {
        state.modalGame = null;
        $('#game-modal').classList.add('hidden');
    }

    async function loadModalRooms() {
        const type = state.modalGame;
        if (!type) return;
        const list = $('#modal-rooms');
        const name = $('#modal-title').textContent;
        try {
            const rooms = (await api('GET', '/api/rooms')).filter(r => r.gameType === type);
            $('#modal-room-count').textContent = rooms.length;
            list.replaceChildren();
            if (rooms.length === 0) {
                list.append(el('li', { class: 'empty dashed' },
                    el('strong', { text: `지금 열린 ${name} 방이 없어요` }),
                    el('span', { class: 'muted small', text: '방을 만들면 여기에 가장 먼저 보여요.' })));
                return;
            }
            for (const r of rooms) list.append(roomItem(r));
        } catch {
            list.replaceChildren(el('li', { class: 'empty', text: '방 목록을 불러오지 못했어요.' }));
        }
    }

    function roomItem(r) {
        return el('li', {},
            el('span', {}, el('span', { class: 'badge', text: r.gameName }), ' ', `${r.hostNickname}의 방`,
                r.playing ? el('span', { class: 'badge live', text: '게임 중' }) : null),
            el('span', { class: 'muted', text: `${r.playerCount}/${r.maxPlayers}` }),
            el('button', { class: 'btn small', type: 'button', onclick: () => joinRoom(r.code) }, '입장'));
    }

    // ------------------------------------------------------------ 혼자하기
    function startSolo(type) {
        const nickname = getNickname();
        if (!nickname) return;
        closeGameModal();
        if (type === 'OMOK') {
            showSoloSetup(type);
        } else {
            createSolo(type, null);
        }
    }

    function showSoloSetup(type) {
        const g = state.games.find(x => x.type === type);
        state.soloGame = type;
        clearInterval(state.roomsTimer);
        $('#solo-icon').textContent = (GAMES[type] || {}).icon || '🎲';
        $('#solo-name').textContent = g ? g.name : '';
        $('#solo-sub').textContent = '컴퓨터 대전 · 무료';
        renderLevels();
        show('solo');
    }

    function renderLevels() {
        const box = $('#level-cards');
        box.replaceChildren();
        for (const level of LEVELS) {
            const selected = level.id === state.soloLevel;
            const card = el('button', { class: `level-card${selected ? ' selected' : ''}`, type: 'button', role: 'radio', 'aria-checked': String(selected) },
                el('span', { class: 'level-head' }, el('strong', { text: level.name }), el('span', { class: 'radio' })),
                el('span', { class: 'level-desc', text: level.desc }),
                el('span', { class: 'level-foot', text: '무료 연습' }));
            card.addEventListener('click', () => { state.soloLevel = level.id; renderLevels(); });
            box.append(card);
        }
        const chosen = LEVELS.find(l => l.id === state.soloLevel);
        $('#solo-start-label').textContent = `${chosen.name}으로 시작`;
    }

    async function createSolo(gameType, level) {
        const nickname = getNickname();
        if (!nickname) return;
        try {
            const result = await api('POST', '/api/rooms/solo', { gameType, nickname, level });
            enterRoom(result);
        } catch (e) { toast(e.message); }
    }

    async function loadOpenRooms() {
        const list = $('#room-list');
        try {
            const rooms = await api('GET', '/api/rooms');
            list.replaceChildren();
            if (rooms.length === 0) {
                list.append(el('li', { class: 'empty', text: '지금 열린 방이 없어요. 직접 만들어 보세요!' }));
                return;
            }
            for (const r of rooms) list.append(roomItem(r));
        } catch (e) {
            list.replaceChildren(el('li', { class: 'empty', text: '방 목록을 불러오지 못했어요.' }));
        }
    }

    async function createRoom(gameType) {
        const nickname = getNickname();
        if (!nickname) return;
        closeGameModal();
        try {
            const result = await api('POST', '/api/rooms', { gameType, nickname });
            enterRoom(result);
        } catch (e) { toast(e.message); }
    }

    async function joinRoom(code) {
        const nickname = getNickname();
        if (!nickname) return;
        try {
            const result = await api('POST', `/api/rooms/${encodeURIComponent(code.trim().toUpperCase())}/join`, { nickname });
            closeGameModal();
            enterRoom(result);
        } catch (e) { toast(e.message); }
    }

    function showLobby() {
        state.me = null;
        state.room = null;
        state.priv = {};
        state.stageKey = null;
        state.game = null;
        if (state.client) { state.client.deactivate(); state.client = null; }
        $('#me-box').textContent = '';
        renderAuth();
        show('lobby');
        loadOpenRooms();
        clearInterval(state.roomsTimer);
        state.roomsTimer = setInterval(() => { loadOpenRooms(); loadModalRooms(); }, 5000);
    }

    // ------------------------------------------------------------ 방 입장 · WebSocket
    function enterRoom(me) {
        state.me = me;
        storage.set(STORAGE_KEY, me);
        $('#auth-box').classList.add('hidden');
        clearInterval(state.roomsTimer);
        history.replaceState(null, '', '/');
        show('room');
        $('#stage').replaceChildren(el('div', { class: 'stage-center' }, el('p', { text: '연결하는 중…' })));
        connect();
    }

    function connect() {
        const { code, token } = state.me;
        const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
        const client = new StompJs.Client({
            brokerURL: `${scheme}://${location.host}/ws`,
            reconnectDelay: 2000,
        });
        client.onConnect = () => {
            client.subscribe(`/topic/rooms/${code}`, msg => onRoom(JSON.parse(msg.body)));
            client.subscribe(`/topic/rooms/${code}/events`, msg => onEvents(JSON.parse(msg.body)));
            client.subscribe(`/topic/rooms/${code}/private/${token}`, msg => onPrivate(JSON.parse(msg.body)));
            // 새로 연결될 때마다(재접속 포함) 내 자리를 다시 알린다.
            send('hello');
        };
        client.onWebSocketClose = () => {
            if (state.me) toast('연결이 끊겼어요. 다시 연결하는 중…');
        };
        client.activate();
        state.client = client;
    }

    function send(kind, type, data) {
        if (!state.client || !state.client.connected || !state.me) {
            toast('서버와 연결되지 않았어요.');
            return;
        }
        state.client.publish({
            destination: `/app/rooms/${state.me.code}/${kind}`,
            body: JSON.stringify({ token: state.me.token, type: type || null, data: data || {} }),
        });
    }

    function onPrivate(msg) {
        if (msg.type === 'error') {
            toast(msg.message);
            if (msg.message.includes('참가자가 아닙니다') || msg.message.includes('방을 찾을 수 없습니다')) {
                storage.remove(STORAGE_KEY);
                showLobby();
            }
            return;
        }
        if (msg.type === 'private') {
            state.priv = msg.state || {};
            if (state.game && state.game.update) state.game.update(state.room);
        }
    }

    function onEvents(events) {
        for (const e of events) {
            if (e.type === 'closed') {
                toast('방이 닫혔어요.');
                storage.remove(STORAGE_KEY);
                showLobby();
                return;
            }
            if (e.type === 'system' && state.room && state.room.gameType !== 'DRAWING') toast(e.text);
            else if (state.game && state.game.onEvent) state.game.onEvent(e);
        }
    }

    function onRoom(view) {
        state.room = view;
        const me = view.players.find(p => p.id === state.me.playerId);
        if (!me) {
            toast('방에서 나와졌어요.');
            storage.remove(STORAGE_KEY);
            showLobby();
            return;
        }
        $('#me-box').textContent = `${me.nickname}${me.host && !view.solo ? ' · 방장' : ''}`;
        $('#room-code-label').textContent = view.solo ? '' : '방 코드';
        $('#room-game').textContent = view.gameName;
        $('#room-code').textContent = view.solo ? '혼자하기' : view.code;
        $('#room-code').disabled = view.solo;
        $('#room').classList.toggle('drawing-mode', view.gameType === 'DRAWING');
        $('#room').classList.toggle('solo-mode', view.solo);
        renderPlayers(view);
        renderStage(view);
    }

    function renderPlayers(view) {
        const scores = (view.game && view.game.scores) || {};
        $('#player-count').textContent = `${view.players.length}/${view.maxPlayers}`;
        const list = $('#players');
        list.replaceChildren();
        for (const p of view.players) {
            list.append(el('li', { class: p.connected ? '' : 'offline' },
                el('span', { class: 'dot', style: `background:${playerColor(p.id)}` }),
                el('span', { class: 'name', text: p.nickname }),
                p.bot ? el('span', { class: 'tag', text: '컴퓨터' }) : null,
                p.host && !view.solo ? el('span', { class: 'tag', text: '방장' }) : null,
                p.id === state.me.playerId ? el('span', { class: 'tag', text: '나' }) : null,
                !p.connected ? el('span', { class: 'tag', text: '연결 끊김' }) : null,
                p.id in scores ? el('span', { class: 'score', text: scores[p.id] }) : null,
            ));
        }
    }

    function isHost() {
        return state.room && state.room.hostId === state.me.playerId;
    }

    // ------------------------------------------------------------ 무대(대기실 · 게임 · 결과)
    function renderStage(view) {
        // 그림맞추기는 대기실·게임·결과를 한 화면에서 이어서 보여준다 (채팅 기록 유지).
        const key = view.gameType === 'DRAWING' ? 'DRAWING' : `${view.status}:${view.gameType}`;
        if (key === state.stageKey && state.game) {
            state.game.update(view);
            return;
        }
        state.stageKey = key;
        const stage = $('#stage');
        stage.replaceChildren();

        if (view.gameType === 'DRAWING') {
            state.game = drawingRoomView(stage);
        } else if (view.status === 'WAITING') {
            state.game = waitingView(stage);
        } else {
            const top = view.status === 'FINISHED' ? resultView(view) : null;
            if (top) stage.append(top);
            const container = el('div');
            stage.append(container);
            const factory = { OMOK: omokView, CROSSWORD: crosswordView }[view.gameType];
            state.game = factory(container);
        }
        state.game.update(view);
    }

    function waitingView(stage) {
        const title = el('h2');
        const desc = el('p');
        const startBtn = el('button', { class: 'btn primary', type: 'button', onclick: () => send('start') }, '게임 시작');
        const invite = el('button', { class: 'btn ghost', type: 'button', onclick: copyInvite }, '초대 링크 복사');
        stage.append(el('div', { class: 'stage-center' }, el('div', {}, title, desc, el('div', { class: 'tools', style: 'justify-content:center;margin-top:16px' }, startBtn, invite))));
        return {
            update(view) {
                const count = view.players.length;
                const enough = count >= view.minPlayers;
                title.textContent = `${view.gameName} 대기실`;
                desc.textContent = enough
                    ? (isHost() ? '준비되면 시작을 눌러 주세요.' : '방장이 게임을 시작하길 기다리는 중이에요.')
                    : `${view.minPlayers}명 이상 모이면 시작할 수 있어요. (지금 ${count}명)`;
                startBtn.classList.toggle('hidden', !isHost());
                startBtn.disabled = !enough;
            },
        };
    }

    function resultView(view) {
        const ranking = view.ranking || [];
        const scores = (view.game && view.game.scores) || {};
        const reasons = { FIVE_IN_ROW: '오목 완성', TIMEOUT: '시간 초과', RESIGN: '기권', LEFT: '상대가 나감', DRAW: '무승부' };
        const reason = view.game && view.game.endReason ? reasons[view.game.endReason] : null;
        return el('div', { class: 'stage-center', style: 'min-height:auto;padding:12px 0 20px' },
            el('h2', { text: '게임 종료' }),
            reason ? el('p', { text: reason }) : null,
            ranking.length
                ? el('ol', { class: 'ranking' }, ranking.map((id, i) => el('li', {},
                    el('strong', { text: `${i + 1}위` }), el('span', { text: nicknameOf(id) }),
                    id in scores ? el('span', { style: 'margin-left:auto', text: `${scores[id]}점` }) : null)))
                : el('p', { text: '승자가 없습니다.' }),
            view.solo
                ? el('button', { class: 'btn primary', type: 'button', onclick: () => send('reset') }, '한 판 더 하기')
                : isHost()
                    ? el('button', { class: 'btn primary', type: 'button', onclick: () => send('reset') }, '대기실로 돌아가기')
                    : el('p', { text: '방장이 다시 시작할 수 있어요.' }),
        );
    }

    // ------------------------------------------------------------ 타이머 표시
    setInterval(() => {
        for (const node of document.querySelectorAll('[data-ends]')) {
            const ends = Number(node.dataset.ends);
            if (!ends) { node.textContent = ''; continue; }
            const left = Math.max(0, Math.ceil((ends - Date.now()) / 1000));
            const m = Math.floor(left / 60);
            const s = String(left % 60).padStart(2, '0');
            node.textContent = m > 0 ? `${m}:${s}` : `${left}초`;
            node.classList.toggle('warn', left <= 10);
        }
    }, 250);

    // ------------------------------------------------------------ 오목
    function omokView(root) {
        const turn = el('span', { class: 'turn' });
        const timer = el('span', { class: 'timer' });
        const resign = el('button', { class: 'btn danger small', type: 'button', onclick: () => {
            if (confirmResign()) send('action', 'resign');
        } }, '기권');
        const canvas = el('canvas', { class: 'omok-board', width: 720, height: 720 });
        root.append(el('div', { class: 'status-bar' }, turn, el('span', {}, timer, ' ', resign)), el('div', { class: 'omok-wrap' }, canvas));

        let game = null;
        let confirmPending = false;
        function confirmResign() {
            // 브라우저 확인창 대신 두 번 누르기로 확인한다.
            if (confirmPending) { confirmPending = false; return true; }
            confirmPending = true;
            toast('한 번 더 누르면 기권합니다.');
            setTimeout(() => { confirmPending = false; }, 2500);
            return false;
        }

        canvas.addEventListener('click', ev => {
            if (!game || game.turnPlayerId !== state.me.playerId) return;
            const rect = canvas.getBoundingClientRect();
            const cell = rect.width / game.size;
            const x = Math.floor((ev.clientX - rect.left) / cell);
            const y = Math.floor((ev.clientY - rect.top) / cell);
            if (game.board[y] && game.board[y][x] === 0) send('action', 'place', { x, y });
        });

        function draw() {
            const ctx = canvas.getContext('2d');
            const n = game.size;
            const w = canvas.width;
            const cell = w / n;
            const style = getComputedStyle(document.documentElement);
            ctx.clearRect(0, 0, w, w);
            ctx.fillStyle = style.getPropertyValue('--wood').trim() || '#e6c48f';
            ctx.fillRect(0, 0, w, w);
            ctx.strokeStyle = style.getPropertyValue('--wood-line').trim() || '#7a5a2c';
            ctx.lineWidth = 1.5;
            for (let i = 0; i < n; i++) {
                const p = cell * i + cell / 2;
                ctx.beginPath(); ctx.moveTo(cell / 2, p); ctx.lineTo(w - cell / 2, p); ctx.stroke();
                ctx.beginPath(); ctx.moveTo(p, cell / 2); ctx.lineTo(p, w - cell / 2); ctx.stroke();
            }
            for (const [sx, sy] of [[3, 3], [11, 3], [7, 7], [3, 11], [11, 11]]) {
                ctx.beginPath(); ctx.arc(cell * sx + cell / 2, cell * sy + cell / 2, 4, 0, Math.PI * 2);
                ctx.fillStyle = ctx.strokeStyle; ctx.fill();
            }
            for (let y = 0; y < n; y++) {
                for (let x = 0; x < n; x++) {
                    const v = game.board[y][x];
                    if (!v) continue;
                    const cx = cell * x + cell / 2;
                    const cy = cell * y + cell / 2;
                    ctx.beginPath(); ctx.arc(cx, cy, cell * 0.42, 0, Math.PI * 2);
                    ctx.fillStyle = v === 1 ? '#151515' : '#fafafa';
                    ctx.fill();
                    ctx.lineWidth = 1; ctx.strokeStyle = '#151515'; ctx.stroke();
                }
            }
            if (game.lastMove) {
                ctx.beginPath();
                ctx.arc(cell * game.lastMove.x + cell / 2, cell * game.lastMove.y + cell / 2, cell * 0.12, 0, Math.PI * 2);
                ctx.fillStyle = '#d9541e'; ctx.fill();
            }
        }

        return {
            update(view) {
                game = view.game;
                if (!game) return;
                const myStone = game.black === state.me.playerId ? '흑' : game.white === state.me.playerId ? '백' : null;
                if (view.status === 'FINISHED') {
                    turn.textContent = game.winnerId ? `${nicknameOf(game.winnerId)} 승리` : '무승부';
                } else if (game.turnPlayerId === state.me.playerId) {
                    turn.textContent = `내 차례 (${myStone})`;
                } else {
                    turn.textContent = `${nicknameOf(game.turnPlayerId)}의 차례`;
                }
                timer.dataset.ends = view.status === 'PLAYING' ? (game.turnEndsAt || '') : '';
                resign.classList.toggle('hidden', view.status !== 'PLAYING' || !myStone);
                canvas.style.cursor = game.turnPlayerId === state.me.playerId ? 'pointer' : 'default';
                draw();
            },
        };
    }

    // ------------------------------------------------------------ 그림맞추기
    // 대기실 · 게임 · 결과를 한 화면에서 이어서 보여준다. 채팅 기록이 판이 바뀌어도 유지된다.
    function drawingRoomView(root) {
        const W = 800;
        const H = 600;

        // 상단 상태 바
        const statusText = el('strong');
        const statusRight = el('span', { class: 'status-right' });
        const timer = el('span', { class: 'timer' });
        const statusBar = el('div', { class: 'draw-status' }, el('span', { class: 'status-icon', text: '👥' }), statusText, statusRight, timer);

        // 제시어 안내 (출제자에게는 제시어, 나머지에게는 글자 수)
        const prompt = el('div', { class: 'prompt hidden' });

        // 도구 모음
        let tool = 'pen';
        let color = COLORS[0];
        let width = 4;
        let group = Date.now() % 100000;
        const toolButtons = TOOLS.map(t => el('button', {
            class: 'tool-btn', type: 'button', title: t.name, dataset: { tool: t.id },
            onclick: () => { tool = t.id; syncTools(); },
        }, el('span', { class: 'tool-icon', text: t.icon }), t.name));
        const swatches = COLORS.map(c => el('button', {
            class: 'swatch', type: 'button', title: c, style: `background:${c}`, dataset: { color: c },
            onclick: () => { color = c; if (tool === 'eraser') tool = 'pen'; syncTools(); },
        }));
        const colorInput = el('input', { type: 'color', value: '#d9541e', 'aria-label': '더 많은 색' });
        colorInput.addEventListener('input', () => { color = colorInput.value; if (tool === 'eraser') tool = 'pen'; syncTools(); });
        const moreColors = el('label', { class: 'more-colors' }, el('span', { text: '🎨' }), '더 많은 색', colorInput);
        const widthSelect = el('select', { class: 'width-select', 'aria-label': '굵기' },
            WIDTHS.map(w => el('option', { value: w, selected: w === width }, `${w}px`)));
        widthSelect.addEventListener('change', () => { width = Number(widthSelect.value); });
        const undoBtn = el('button', { class: 'icon-btn', type: 'button', title: '되돌리기', onclick: () => send('action', 'undo') }, '↶');
        const redoBtn = el('button', { class: 'icon-btn', type: 'button', title: '다시하기', onclick: () => send('action', 'redo') }, '↷');
        const trashBtn = el('button', { class: 'icon-btn', type: 'button', title: '모두 지우기', onclick: () => send('action', 'clear') }, '🗑');
        const toolbar = el('div', { class: 'toolbar' },
            el('div', { class: 'tool-row' }, toolButtons, el('div', { class: 'history' }, undoBtn, redoBtn, trashBtn)),
            el('div', { class: 'tool-row' },
                el('div', { class: 'swatches' }, swatches), moreColors,
                el('label', { class: 'width-label' }, '굵기', widthSelect)));

        // 그림판 + 미리보기 + 안내 덮개
        const canvas = el('canvas', { class: 'draw-canvas', width: W, height: H });
        const preview = el('canvas', { class: 'draw-preview', width: W, height: H });
        const overlay = el('div', { class: 'canvas-overlay hidden' });
        const canvasWrap = el('div', { class: 'canvas-wrap' }, canvas, preview, overlay);
        const hint = el('p', { class: 'draw-hint' });

        // 참가자
        const playerCards = el('div', { class: 'player-cards' });

        // 채팅
        const chatCount = el('span', { class: 'muted' });
        const notice = el('div', { class: 'chat-notice' });
        const log = el('div', { class: 'chat-log' });
        const input = el('input', { maxlength: 50, placeholder: '반갑게 인사해 보세요', autocomplete: 'off' });
        const form = el('form', { class: 'chat-form' }, input, el('button', { class: 'send-btn', type: 'submit', 'aria-label': '보내기' }, '➤'));
        const chat = el('aside', { class: 'draw-chat' },
            el('div', { class: 'chat-head' }, el('strong', {}, '💬 함께 떠들어요'), chatCount),
            notice, log, form, el('p', { class: 'chat-foot', text: 'Enter로 보내기 · 서로 기분 좋은 한마디' }));

        root.append(el('div', { class: 'draw-room' },
            el('div', { class: 'draw-main' },
                el('div', { class: 'draw-board' }, statusBar, prompt, toolbar, canvasWrap, hint),
                playerCards),
            chat));

        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        const pctx = preview.getContext('2d');
        let view = null;
        let game = null;
        let lastRoundKey = null;

        form.addEventListener('submit', ev => {
            ev.preventDefault();
            const text = input.value.trim();
            if (!text) return;
            send('chat', null, { text });
            input.value = '';
        });

        function amDrawer() {
            return Boolean(game && view && view.status === 'PLAYING' && game.phase === 'DRAWING' && game.drawerId === state.me.playerId);
        }

        function syncTools() {
            toolButtons.forEach(b => b.classList.toggle('active', b.dataset.tool === tool));
            swatches.forEach(b => b.classList.toggle('active', b.dataset.color === color && tool !== 'eraser'));
            const drawer = amDrawer();
            toolbar.classList.toggle('disabled', !drawer);
            [...toolButtons, ...swatches, undoBtn, redoBtn, trashBtn, widthSelect, colorInput].forEach(b => { b.disabled = !drawer; });
            canvas.classList.toggle('can-draw', drawer);
            canvas.dataset.tool = tool;
        }

        function addLog(text, cls) {
            log.append(el('div', { class: cls || '', text }));
            log.scrollTop = log.scrollHeight;
        }

        // ---- 그리기 명령을 캔버스에 그리기 (모든 사람이 같은 순서로 같은 결과를 얻는다)
        function clearCanvas() {
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, W, H);
        }

        function drawOp(target, op) {
            const pts = (op.points || []).map(([x, y]) => [x * W, y * H]);
            if (!pts.length) return;
            if (op.tool === 'fill') {
                floodFill(pts[0][0], pts[0][1], op.color);
                return;
            }
            target.strokeStyle = op.color;
            target.lineWidth = op.width;
            target.lineCap = 'round';
            target.lineJoin = 'round';
            target.beginPath();
            if (op.tool === 'line') {
                target.moveTo(pts[0][0], pts[0][1]);
                target.lineTo(pts[1][0], pts[1][1]);
            } else if (op.tool === 'rect') {
                target.rect(Math.min(pts[0][0], pts[1][0]), Math.min(pts[0][1], pts[1][1]),
                    Math.abs(pts[1][0] - pts[0][0]), Math.abs(pts[1][1] - pts[0][1]));
            } else if (op.tool === 'circle') {
                const cx = (pts[0][0] + pts[1][0]) / 2;
                const cy = (pts[0][1] + pts[1][1]) / 2;
                target.ellipse(cx, cy, Math.abs(pts[1][0] - pts[0][0]) / 2 || 0.5, Math.abs(pts[1][1] - pts[0][1]) / 2 || 0.5, 0, 0, Math.PI * 2);
            } else {
                target.moveTo(pts[0][0], pts[0][1]);
                for (const [x, y] of pts.slice(1)) target.lineTo(x, y);
                if (pts.length === 1) target.lineTo(pts[0][0] + 0.1, pts[0][1]);
            }
            target.stroke();
        }

        function hexToRgb(hex) {
            const n = parseInt(hex.slice(1), 16);
            return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
        }

        // 같은 색으로 이어진 영역을 칠한다. 경계선의 부드러운 가장자리까지 덮도록 약간의 색 차이는 허용한다.
        function floodFill(sx, sy, hex) {
            const x0 = Math.floor(sx);
            const y0 = Math.floor(sy);
            if (x0 < 0 || y0 < 0 || x0 >= W || y0 >= H) return;
            const image = ctx.getImageData(0, 0, W, H);
            const data = image.data;
            const at = (x, y) => (y * W + x) * 4;
            const start = at(x0, y0);
            const target = [data[start], data[start + 1], data[start + 2]];
            const fill = hexToRgb(hex);
            if (Math.abs(target[0] - fill[0]) + Math.abs(target[1] - fill[1]) + Math.abs(target[2] - fill[2]) < 8) return;
            const tolerance = 96;
            const matches = i => Math.abs(data[i] - target[0]) + Math.abs(data[i + 1] - target[1]) + Math.abs(data[i + 2] - target[2]) <= tolerance;
            const seen = new Uint8Array(W * H);
            const stack = [[x0, y0]];
            while (stack.length) {
                const [x, y] = stack.pop();
                let left = x;
                while (left >= 0 && !seen[y * W + left] && matches(at(left, y))) left--;
                left++;
                let spanUp = false;
                let spanDown = false;
                for (let cx = left; cx < W && !seen[y * W + cx] && matches(at(cx, y)); cx++) {
                    const i = at(cx, y);
                    data[i] = fill[0]; data[i + 1] = fill[1]; data[i + 2] = fill[2]; data[i + 3] = 255;
                    seen[y * W + cx] = 1;
                    if (y > 0) {
                        const up = !seen[(y - 1) * W + cx] && matches(at(cx, y - 1));
                        if (up && !spanUp) stack.push([cx, y - 1]);
                        spanUp = up;
                    }
                    if (y < H - 1) {
                        const down = !seen[(y + 1) * W + cx] && matches(at(cx, y + 1));
                        if (down && !spanDown) stack.push([cx, y + 1]);
                        spanDown = down;
                    }
                }
            }
            ctx.putImageData(image, 0, 0);
        }

        function redrawAll(strokes) {
            clearCanvas();
            (strokes || []).forEach(op => drawOp(ctx, op));
        }

        // ---- 그리는 사람의 입력
        const pointOf = ev => {
            const rect = canvas.getBoundingClientRect();
            return [Math.min(1, Math.max(0, (ev.clientX - rect.left) / rect.width)),
                Math.min(1, Math.max(0, (ev.clientY - rect.top) / rect.height))];
        };
        let drag = null;   // { start, points, group, last }
        let flushTimer = null;

        function sendOp(op) {
            drawOp(ctx, op);
            send('action', 'stroke', op);
        }

        function flushPen() {
            if (!drag || drag.points.length < 2) return;
            const op = { tool, points: drag.points, color: tool === 'eraser' ? '#ffffff' : color, width: tool === 'eraser' ? Math.max(width, 14) : width, group: drag.group };
            sendOp(op);
            drag.points = [drag.points[drag.points.length - 1]];
        }

        canvas.addEventListener('pointerdown', ev => {
            if (!amDrawer()) return;
            const p = pointOf(ev);
            group += 1;
            if (tool === 'fill') {
                sendOp({ tool: 'fill', points: [p], color, width, group });
                return;
            }
            canvas.setPointerCapture(ev.pointerId);
            drag = { start: p, points: [p], group };
            if (tool === 'pen' || tool === 'eraser') {
                clearInterval(flushTimer);
                flushTimer = setInterval(flushPen, 40);
            }
        });
        canvas.addEventListener('pointermove', ev => {
            if (!drag) return;
            const p = pointOf(ev);
            if (tool === 'pen' || tool === 'eraser') {
                drag.points.push(p);
            } else {
                drag.end = p;
                pctx.clearRect(0, 0, W, H);
                drawOp(pctx, { tool, points: [drag.start, p], color, width });
            }
        });
        const endDrag = () => {
            if (!drag) return;
            if (tool === 'pen' || tool === 'eraser') {
                clearInterval(flushTimer);
                if (drag.points.length === 1) drag.points.push(drag.points[0]);
                flushPen();
            } else if (drag.end) {
                pctx.clearRect(0, 0, W, H);
                sendOp({ tool, points: [drag.start, drag.end], color, width, group: drag.group });
            }
            drag = null;
        };
        canvas.addEventListener('pointerup', endDrag);
        canvas.addEventListener('pointercancel', endDrag);

        // ---- 화면 갱신
        function renderStatus() {
            const count = view.players.length;
            statusRight.textContent = `${count}/${view.maxPlayers}명`;
            timer.dataset.ends = '';
            if (view.status === 'WAITING') {
                if (view.autoStartAt) {
                    statusText.textContent = '곧 시작해요!';
                    timer.dataset.ends = view.autoStartAt;
                } else {
                    statusText.textContent = '친구를 기다리고 있어요';
                }
            } else if (view.status === 'FINISHED' || !game || game.phase === 'FINISHED') {
                statusText.textContent = '이번 판이 끝났어요';
            } else if (game.phase === 'REVEAL') {
                statusText.textContent = `${game.round}/${game.totalRounds} 라운드 · 정답 공개`;
                timer.dataset.ends = game.phaseEndsAt;
            } else {
                statusText.textContent = `${game.round}/${game.totalRounds} 라운드 · ${nicknameOf(game.drawerId)}님이 그리는 중`;
                timer.dataset.ends = game.phaseEndsAt;
            }
            statusBar.classList.toggle('waiting', view.status !== 'PLAYING');
        }

        function renderPrompt() {
            prompt.className = 'prompt';
            if (view.status !== 'PLAYING' || !game || game.phase === 'FINISHED') {
                prompt.classList.add('hidden');
                return;
            }
            if (game.phase === 'REVEAL') {
                prompt.classList.add('reveal');
                prompt.replaceChildren(el('span', { class: 'prompt-label', text: '정답' }),
                    el('strong', { class: 'prompt-word', text: game.revealedWord || '' }));
            } else if (amDrawer()) {
                prompt.classList.add('mine');
                prompt.replaceChildren(el('span', { class: 'prompt-label', text: '제시어 · 나만 보여요' }),
                    el('strong', { class: 'prompt-word', text: state.priv.word || '…' }),
                    el('span', { class: 'prompt-help', text: '글자나 숫자는 쓰지 말고 그림으로만 표현해 주세요.' }));
            } else {
                const length = game.wordLength || 0;
                prompt.replaceChildren(el('span', { class: 'prompt-label', text: `${length}글자 단어` }),
                    el('strong', { class: 'prompt-word blanks', text: '○'.repeat(length) }));
            }
        }

        function renderOverlay() {
            overlay.replaceChildren();
            if (view.status === 'WAITING') {
                const need = Math.max(0, view.minPlayers - view.players.length);
                overlay.append(
                    el('div', { class: 'overlay-icon', text: '✏️' }),
                    el('h2', { text: view.autoStartAt ? '곧 시작해요!' : need > 0 ? `친구 ${need}명만 더 오면 바로 시작해요` : '모두 연결되면 시작해요' }),
                    el('p', { text: '들어온 순서대로 돌아가며 그리고, 나머지는 채팅으로 맞혀요.' }),
                    el('p', { text: '중간에 들어와도 바로 함께할 수 있어요.' }),
                    el('button', { class: 'btn invite', type: 'button', onclick: copyInvite }, '🔗 친구 초대하기'));
                overlay.classList.remove('hidden');
            } else if (view.status === 'FINISHED' || (game && game.phase === 'FINISHED')) {
                const scores = (game && game.scores) || {};
                const ranking = view.ranking && view.ranking.length ? view.ranking
                    : Object.keys(scores).sort((a, b) => scores[b] - scores[a]);
                overlay.append(
                    el('div', { class: 'overlay-icon', text: '🏆' }),
                    el('h2', { text: '이번 판 결과' }),
                    el('ol', { class: 'ranking' }, ranking.map((id, i) => el('li', {},
                        el('strong', { text: `${i + 1}위` }), el('span', { text: nicknameOf(id) }),
                        el('span', { style: 'margin-left:auto', text: `${scores[id] || 0}점` })))),
                    el('p', { text: '잠시 후 대기실로 돌아가요. 인원이 모이면 다음 판이 바로 시작돼요.' }));
                overlay.classList.remove('hidden');
            } else {
                overlay.classList.add('hidden');
            }
        }

        function renderHint() {
            if (view.status !== 'PLAYING' || !game) {
                hint.textContent = '그림 속 단서를 보고 채팅에 정답을 입력해요.';
            } else if (amDrawer()) {
                hint.textContent = '제시어를 그림으로 표현해 주세요. 채팅으로 정답을 말할 수는 없어요.';
            } else if ((game.guessed || []).includes(state.me.playerId)) {
                hint.textContent = '정답을 맞혔어요! 다른 사람이 맞힐 때까지 기다려 주세요.';
            } else {
                hint.textContent = '그림 속 단서를 보고 채팅에 정답을 입력해요.';
            }
        }

        function renderPlayerCards() {
            const scores = (game && game.scores) || {};
            const guessed = (game && game.guessed) || [];
            playerCards.replaceChildren(...view.players.map(p => {
                const drawing = view.status === 'PLAYING' && game && game.phase === 'DRAWING' && game.drawerId === p.id;
                return el('div', { class: `player-card${p.connected ? '' : ' offline'}${drawing ? ' drawing' : ''}` },
                    el('span', { class: 'avatar', style: `background:${playerColor(p.id)}`, text: p.nickname.slice(0, 1) }),
                    el('span', { class: 'player-text' },
                        el('strong', {}, p.nickname, p.id === state.me.playerId ? el('span', { class: 'me-tag', text: '나' }) : null),
                        el('span', { class: 'muted small', text: `${scores[p.id] || 0}점` })),
                    drawing ? el('span', { class: 'badge', text: '그리는 중' }) : null,
                    guessed.includes(p.id) ? el('span', { class: 'badge good', text: '정답!' }) : null,
                    !p.connected ? el('span', { class: 'badge muted-badge', text: '연결 끊김' }) : null);
            }));
        }

        function renderChatMeta() {
            chatCount.textContent = `${view.players.length}명`;
            const drawer = amDrawer();
            if (view.status === 'WAITING') {
                notice.textContent = view.autoStartAt ? '곧 게임이 시작돼요!' : '친구가 들어오면 바로 시작해요.';
                input.placeholder = '반갑게 인사해 보세요';
            } else if (view.status === 'PLAYING' && game && game.phase === 'DRAWING') {
                notice.textContent = drawer ? '내가 그릴 차례예요.' : '정답이라고 생각하는 단어를 입력해 보세요.';
                input.placeholder = drawer ? '채팅 (정답은 말할 수 없어요)' : '정답을 입력해 보세요';
            } else {
                notice.textContent = '수고했어요! 서로 한마디 나눠 보세요.';
                input.placeholder = '반갑게 인사해 보세요';
            }
        }

        return {
            update(next) {
                view = next;
                game = next.game;
                renderStatus();
                renderPrompt();
                renderOverlay();
                renderHint();
                renderPlayerCards();
                renderChatMeta();
                syncTools();

                const roundKey = game ? `${game.round}:${game.phase}` : view.status;
                if (roundKey !== lastRoundKey && game && view.status === 'PLAYING' && game.phase === 'DRAWING') {
                    addLog(amDrawer() ? '내 차례예요! 제시어를 그려 주세요.' : `${nicknameOf(game.drawerId)}님이 그림을 그려요.`, 'sys');
                }
                lastRoundKey = roundKey;

                // 서버에 저장된 그리기 명령으로 다시 그린다. 새로 들어온 사람도 같은 그림을 본다.
                if (!drag) {
                    if (view.status === 'PLAYING' && game && game.phase === 'DRAWING') redrawAll(game.strokes);
                    else if (view.status === 'WAITING') clearCanvas();
                }
            },
            onEvent(e) {
                if (e.type === 'stroke') {
                    if (!amDrawer()) drawOp(ctx, e.stroke);
                } else if (e.type === 'redraw') {
                    redrawAll(e.strokes);
                } else if (e.type === 'clear') {
                    clearCanvas();
                } else if (e.type === 'chat') {
                    addLog(`${e.nickname}: ${e.text}`, e.playerId === state.me.playerId ? 'mine' : '');
                } else if (e.type === 'system') {
                    addLog(e.text, 'sys center');
                } else if (e.type === 'correct') {
                    addLog(`🎉 ${e.nickname}님 정답! (+${e.points})`, 'good');
                } else if (e.type === 'reveal') {
                    addLog(`정답은 "${e.word}" 였어요.`, 'sys');
                }
            },
        };
    }

    // ------------------------------------------------------------ 십자격돌
    function crosswordView(root) {
        const title = el('span', { class: 'turn' });
        const timer = el('span', { class: 'timer' });
        const grid = el('div', { class: 'cross-grid' });
        const selected = el('div', { class: 'question', text: '판의 빈칸을 누르면 문제가 나와요.' });
        const input = el('input', { class: 'input', maxlength: 10, placeholder: '정답 입력', autocomplete: 'off' });
        const form = el('form', { class: 'answer-form' }, input, el('button', { class: 'btn primary', type: 'submit' }, '제출'));
        const across = el('div');
        const down = el('div');
        root.append(
            el('div', { class: 'status-bar' }, title, timer),
            el('div', { class: 'cross-layout' },
                el('div', {}, grid, selected, form),
                el('div', { class: 'clues' }, el('div', {}, el('h3', { text: '가로' }), across), el('div', {}, el('h3', { text: '세로' }), down))),
        );

        let game = null;
        let selectedId = null;
        let playing = false;
        let lastCell = null;

        // 칸을 누르면 그 칸을 지나는 문제를 고른다.
        // 가로·세로 두 문제가 겹치는 칸을 다시 누르면 다른 방향 문제로 바꾼다.
        grid.addEventListener('click', ev => {
            const node = ev.target.closest('.cell');
            if (!node || !game || !playing || node.classList.contains('block')) return;
            const r = Number(node.dataset.r);
            const c = Number(node.dataset.c);
            const covering = game.clues.filter(clue => clue.status === 'OPEN'
                && cellsOf(clue).some(([cr, cc]) => cr === r && cc === c));
            if (covering.length === 0) {
                toast('이미 풀린 칸이에요.');
                return;
            }
            covering.sort((a, b) => (a.direction === 'ACROSS' ? 0 : 1) - (b.direction === 'ACROSS' ? 0 : 1));
            const cellKey = `${r}:${c}`;
            const index = covering.findIndex(clue => clue.wordId === selectedId);
            let next = covering[0];
            if (index >= 0) {
                // 같은 칸을 다시 누르면 방향을 바꾸고, 다른 칸이면 지금 방향을 유지한다.
                next = cellKey === lastCell ? covering[(index + 1) % covering.length] : covering[index];
            }
            lastCell = cellKey;
            select(next.wordId);
        });

        form.addEventListener('submit', ev => {
            ev.preventDefault();
            const text = input.value.trim();
            if (!selectedId) { toast('먼저 판의 빈칸이나 문제를 눌러 주세요.'); return; }
            if (!text) return;
            send('action', 'answer', { wordId: selectedId, text });
            input.value = '';
        });

        function cellsOf(clue) {
            const cells = [];
            for (let i = 0; i < clue.length; i++) {
                cells.push(clue.direction === 'ACROSS' ? [clue.row, clue.col + i] : [clue.row + i, clue.col]);
            }
            return cells;
        }

        function select(id) {
            selectedId = id;
            render();
            input.focus();
        }

        function render() {
            if (!game) return;
            const owners = {};
            for (const clue of game.clues) {
                if (!clue.ownerId) continue;
                for (const [r, c] of cellsOf(clue)) owners[`${r}:${c}`] ??= clue.ownerId;
            }
            const current = game.clues.find(c => c.wordId === selectedId);
            if (current && current.status !== 'OPEN') selectedId = null;
            const highlighted = new Set(current && selectedId ? cellsOf(current).map(([r, c]) => `${r}:${c}`) : []);

            grid.replaceChildren();
            game.grid.forEach((row, r) => row.forEach((cell, c) => {
                const key = `${r}:${c}`;
                const owner = owners[key];
                const open = !cell.block && !cell.letter;
                const node = el('div', { class: `cell${cell.block ? ' block' : ''}${open && playing ? ' open' : ''}${highlighted.has(key) ? ' selected' : ''}${owner ? ' owned' : ''}`, dataset: { r, c } },
                    cell.number ? el('span', { class: 'num', text: cell.number }) : null,
                    cell.letter || '');
                if (owner) node.style.background = playerColor(owner);
                grid.append(node);
            }));

            const clueButton = clue => {
                const done = clue.status !== 'OPEN';
                const btn = el('button', { class: `clue${clue.wordId === selectedId ? ' active' : ''}${done ? ' done' : ''}`, type: 'button', disabled: done },
                    `${clue.number}. ${clue.clue} (${clue.length}글자)`,
                    clue.ownerId ? el('span', { class: 'owner', style: `color:${playerColor(clue.ownerId)}`, text: nicknameOf(clue.ownerId) }) : null,
                    clue.status === 'AUTO' ? el('span', { class: 'owner', text: '자동' }) : null);
                btn.addEventListener('click', () => select(clue.wordId));
                return btn;
            };
            across.replaceChildren(...game.clues.filter(c => c.direction === 'ACROSS').map(clueButton));
            down.replaceChildren(...game.clues.filter(c => c.direction === 'DOWN').map(clueButton));

            const sel = game.clues.find(c => c.wordId === selectedId);
            selected.classList.toggle('active', Boolean(sel));
            if (sel) {
                selected.replaceChildren(
                    el('span', { class: 'question-tag', text: `${sel.number}번 ${sel.direction === 'ACROSS' ? '가로' : '세로'} · ${sel.length}글자` }),
                    el('span', { class: 'question-text', text: sel.clue }));
            } else {
                selected.textContent = game.finished || !playing ? '모든 문제가 공개됐어요.' : '판의 빈칸을 누르면 문제가 나와요.';
            }
            input.placeholder = sel ? `${sel.length}글자 정답 입력` : '빈칸을 먼저 눌러 주세요';
            input.disabled = !sel;
        }

        return {
            update(view) {
                game = view.game;
                if (!game) return;
                title.textContent = `${game.puzzleTitle} · 먼저 맞힌 사람이 단어를 차지해요`;
                playing = view.status === 'PLAYING';
                timer.dataset.ends = playing ? game.endsAt : '';
                form.classList.toggle('hidden', !playing);
                render();
            },
            onEvent(e) {
                if (e.type === 'claimed') {
                    const mine = e.playerId === state.me.playerId;
                    toast(`${mine ? '내가' : `${e.nickname}님이`} ${e.number}번을 차지했어요! +${e.points}${e.combo > 1 ? ` (${e.combo}연속)` : ''}`);
                }
            },
        };
    }

    // ------------------------------------------------------------ 초대 · 나가기
    async function copyInvite() {
        const url = `${location.origin}/?room=${state.me.code}`;
        try {
            await navigator.clipboard.writeText(url);
            toast('초대 링크를 복사했어요.');
        } catch {
            toast(`초대 링크: ${url}`);
        }
    }

    async function leave() {
        if (!state.me) return;
        const { code, token } = state.me;
        storage.remove(STORAGE_KEY);
        try { await api('POST', `/api/rooms/${code}/leave`, { token }); } catch { /* 이미 나간 경우 */ }
        showLobby();
    }

    // ------------------------------------------------------------ 로그인 · 회원가입
    const USERNAME_RE = /^[a-z0-9]{4,20}$/;

    function setAuth(auth) {
        state.auth = auth;
        if (auth) storage.set(AUTH_KEY, auth); else storage.remove(AUTH_KEY);
        renderAuth();
    }

    /** 상단 로그인 영역과 로비 닉네임 칸을 로그인 상태에 맞춘다. */
    function renderAuth() {
        const box = $('#auth-box');
        const inRoom = !!state.me;
        box.replaceChildren();
        box.classList.toggle('hidden', inRoom);
        if (state.auth) {
            box.append(
                el('span', { class: 'who' }, state.auth.member.nickname, el('small', { text: '님' })),
                el('button', { class: 'btn ghost small', type: 'button', id: 'logout-btn', onclick: logout, text: '로그아웃' }));
        } else {
            box.append(
                el('button', { class: 'btn top-login', type: 'button', id: 'top-login', onclick: () => showAuth('login'), text: '로그인' }),
                el('button', { class: 'btn top-signup', type: 'button', id: 'top-signup', onclick: () => showAuth('signup'), text: '회원가입' }));
        }
        const input = $('#nickname');
        const member = state.auth && state.auth.member;
        input.readOnly = !!member;
        input.value = member ? member.nickname : (storage.get(NICK_KEY) || '');
        $('#nickname-box').classList.toggle('member', !!member);
        $('#nickname-label').textContent = member ? '회원 닉네임' : '닉네임';
        input.title = member ? '로그인한 회원은 회원 닉네임으로 참가해요.' : '';
        $('#hero-text').textContent = member
            ? `${member.nickname}님, 오늘은 어떤 게임을 해 볼까요?`
            : '설치 없이 바로 시작해요. 로그인하면 내 닉네임을 지킬 수 있어요.';
    }

    function showAuth(which) {
        if (state.me) return;
        closeGameModal();
        clearInterval(state.roomsTimer);
        for (const form of [$('#login-form'), $('#signup-form')]) {
            form.reset();
            form.querySelectorAll('.invalid').forEach(n => n.classList.remove('invalid'));
        }
        $('#login-error').textContent = '';
        $('#signup-error').textContent = '';
        resetHints();
        show(which);
        $(which === 'login' ? '#login-username' : '#signup-username').focus();
    }

    function setBusy(button, busy, label) {
        button.disabled = busy;
        if (label) button.textContent = label;
    }

    async function submitLogin(ev) {
        ev.preventDefault();
        const username = $('#login-username').value.trim().toLowerCase();
        const password = $('#login-password').value;
        const error = $('#login-error');
        if (!username || !password) {
            error.textContent = '아이디와 비밀번호를 모두 입력해 주세요.';
            (!username ? $('#login-username') : $('#login-password')).focus();
            return;
        }
        const button = $('#login-submit');
        setBusy(button, true, '로그인 중…');
        try {
            const result = await api('POST', '/api/auth/login', { username, password });
            setAuth(result);
            toast(`${result.member.nickname}님, 반가워요!`);
            showLobby();
        } catch (e) {
            error.textContent = e.message;
            $('#login-password').value = '';
            $('#login-password').focus();
        } finally {
            setBusy(button, false, '로그인');
        }
    }

    /** 회원가입 칸별 규칙. 통과하면 null, 아니면 안내 문구. 서버(AuthService)와 같은 규칙이다. */
    function signupProblem(name, values) {
        const { username, nickname, password, password2 } = values;
        switch (name) {
            case 'username':
                if (!username) return '아이디를 입력해 주세요.';
                return USERNAME_RE.test(username) ? null : '영문 소문자와 숫자로 4~20자여야 해요.';
            case 'nickname':
                if (!nickname) return '닉네임을 입력해 주세요.';
                return nickname.length <= 12 ? null : '닉네임은 12자까지 쓸 수 있어요.';
            case 'password':
                if (password.length < 8 || password.length > 72) return '8~72자로 입력해 주세요.';
                return null;
            case 'password2':
                if (!password2) return '비밀번호를 한 번 더 입력해 주세요.';
                return password2 === password ? null : '비밀번호가 서로 달라요.';
            default:
                return null;
        }
    }

    const HINTS = {
        username: '영문 소문자·숫자 4~20자',
        nickname: '게임에서 보이는 이름 · 1~12자',
        password: '8~72자로 입력해 주세요',
        password2: '한 번 더 입력해 주세요',
    };

    function signupValues() {
        return {
            username: $('#signup-username').value.trim().toLowerCase(),
            nickname: $('#signup-nickname').value.trim(),
            password: $('#signup-password').value,
            password2: $('#signup-password2').value,
        };
    }

    function resetHints() {
        for (const [name, text] of Object.entries(HINTS)) {
            const hint = $(`#signup-form .hint[data-for="${name}"]`);
            hint.textContent = text;
            hint.classList.remove('bad', 'good');
        }
    }

    /** 칸 하나를 검사해 아래 안내 문구를 바꾼다. 통과하면 true. */
    function checkField(name, values = signupValues()) {
        const problem = signupProblem(name, values);
        const input = $(`#signup-${name}`);
        const hint = $(`#signup-form .hint[data-for="${name}"]`);
        input.classList.toggle('invalid', !!problem);
        hint.classList.toggle('bad', !!problem);
        hint.classList.toggle('good', !problem);
        hint.textContent = problem || (name === 'password2' ? '비밀번호가 일치해요.' : '좋아요!');
        return !problem;
    }

    async function submitSignup(ev) {
        ev.preventDefault();
        const values = signupValues();
        const error = $('#signup-error');
        error.textContent = '';
        let firstBad = null;
        for (const name of Object.keys(HINTS)) {
            if (!checkField(name, values) && !firstBad) firstBad = name;
        }
        if (firstBad) { $(`#signup-${firstBad}`).focus(); return; }

        const button = $('#signup-submit');
        setBusy(button, true, '가입하는 중…');
        try {
            const result = await api('POST', '/api/auth/signup',
                { username: values.username, nickname: values.nickname, password: values.password });
            setAuth(result);
            toast(`가입을 환영해요, ${result.member.nickname}님!`);
            showLobby();
        } catch (e) {
            error.textContent = e.message;
            if (e.message.includes('아이디')) $('#signup-username').focus();
            else if (e.message.includes('닉네임')) $('#signup-nickname').focus();
        } finally {
            setBusy(button, false, '가입하고 시작하기');
        }
    }

    async function logout() {
        const auth = state.auth;
        setAuth(null);
        if (auth) {
            try {
                await fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${auth.token}` } });
            } catch { /* 서버에 못 닿아도 이 기기에서는 로그아웃 */ }
        }
        toast('로그아웃했어요.');
        if (!state.me) showLobby();
    }

    /** 저장해 둔 로그인이 아직 유효한지 서버에 확인한다. */
    async function restoreAuth() {
        const saved = storage.get(AUTH_KEY);
        if (!saved || !saved.token) return;
        state.auth = saved;
        try {
            const member = await api('GET', '/api/auth/me');
            setAuth({ token: saved.token, member });
        } catch (e) {
            // 만료·로그아웃된 토큰이면 지운다. 서버가 잠깐 꺼진 경우(status 0)는 그대로 둔다.
            if (e.status === 401) setAuth(null);
        }
    }

    function bindAuthForms() {
        $('#login-form').addEventListener('submit', submitLogin);
        $('#signup-form').addEventListener('submit', submitSignup);
        for (const name of Object.keys(HINTS)) {
            const input = $(`#signup-${name}`);
            input.addEventListener('blur', () => { if (input.value) checkField(name); });
            input.addEventListener('input', () => {
                if (input.classList.contains('invalid') || $(`#signup-form .hint[data-for="${name}"]`).classList.contains('good')) checkField(name);
                if (name === 'password' && $('#signup-password2').value) checkField('password2');
            });
        }
        document.addEventListener('click', ev => {
            const link = ev.target.closest('[data-go]');
            if (!link) return;
            ev.preventDefault();
            if (link.dataset.go === 'lobby') showLobby(); else showAuth(link.dataset.go);
        });
    }

    // ------------------------------------------------------------ 시작
    async function init() {
        $('#nickname').value = storage.get(NICK_KEY) || '';
        bindAuthForms();
        await restoreAuth();
        renderAuth();
        $('#join-form').addEventListener('submit', ev => {
            ev.preventDefault();
            const code = $('#join-code').value.trim();
            if (code.length !== 6) { toast('6자리 코드를 입력해 주세요.'); return; }
            joinRoom(code);
        });
        $('#refresh-rooms').addEventListener('click', loadOpenRooms);
        $('#modal-close').addEventListener('click', closeGameModal);
        $('#game-modal').addEventListener('click', ev => { if (ev.target.id === 'game-modal') closeGameModal(); });
        document.addEventListener('keydown', ev => { if (ev.key === 'Escape') closeGameModal(); });
        $('#modal-create').addEventListener('click', () => createRoom(state.modalGame));
        $('#modal-solo').addEventListener('click', () => startSolo(state.modalGame));
        $('#solo-back').addEventListener('click', showLobby);
        $('#solo-start').addEventListener('click', () => createSolo(state.soloGame, state.soloLevel));
        $('#leave-btn').addEventListener('click', leave);
        $('#room-code').addEventListener('click', copyInvite);
        $('#brand').addEventListener('click', ev => { if (state.me) { ev.preventDefault(); toast('나가기 버튼으로 방을 나갈 수 있어요.'); } });
        renderGameCards();

        const invited = new URLSearchParams(location.search).get('room');
        if (invited) $('#join-code').value = invited.toUpperCase();

        // 새로고침 전에 있던 방이 아직 살아 있으면 같은 자리로 돌아간다.
        const saved = storage.get(STORAGE_KEY);
        if (saved && saved.code && saved.token) {
            try {
                await api('GET', `/api/rooms/${saved.code}?token=${encodeURIComponent(saved.token)}`);
                enterRoom(saved);
                return;
            } catch {
                storage.remove(STORAGE_KEY);
            }
        }
        showLobby();
    }

    init();
})();
