# 2026-07-23 — remaining source JS adapter pass

## Scope

The eight complete source scripts under `config/source-scripts/` are now retained as first-class project artifacts. They are copied to `public/source-scripts/` for deploy packaging and referenced by the adapter registry.

## Implemented

- `yzx`: TypeScript adapter for encrypted home/search/detail/play responses. Live home, search, detail and m3u8 play URL were verified.
- `xifanacg`: HTML home/search/detail/play adapter. Live home, search, detail and direct media extraction were verified.
- `gugu`: complete AES API contract for init, category lists, search, detail and `vodParse`; detail and catalogue retrieval were verified. One current upstream play attempt returned `Authentication failed`, so the source must not be labelled play-healthy until the upstream token/signature contract is rechecked.
- `guazi`, `shuangxing`, `akianime`, `lmm85`, `dmbus`: registered through `CompleteScriptAdapter`, preserving the full script entry points (`categories`, `home/homeSections`, `search`, `searchFiltered`, `detail`, `play`) instead of extracting a single method.

## Runtime boundary

The local Vinext preview worker rejects `child_process` execution (`ERR_METHOD_NOT_IMPLEMENTED`). The five complete-script adapters therefore fail in isolation and do not block native/remote sources. A Node-capable server worker is required before these five are marked `playVerified`; no CAPTCHA, login, paywall, or anti-hotlink control is bypassed.

## Verification

- `npm run lint`: passes with the existing remote-image `<img>` warning.
- `npm run build`: passes.
- `/api/home?source=gugu`: returns live sections.
- The five complete-script routes return isolated adapter errors in the current preview runtime, as designed.
