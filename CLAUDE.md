# etzhayyim-project-port — Port Infrastructure + Call Tracking

> **T1 Logical Actor**: Manifest-driven (`20-actors/port/actor-manifest.jsonld`).

`port.etzhayyim.com` (nanoid: `p0rt7890`) — 8.5K major ports globally (UNCTAD + World Port Source). UN/LOCODE 5-character identifier. Port call event tracking.

## cross-actor
- `vessel` — port call (vessel ↔ port arrival/departure)
- `cargo` — loadingPortDid + dischargePortDid
- `bunker` — fuel supply at port
- `oil-distribution` — terminal at port
- `oil-shipping` — chokepoint exposure (port-level granularity)

## Design
→ ADR-0013: 海運 + エネルギー クラスタ cross-actor (maritime ops)
