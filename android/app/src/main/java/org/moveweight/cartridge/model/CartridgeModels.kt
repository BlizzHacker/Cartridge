package org.moveweight.cartridge.model

import java.io.File

data class ServerConfig(
    val url: String = "",
    val token: String = "",
    val basicAuth: String = "",
    val username: String = "",
    val authMode: String = "password",
    val allowInsecureCerts: Boolean = false,
    val preferExternalEmulator: Boolean = true
) {
    val isConfigured: Boolean
        get() = url.isNotBlank()
}

data class ProbeResult(
    val ok: Boolean,
    val resolvedUrl: String = "",
    val serverVersion: String = "",
    val message: String = "",
    val logs: List<String> = emptyList()
)

data class PlatformItem(
    val id: Int,
    val slug: String,
    val name: String,
    val romCount: Int = 0
)

data class RomItem(
    val id: Int,
    val platformId: Int,
    val platformSlug: String,
    val platformName: String,
    val name: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val summary: String,
    val coverUrl: String,
    val regions: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val releaseYear: Int? = null,
    val crcHash: String = "",
    val md5Hash: String = "",
    val sha1Hash: String = "",
    val isFavorite: Boolean = false
) {
    val formattedSize: String
        get() = formatBytes(fileSizeBytes)
}

data class LocalRomEntry(
    val romId: Int,
    val title: String,
    val platformSlug: String,
    val file: File
) {
    val sizeBytes: Long get() = if (file.exists()) file.length() else 0L
    val formattedSize: String get() = formatBytes(sizeBytes)
}

data class DownloadState(
    val romId: Int,
    val romTitle: String,
    val bytesRead: Long,
    val totalBytes: Long,
    val status: String,
    val isRunning: Boolean,
    val error: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class EmulatorTarget(
    val id: String,
    val displayName: String,
    val systemLabel: String,
    val packageNames: List<String>,
    val supportedSlugs: Set<String>,
    val recommendedCore: String? = null,
    val websiteUrl: String
)

data class InstalledEmulatorStatus(
    val target: EmulatorTarget,
    val installedPackage: String?,
    val isInstalled: Boolean
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
