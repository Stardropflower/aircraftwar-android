/**
 * 飞机大战 · 网络对战服务端（比分竞赛）
 *
 * 协议：JSON over WebSocket（与客户端 net/BattleProtocol.java 一一对应）。
 *
 * 玩法：两名玩家凭 6 位房间码进入同一局，各自在本地跑关卡，
 * 每 1 秒上报分数/血量，先到目标分者胜，胜负由服务端权威判定。
 *
 * 关键约定：
 *  - 房间码 6 位（大写字母 + 数字，排除 O/0/I/1）
 *  - 服务端权威判定胜负
 *  - 断线后保留房间 60 秒，重连（带同一名字重新 join）补发当前 state
 *  - 心跳：客户端每 15s 发 ping，连续 30s 没动静判掉线
 *
 * 运行：npm install && npm start    （监听 8765 端口，可用环境变量 PORT 覆盖）
 */

const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT) || 8765;

const ROOM_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 排除 O/0/I/1
const ROOM_CODE_LENGTH = 6;
const DISCONNECT_GRACE_MS = 60 * 1000;   // 断线保留 60 秒
const HEARTBEAT_TIMEOUT_MS = 30 * 1000;  // 30 秒没动静判掉线
const ROOM_TTL_MS = 30 * 60 * 1000;      // 房间最长存活 30 分钟
const CLEANUP_DELAY_MS = 3 * 1000;       // 结束后延迟销毁，确保双方都收到结果

/** 房间表：code -> room */
const rooms = new Map();

// ---------------------------------------------------------------------------
// HTTP + WebSocket 服务
// ---------------------------------------------------------------------------

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('飞机大战对战服务端运行中，房间数：' + rooms.size);
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  ws.isAlive = true;
  ws.room = null;   // 关联的房间对象
  ws.slot = null;   // 'A' | 'B'

  ws.on('message', (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch (e) {
      return; // 忽略坏消息
    }
    onMessage(ws, msg);
  });

  ws.on('close', () => onDisconnect(ws));
  ws.on('error', () => {});
});

server.listen(PORT, () => {
  console.log('飞机大战对战服务端已启动：ws://0.0.0.0:' + PORT);
});

// ---------------------------------------------------------------------------
// 消息分发
// ---------------------------------------------------------------------------

function onMessage(ws, msg) {
  const type = msg.type;
  if (!type) return;

  ws.lastSeen = Date.now();

  switch (type) {
    case 'create':
      handleCreate(ws, msg);
      break;
    case 'join':
      handleJoin(ws, msg);
      break;
    case 'score':
      handleScore(ws, msg);
      break;
    case 'leave':
      handleLeave(ws);
      break;
    case 'ping':
      send(ws, { type: 'pong' });
      break;
    default:
      break;
  }
}

// ---------------------------------------------------------------------------
// 建房 / 进房
// ---------------------------------------------------------------------------

function handleCreate(ws, msg) {
  const name = cleanName(msg.name);
  const difficulty = (msg.difficulty || 'NORMAL').toString().toUpperCase();
  const target = clampInt(msg.target, 5000, 100, 1000000);

  const code = generateCode();
  const player = makePlayer(name, ws);
  const room = {
    code,
    difficulty,
    target,
    createdAt: Date.now(),
    a: player, // 槽位 A = 房主
    b: null,   // 槽位 B = 加入者
    ended: false,
  };
  rooms.set(code, room);
  ws.room = room;
  ws.slot = 'A';

  sendJoined(room, 'A');
  console.log('房间创建：', code, '难度', difficulty, '目标', target, '房主', name);
}

function handleJoin(ws, msg) {
  const code = (msg.room || '').toString().toUpperCase();
  const room = rooms.get(code);
  if (!room) {
    sendError(ws, '房间不存在');
    return;
  }
  if (room.ended) {
    sendError(ws, '房间已结束');
    return;
  }
  const name = cleanName(msg.name);

  // 1) 断线重连：名字和某个槽位一致，且该槽位当前处于断开状态
  if (room.a && room.a.name === name && !room.a.connected) {
    resume(room, 'A', ws);
    return;
  }
  if (room.b && room.b.name === name && !room.b.connected) {
    resume(room, 'B', ws);
    return;
  }

  // 2) 新玩家：找空槽位
  if (!room.a) {
    assignSlot(room, 'A', name, ws);
    return;
  }
  if (!room.b) {
    assignSlot(room, 'B', name, ws);
    return;
  }

  sendError(ws, '房间已满');
}

function assignSlot(room, slot, name, ws) {
  const player = makePlayer(name, ws);
  room[slot === 'A' ? 'a' : 'b'] = player;
  ws.room = room;
  ws.slot = slot;
  sendJoined(room, slot);
  broadcastState(room);
  console.log('房间', room.code, slot === 'A' ? 'A' : 'B', '加入：', name);
}

function resume(room, slot, ws) {
  const key = slot === 'A' ? 'a' : 'b';
  const player = room[key];
  player.socket = ws;
  player.connected = true;
  player.lastSeen = Date.now();
  ws.room = room;
  ws.slot = slot;
  sendJoined(room, slot);
  broadcastState(room); // 补发当前 state
  console.log('房间', room.code, slot, '重连：', player.name);
}

// ---------------------------------------------------------------------------
// 分数上报与胜负判定
// ---------------------------------------------------------------------------

function handleScore(ws, msg) {
  const room = ws.room;
  const slot = ws.slot;
  if (!room || !slot || room.ended) return;
  const key = slot === 'A' ? 'a' : 'b';
  const player = room[key];
  if (!player || player.socket !== ws) return;

  player.score = Math.max(player.score, clampInt(msg.score, player.score, 0, 1e9));
  player.hp = clampInt(msg.hp, player.hp, 0, 1e9);
  player.lastSeen = Date.now();

  checkWin(room, slot);
  if (!room.ended) {
    broadcastState(room); // 已经判出胜负就不要再广播 state 了
  }
}

function checkWin(room, slot) {
  if (room.ended) return;
  const player = room[slot === 'A' ? 'a' : 'b'];

  // 1) 达到目标分 → 直接判胜
  if (player && player.score >= room.target) {
    declareOver(room, slot, '达到目标分');
    return;
  }

  // 2) 双方都阵亡 → 比分高者胜（平局 winner 为 null）
  const a = room.a;
  const b = room.b;
  if (a && b && a.hp <= 0 && b.hp <= 0) {
    if (a.score > b.score) declareOver(room, 'A', '双方阵亡，比分判胜');
    else if (b.score > a.score) declareOver(room, 'B', '双方阵亡，比分判胜');
    else declareOver(room, null, '平局');
  }
}

function declareOver(room, winner, reason) {
  if (room.ended) return;
  room.ended = true;
  const msg = {
    type: 'over',
    winner: winner, // 'A' | 'B' | null（平局）
    scoreA: room.a ? room.a.score : 0,
    scoreB: room.b ? room.b.score : 0,
    reason: reason || '',
  };
  sendToPlayer(room.a, msg);
  sendToPlayer(room.b, msg);
  console.log('房间', room.code, '结束：', winner ? winner + ' 胜' : '平局', '（' + reason + '）');
  scheduleCleanup(room, CLEANUP_DELAY_MS);
}

// ---------------------------------------------------------------------------
// 离开 / 掉线
// ---------------------------------------------------------------------------

function handleLeave(ws) {
  const room = ws.room;
  const slot = ws.slot;
  if (!room || !slot || room.ended) return;
  // 对手直接判胜（显式退出不等宽限期）
  const other = room[otherKey(slot)];
  if (other && other.connected) {
    send(other.socket, { type: 'opponent_left' });
  }
  room.ended = true;
  console.log('房间', room.code, slot, '主动离开');
  scheduleCleanup(room, CLEANUP_DELAY_MS);
}

function onDisconnect(ws) {
  const room = ws.room;
  const slot = ws.slot;
  if (!room || !slot || room.ended) return;
  const key = slot === 'A' ? 'a' : 'b';
  const player = room[key];
  if (!player || player.socket !== ws) return;

  player.connected = false;
  player.disconnectedAt = Date.now();
  console.log('房间', room.code, slot, '掉线，进入 60 秒重连宽限期');

  // 60 秒内没重连 → 对手判胜（对手若还连着），否则销毁房间
  setTimeout(() => {
    if (room.ended) return;
    const p = room[key];
    if (!p || p.connected) return; // 已重连
    const other = room[otherKey(slot)];
    if (other && other.connected) {
      send(other.socket, { type: 'opponent_left' });
      room.ended = true;
      console.log('房间', room.code, slot, '超时未重连，对手判胜');
      scheduleCleanup(room, CLEANUP_DELAY_MS);
    } else {
      destroyRoom(room);
    }
  }, DISCONNECT_GRACE_MS);
}

// ---------------------------------------------------------------------------
// 心跳清理（每 15s 扫一次）
// ---------------------------------------------------------------------------

setInterval(() => {
  const now = Date.now();
  for (const room of rooms.values()) {
    // 超时房间销毁
    if (now - room.createdAt > ROOM_TTL_MS) {
      destroyRoom(room);
      continue;
    }
    // 已连接的玩家超过 30s 没任何消息 → 判掉线（走 60s 宽限期）
    for (const slot of ['A', 'B']) {
      const p = room[slot === 'A' ? 'a' : 'b'];
      if (p && p.connected && now - p.lastSeen > HEARTBEAT_TIMEOUT_MS) {
        p.connected = false;
        p.disconnectedAt = now;
        // 复用 onDisconnect 里的宽限逻辑：直接安排 60 秒后检查
        const key = slot === 'A' ? 'a' : 'b';
        const other = room[otherKey(slot)];
        setTimeout(() => {
          if (room.ended) return;
          const pp = room[key];
          if (!pp || pp.connected) return;
          if (other && other.connected) {
            send(other.socket, { type: 'opponent_left' });
            room.ended = true;
            scheduleCleanup(room, CLEANUP_DELAY_MS);
          } else {
            destroyRoom(room);
          }
        }, DISCONNECT_GRACE_MS);
      }
    }
  }
}, 15 * 1000);

// ---------------------------------------------------------------------------
// 工具函数
// ---------------------------------------------------------------------------

function generateCode() {
  let code;
  do {
    code = '';
    for (let i = 0; i < ROOM_CODE_LENGTH; i++) {
      code += ROOM_CODE_ALPHABET[Math.floor(Math.random() * ROOM_CODE_ALPHABET.length)];
    }
  } while (rooms.has(code));
  return code;
}

function makePlayer(name, socket) {
  return {
    name,
    score: 0,
    hp: 1000,        // 与 HeroAircraft.MAX_HP 一致
    socket,
    connected: true,
    lastSeen: Date.now(),
  };
}

function cleanName(name) {
  return (name || '玩家').toString().trim().slice(0, 20) || '玩家';
}

function clampInt(value, def, min, max) {
  const n = Number(value);
  if (!Number.isFinite(n)) return def;
  return Math.max(min, Math.min(max, Math.floor(n)));
}

function otherKey(slot) {
  return slot === 'A' ? 'b' : 'a';
}

function sendJoined(room, slot) {
  const key = slot === 'A' ? 'a' : 'b';
  const player = room[key];
  const other = room[otherKey(slot)];
  send(player.socket, {
    type: 'joined',
    room: room.code,
    you: slot,
    opponent: other ? other.name : null,
    difficulty: room.difficulty,
    target: room.target,
  });
}

function broadcastState(room) {
  const state = {
    type: 'state',
    a: playerView(room.a),
    b: playerView(room.b),
  };
  sendToPlayer(room.a, state);
  sendToPlayer(room.b, state);
}

function playerView(player) {
  return player
    ? { name: player.name, score: player.score, hp: player.hp }
    : { name: '', score: 0, hp: 0 };
}

function sendToPlayer(player, msg) {
  if (player && player.connected) {
    send(player.socket, msg);
  }
}

function send(socket, obj) {
  if (socket && socket.readyState === 1) { // 1 = OPEN
    try {
      socket.send(JSON.stringify(obj));
    } catch (e) {
      // 发送失败忽略
    }
  }
}

function sendError(ws, message) {
  send(ws, { type: 'error', message });
}

function scheduleCleanup(room, delayMs) {
  setTimeout(() => destroyRoom(room), delayMs);
}

function destroyRoom(room) {
  if (!rooms.has(room.code)) return;
  rooms.delete(room.code);
  for (const p of [room.a, room.b]) {
    if (p && p.socket) {
      try { p.socket.close(); } catch (e) { /* ignore */ }
    }
  }
}
