# Float Stream Signaling Server

Minimal Node.js/WebSocket server for the Float private screen-streaming feature.

## What it does

- Creates private stream sessions with random IDs/tokens.
- Relays WebRTC SDP offers/answers and ICE candidates between the Android sender and one browser viewer.
- Serves a minimal browser viewer page.
- Does **not** process, transcode, or retransmit video. The media flows peer-to-peer via WebRTC.

## Run locally

```bash
cd server
npm install
node index.js
```

The server listens on `0.0.0.0:3000` by default.

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3000` | HTTP/WebSocket port |
| `HOST` | `0.0.0.0` | Bind address |
| `PUBLIC_URL` | `http://localhost:3000` | Public URL used in viewer links |
| `STUN_SERVERS` | Google STUN | Comma-separated STUN URLs |
| `TURN_SERVERS` | *(none)* | Optional TURN fallback. Format: `url\|username\|credential` |

Copy `.env.example` to `.env` and adjust as needed.

## Deploy

Put this behind any server with a public IP or domain. Make sure the Android app is configured to point to `http://YOUR_SERVER:3000` in the Stream tab.

If the sender and viewer are on different networks, you almost certainly need a TURN server for NAT/firewall fallback. The server does not provide TURN itself; configure one (e.g. coturn) and set `TURN_SERVERS`.
