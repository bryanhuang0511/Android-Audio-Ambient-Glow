# 🎵 手機音效氣氛燈 (AudioAmbientGlow)

<div align="center">

![Platform](https://img.shields.io/badge/Platform-Android%208.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/Jetpack%20Compose-1.7-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![FPS](https://img.shields.io/badge/Refresh%20Rate-144Hz%20Smooth-FF007F?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-00F5FF?style=for-the-badge)

**專為 Android 打造的 144Hz 極致霓虹音樂邊框流光 × AOD 動態同步歌詞音樂中樞**  
*零硬線生硬感 • 16 環密集高斯霧化 • AuraFlow 智慧雙模音訊感知 • 8 倍動態節奏起伏*

</div>

---

## 📸 實機畫面展示 (Showcase)

<div align="center">

<img src="Screenshots/02_Portrait_AOD_Mode.jpg" width="320" alt="手機音效氣氛燈 AOD 動態歌詞與霓虹流光實機展示"/>

*▲ 專為息屏打造的極致沉浸 AOD 音樂中樞（純白雙緩衝歌詞 × 16 環密集高斯霓虹流光）*

</div>

---

## ✨ 核心特色與亮點功能 (Key Features)

### 1. 🎤 單行純白粗體雙緩衝歌詞 (Dual-Buffer Dynamic Glide)
- **340ms 絲滑浮現**：採用雙緩衝 (`Slot A / B`) 平滑升起切換，換行時新歌詞自下方平穩升起、舊歌詞向上淡出，徹底告別生硬閃爍。
- **純白極致清晰**：`#FFFFFF` 20.5sp Bold 純白粗體，自動與系統圓角字型（`sans-serif-medium`）深度融合。
- **黃金比例排版**：直向置於中下黃金視野區（最多 4 行向下自然延展，絕不推擠時鐘）；橫向頂部錨定鎖定，消除垂直跳動。

### 2. 🌫️ 16 環密集高斯平滑網格 (Dense Gaussian Mist)
- 全新升級 **16 環密集 GPU 頂點網格 (GPU `drawVertices`)** 搭配 $e^{-3.2u^2}(1-u^3)$ 立方加速沉黑曲線。
- **徹底消除 LCD 面板上的同心圓跑道斷層與暗部灰霧條紋**，呈現如相機鏡頭般細膩深邃的光暈！

### 3. 🏎️ 8 倍超大節奏動態對比 (8x Dynamic Speed Contrast)
- **平緩巡航**：`0.12 rev/s`（約 8 秒緩緩流動一整圈），呈現極度放鬆、優雅的呼吸慢速。
- **重低音爆發**：偵測到 Kick drum 或重低音下潛時，瞬間加速至 `0.95 ~ 1.30 rev/s`（提速 800% / 8 倍對比爆發！）。
- **打擊感強烈**：高潮鼓點一打，邊框如電流般向前狂衝；節奏一緩，迅速回落至慢速悠遊。

### 4. ⚡ AuraFlow 智慧雙模音訊感知 (Smart Dual-Mode Perception)
- **Mode A (標準 DSP)**：針對開放混音通道之手機（如 vivo、Pixel、小米），30ms 極限低延遲捕捉真實音樂振幅。
- **Mode B (AuraFlow 智慧直通)**：針對 Samsung One UI、聯發科晶片或開啟 Dolby Atmos 等將音訊直通硬體 DAC 之機型，自動無縫點亮流光並提供 8 倍動態節奏起伏！

### 5. 📱 亮屏 / 息屏真滿版覆蓋 (Real Edge-to-Edge)
- 突破系統導航列與狀態欄限制，燈條 100% 貼合手機實體極限邊框。
- **0.0 秒暫停即刻深睡**：音樂暫停時 100% 熄滅，GPU/CPU 佔用歸零，全天候播放每小時耗電僅約 2.5% ~ 3.5%！

---

## 📦 版本挑選與下載指南 (Download & Version Guide)

本專案於 `v1.2.0` 正式提供兩款針對不同螢幕與系統架構最佳化的獨立 APK：

| 安裝包名稱 | 適用機型與面板 | 核心優勢 |
| :--- | :--- | :--- |
| 📱 **`手機音效氣氛燈_v1.2.0_全機型通用版.apk`**<br>*(強烈推薦 / 預設首選)* | **Samsung Galaxy 全系列 (One UI)**、**TFT LCD 面板手機**、小米、OPPO、POCO、Realme 等所有 Android 裝置 | • 內建 16 環密集抗斷層網格<br>• 2.8dp 鮮亮霓虹外邊緣<br>• 搭載 AuraFlow 智慧直通感知（防不亮） |
| 📱 **`手機音效氣氛燈_v1.2.0_OLED極致版.apk`** | **vivo (OriginOS)**、**Google Pixel** 等具備 OLED 面板與標準全局音效混音之旗艦機 | • 144Hz 極限低延遲實時波形反應<br>• 大範圍深邃光學羽化 |

---

## 🚀 快速上手與權限設定 (Quick Start)

首次安裝開啟後，請依序授予以下 3 項系統權限以發揮完整功能：

1. **🔔 通知存取權限 (Notification Listener)**：
   - 用於即時獲取正在播放的歌名、歌手、播放進度與自動載入動態歌詞。
2. **🪟 顯示在其他應用程式上層 (Display Over Other Apps)**：
   - 用於在手機桌面、一般 App 亮屏使用時呈現邊框流光。
3. **🔋 電池最佳化白名單 (Battery Whitelist)**：
   - 建議將本 App 設為「不受限制」，確保息屏掛機播放時服務不被系統後台誤殺。

---

## 🎮 操作與手勢說明 (Gestures & Usage)

- **進入 AOD 音樂中樞**：開啟 App 點擊「啟動 AOD 模式」，或於息屏播歌時自動喚起。
- **解鎖離開**：在 AOD 畫面中央任意位置 **向上輕滑（Swipe UP）** 即可秒解鎖回到系統桌面，純淨防誤觸。
- **橫豎屏自動旋轉**：支援手機直向與橫向自動適配，橫放時自動切換為復古劇院音響視圖。
- **支援播放器**：Spotify、YouTube Music、Apple Music、KKBOX、網易雲音樂、Poweramp 及所有標準 Android 本地播放器。

---

## ⚠️ 注意事項與常見問題 (FAQ)

- **Q: 為什麼三星手機在一般懸浮模式下不會隨音樂閃爍？**  
  A: 三星 One UI 內建 SoundAlive 與硬體直通技術，繞過了 Android 全局混音通道。請下載 **「全機型通用版」**，即可透過內建的 AuraFlow 智慧引擎完美發光與加減速！
- **Q: 會不會很耗電？**  
  A: 本 App 採用 GPU 頂點網格硬體繪製，運行中每幀 0 Byte 記憶體分配；音樂暫停時立即 0.0 秒徹底休眠，全天使用完全無感！

---

## 📄 開源授權 (License)

本專案基於 [MIT License](LICENSE) 條款開源發布。
