package org.moveweight.cartridge.emulators

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.moveweight.cartridge.model.EmulatorTarget
import org.moveweight.cartridge.model.InstalledEmulatorStatus
import java.io.File

object EmulatorLauncher {

    private val RETROARCH_PACKAGES = setOf(
        "com.retroarch.aarch64",
        "com.retroarch.ra32",
        "com.retroarch"
    )

    // Platform slug -> recommended libretro Android core filename
    private val SLUG_TO_CORE = mapOf(
        "n64" to "mupen64plus_next_gles3_libretro_android.so",
        "nintendo-64" to "mupen64plus_next_gles3_libretro_android.so",
        "snes" to "snes9x_libretro_android.so",
        "sfam" to "snes9x_libretro_android.so",
        "super-nintendo" to "snes9x_libretro_android.so",
        "nes" to "fceumm_libretro_android.so",
        "fds" to "fceumm_libretro_android.so",
        "famicom" to "fceumm_libretro_android.so",
        "gba" to "mgba_libretro_android.so",
        "gb" to "mgba_libretro_android.so",
        "gbc" to "mgba_libretro_android.so",
        "genesis" to "genesis_plus_gx_libretro_android.so",
        "megadrive" to "genesis_plus_gx_libretro_android.so",
        "sms" to "genesis_plus_gx_libretro_android.so",
        "gg" to "genesis_plus_gx_libretro_android.so",
        "segacd" to "genesis_plus_gx_libretro_android.so",
        "psx" to "pcsx_rearmed_libretro_android.so",
        "ps" to "pcsx_rearmed_libretro_android.so",
        "ps1" to "pcsx_rearmed_libretro_android.so",
        "playstation" to "pcsx_rearmed_libretro_android.so",
        "arcade" to "fbneo_libretro_android.so",
        "mame" to "fbneo_libretro_android.so",
        "fbneo" to "fbneo_libretro_android.so",
        "neogeo" to "fbneo_libretro_android.so",
        "nds" to "melonds_libretro_android.so",
        "psp" to "ppsspp_libretro_android.so",
        "dc" to "flycast_libretro_android.so",
        "dreamcast" to "flycast_libretro_android.so"
    )

    val CATALOG: List<EmulatorTarget> = listOf(
        EmulatorTarget(
            id = "retroarch",
            displayName = "RetroArch (Multi-System)",
            systemLabel = "N64, SNES, NES, GBA/GB/GBC, Genesis, PS1, Arcade, Neo Geo",
            packageNames = listOf("com.retroarch.aarch64", "com.retroarch.ra32", "com.retroarch"),
            supportedSlugs = setOf(
                "nes", "fds", "famicom", "snes", "sfam", "super-nintendo",
                "gb", "gbc", "gba", "genesis", "megadrive",
                "sms", "gg", "segacd", "32x", "saturn", "psx", "ps", "ps1", "playstation",
                "n64", "nintendo-64", "nds", "arcade", "mame", "fbneo", "neogeo",
                "pce", "tg16", "atari2600", "atari5200", "atari7800", "lynx",
                "jaguar", "ngp", "ngpc", "wonderswan", "wonderswancolor",
                "colecovision", "intellivision", "vectrex", "virtualboy"
            ),
            recommendedCore = "auto",
            websiteUrl = "https://www.retroarch.com/?page=platforms"
        ),
        EmulatorTarget(
            id = "m64plusfz",
            displayName = "M64Plus FZ",
            systemLabel = "Nintendo 64 (N64)",
            packageNames = listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita"),
            supportedSlugs = setOf("n64", "nintendo-64"),
            websiteUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita"
        ),
        EmulatorTarget(
            id = "duckstation",
            displayName = "DuckStation",
            systemLabel = "Sony PlayStation (PS1 / PSX)",
            packageNames = listOf("com.github.stenzek.duckstation"),
            supportedSlugs = setOf("psx", "ps", "ps1", "playstation"),
            websiteUrl = "https://www.duckstation.org/"
        ),
        EmulatorTarget(
            id = "ppsspp",
            displayName = "PPSSPP",
            systemLabel = "Sony PlayStation Portable (PSP)",
            packageNames = listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp"),
            supportedSlugs = setOf("psp", "playstation-portable"),
            websiteUrl = "https://www.ppsspp.org/download/"
        ),
        EmulatorTarget(
            id = "dolphin",
            displayName = "Dolphin Emulator",
            systemLabel = "Nintendo GameCube & Wii",
            packageNames = listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr"),
            supportedSlugs = setOf("gc", "ngc", "gamecube", "wii"),
            websiteUrl = "https://dolphin-emu.org/download/"
        ),
        EmulatorTarget(
            id = "nethersx2",
            displayName = "NetherSX2 / AetherSX2",
            systemLabel = "Sony PlayStation 2 (PS2)",
            packageNames = listOf("xyz.aethersx2.android"),
            supportedSlugs = setOf("ps2", "playstation-2"),
            websiteUrl = "https://github.com/Trixarian/NetherSX2-patch"
        ),
        EmulatorTarget(
            id = "melonds",
            displayName = "melonDS / DraStic",
            systemLabel = "Nintendo DS (NDS)",
            packageNames = listOf("me.magnum.melonds", "com.dsemu.drastic"),
            supportedSlugs = setOf("nds", "nintendo-ds"),
            websiteUrl = "https://github.com/rafaelvcaetano/melonDS-android/releases"
        ),
        EmulatorTarget(
            id = "redream",
            displayName = "Redream / Flycast",
            systemLabel = "Sega Dreamcast",
            packageNames = listOf("io.recompiled.redream", "com.flycast.emulator"),
            supportedSlugs = setOf("dc", "dreamcast"),
            websiteUrl = "https://redream.io/download"
        ),
        EmulatorTarget(
            id = "pizzaboy",
            displayName = "Pizza Boy / Lemuroid",
            systemLabel = "Game Boy, GBC & Game Boy Advance",
            packageNames = listOf("it.dbtecno.pizzaboygbapro", "it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboy", "com.swordfish.lemuroid"),
            supportedSlugs = setOf("gb", "gbc", "gba"),
            websiteUrl = "https://github.com/Swordfish90/Lemuroid"
        )
    )

    fun scanInstalledEmulators(context: Context): List<InstalledEmulatorStatus> {
        val pm = context.packageManager
        return CATALOG.map { target ->
            val foundPkg = target.packageNames.firstOrNull { pkg ->
                isPackageInstalled(pm, pkg)
            }
            InstalledEmulatorStatus(
                target = target,
                installedPackage = foundPkg,
                isInstalled = foundPkg != null
            )
        }
    }

    fun matchingEmulatorsForSlug(context: Context, platformSlug: String): List<InstalledEmulatorStatus> {
        val normalized = platformSlug.trim().lowercase()
        val all = scanInstalledEmulators(context)
        val matching = all.filter { normalized in it.target.supportedSlugs }
        return matching.sortedWith(
            compareByDescending<InstalledEmulatorStatus> { it.isInstalled }
                .thenBy { it.target.id == "retroarch" }
        )
    }

    fun bestInstalledForSlug(context: Context, platformSlug: String): InstalledEmulatorStatus? {
        return matchingEmulatorsForSlug(context, platformSlug).firstOrNull { it.isInstalled }
            ?: scanInstalledEmulators(context).firstOrNull { it.target.id == "retroarch" && it.isInstalled }
    }

    fun coreForPlatform(platformSlug: String, fileExtension: String = ""): String? {
        val slug = platformSlug.trim().lowercase()
        SLUG_TO_CORE[slug]?.let { return it }
        return when (fileExtension.lowercase()) {
            "z64", "n64", "v64" -> "mupen64plus_next_gles3_libretro_android.so"
            "smc", "sfc" -> "snes9x_libretro_android.so"
            "nes" -> "fceumm_libretro_android.so"
            "gba", "gb", "gbc" -> "mgba_libretro_android.so"
            "md", "gen", "bin", "sms", "gg" -> "genesis_plus_gx_libretro_android.so"
            "chd", "pbp", "cue" -> "pcsx_rearmed_libretro_android.so"
            else -> null
        }
    }

    fun launchRomInExternalEmulator(
        context: Context,
        romFile: File,
        packageName: String? = null,
        platformSlug: String = ""
    ): Result<String> {
        return try {
            val resolvedPkg = packageName
                ?: bestInstalledForSlug(context, platformSlug)?.installedPackage

            // Direct RetroArch RetroActivityFuture launch for instant zero-menu gameplay!
            if (resolvedPkg != null && resolvedPkg in RETROARCH_PACKAGES) {
                val coreName = coreForPlatform(platformSlug, romFile.extension)
                if (coreName != null) {
                    val launchableFile = stageReadableRomIfNeeded(romFile, platformSlug)
                    val raIntent = buildRetroArchIntent(context, resolvedPkg, launchableFile, coreName)
                    try {
                        context.startActivity(raIntent)
                        return Result.success("Playing ${romFile.nameWithoutExtension} in RetroArch")
                    } catch (_: Exception) {
                        // Fall through to generic intent if RetroActivityFuture rejects
                    }
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                romFile
            )
            val ext = romFile.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                if (!resolvedPkg.isNullOrBlank()) {
                    setPackage(resolvedPkg)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                context.startActivity(viewIntent)
                Result.success("Launched ${romFile.name}")
            } catch (_: Exception) {
                if (!resolvedPkg.isNullOrBlank()) {
                    val launchAppIntent = context.packageManager.getLaunchIntentForPackage(resolvedPkg)
                    if (launchAppIntent != null) {
                        launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchAppIntent)
                        return Result.success("Opened $resolvedPkg (load ${romFile.name})")
                    }
                }
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "*/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Play ${romFile.name} with..."
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                Result.success("Opened system emulator chooser for ${romFile.name}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun stageReadableRomIfNeeded(romFile: File, platformSlug: String): File {
        return try {
            val sharedDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Cartridge/${platformSlug.ifBlank { "roms" }}"
            )
            if (sharedDir.exists() || sharedDir.mkdirs()) {
                val target = File(sharedDir, romFile.name)
                if (!target.exists() || target.length() != romFile.length()) {
                    romFile.copyTo(target, overwrite = true)
                }
                if (target.exists() && target.length() > 0L) return target
            }
            romFile
        } catch (_: Exception) {
            romFile
        }
    }

    private fun buildRetroArchIntent(
        context: Context,
        raPackage: String,
        romFile: File,
        coreFileName: String
    ): Intent {
        val sdcard = Environment.getExternalStorageDirectory().absolutePath
        val dataDir = "/data/data/$raPackage"
        val externalDir = "$sdcard/Android/data/$raPackage/files"
        val corePath = "$dataDir/cores/$coreFileName"
        val configPath = "$externalDir/retroarch.cfg"
        val apkPath = try {
            context.packageManager.getApplicationInfo(raPackage, 0).sourceDir
        } catch (_: Exception) {
            ""
        }

        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(raPackage, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", romFile.absolutePath)
            putExtra("LIBRETRO", corePath)
            putExtra("CONFIGFILE", configPath)
            putExtra("IME", "com.android.inputmethod.latin/.LatinIME")
            putExtra("DATADIR", dataDir)
            if (apkPath.isNotBlank()) putExtra("APK", apkPath)
            putExtra("SDCARD", sdcard)
            putExtra("DOWNLOADS", "$sdcard/Download")
            putExtra("SCREENSHOTS", "$sdcard/Pictures")
            putExtra("EXTERNAL", externalDir)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    fun openEmulatorOrStore(context: Context, status: InstalledEmulatorStatus) {
        try {
            val pkg = status.installedPackage
            if (pkg != null) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return
                }
            }
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(status.target.websiteUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
