/**
 * Minimal WebRTC signaling server for Float private screen streaming.
 *
 * Responsibilities:
 * - Create private streams with random IDs/tokens.
 * - Relay SDP offers/answers and ICE candidates between sender and viewer.
 * - Serve the browser viewer page.
 *
 * The server does NOT process, transcode, or retransmit video. WebRTC is
 * peer-to-peer; this server only helps establish the connection.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

const PUBLIC_URL = process.env.PUBLIC_URL || `http://localhost:${PORT}`;

function parseIceServers() {
  const stun = (process.env.STUN_SERVERS || 'stun:stun.l.google.com:19302')
    .split(',')
    .map((url) => url.trim())
    .filter(Boolean)
    .map((url) => ({ urls: url }));

  const turn = (process.env.TURN_SERVERS || '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [url, username, credential] = entry.split('|');
      return { urls: url, username, credential };
    });

  return [...stun, ...turn];
}

const iceServers = parseIceServers();

// In-memory room registry. Rooms are discarded when sender disconnects.
const rooms = new Map();

function generateToken() {
  return crypto.randomBytes(24).toString('hex');
}

function cleanupRoom(streamId) {
  const room = rooms.get(streamId);
  if (!room) return;
  if (room.sender) room.sender.close();
  if (room.viewer) room.viewer.close();
  rooms.delete(streamId);
  console.log(`[room ${streamId}] cleaned up`);
}

function send(ws, message) {
  if (ws && ws.readyState === 1) {
    ws.send(JSON.stringify(message));
  }
}

function createStream(req, res) {
  const streamId = crypto.randomBytes(16).toString('hex');
  const token = generateToken();
  const viewerUrl = `${PUBLIC_URL}/watch/${streamId}?token=${token}`;

  rooms.set(streamId, {
    token,
    sender: null,
    viewer: null,
  });

  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ streamId, token, viewerUrl }));
  console.log(`[http] created stream ${streamId}`);
}

function handleHttp(req, res) {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (req.method === 'POST' && url.pathname === '/stream') {
    return createStream(req, res);
  }

  if (req.method === 'GET' && url.pathname === '/ice-config') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ iceServers }));
    return;
  }

  if (req.method === 'GET' && url.pathname.startsWith('/watch/')) {
    const filePath = path.join(__dirname, 'static', 'watch.html');
    fs.readFile(filePath, (err, data) => {
      if (err) {
        res.writeHead(500);
        res.end('Failed to load viewer');
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(data);
    });
    return;
  }

  res.writeHead(404);
  res.end('Not found');
}

function handleWebSocket(ws, req) {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const parts = url.pathname.split('/').filter(Boolean);

  if (parts.length < 3 || parts[0] !== 'ws') {
    ws.close(4001, 'Invalid path');
    return;
  }

  const role = parts[1];
  const streamId = parts[2];
  const token = url.searchParams.get('token');

  const room = rooms.get(streamId);
  if (!room) {
    ws.close(4004, 'Stream not found');
    return;
  }

  if (role === 'sender') {
    if (room.sender) {
      ws.close(4009, 'Sender already connected');
      return;
    }
    room.sender = ws;
    console.log(`[room ${streamId}] sender connected`);
  } else if (role === 'viewer') {
    if (token !== room.token) {
      ws.close(4003, 'Invalid token');
      return;
    }
    if (room.viewer) {
      ws.close(4009, 'Viewer already connected');
      return;
    }
    room.viewer = ws;
    console.log(`[room ${streamId}] viewer connected`);
  } else {
    ws.close(4001, 'Invalid role');
    return;
  }

  ws.on('message', (raw) => {
    let message;
    try {
      message = JSON.parse(raw);
    } catch (e) {
      console.error('[ws] invalid JSON', raw.toString());
      return;
    }

    const peer = role === 'sender' ? room.viewer : room.sender;

    // Relay everything except connection-state probes.
    if (message.type === 'ping') {
      send(ws, { type: 'pong' });
      return;
    }

    if (peer) {
      send(peer, message);
    }
  });

  ws.on('close', () => {
    if (role === 'sender') {
      cleanupRoom(streamId);
    } else {
      room.viewer = null;
      send(room.sender, { type: 'viewer-disconnected' });
      console.log(`[room ${streamId}] viewer disconnected`);
    }
  });
}

const server = http.createServer(handleHttp);
const wss = new WebSocketServer({ server });

wss.on('connection', handleWebSocket);

server.listen(PORT, HOST, () => {
  console.log(`Float signaling server listening on http://${HOST}:${PORT}`);
});
