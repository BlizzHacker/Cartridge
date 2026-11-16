package org.moveweight.cartridge.emulators

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.moveweight.cartridge.model.EmulatorTarget
import org.moveweight.cartridge.model.InstalledEmulatorStatus
import java.io.File

object EmulatorLauncher {

    val CATALOG: List<EmulatorTarget> = listOf(
        EmulatorTarget(
            id = "retroarch",
            displayName = "RetroArch (Multi-System)",
            systemLabel = "NES, SNES, GB/GBC/GBA, Genesis, PS1, N64, Arcade, Neo Geo, Saturn",
            packageNames = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32"),
            supportedSlugs = setOf(
                "nes", "fds", "snes", "sfam", "gb", "gbc", "gba", "genesis", "megadrive",
                "sms", "gg", "segacd", "32x", "saturn", "psx", "ps", "ps1", "n64",
                "nds", "arcade", "mame", "fbneo", "neogeo", "pce", "tg16", "atari2600",
                "atari5200", "atari7800", "lynx", "jaguar", "ngp", "ngpc", "wonderswan",
                "wonderswancolor", "colecovision", "intellivision", "vectrex", "virtualboy"
            ),
            recommendedCore = "auto",
            websiteUrl = "https://www.retroarch.com/?page=platforms"
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
            id = "nethersx2",
            displayName = "NetherSX2 / AetherSX2",
            systemLabel = "Sony PlayStation 2 (PS2)",
            packageNames = listOf("xyz.aethersx2.android"),
            supportedSlugs = setOf("ps2", "playstation-2"),
            websiteUrl = "https://github.com/Trixarian/NetherSX2-patch"
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
            id = "azahar",
            displayName = "Azahar / Lime3DS / Citra",
            systemLabel = "Nintendo 3DS",
            packageNames = listOf("org.azahar_emu.azahar", "io.github.lime3ds.android", "org.citra.citra_emu"),
            supportedSlugs = setOf("3ds", "nintendo-3ds", "new-nintendo-3ds"),
            websiteUrl = "https://github.com/azahar-emu/azahar/releases"
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
            id = "m64plusfz",
            displayName = "M64Plus FZ",
            systemLabel = "Nintendo 64 (N64)",
            packageNames = listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita"),
            supportedSlugs = setOf("n64", "nintendo-64"),
            websiteUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita"
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
            id = "yabasanshiro",
            displayName = "Yaba Sanshiro 2",
            systemLabel = "Sega Saturn",
            packageNames = listOf("org.devmiyax.yabasanshioro2.pro", "org.devmiyax.yabasanshioro2"),
            supportedSlugs = setOf("saturn", "sega-saturn"),
            websiteUrl = "https://www.uoyabause.org/"
        ),
        EmulatorTarget(
            id = "pizzaboy",
            displayName = "Pizza Boy / Lemuroid",
            systemLabel = "Game Boy, GBC & Game Boy Advance",
            packageNames = listOf("it.dbtecno.pizzaboygbapro", "it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboy", "com.swordfish.lemuroid"),
            supportedSlugs = setOf("gb", "gbc", "gba"),
            websiteUrl = "https://github.com/Swordfish90/Lemuroid"
        ),
        EmulatorTarget(
            id = "vita3k",
            displayName = "Vita3K",
            systemLabel = "Sony PlayStation Vita",
            packageNames = listOf("org.vita3k.emulator"),
            supportedSlugs = setOf("psvita", "vita"),
            websiteUrl = "https://vita3k.org/"
        ),
        EmulatorTarget(
            id = "winlator",
            displayName = "Winlator",
            systemLabel = "Windows / DOS PC Games",
            packageNames = listOf("com.winlator"),
            supportedSlugs = setOf("dos", "win", "windows", "pc"),
            websiteUrl = "https://github.com/brunodev85/winlator/releases"
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
        // Prefer dedicated standalone emulators first if installed, then RetroArch
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

    fun launchRomInExternalEmulator(
        context: Context,
        romFile: File,
        packageName: String? = null
    ): Result<String> {
        return try {
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
                if (!packageName.isNullOrBlank()) {
                    setPackage(packageName)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                context.startActivity(viewIntent)
                Result.success("Launched ${romFile.name}")
            } catch (_: Exception) {
                if (!packageName.isNullOrBlank()) {
                    val launchAppIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchAppIntent != null) {
                        launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchAppIntent)
                        return Result.success("Opened $packageName (load ${romFile.name} from ROMs folder)")
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
