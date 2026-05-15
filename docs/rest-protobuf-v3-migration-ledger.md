# REST-to-Protobuf V3 Migration Ledger

This ledger is the backend-owned source of truth for additive `/v3` protobuf REST work. `/v1` and `/v2` remain available for legacy clients until all deployed Minecraft plugins and panel code have moved.

## Rules

- Keep `/v3` additive. Do not remove or retarget `/v1` or `/v2` routes as part of this migration.
- Use binary protobuf for Modl-controlled `/v3` REST routes with `application/x-protobuf`.
- Return `ApiError` protobuf bodies for `/v3` error paths, including framework, validation, auth, media type, and rate-limit failures.
- Preserve existing service and authorization boundaries; V3 controllers should adapt transport only.
- Do not use Java `var` or inline fully-qualified class names in new or touched code.
- Publish and consume a real `gg.modl:proto` / `@modl-gg/proto` release before downstream client adoption. Local composite builds are development-only.

## Current Backend State

| Legacy area | V3 status | Notes |
| --- | --- | --- |
| `POST /v1/minecraft/players/login` | Implemented | `POST /v3/minecraft/players/login` using `PlayerLoginRequest`. |
| `POST /v1/minecraft/players/disconnect` | Implemented | `POST /v3/minecraft/players/disconnect`. |
| `POST /v1/minecraft/players/update-server` | Implemented | `POST /v3/minecraft/players/update-server`. |
| `POST /v1/minecraft/players/submit-ip-info` | Implemented | `POST /v3/minecraft/players/submit-ip-info`. |
| `GET /v1/minecraft/players/online` | Implemented | `GET /v3/minecraft/players/online`. |
| Player lookup/profile routes | Implemented | UUID, name, lookup, and lookup-profile exist. `NoteEntry.id` was added to preserve note identity. |
| Player notes, linked accounts, punishments, reports, pardon | Implemented | Additive `/v3/minecraft/players/...` parity routes now cover these legacy player subroutes. |
| Chat and command log submission | Implemented | `POST /v3/minecraft/players/chat-log` and `/command-log`. |
| Chat and command log readback | Deferred | Legacy readback routes exist; migrate only if Minecraft still consumes them. |
| Punishment create/read/mutate routes | Implemented | Preview, recent, detail, create, acknowledge, stat wipe, duration, toggle, note, evidence, upload token, pardon, ticket association exist. |
| Punishment types | Implemented | `GET /v3/minecraft/punishments/types` uses `PunishmentTypesResponse`. |
| Tickets read and write routes | Implemented | List, detail, player tickets, by-ids, create, unfinished create, and claim exist. |
| Reports routes | Implemented | List, player, assign, dismiss, and resolve exist under `/v3/minecraft/reports`. |
| Dashboard stats | Implemented | `GET /v3/minecraft/dashboard/stats`. |
| Staff routes | Implemented | List, permissions, role update, and disconnect exist. |
| Role routes | Implemented | List, detail, and permission update exist. |
| Replay routes | Implemented | Init upload and confirm upload exist. |
| Notification acknowledge | Implemented with schema caveat | `acknowledged_at` nullability is represented as an empty string today; schema should be made explicit. |
| Startup and sync | Implemented | `POST /v3/minecraft/startup` and `POST /v3/minecraft/players/sync` exist. `StartupResponse` was extended with realtime bootstrap fields. |
| Migration upload/progress | Missing | Migrate only if still needed by supported Minecraft migration flow. |

## Release Gate

- `proto/proto/modl/v1/player.proto`, `proto/proto/modl/v1/sync.proto`, and `proto/proto/modl/v1/ticket.proto` are now ahead of the currently resolved published artifacts.
- Backend local verification must use `-Pmodl.useLocalProto=true` until `gg.modl:proto:1.2.0` and `@modl-gg/proto@1.2.0` are published with `NoteEntry.id`, `PlayerNoteCreateResponse.success`, startup realtime bootstrap fields, and Minecraft ticket/report validation constraints.
- After publishing, run backend verification without `-Pmodl.useLocalProto=true` to prove the release path no longer depends on sibling source.

## Client Rollout Order

1. Backend and proto complete, reviewed, and verified.
2. Minecraft HTTP client migrates route group by route group to `/v3` protobuf while preserving the public plugin API.
3. Panel transport work starts after query key, URL, and decoder responsibilities are separated; realtime invalidation remains cache invalidation plus HTTP refetch.
