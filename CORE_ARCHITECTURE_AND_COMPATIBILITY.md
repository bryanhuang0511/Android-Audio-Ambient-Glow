# 📱 AudioAmbientGlow 全機型核心架構與工程規範 (Core Architecture Spec)

> **版本基準**：🟢 **v1.2.0 (全機型通用 / OLED 極致雙模架構)**  
> **適用平台**：Android 8.0+ (API 24 ~ 36) • 全 Android 廠牌（Samsung One UI / vivo OriginOS / Google Pixel / Xiaomi / OPPO 等）  
> **定位說明**：內部開發者技術規範手冊，闡述核心歌詞引擎、GPU 網格渲染與音訊感知管線之底層實作。

---

## 🧭 一、跨裝置統一核心模組 (Universal Architecture)

本專案之下列核心模組在所有 Android 裝置上均採用 **單一程式碼庫（Single Unified Codebase）**，具備高度相容性與無縫移植性：

### 1. 🎤 動態雙緩衝歌詞引擎 (`LyricsEngine.kt`)
- **多線程並行檢索**：自動並行請求 LRCLIB 多組端點（精準檢索、歌手+歌名清理、Fallback 標題），0 延遲自動套用最快回應。
- **單行純白粗體滑動**：採用 Dual-Buffer (`tvActiveA` / `tvActiveB`) 機制，換行時新歌詞自下方 340ms 平滑升起，舊歌詞向上淡出，純白粗體 (`#FFFFFF`, 20.5sp, Bold) 視覺極度清晰。
- **LRC 時間軸校準**：支援 `[offset]` 時間軸標籤與前奏空白防早現保護。

### 2. 📐 黃金視角排版與系統字型繼承 (`AodGlowActivity.kt`)
- **直向模式 (Portrait)**：中下黃金視野區配置（`midSpacer: weight = 0.85f`），最多支援 4 行歌詞向下自然延展，絕不推擠時鐘與進度條。
- **橫向模式 (Landscape)**：右欄整體下移（`topMargin = 34dp` + 歌詞間距 `26dp`），頂部鎖定防跳動（Top-Anchored），徹底消除垂直抖動。
- **系統字型深度同步**：自動繼承 Android 系統預設字體與圓角粗體（`sans-serif-medium` / `sans-serif-rounded`），無論原廠字體或使用者自訂字型皆完美融合。
- **純淨防誤觸解鎖**：純向上滑動（Swipe UP）手勢解鎖，消除左右滑動誤觸干擾。

### 3. ⏱️ 播放狀態與進度條即時追蹤 (`MediaPlaybackDetector.kt`)
- **生命週期精準同步**：暫停時進度條與歌詞精準定格；續播時毫秒級接續；停止播放後卡片自動純淨隱藏（`View.GONE`）。

---

## 🔬 二、GPU 渲染與音訊感知管線 (Rendering & Audio Pipeline)

### 1. 🌫️ 16 環密集高斯羽化網格 (`GlowTrackView.kt`)
- 採用 GPU 頂點網格（`Canvas.drawVertices`），預先分配 16 環 × 160 分段之頂點與索引緩衝區。
- 採用 $e^{-3.2u^2}(1-u^3)$ 複合高斯與立方加速沉黑曲線，確保光芒平滑羽化至 0.0 alpha，在 LCD 與 OLED 螢幕上皆零色階斷層。
- 結合 2.8dp 鮮亮霓虹外邊緣（`corePaint`），突破實體手機外框遮擋，呈現立體深邃光芒。

### 2. ⚡ AuraFlow 智慧雙模音訊引擎 (`GlowPhysicsEngine.kt`)
- **Mode A (即時 DSP 感知)**：開放全局音效通道 (`Visualizer(0)`) 之裝置，以 25ms 瞬態響應捕捉真實重低音鼓點。
- **Mode B (AuraFlow 智慧直通)**：針對 Samsung One UI 等將音訊直通硬體 DAC 輸出之機型，自動在播歌時生成 8 倍動態節奏起伏（0.12 rev/s 慢速巡航 $\rightarrow$ 1.30 rev/s 鼓點狂飆）。
- **100% 靜音熄滅**：音樂暫停時 0.0 秒徹底停止渲染管線，CPU/GPU 負載歸零。
