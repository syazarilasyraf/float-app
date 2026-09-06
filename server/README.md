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
| `PUBLIC_URL` | `http://localhost:3000` | URL used in viewer links (your laptop's local IP for home use) |
| `STUN_SERVERS` | Google STUN | Comma-separated STUN URLs |
| `TURN_SERVERS` | Metered Open Relay (free public) | Optional TURN fallback. Format: `url\|username\|credential` |

Copy `.env.example` to `.env` and adjust as needed.

For local Wi-Fi streaming, set `PUBLIC_URL` to your laptop's local IP, for example:

```env
PUBLIC_URL=http://192.168.1.100:3000
```

## TURN fallback

Direct peer-to-peer WebRTC usually works on the same Wi-Fi or behind friendly home-router NAT. It does **not** work when the sender is on mobile data behind carrier CGNAT (e.g. the Pixel sharing its own hotspot) — neither side has a publicly reachable address, so STUN alone cannot establish a connection. In that case media relays through TURN.

By default the server uses a free public TURN fallback (Metered Open Relay; media stays DTLS-encrypted end to end, so the relay cannot see stream content). To use your own TURN server instead:

```bash
TURN_SERVERS="turn:your-turn-server:3478|user|pass"
```

Note: the signaling server itself must be reachable from the internet for a remote viewer. If your laptop (running this server) joins the Pixel's hotspot, remote viewers cannot reach it — keep the server on a network with inbound access (e.g. home router with port forwarding) and only the Pixel moves to hotspot/mobile data.

## Bandwidth estimate (one 720p30 stream)

- WebRTC direct: **~2-3 Mbps upload** from the Pixel.
- TURN relay: roughly the same amount passes through the TURN server.

## Architecture summary

```
Pixel 9  --WebRTC-->  browser viewer
           (direct peer-to-peer)

Signaling server only relays SDP/ICE messages.
Video never passes through the server unless TURN fallback is active.
```
