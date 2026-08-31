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

## Deploy on the Internet

For the viewer to connect from another network:

1. Put this server behind a public address with HTTPS.
2. Set `PUBLIC_URL` to that address (e.g. `https://stream.example.com`).
3. Configure the Android Stream tab to **Internet** mode and enter the same URL.
4. For NAT/firewall fallback, configure a TURN server and set `TURN_SERVERS`.

### Why HTTPS matters

Browsers require a secure context for WebRTC on the public Internet. Use HTTPS for `PUBLIC_URL`.

### Cheapest hosting options

- **VPS**: A tiny VM (1 vCPU, 512 MB RAM, ~$3-5/month) is enough for signaling. TURN bandwidth is the main cost if fallback is used.
- **Cloudflare Tunnel**: Free. Run `cloudflared` on your home server/laptop to expose the local server over HTTPS without opening router ports. Good for personal use.
- **Reverse proxy**: Caddy or nginx can provide HTTPS with a real certificate. Use Caddy if you want automatic HTTPS.

### TURN server

Direct peer-to-peer WebRTC usually works, but strict NAT/firewalls may need TURN. Options:

- **coturn** on the same VPS.
- A managed TURN service (e.g. Twilio, Metered, Xirsys) — pay per GB relayed.

Set `TURN_SERVERS` like:

```bash
TURN_SERVERS="turn:stream.example.com:3478|user|pass"
```

### Bandwidth estimate (one 720p30 stream)

- Normal WebRTC direct: **~2-3 Mbps upload** from the Pixel.
- TURN relay: roughly the same amount passes through the TURN server.

### Architecture summary

```
Pixel 9  --WebRTC-->  wife's browser
           (direct peer-to-peer)

Signaling server only relays SDP/ICE messages.
Video never passes through the server unless TURN fallback is active.
```
