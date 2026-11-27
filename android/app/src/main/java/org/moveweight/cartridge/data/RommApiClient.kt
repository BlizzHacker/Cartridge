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
import org.moveweight.cartridge.model.CollectionItem
import org.moveweight.cartridge.model.LocalRomEntry
import org.moveweight.cartridge.model.PlatformItem
import org.moveweight.cartridge.model.ProbeResult
import org.moveweight.cartridge.model.RomItem
import org.moveweight.cartridge.model.ServerConfig
import org.moveweight.cartridge.model.ServerProfile
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

    private val coverCache = object : LruCache<String, Bitmap>(72) {}

    fun loadSavedConfig(context: Context): ServerConfig {
        val prefs = context.getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("server_url", "")?.trim()?.trimEnd('/') ?: ""
        val localUrl = prefs.getString("local_url", "")?.trim()?.trimEnd('/') ?: ""
        val remoteUrl = prefs.getString("remote_url", "")?.trim()?.trimEnd('/') ?: ""
        return ServerConfig(
            url = url.ifBlank { localUrl }.ifBlank { remoteUrl },
            localUrl = localUrl,
            remoteUrl = remoteUrl,
            activeServerName = prefs.getString("server_name", "Primary Library") ?: "Primary Library",
            isUsingLocalLan = isLanUrl(url.ifBlank { localUrl }),
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
        val activeUrl = config.effectiveUrl
        prefs.edit()
            .putString("server_url", activeUrl)
            .putString("local_url", config.localUrl.trim().trimEnd('/'))
            .putString("remote_url", config.remoteUrl.trim().trimEnd('/'))
            .putString("server_name", config.activeServerName.ifBlank { "Primary Library" })
            .putString("auth_token", config.token)
            .putString("basic_auth", config.basicAuth)
            .putString("auth_username", config.username)
            .putString("auth_mode", config.authMode)
            .putBoolean("insecure_certs", config.allowInsecureCerts)
            .putBoolean("prefer_external_emu", config.preferExternalEmulator)
            .apply()
    }

    fun loadServerProfiles(context: Context): List<ServerProfile> {
        val prefs = context.getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("server_profiles_json", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ServerProfile>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    ServerProfile(
                        id = o.optString("id", "srv_$i"),
                        name = o.optString("name", "Library ${i + 1}"),
                        localUrl = o.optString("localUrl", ""),
                        remoteUrl = o.optString("remoteUrl", ""),
                        token = o.optString("token", ""),
                        basicAuth = o.optString("basicAuth", ""),
                        username = o.optString("username", ""),
                        authMode = o.optString("authMode", "password"),
                        allowInsecureCerts = o.optBoolean("allowInsecureCerts", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveServerProfiles(context: Context, profiles: List<ServerProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("localUrl", p.localUrl)
                    .put("remoteUrl", p.remoteUrl)
                    .put("token", p.token)
                    .put("basicAuth", p.basicAuth)
                    .put("username", p.username)
                    .put("authMode", p.authMode)
                    .put("allowInsecureCerts", p.allowInsecureCerts)
            )
        }
        val prefs = context.getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("server_profiles_json", arr.toString()).apply()
    }

    fun upsertProfileFromConfig(context: Context, config: ServerConfig): List<ServerProfile> {
        val current = loadServerProfiles(context).toMutableList()
        val id = config.activeServerName.lowercase().replace(Regex("[^a-z0-9]+"), "_").ifBlank { "primary" }
        val profile = ServerProfile(
            id = id,
            name = config.activeServerName.ifBlank { "Primary Library" },
            localUrl = config.localUrl.ifBlank { if (isLanUrl(config.url)) config.url else "" },
            remoteUrl = config.remoteUrl.ifBlank { if (!isLanUrl(config.url)) config.url else "" },
            token = config.token,
            basicAuth = config.basicAuth,
            username = config.username,
            authMode = config.authMode,
            allowInsecureCerts = config.allowInsecureCerts
        )
        val idx = current.indexOfFirst { it.id == id || (it.localUrl.isNotBlank() && it.localUrl == profile.localUrl) }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        saveServerProfiles(context, current)
        return current
    }

    fun isLanUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("://192.168.") ||
            lower.contains("://10.") ||
            lower.contains("://127.") ||
            Regex("://172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(lower) ||
            lower.contains(".local")
    }

    /**
     * Automatically prefers direct local LAN IP when on home Wi-Fi (bypassing Cloudflare),
     * and falls back seamlessly to the remote domain when away from home.
     */
    suspend fun resolveBestConfig(config: ServerConfig): ServerConfig = withContext(Dispatchers.IO) {
        val local = config.localUrl.trim().trimEnd('/')
        val remote = config.remoteUrl.trim().trimEnd('/')
        if (local.isNotBlank()) {
            if (quickReachable(local, config.allowInsecureCerts, timeoutMs = 1500)) {
                return@withContext config.copy(url = local, isUsingLocalLan = true)
            }
        }
        if (remote.isNotBlank()) {
            if (quickReachable(remote, config.allowInsecureCerts, timeoutMs = 3500)) {
                return@withContext config.copy(url = remote, isUsingLocalLan = isLanUrl(remote))
            }
        }
        val fallback = config.effectiveUrl
        config.copy(url = fallback, isUsingLocalLan = isLanUrl(fallback))
    }

    private fun quickReachable(baseUrl: String, allowInsecure: Boolean, timeoutMs: Int): Boolean {
        for (path in listOf("/api/heartbeat", "/api/v1/status", "/api/platforms")) {
            try {
                val (code, _) = requestText(
                    urlString = "$baseUrl$path",
                    method = "GET",
                    allowInsecure = allowInsecure,
                    timeoutMs = timeoutMs
                )
                if (code in 200..299 || code == 401 || code == 403) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    suspend fun probeServer(rawInput: String, allowInsecure: Boolean): ProbeResult = withContext(Dispatchers.IO) {
        val cleaned = rawInput.trim().trimEnd('/')
        if (cleaned.isBlank()) {
            return@withContext ProbeResult(
                ok = false,
                message = "Enter your RomM or ROMarr server address (for example http://192.168.0.94:8080)."
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
            val lanFirst = isLanUrl("http://$cleaned")
            if (lanFirst) {
                baseCandidates.add("http://$cleaned")
                baseCandidates.add("https://$cleaned")
            } else {
                baseCandidates.add("https://$cleaned")
                baseCandidates.add("http://$cleaned")
            }
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
                        timeoutMs = 5000
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
            message = "Could not reach server at $cleaned. Check Wi-Fi/LAN or port.",
            logs = logs
        )
    }

    suspend fun signInWithPassword(
        config: ServerConfig,
        username: String,
        password: String
    ): Result<ServerConfig> = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveUrl
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        val basicRaw = "$username:$password"
        val basicEncoded = Base64.encodeToString(basicRaw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        try {
            val scope = "me.read roms.read platforms.read assets.read assets.write collections.read"
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
        val baseUrl = config.effectiveUrl
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
        val baseUrl = config.effectiveUrl
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
                if (romCount > 0 || list.size < 40) {
                    list.add(PlatformItem(id = id, slug = slug, name = name, romCount = romCount))
                }
            }
            val prioritySlugs = listOf(
                "n64", "snes", "nes", "gba", "psx", "genesis", "arcade", "nds",
                "psp", "gb", "gbc", "gc", "dc", "ps2", "saturn", "sms"
            )
            Result.success(
                list.sortedWith(
                    compareBy<PlatformItem> {
                        val idx = prioritySlugs.indexOf(it.slug.lowercase())
                        if (idx >= 0) idx else prioritySlugs.size + 1
                    }.thenByDescending { it.romCount }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCollections(config: ServerConfig): Result<List<CollectionItem>> = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveUrl
        if (baseUrl.isBlank()) return@withContext Result.success(emptyList())
        try {
            val (code, body) = requestText(
                urlString = "$baseUrl/api/collections",
                method = "GET",
                headers = authHeaders(config),
                allowInsecure = config.allowInsecureCerts,
                timeoutMs = 8000
            )
            if (code !in 200..299) return@withContext Result.success(emptyList())
            val arr = parseJsonArray(body)
            val out = mutableListOf<CollectionItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out.add(
                    CollectionItem(
                        id = obj.optInt("id", i + 1),
                        name = obj.optString("name", "Collection"),
                        romCount = obj.optInt("rom_count", 0),
                        isPublic = obj.optBoolean("is_public", false),
                        isVirtual = obj.optBoolean("is_virtual", false)
                    )
                )
            }
            Result.success(out)
        } catch (_: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun fetchRoms(
        config: ServerConfig,
        platformId: Int? = null,
        searchQuery: String = "",
        collectionId: Int? = null
    ): Result<List<RomItem>> = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveUrl
        if (baseUrl.isBlank()) return@withContext Result.success(emptyList())

        try {
            val params = mutableListOf("limit=250", "offset=0")
            if (platformId != null && platformId > 0) {
                params.add("platform_id=$platformId")
                params.add("platform_ids=$platformId")
            }
            if (collectionId != null && collectionId > 0) {
                params.add("collection_id=$collectionId")
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
                val item = parseRomObject(baseUrl, obj, i)
                val lowerFile = item.fileName.lowercase()
                if (lowerFile.endsWith(".txt") || lowerFile.endsWith(".nfo") || lowerFile.startsWith("_info")) {
                    continue
                }
                roms.add(item)
            }
            Result.success(roms.sortedByDescending { it.coverUrl.isNotBlank() })
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

    private fun romsBaseDir(context: Context): File {
        val external = context.getExternalFilesDir(null)
        return File(external ?: context.filesDir, "roms")
    }

    suspend fun downloadRom(
        context: Context,
        config: ServerConfig,
        rom: RomItem,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveUrl
        val safeFileName = rom.fileName.ifBlank { "${rom.name.replace(Regex("[^a-zA-Z0-9._ -]"), "_")}.rom" }
        val encodedFileName = Uri.encode(safeFileName)
        val downloadUrl = "$baseUrl/api/roms/${rom.id}/content/$encodedFileName"

        val romsRoot = File(romsBaseDir(context), rom.platformSlug.ifBlank { "default" })
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
        val dir = File(romsBaseDir(context), rom.platformSlug.ifBlank { "default" })
        val f = File(dir, safeFileName)
        return if (f.exists() && f.length() > 0L) f else null
    }

    fun listLocalRoms(context: Context): List<LocalRomEntry> {
        val root = romsBaseDir(context)
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

    /**
     * Pulls the latest cloud save for a ROM from RomM (/api/saves?rom_id=...)
     * directly into RetroArch's saves folder so 2-4P couch sessions resume seamlessly.
     */
    suspend fun pullCloudSaveForRom(
        config: ServerConfig,
        rom: RomItem
    ): Result<File?> = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveUrl
        if (baseUrl.isBlank()) return@withContext Result.success(null)
        try {
            val (code, body) = requestText(
                urlString = "$baseUrl/api/saves?rom_id=${rom.id}",
                method = "GET",
                headers = authHeaders(config),
                allowInsecure = config.allowInsecureCerts,
                timeoutMs = 8000
            )
            if (code !in 200..299 || body.isBlank()) return@withContext Result.success(null)
            val arr = parseJsonArray(body)
            if (arr.length() == 0) return@withContext Result.success(null)
            val latest = arr.optJSONObject(0) ?: return@withContext Result.success(null)
            val downloadPath = latest.optString("download_path", "")
            val fileName = latest.optString("file_name", "").ifBlank { "${rom.fileName.substringBeforeLast('.')}.srm" }
            if (downloadPath.isBlank()) return@withContext Result.success(null)

            val fullUrl = if (downloadPath.startsWith("http")) downloadPath else "$baseUrl$downloadPath"
            val savesDir = File(android.os.Environment.getExternalStorageDirectory(), "RetroArch/saves")
            savesDir.mkdirs()
            val outFile = File(savesDir, fileName)

            val conn = openConnection(fullUrl, "GET", authHeaders(config), config.allowInsecureCerts, 15000)
            try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    }
                    return@withContext Result.success(outFile)
                }
            } finally {
                conn.disconnect()
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads a local RetroArch save file (.srm / .sav / .sra / .eep / .mpk) back to RomM
     * via POST /api/saves?rom_id=<id>&emulator=retroarch so progress is preserved across devices.
     */
    suspend fun pushCloudSaveForRom(
        config: ServerConfig,
        rom: RomItem
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = config.effectiveUrl
        if (baseUrl.isBlank()) return@withContext Result.failure(IllegalStateException("No server configured"))
        try {
            val savesDir = File(android.os.Environment.getExternalStorageDirectory(), "RetroArch/saves")
            val baseName = rom.fileName.substringBeforeLast('.')
            val saveFile = listOf("srm", "sav", "sra", "eep", "mpk")
                .map { File(savesDir, "$baseName.$it") }
                .firstOrNull { it.exists() && it.length() > 0L }
                ?: return@withContext Result.success("No local save found yet for ${rom.name}")

            val boundary = "----CartridgeSaveBoundary${System.currentTimeMillis()}"
            val url = URL("$baseUrl/api/saves?rom_id=${rom.id}&emulator=retroarch")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                authHeaders(config).forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                conn.outputStream.use { out ->
                    val header = "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"saveFile\"; filename=\"${saveFile.name}\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    saveFile.inputStream().use { it.copyTo(out) }
                    out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    Result.success("Synced ${saveFile.name} to RomM!")
                } else {
                    Result.failure(IllegalStateException("RomM save upload HTTP $code"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        val summary = if (obj.isNull("summary")) "" else obj.optString("summary", "").takeUnless { it == "null" } ?: ""

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
            obj.optJSONArray("collections")?.let { return it }
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
            setRequestProperty("User-Agent", "Cartridge-Android/0.33.0")
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
