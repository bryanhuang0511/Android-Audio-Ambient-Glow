# 📱 AudioAmbientGlow 全機型通用核心架構與跨裝置相容性規範

> **建立時間**：2026-09-02  
> **適用平台**：Android 8.0+ (API 24 ~ 36) • 全 Android 廠牌（Samsung One UI / vivo OriginOS / Google Pixel / POCO / Xiaomi / OPPO 等）  
> **核心狀態**：🟢 **AOD 視覺排版與動態歌詞引擎 100% 全機型統一**

---

## 🧭 一、跨裝置統一模組清單 (100% Universal Components)

本專案之下列核心模組在 **Samsung Galaxy A32**、**vivo** 以及所有 Android 裝置上均採用 **單一程式碼庫（Single Unified Codebase）**，具備高度相容性與無縫移植性：

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

## 🔬 二、音訊感知與發光技術機制深度解析 (Audio & Glow Pipeline)

### 1. 💡 v1.1.0 vs v1.2.0 在 Samsung A32 上的發光差異根因
| 版本 | 發光行為 | 底層邏輯差異 | 說明 |
| :--- | :--- | :--- | :--- |
| **v1.1.0** | ⚠️ **會發光，但無節奏感且暫停不熄滅** | 包含 `if (isAodFullscreen) brightness.coerceAtLeast(0.45f)` 兜底機制 | 即使音訊波形為 0，AOD 全螢幕仍強制維持 45% 亮度旋轉，但無法隨音樂鼓點跳動，且暫停時無法完全熄滅。 |
| **v1.2.0** | 🌑 **完全不發光 (全黑)** | 導入 `100% Silence Shutoff` 嚴格節電熄滅機制 | 三星 One UI 採用硬體音訊直通 (Direct Offload)，`Visualizer(0)` 獲取振幅為 0，被判定為「靜音」而徹底熄滅。 |

### 2. 🛠️ 未來三星 A32 專屬發光修復策略（待實作）
- **智慧雙模感知 (Smart Dual-Mode Fallback)**：
  - **有波形輸入時 (vivo 等)**：100% 真實 30ms DSP 瞬態鼓點狂飆。
  - **波形為 0 但正在播歌時 (`isPlaying == true`, Samsung A32)**：自動切換至「**AuraFlow 智慧巡航律動**」，根據歌曲播放進度驅動 144Hz 邊框呼吸流光；**音樂暫停時則 100% 熄滅節電**。

---

## 🗄️ 三、歷史備份目錄說明 (`vivo_version_backup`)
- **路徑**：`mobile/vivo_version_backup/GlowTrackView_VivoOriginal.kt`
- **性質**：早期 4 環硬線邊框與舊版渲染管線的歷史參考源碼。
- **維護方針**：目前主線已全面由 6 環高斯羽化 GPU Mesh 與雙緩衝歌詞接管，此檔案可作為歷史存檔保留，亦可隨時清理而不影響任何專案運作。
