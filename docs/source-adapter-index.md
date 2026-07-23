# Source adapter index

| Source | Complete script | Adapter | Current verification |
|---|---|---|---|
| YZX | `config/source-scripts/yzx.js` | `YzxAdapter` | home/search/detail/play URL verified |
| XifanACG | `config/source-scripts/xifanacg.js` | `XifanAdapter` | home/search/detail/play URL verified |
| Gugu | `config/source-scripts/gugu.js` | `GuguAdapter` | home/search/detail verified; play upstream authentication pending |
| Guazi | `config/source-scripts/guazi.js` | `CompleteScriptAdapter` | requires Node-capable JS worker |
| Shuangxing | `config/source-scripts/shuangxing.js` | `CompleteScriptAdapter` | requires Node-capable JS worker |
| AkiAnime | `config/source-scripts/akianime.js` | `CompleteScriptAdapter` | browser-oriented script; requires Node-capable JS worker/browser boundary |
| LMM85 | `config/source-scripts/lmm85.js` | `CompleteScriptAdapter` | browser challenge/sniffing boundary; requires Node-capable worker |
| DMBus | `config/source-scripts/dmbus.js` | `CompleteScriptAdapter` | browser/HHJX boundary; requires Node-capable worker |

The complete-script adapter passes all contract entry points through the worker boundary. It does not rewrite a source into a single search or play function. The current Vinext preview sandbox disallows child-process execution, so the five worker-backed sources report isolated errors while other sources continue to serve.
