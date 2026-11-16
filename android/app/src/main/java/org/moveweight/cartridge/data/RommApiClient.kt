package org.moveweight.cartridge.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.moveweight.cartridge.model.LocalRomEntry
import org.moveweight.cartridge.model.PlatformItem
import org.moveweight.cartridge.model.ProbeResult
import org.moveweight.cartridge.model.RomItem
import org.moveweight.cartridge.model.ServerConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RommApiClient {

    private val coverCache = object : LruCache<String, Bitmap>(48) {}

    fun loadSavedConfig(context: Context): ServerConfig {
        val prefs = context.getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
        return ServerConfig(
            url = prefs.getString("server_url", "")?.trim()?.trimEnd('/') ?: "",
            token = prefs.getString("auth_token", "") ?: "",
            basicAuth = prefs.getString("basic_auth", "") ?: "",
            username = prefs.getString("auth_username", "") ?: "",
            authMode = prefs.getString("auth_mode", "password") ?: "password",
            allowInsecureCerts = prefs.getBoolean("insecure_certs", false),
            preferExternalEmulator = prefs.getBoolean("prefer_external_emu", true)
        )
    }

    fun saveConfig(context: Context, config: ServerConfig) {
        val prefs = context.getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("server_url", config.url.trim().trimEnd('/'))
            .putString("auth_token", config.token)
            .putString("basic_auth", config.basicAuth)
            .putString("auth_username", config.username)
            .putString("auth_mode", config.authMode)
            .putBoolean("insecure_certs", config.allowInsecureCerts)
            .putBoolean("prefer_external_emu", config.preferExternalEmulator)
            .apply()
    }

    suspend fun probeServer(rawInput: String, allowInsecure: Boolean): ProbeResult = withContext(Dispatchers.IO) {
        val cleaned = rawInput.trim().trimEnd('/')
        if (cleaned.isBlank()) {
            return@withContext ProbeResult(
                ok = false,
                message = "Enter your RomM or ROMarr server address (for example http://192.168.1.100:6868)."
            )
        }

        val logs = mutableListOf<String>()
        val baseCandidates = mutableListOf<String>()
        if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            baseCandidates.add(cleaned)
            if (!cleaned.endsWith("/romm")) {
                baseCandidates.add("$cleaned/romm")
            }
        } else {
            baseCandidates.add("http://$cleaned")
            baseCandidates.add("https://$cleaned")
            baseCandidates.add("http://$cleaned/romm")
            baseCandidates.add("https://$cleaned/romm")
        }

        for (candidate in baseCandidates) {
            for (probePath in listOf("/api/heartbeat", "/api/v1/status", "/api/platforms")) {
                val targetUrl = "$candidate$probePath"
                try {
                    logs.add("Probing $targetUrl ...")
                    val (code, body) = requestText(
                        urlString = targetUrl,
                        method = "GET",
                        allowInsecure = allowInsecure,
                        timeoutMs = 6000
                    )
                    if (code in 200..299 || code == 401 || code == 403) {
                        val version = parseVersionFromHeartbeat(body)
                        val msg = if (version.isNotBlank()) {
                            "Connected to $candidate ($version)"
                        } else if (code == 401 || code == 403) {
                            "Reached $candidate (authentication required)"
                        } else {
                            "Connected to $candidate"
                        }
                        logs.add("OK ($code): $msg")
                        return@withContext ProbeResult(
                            ok = true,
                            resolvedUrl = candidate,
                            serverVersion = version.ifBlank { "RomM / ROMarr" },
                            message = msg,
                            logs = logs
                        )
                    } else {
                        logs.add("HTTP $code from $targetUrl")
                    }
                } catch (e: Exception) {
                    logs.add("Failed $targetUrl: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }

        ProbeResult(
            ok = false,
            message = "Could not reach server at $cleaned. Check that your phone is on the same Wi-Fi/LAN or VPN and the port is correct.",
            logs = logs
        )
    }

    suspend fun signInWithPassword(
        config: ServerConfig,
        username: String,
        password: String
    ): Result<ServerConfig> = withContext(Dispatchers.IO) {
        val baseUrl = config.url.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        val basicRaw = "$username:$password"
        val basicEncoded = Base64.encodeToString(basicRaw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // 1. Try RomM OAuth2 /api/token
        try {
            val scope = "me.read roms.read platforms.read assets.read assets.write"
            val form = "grant_type=password" +
                "&username=" + URLEncoder.encode(username, "UTF-8") +
                "&password=" + URLEncoder.encode(password, "UTF-8") +
                "&scope=" + URLEncoder.encode(scope, "UTF-8")
            val (code, body) = requestText(
                urlString = "$baseUrl/api/token",
                method = "POST",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = form,
                allowInsecure = config.allowInsecureCerts
            )
            if (code in 200..299 && body.isNotBlank()) {
                val json = JSONObject(body)
                val accessToken = json.optString("access_token", "")
                if (accessToken.isNotBlank()) {
                    return@withContext Result.success(
                        config.copy(
                            token = accessToken,
                            basicAuth = basicEncoded,
                            username = username,
                            authMode = "password"
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 2. Try Basic Auth verification against /api/platforms or /api/users/me
        try {
            val (code, _) = requestText(
                urlString = "$baseUrl/api/platforms",
                method = "GET",
                headers = mapOf("Authorization" to "Basic $basicEncoded"),
                allowInsecure = config.allowInsecureCerts
            )
            if (code in 200..299) {
                return@withContext Result.success(
                    config.copy(
                        token = "",
                        basicAuth = basicEncoded,
                        username = username,
                        authMode = "password"
                    )
                )
            }
            return@withContext Result.failure(IllegalStateException("Sign-in rejected by server (HTTP $code)"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun pairWithCode(
        config: ServerConfig,
        rawCode: String
    ): Result<ServerConfig> = withContext(Dispatchers.IO) {
        val baseUrl = config.url.trim().trimEnd('/')
        val cleanCode = rawCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter the pairing code shown in RomM / ROMarr"))
        }

        try {
            val payload = JSONObject().put("code", cleanCode).toString()
            val (code, body) = requestText(
                urlString = "$baseUrl/api/client-tokens/exchange",
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = payload,
                allowInsecure = config.allowInsecureCerts
            )
            if (code in 200..299 && body.isNotBlank()) {
                val json = JSONObject(body)
                val token = json.optString("raw_token", "")
                    .ifBlank { json.optString("token", "") }
                    .ifBlank { json.optString("access_token", "") }
                if (token.isNotBlank()) {
                    return@withContext Result.success(
                        config.copy(
                            token = token,
                            basicAuth = "",
                            authMode = "pair"
                        )
                    )
                }
            }
            Result.failure(IllegalStateException("Pairing failed (HTTP $code). Verify the code in Settings -> Client API Tokens."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPlatforms(config: ServerConfig): Result<List<PlatformItem>> = withContext(Dispatchers.IO) {
        val baseUrl = config.url.trim().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext Result.success(emptyList())

        try {
            val (code, body) = requestText(
                urlString = "$baseUrl/api/platforms",
                method = "GET",
                headers = authHeaders(config),
                allowInsecure = config.allowInsecureCerts
            )
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code loading platforms"))
            }
            val arr = parseJsonArray(body)
            val list = mutableListOf<PlatformItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optInt("id", i + 1)
                val slug = obj.optString("slug", "").ifBlank { obj.optString("fs_slug", "unknown") }
                val name = obj.optString("display_name", "")
                    .ifBlank { obj.optString("name", slug.uppercase()) }
                val romCount = obj.optInt("rom_count", 0)
                list.add(PlatformItem(id = id, slug = slug, name = name, romCount = romCount))
            }
            Result.success(list.sortedBy { it.name.lowercase() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRoms(
        config: ServerConfig,
        platformId: Int? = null,
        searchQuery: String = ""
    ): Result<List<RomItem>> = withContext(Dispatchers.IO) {
        val baseUrl = config.url.trim().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext Result.success(emptyList())

        try {
            val params = mutableListOf("limit=250", "offset=0")
            if (platformId != null && platformId > 0) {
                params.add("platform_id=$platformId")
                params.add("platform_ids=$platformId")
            }
            if (searchQuery.isNotBlank()) {
                params.add("search_term=" + URLEncoder.encode(searchQuery.trim(), "UTF-8"))
            }
            val queryString = params.joinToString("&")
            val (code, body) = requestText(
                urlString = "$baseUrl/api/roms?$queryString",
                method = "GET",
                headers = authHeaders(config),
                allowInsecure = config.allowInsecureCerts,
                timeoutMs = 12000
            )
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code loading library"))
            }
            val arr = parseJsonArray(body)
            val roms = mutableListOf<RomItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                roms.add(parseRomObject(baseUrl, obj, i))
            }
            Result.success(roms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadCoverBitmap(config: ServerConfig, coverUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        if (coverUrl.isBlank()) return@withContext null
        coverCache.get(coverUrl)?.let { return@withContext it }

        try {
            val conn = openConnection(coverUrl, "GET", authHeaders(config), config.allowInsecureCerts, 8000)
            try {
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bmp != null) {
                        coverCache.put(coverUrl, bmp)
                    }
                    return@withContext bmp
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
        }
        null
    }

    suspend fun downloadRom(
        context: Context,
        config: ServerConfig,
        rom: RomItem,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val baseUrl = config.url.trim().trimEnd('/')
        val safeFileName = rom.fileName.ifBlank { "${rom.name.replace(Regex("[^a-zA-Z0-9._ -]"), "_")}.rom" }
        val encodedFileName = Uri.encode(safeFileName)
        val downloadUrl = "$baseUrl/api/roms/${rom.id}/content/$encodedFileName"

        val romsRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "roms/${rom.platformSlug.ifBlank { "default" }}")
        romsRoot.mkdirs()
        val destFile = File(romsRoot, safeFileName)
        val tempFile = File(romsRoot, "$safeFileName.part")

        try {
            val conn = openConnection(downloadUrl, "GET", authHeaders(config), config.allowInsecureCerts, 30000)
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    return@withContext Result.failure(IllegalStateException("Download failed with HTTP $code"))
                }
                val total = conn.contentLengthLong.takeIf { it > 0L } ?: rom.fileSizeBytes
                var bytesRead = 0L
                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            onProgress(bytesRead, total)
                        }
                        output.flush()
                    }
                }
                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)
                Result.success(destFile)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    fun getLocalRomFile(context: Context, rom: RomItem): File? {
        val safeFileName = rom.fileName.ifBlank { return null }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "roms/${rom.platformSlug.ifBlank { "default" }}")
        val f = File(dir, safeFileName)
        return if (f.exists() && f.length() > 0L) f else null
    }

    fun listLocalRoms(context: Context): List<LocalRomEntry> {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "roms")
        if (!root.exists()) return emptyList()
        val entries = mutableListOf<LocalRomEntry>()
        root.listFiles()?.forEach { platDir ->
            if (platDir.isDirectory) {
                platDir.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.endsWith(".part")) {
                        entries.add(
                            LocalRomEntry(
                                romId = file.absolutePath.hashCode(),
                                title = file.nameWithoutExtension,
                                platformSlug = platDir.name,
                                file = file
                            )
                        )
                    }
                }
            }
        }
        return entries.sortedBy { it.title.lowercase() }
    }

    private fun parseRomObject(baseUrl: String, obj: JSONObject, index: Int): RomItem {
        val id = obj.optInt("id", index + 1)
        val platformId = obj.optInt("platform_id", 0)
        val platformSlug = obj.optString("platform_slug", "")
            .ifBlank { obj.optString("platform_fs_slug", "rom") }
        val platformName = obj.optString("platform_display_name", "")
            .ifBlank { obj.optString("platform_name", platformSlug.uppercase()) }
        val name = obj.optString("name", "")
            .ifBlank { obj.optString("fs_name_no_ext", "Game #$id") }
        val fileName = obj.optString("fs_name", "")
            .ifBlank { obj.optString("file_name", "$name.rom") }
        val fileSizeBytes = obj.optLong("fs_size_bytes", obj.optLong("file_size_bytes", 0L))
        val summary = obj.optString("summary", "")

        val rawCover = obj.optString("path_cover_large", "")
            .ifBlank { obj.optString("path_cover_small", "") }
            .ifBlank { obj.optString("url_cover", "") }
        val coverUrl = when {
            rawCover.startsWith("http://") || rawCover.startsWith("https://") -> rawCover
            rawCover.startsWith("/") -> "$baseUrl$rawCover"
            rawCover.isNotBlank() -> "$baseUrl/$rawCover"
            else -> ""
        }

        val regions = mutableListOf<String>()
        obj.optJSONArray("regions")?.let { arr ->
            for (j in 0 until arr.length()) {
                val r = arr.optString(j, "").trim()
                if (r.isNotBlank()) regions.add(r)
            }
        }

        val genres = mutableListOf<String>()
        obj.optJSONArray("genres")?.let { arr ->
            for (j in 0 until arr.length()) {
                val g = arr.optString(j, "").trim()
                if (g.isNotBlank()) genres.add(g)
            }
        }

        val crc = obj.optString("crc_hash", "")
        val md5 = obj.optString("md5_hash", "")
        val sha1 = obj.optString("sha1_hash", "")

        return RomItem(
            id = id,
            platformId = platformId,
            platformSlug = platformSlug,
            platformName = platformName,
            name = name,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            summary = summary,
            coverUrl = coverUrl,
            regions = regions,
            genres = genres,
            crcHash = crc,
            md5Hash = md5,
            sha1Hash = sha1
        )
    }

    private fun parseJsonArray(body: String): JSONArray {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed)
        }
        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            obj.optJSONArray("items")?.let { return it }
            obj.optJSONArray("results")?.let { return it }
            obj.optJSONArray("roms")?.let { return it }
            obj.optJSONArray("platforms")?.let { return it }
        }
        return JSONArray()
    }

    private fun parseVersionFromHeartbeat(body: String): String {
        if (body.isBlank()) return ""
        return try {
            val obj = JSONObject(body)
            val sys = obj.optJSONObject("SYSTEM")
            if (sys != null) {
                "RomM ${sys.optString("VERSION", "")}".trim()
            } else {
                val ver = obj.optString("version", "")
                if (ver.isNotBlank()) "ROMarr $ver" else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun authHeaders(config: ServerConfig): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (config.token.isNotBlank()) {
            map["Authorization"] = "Bearer ${config.token}"
        } else if (config.basicAuth.isNotBlank()) {
            map["Authorization"] = "Basic ${config.basicAuth}"
        }
        return map
    }

    private fun requestText(
        urlString: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        allowInsecure: Boolean = false,
        timeoutMs: Int = 10000
    ): Pair<Int, String> {
        val conn = openConnection(urlString, method, headers, allowInsecure, timeoutMs)
        return try {
            if (body != null) {
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(
        urlString: String,
        method: String,
        headers: Map<String, String>,
        allowInsecure: Boolean,
        timeoutMs: Int
    ): HttpURLConnection {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("User-Agent", "Cartridge-Android/0.32.0")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        if (conn is HttpsURLConnection && allowInsecure) {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, SecureRandom())
            conn.sslSocketFactory = sslContext.socketFactory
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        return conn
    }
}
