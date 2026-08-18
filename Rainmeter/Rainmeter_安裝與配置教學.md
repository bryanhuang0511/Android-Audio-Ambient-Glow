# 🌟 Windows Rainmeter 音效流光與工作列小工具安裝指南

本套件專為追求極致音樂視覺化體驗打造，包含 **360° 全螢幕四周邊框動態環繞流光 (`MusicAudioGlow`)** 與 **工作列音樂律動小工具 (`BMediaTaskbarWidget`)**。

---

## 🚀 快速開始 (一鍵安裝)

### 步驟 1：下載並安裝 Rainmeter
* 若你的電腦尚未安裝 Rainmeter，請前往官方網站下載最新版本：
  🔗 **[Rainmeter 官方網站下載點](https://www.rainmeter.net/)** (建議下載 Final Release 或 Beta 版)。

### 步驟 2：一鍵安裝 `.rmskin` 套件包
1. 在本倉庫的 [`Release_Artifacts/`](../Release_Artifacts/) 資料夾或此目錄下找到 **`MusicAudioGlow_Suite_v2.0.rmskin`**。
2. 雙擊執行 `MusicAudioGlow_Suite_v2.0.rmskin`。
3. 在彈出的 Rainmeter Skin Installer 視窗中點擊 **「Install」**。
4. 安裝完成後，螢幕四周將立即出現流光特效！

---

## 🎨 5 大燈光風格自由切換

本套件內建 5 種數學雙色漸變與彩虹光譜主題，可隨心所欲自由切換：

| 數值 (`ColorStyle`) | 風格名稱 | 配色特點 | 推薦聆聽曲風 |
| :---: | :---: | :---: | :---: |
| **`1`** (預設) | **全彩光譜閉環 (Rainbow 360°)** | 360° 連續無縫彩虹色盤旋轉 | 流行、K-pop、搖滾 |
| **`2`** | **賽博龐克 (Cyberpunk)** | 霓虹粉紫 + 科技電光青 | 電子舞曲 (EDM)、Synthwave |
| **`3`** | **落日金光 (Sunset Gold)** | 熾熱金橙 + 魅惑洋紅 | 抒情、爵士、R&B |
| **`4`** | **極光綠境 (Aurora)** | 翡翠碧綠 + 冰川冰藍 | 輕音樂、環境氛圍音 (Lo-fi) |
| **`5`** | **熔岩烈焰 (Lava Crimson)** | 烈焰赤紅 + 狂暴金黃 | 重金屬、重低音 Trap/Dubstep |

### 🛠️ 風格切換步驟：
1. 在螢幕右下角系統匣對 Rainmeter 圖示點擊右鍵 ➜ 點選 **「Skins」** ➜ **「MusicAudioGlow」** ➜ **「MusicAudioGlow.ini」** ➜ **「Edit skin」**。
2. 找到檔案開頭的 `[Variables]` 區塊：
   ```ini
   [Variables]
   ScreenWidth=1707
   ScreenHeight=960
   BloomDepth=32

   ; 將數值改為 1 ~ 5 即可
   ColorStyle=2
   ```
3. 存檔後對桌面流光按右鍵點選 **「Refresh skin (重新整理)」**，立刻生效！

---

## ⚙️ 進階核心參數自訂

* **螢幕解析度調整 (`ScreenWidth` / `ScreenHeight`)**：
  預設適配 1080p / 2K 縮放比例，若流光邊界未貼齊螢幕邊緣，請修改為你當前的實際像素寬高（例如 `ScreenWidth=1920`, `ScreenHeight=1080`）。
* **音效靈敏度 (`AudioSensitivity`)**：
  預設值為 `42`。若平時聽歌音量較小，可調高至 `48~55`；若音量極大容易滿格，可下調至 `35~40`。
* **重低音加速響應 (`MeasureBeatEnergy`)**：
  本套件採用 `[MeasureBass] * 1.6 + [MeasureLowMid] * 0.7` 的非線性爆衝演算法，在大鼓與重低音落下時瞬間提供高達 8~10 倍的流光狂飆速度！

---

## 🎵 工作列音樂小工具 (`BMediaTaskbarWidget`)

* 支援 Spotify、Windows 內建 Media Player、網頁播放器等多種音訊源。
* 包含「彩色波紋」、「文字震動」、「彩色邊框」等多種不同視覺風格，可在 Rainmeter 管理視窗中自由載入搭配！

---

## 📜 授權協議

本項目基於 MIT License 開源，歡迎自由使用、修改與二次開發！
