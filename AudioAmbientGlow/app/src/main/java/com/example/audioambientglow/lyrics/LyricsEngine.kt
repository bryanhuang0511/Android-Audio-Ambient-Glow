package com.example.audioambientglow.lyrics

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsState(
    val queryKey: String = "",
    val cleanTitle: String = "",
    val cleanArtist: String = "",
    val lines: List<LyricLine> = emptyList(),
    val isLoading: Boolean = false,
    val isFound: Boolean = false,
    val currentIndex: Int = -1
)

object LyricsEngine {

    private const val TAG = "LyricsEngine"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentFetchJob: Job? = null

    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    private val _lyricsState = MutableStateFlow(LyricsState())
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private val lrcTimePattern = Pattern.compile("""\[(\d{2}):(\d{2})\.?(\d{2,3})?\]""")

    /**
     * Clean messy browser / YouTube / YouTube Music track titles
     */
    fun cleanTrackInfo(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()

        if (title.isEmpty()) return Pair("", "")

        // 1. If title contains " - " or " — " (e.g. "BABYMONSTER - SHEESH", "ILLIT - Magnetic"), split into artist & title
        val dashSeparators = arrayOf(" - ", " — ", " | ", "·", " -")
        for (sep in dashSeparators) {
            if (title.contains(sep)) {
                val parts = title.split(sep, limit = 2)
                if (parts.size == 2) {
                    val p0 = parts[0].trim()
                    val p1 = parts[1].trim()
                    if (artist.isEmpty() || artist.equals("YouTube", ignoreCase = true) || artist.equals("Chrome", ignoreCase = true)) {
                        artist = p0
                        title = p1
                    }
                }
                break
            }
        }

        // 2. Remove MV / Official / Lyric Video tags
        val removeRegexes = arrayOf(
            """(?i)\s*[\(\[（【][^\)\]）】]*(official|mv|m/v|music video|audio|video|lyric|visualizer|teaser|remix|live|hd|4k|performance|choreography|ver\.|color coded)[^\)\]）】]*[\)\]）】]""",
            """['"“”「」『』]""",
            """(?i)\s*-\s*remix$""",
            """(?i)\s*-\s*official$"""
        )

        for (rx in removeRegexes) {
            title = title.replace(Regex(rx), " ").trim()
            artist = artist.replace(Regex(rx), " ").trim()
        }

        title = title.replace(Regex("""\s+"""), " ").trim()
        artist = artist.replace(Regex("""\s+"""), " ").trim()

        if (artist.equals("YouTube", ignoreCase = true) || artist.equals("Google", ignoreCase = true) || artist.equals("Chrome", ignoreCase = true)) {
            artist = ""
        }

        return Pair(title, artist)
    }

    fun fetchLyrics(rawTitle: String, rawArtist: String) {
        val (cleanTitle, cleanArtist) = cleanTrackInfo(rawTitle, rawArtist)
        if (cleanTitle.isEmpty()) {
            _lyricsState.value = LyricsState()
            return
        }

        val queryKey = "_"
        if (queryKey == _lyricsState.value.queryKey && _lyricsState.value.lines.isNotEmpty()) {
            return
        }

        // 🛑 Immediately FLUSH old lyrics so previous song's lines NEVER persist onto new song!
        currentFetchJob?.cancel()

        // Check in-memory cache
        val cached = cache[queryKey]
        if (cached != null) {
            _lyricsState.value = LyricsState(
                queryKey = queryKey,
                cleanTitle = cleanTitle,
                cleanArtist = cleanArtist,
                lines = cached,
                isLoading = false,
                isFound = true
            )
            return
        }

        // Reset state to loading with empty lines immediately
        _lyricsState.value = LyricsState(
            queryKey = queryKey,
            cleanTitle = cleanTitle,
            cleanArtist = cleanArtist,
            lines = emptyList(),
            isLoading = true,
            isFound = false
        )

        currentFetchJob = scope.launch {
            val result = fetchFromLrclib(cleanTitle, cleanArtist, rawTitle)
            withContext(Dispatchers.Main) {
                // If song changed while fetching, ignore stale result
                if (_lyricsState.value.queryKey != queryKey) return@withContext

                if (result.isNotEmpty()) {
                    cache[queryKey] = result
                    _lyricsState.value = LyricsState(
                        queryKey = queryKey,
                        cleanTitle = cleanTitle,
                        cleanArtist = cleanArtist,
                        lines = result,
                        isLoading = false,
                        isFound = true
                    )
                    Log.i(TAG, "Successfully loaded  lyrics lines for '' by ''")
                } else {
                    _lyricsState.value = LyricsState(
                        queryKey = queryKey,
                        cleanTitle = cleanTitle,
                        cleanArtist = cleanArtist,
                        lines = emptyList(),
                        isLoading = false,
                        isFound = false
                    )
                    Log.d(TAG, "No lyrics found for '' ()")
                }
            }
        }
    }

    private fun fetchFromLrclib(cleanTitle: String, cleanArtist: String, rawTitle: String): List<LyricLine> {
        try {
            // Strategy 1: Direct Exact Match if artist is known
            if (cleanArtist.isNotEmpty()) {
                val exactUrl = "https://lrclib.net/api/get?track_name=" + URLEncoder.encode(cleanTitle, "UTF-8") + "&artist_name=" + URLEncoder.encode(cleanArtist, "UTF-8")
                val directJson = httpGet(exactUrl)
                if (directJson != null) {
                    val parsed = parseLrcFromJson(directJson)
                    if (parsed.isNotEmpty()) return parsed
                }
            }

            // Strategy 2: Search with Clean Title + Artist
            val q1 = if (cleanArtist.isNotEmpty()) " " else cleanTitle
            val searchUrl1 = "https://lrclib.net/api/search?q=" + URLEncoder.encode(q1, "UTF-8")
            val searchJson1 = httpGet(searchUrl1)
            if (searchJson1 != null) {
                val parsed = extractBestFromSearchArray(searchJson1)
                if (parsed.isNotEmpty()) return parsed
            }

            // Strategy 3: Search with Artist + Title reversed
            if (cleanArtist.isNotEmpty()) {
                val qReversed = " "
                val searchUrlRev = "https://lrclib.net/api/search?q=" + URLEncoder.encode(qReversed, "UTF-8")
                val searchJsonRev = httpGet(searchUrlRev)
                if (searchJsonRev != null) {
                    val parsed = extractBestFromSearchArray(searchJsonRev)
                    if (parsed.isNotEmpty()) return parsed
                }
            }

            // Strategy 4: Search with Clean Title alone
            val searchUrl2 = "https://lrclib.net/api/search?q=" + URLEncoder.encode(cleanTitle, "UTF-8")
            val searchJson2 = httpGet(searchUrl2)
            if (searchJson2 != null) {
                val parsed = extractBestFromSearchArray(searchJson2)
                if (parsed.isNotEmpty()) return parsed
            }

            // Strategy 5: Raw Title Clean Search
            val rawClean = rawTitle.replace(Regex("""['"“”「」『』]"""), " ").replace(Regex("""\s+"""), " ").trim()
            val searchUrl3 = "https://lrclib.net/api/search?q=" + URLEncoder.encode(rawClean, "UTF-8")
            val searchJson3 = httpGet(searchUrl3)
            if (searchJson3 != null) {
                val parsed = extractBestFromSearchArray(searchJson3)
                if (parsed.isNotEmpty()) return parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching lyrics from LRCLIB: ")
        }
        return emptyList()
    }

    private fun extractBestFromSearchArray(jsonStr: String): List<LyricLine> {
        try {
            val array = JSONArray(jsonStr)
            if (array.length() == 0) return emptyList()

            // 1. Prioritize results with syncedLyrics
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val synced = item.optString("syncedLyrics", "")
                if (synced.isNotEmpty()) {
                    val parsed = parseLrcString(synced)
                    if (parsed.isNotEmpty()) return parsed
                }
            }

            // 2. Fallback to first plainLyrics
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val plain = item.optString("plainLyrics", "")
                if (plain.isNotEmpty()) {
                    val parsed = parsePlainString(plain)
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing search array: ")
        }
        return emptyList()
    }

    private fun parseLrcFromJson(jsonStr: String): List<LyricLine> {
        try {
            val json = JSONObject(jsonStr)
            val syncedLyrics = json.optString("syncedLyrics", "")
            if (syncedLyrics.isNotEmpty()) {
                return parseLrcString(syncedLyrics)
            }
            val plainLyrics = json.optString("plainLyrics", "")
            if (plainLyrics.isNotEmpty()) {
                return parsePlainString(plainLyrics)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing JSON lyrics: ")
        }
        return emptyList()
    }

    fun parseLrcString(lrcText: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val lines = lrcText.split("\n")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matcher = lrcTimePattern.matcher(trimmed)
            val times = mutableListOf<Long>()
            var lastEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = msStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val totalMs = (min * 60 + sec) * 1000 + ms
                times.add(totalMs)
                lastEnd = matcher.end()
            }

            if (times.isNotEmpty()) {
                val content = trimmed.substring(lastEnd).trim()
                if (content.isNotEmpty() && !content.startsWith("by:") && !content.startsWith("ar:") && !content.startsWith("ti:")) {
                    for (t in times) {
                        result.add(LyricLine(t, content))
                    }
                }
            }
        }

        result.sortBy { it.timeMs }

        // Deduplicate consecutive identical lines
        val deduplicated = mutableListOf<LyricLine>()
        var lastText = ""
        for (item in result) {
            if (item.text != lastText) {
                deduplicated.add(item)
                lastText = item.text
            }
        }
        return deduplicated
    }

    private fun parsePlainString(plainText: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        val lines = plainText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { idx, line ->
            list.add(LyricLine(idx * 4000L, line))
        }
        return list
    }

    fun getActiveLines(positionMs: Long): Triple<String, String, String> {
        val lines = _lyricsState.value.lines
        if (lines.isEmpty()) {
            return Triple("", "", "")
        }

        var activeIndex = -1
        for (i in lines.indices) {
            if (positionMs >= lines[i].timeMs) {
                activeIndex = i
            } else {
                break
            }
        }

        if (activeIndex == -1) {
            return Triple("", lines.firstOrNull()?.text ?: "", lines.getOrNull(1)?.text ?: "")
        }

        val prev = if (activeIndex > 0) lines[activeIndex - 1].text else ""
        val current = lines[activeIndex].text
        val next = if (activeIndex < lines.size - 1) lines[activeIndex + 1].text else ""

        return Triple(prev, current, next)
    }

    private fun httpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}