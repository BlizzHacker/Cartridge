package org.moveweight.cartridge.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.moveweight.cartridge.data.RommApiClient
import org.moveweight.cartridge.emulators.EmulatorLauncher
import org.moveweight.cartridge.model.CollectionItem
import org.moveweight.cartridge.model.DownloadState
import org.moveweight.cartridge.model.InstalledEmulatorStatus
import org.moveweight.cartridge.model.LocalRomEntry
import org.moveweight.cartridge.model.PlatformItem
import org.moveweight.cartridge.model.ProbeResult
import org.moveweight.cartridge.model.RomItem
import org.moveweight.cartridge.model.ServerConfig
import org.moveweight.cartridge.model.ServerProfile
import org.moveweight.cartridge.model.formatBytes

private val BgDeep = Color(0xFF070B16)
private val BgSurface = Color(0xFF10182E)
private val BgCard = Color(0xFF14203C)
private val AccentCyan = Color(0xFF38BDF8)
private val AccentEmerald = Color(0xFF4ADE80)
private val AccentViolet = Color(0xFFA78BFA)
private val BorderSubtle = Color(0xFF283B63)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

private val CartridgeDarkColors = darkColorScheme(
    primary = AccentCyan,
    onPrimary = BgDeep,
    secondary = AccentEmerald,
    background = BgDeep,
    surface = BgSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1E293B),
    outline = BorderSubtle
)

@Composable
fun CartridgeLauncherApp(
    gamepadEvents: SharedFlow<String>,
    onOpenBuiltInWebPlayer: (ServerConfig, RomItem?) -> Unit,
    onOpenStreamPlayer: (ServerConfig, RomItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTvWide = configuration.screenWidthDp >= 640

    var config by remember { mutableStateOf(RommApiClient.loadSavedConfig(context)) }
    var serverProfiles by remember { mutableStateOf(RommApiClient.loadServerProfiles(context)) }
    var currentTab by remember { mutableIntStateOf(if (config.isConfigured) 0 else 3) }

    var platforms by remember { mutableStateOf<List<PlatformItem>>(emptyList()) }
    var collections by remember { mutableStateOf<List<CollectionItem>>(emptyList()) }
    var selectedPlatformId by remember { mutableStateOf<Int?>(null) }
    var selectedCollectionId by remember { mutableStateOf<Int?>(null) }
    var roms by remember { mutableStateOf<List<RomItem>>(emptyList()) }
    var focusedRom by remember { mutableStateOf<RomItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var onlyInstalledFilter by remember { mutableStateOf(false) }
    var isLoadingLibrary by remember { mutableStateOf(false) }
    var libraryError by remember { mutableStateOf<String?>(null) }

    val coverBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var localRoms by remember { mutableStateOf(RommApiClient.listLocalRoms(context)) }
    var installedEmulators by remember { mutableStateOf(EmulatorLauncher.scanInstalledEmulators(context)) }

    var selectedRom by remember { mutableStateOf<RomItem?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var activeDownload by remember { mutableStateOf<DownloadState?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    fun refreshLocalState() {
        localRoms = RommApiClient.listLocalRoms(context)
        installedEmulators = EmulatorLauncher.scanInstalledEmulators(context)
        serverProfiles = RommApiClient.loadServerProfiles(context)
    }

    fun syncLibrary(
        targetConfig: ServerConfig = config,
        platId: Int? = selectedPlatformId,
        query: String = searchQuery,
        collId: Int? = selectedCollectionId
    ) {
        if (!targetConfig.isConfigured) return
        scope.launch {
            isLoadingLibrary = true
            libraryError = null
            val resolved = RommApiClient.resolveBestConfig(targetConfig)
            if (resolved.url != config.url || resolved.isUsingLocalLan != config.isUsingLocalLan) {
                config = resolved
                RommApiClient.saveConfig(context, resolved)
            }
            val platRes = RommApiClient.fetchPlatforms(resolved)
            platRes.onSuccess { platforms = it }
            val collRes = RommApiClient.fetchCollections(resolved)
            collRes.onSuccess { collections = it }
            val romRes = RommApiClient.fetchRoms(resolved, platId, query, collId)
            romRes.fold(
                onSuccess = { loaded ->
                    roms = loaded
                    if (focusedRom == null || loaded.none { it.id == focusedRom?.id }) {
                        focusedRom = loaded.firstOrNull()
                    }
                    refreshLocalState()
                },
                onFailure = { err ->
                    libraryError = err.message ?: "Could not load library from ${resolved.effectiveUrl}"
                }
            )
            isLoadingLibrary = false
        }
    }

    fun switchProfile(profile: ServerProfile) {
        val next = ServerConfig(
            url = profile.localUrl.ifBlank { profile.remoteUrl },
            localUrl = profile.localUrl,
            remoteUrl = profile.remoteUrl,
            activeServerName = profile.name,
            isUsingLocalLan = RommApiClient.isLanUrl(profile.localUrl.ifBlank { profile.remoteUrl }),
            token = profile.token,
            basicAuth = profile.basicAuth,
            username = profile.username,
            authMode = profile.authMode,
            allowInsecureCerts = profile.allowInsecureCerts
        )
        config = next
        RommApiClient.saveConfig(context, next)
        selectedPlatformId = null
        selectedCollectionId = null
        searchQuery = ""
        currentTab = 0
        syncLibrary(next, null, "", null)
    }

    LaunchedEffect(config.localUrl, config.remoteUrl, config.url, config.token, config.basicAuth) {
        if (config.isConfigured) {
            syncLibrary(config, selectedPlatformId, searchQuery, selectedCollectionId)
        }
    }

    LaunchedEffect(Unit) {
        gamepadEvents.collect { key ->
            when (key) {
                "l1" -> if (platforms.isNotEmpty() && currentTab == 0) {
                    val idx = platforms.indexOfFirst { it.id == selectedPlatformId }
                    selectedPlatformId = if (idx <= 0) null else platforms[idx - 1].id
                    selectedCollectionId = null
                    syncLibrary(config, selectedPlatformId, searchQuery, null)
                }
                "r1" -> if (platforms.isNotEmpty() && currentTab == 0) {
                    val idx = platforms.indexOfFirst { it.id == selectedPlatformId }
                    val nextIdx = (idx + 1).coerceAtMost(platforms.lastIndex)
                    selectedPlatformId = platforms.getOrNull(nextIdx)?.id
                    selectedCollectionId = null
                    syncLibrary(config, selectedPlatformId, searchQuery, null)
                }
                "l2" -> {
                    currentTab = (currentTab - 1).coerceAtLeast(0)
                    refreshLocalState()
                }
                "r2" -> {
                    currentTab = (currentTab + 1).coerceAtMost(3)
                    refreshLocalState()
                }
                "x" -> if (currentTab == 0) {
                    onlyInstalledFilter = !onlyInstalledFilter
                }
                "y" -> if (currentTab == 0) {
                    if (isTvWide) {
                        showSearchDialog = true
                    } else {
                        runCatching { searchFocusRequester.requestFocus() }
                    }
                }
            }
        }
    }

    fun startRomDownload(rom: RomItem, launchPackageAfter: String? = null, triggerLaunchAfter: Boolean = false) {
        scope.launch {
            activeDownload = DownloadState(
                romId = rom.id,
                romTitle = rom.name,
                bytesRead = 0L,
                totalBytes = rom.fileSizeBytes,
                status = "Downloading ${rom.fileName}...",
                isRunning = true
            )
            val res = RommApiClient.downloadRom(context, config, rom) { read, total ->
                activeDownload = DownloadState(
                    romId = rom.id,
                    romTitle = rom.name,
                    bytesRead = read,
                    totalBytes = total,
                    status = "Downloading ${rom.fileName}...",
                    isRunning = true
                )
            }
            res.fold(
                onSuccess = { file ->
                    refreshLocalState()
                    activeDownload = DownloadState(
                        romId = rom.id,
                        romTitle = rom.name,
                        bytesRead = file.length(),
                        totalBytes = file.length(),
                        status = "Ready to play!",
                        isRunning = false
                    )
                    if (triggerLaunchAfter) {
                        val launchRes = EmulatorLauncher.launchRomInExternalEmulator(
                            context = context,
                            romFile = file,
                            packageName = launchPackageAfter,
                            platformSlug = rom.platformSlug
                        )
                        launchRes.onFailure { err ->
                            Toast.makeText(context, "Launch error: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onFailure = { err ->
                    activeDownload = DownloadState(
                        romId = rom.id,
                        romTitle = rom.name,
                        bytesRead = 0L,
                        totalBytes = rom.fileSizeBytes,
                        status = "Download failed: ${err.message}",
                        isRunning = false,
                        error = err.message
                    )
                }
            )
        }
    }

    var lastPlayedRom by remember { mutableStateOf<RomItem?>(null) }

    fun syncBackLastPlayedSave() {
        val prev = lastPlayedRom ?: return
        scope.launch {
            RommApiClient.pushCloudSaveForRom(config, prev).onSuccess { msg ->
                if (msg.startsWith("Synced")) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun playRomNow(rom: RomItem, targetPkg: String? = null) {
        // PS1+ consoles (Switch, Wii U, PS2, GC, Wii, 3DS, PS1, etc.) ALWAYS stream via RomM Stream Server!
        if (rom.shouldStream && targetPkg == null) {
            selectedRom = null
            onOpenStreamPlayer(config, rom)
            return
        }
        val existing = RommApiClient.getLocalRomFile(context, rom)
        val bestPkg = targetPkg ?: EmulatorLauncher.bestInstalledForSlug(context, rom.platformSlug)?.installedPackage
        lastPlayedRom = rom
        if (existing != null) {
            scope.launch {
                // Pull latest RomM cloud save before launching so 2-4P couch progress resumes!
                RommApiClient.pullCloudSaveForRom(config, rom)
                val res = EmulatorLauncher.launchRomInExternalEmulator(
                    context = context,
                    romFile = existing,
                    packageName = bestPkg,
                    platformSlug = rom.platformSlug
                )
                res.onFailure { err ->
                    Toast.makeText(context, "Launch error: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            selectedRom = rom
            scope.launch { RommApiClient.pullCloudSaveForRom(config, rom) }
            startRomDownload(rom, launchPackageAfter = bestPkg, triggerLaunchAfter = true)
        }
    }

    MaterialTheme(colorScheme = CartridgeDarkColors) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0A1228), BgDeep))
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            containerColor = Color.Transparent,
            topBar = {
                if (isTvWide) {
                    TvConsoleTopBar(
                        config = config,
                        currentTab = currentTab,
                        localRomCount = localRoms.size,
                        onSelectTab = { idx ->
                            refreshLocalState()
                            currentTab = idx
                        },
                        onRefresh = {
                            syncBackLastPlayedSave()
                            syncLibrary(config, selectedPlatformId, searchQuery, selectedCollectionId)
                        }
                    )
                }
            },
            bottomBar = {
                if (isTvWide) {
                    TvControllerHintBar(currentTab = currentTab)
                } else {
                    NavigationBar(
                        containerColor = Color(0xFF0F172A),
                        tonalElevation = 8.dp
                    ) {
                        val items = listOf(
                            Triple(0, "Library", Icons.Default.Star),
                            Triple(1, "On Device (${localRoms.size})", Icons.Default.List),
                            Triple(2, "Emulators", Icons.Default.PlayArrow),
                            Triple(3, "Servers", Icons.Default.Settings)
                        )
                        items.forEach { (index, label, icon) ->
                            NavigationBarItem(
                                selected = currentTab == index,
                                onClick = {
                                    refreshLocalState()
                                    currentTab = index
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BgDeep,
                                    selectedTextColor = AccentCyan,
                                    indicatorColor = AccentCyan,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    0 -> LibraryScreen(
                        isTvWide = isTvWide,
                        config = config,
                        serverProfiles = serverProfiles,
                        platforms = platforms,
                        collections = collections,
                        selectedPlatformId = selectedPlatformId,
                        selectedCollectionId = selectedCollectionId,
                        roms = if (onlyInstalledFilter) {
                            roms.filter { RommApiClient.getLocalRomFile(context, it) != null }
                        } else roms,
                        focusedRom = focusedRom,
                        searchQuery = searchQuery,
                        searchFocusRequester = searchFocusRequester,
                        onlyInstalledFilter = onlyInstalledFilter,
                        isLoading = isLoadingLibrary,
                        errorMessage = libraryError,
                        coverBitmaps = coverBitmaps,
                        onSearchQueryChange = { searchQuery = it },
                        onOpenTvSearch = { showSearchDialog = true },
                        onTriggerSearch = {
                            syncLibrary(config, selectedPlatformId, searchQuery, selectedCollectionId)
                        },
                        onSelectPlatform = { pid ->
                            selectedPlatformId = pid
                            selectedCollectionId = null
                            syncLibrary(config, pid, searchQuery, null)
                        },
                        onSelectCollection = { cid ->
                            selectedCollectionId = cid
                            selectedPlatformId = null
                            syncLibrary(config, null, searchQuery, cid)
                        },
                        onSwitchProfile = { switchProfile(it) },
                        onToggleInstalledOnly = { onlyInstalledFilter = !onlyInstalledFilter },
                        onRefresh = {
                            syncBackLastPlayedSave()
                            syncLibrary(config, selectedPlatformId, searchQuery, selectedCollectionId)
                        },
                        onOpenServerSetup = { currentTab = 3 },
                        onFocusRom = { focusedRom = it },
                        onSelectRom = { rom -> selectedRom = rom },
                        onQuickPlayRom = { rom -> playRomNow(rom) }
                    )

                    1 -> LocalDownloadsScreen(
                        localRoms = localRoms,
                        onPlayLocal = { entry ->
                            val best = EmulatorLauncher.bestInstalledForSlug(context, entry.platformSlug)
                            EmulatorLauncher.launchRomInExternalEmulator(
                                context = context,
                                romFile = entry.file,
                                packageName = best?.installedPackage,
                                platformSlug = entry.platformSlug
                            )
                        },
                        onDeleteLocal = { entry ->
                            entry.file.delete()
                            refreshLocalState()
                        }
                    )

                    2 -> EmulatorsScreen(
                        emulators = installedEmulators,
                        onRefresh = { refreshLocalState() },
                        onOpenEmulator = { status ->
                            EmulatorLauncher.openEmulatorOrStore(context, status)
                        }
                    )

                    3 -> ServerSetupScreen(
                        config = config,
                        profiles = serverProfiles,
                        onSwitchProfile = { switchProfile(it) },
                        onDeleteProfile = { profile ->
                            val updated = serverProfiles.filterNot { it.id == profile.id }
                            RommApiClient.saveServerProfiles(context, updated)
                            serverProfiles = updated
                        },
                        onSaveAndConnect = { updated ->
                            config = updated
                            RommApiClient.saveConfig(context, updated)
                            serverProfiles = RommApiClient.upsertProfileFromConfig(context, updated)
                            syncLibrary(updated, null, "", null)
                            currentTab = 0
                        }
                    )
                }
            }

            selectedRom?.let { rom ->
                val localFile = RommApiClient.getLocalRomFile(context, rom)
                val matchingEmus = remember(rom.platformSlug, installedEmulators) {
                    EmulatorLauncher.matchingEmulatorsForSlug(context, rom.platformSlug)
                }
                GameDetailDialog(
                    rom = rom,
                    coverBitmap = coverBitmaps[rom.id],
                    localFile = localFile,
                    downloadState = activeDownload?.takeIf { it.romId == rom.id },
                    matchingEmulators = matchingEmus,
                    onDismiss = { selectedRom = null },
                    onDownload = { startRomDownload(rom, triggerLaunchAfter = false) },
                    onPlayExternal = { targetPkg -> playRomNow(rom, targetPkg) },
                    onPlayBuiltInWeb = {
                        val targetRom = rom
                        selectedRom = null
                        onOpenBuiltInWebPlayer(config, targetRom)
                    },
                    onDeleteLocal = {
                        RommApiClient.getLocalRomFile(context, rom)?.delete()
                        refreshLocalState()
                    }
                )
            }

            if (showSearchDialog) {
                TvSearchDialog(
                    initialQuery = searchQuery,
                    onDismiss = { showSearchDialog = false },
                    onSearch = { q ->
                        searchQuery = q
                        showSearchDialog = false
                        syncLibrary(config, selectedPlatformId, q, selectedCollectionId)
                    }
                )
            }
        }
    }
}

@Composable
private fun TvConsoleTopBar(
    config: ServerConfig,
    currentTab: Int,
    localRomCount: Int,
    onSelectTab: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = Color(0xFF0A1124).copy(alpha = 0.96f),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderSubtle.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(AccentCyan, AccentViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = BgDeep,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "CARTRIDGE",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (config.isUsingLocalLan) AccentEmerald else AccentCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (config.isConfigured) {
                                val mode = if (config.isUsingLocalLan) "LAN DIRECT" else "REMOTE"
                                "$mode • ${config.activeServerName}"
                            } else {
                                "Not Connected"
                            },
                            color = if (config.isUsingLocalLan) AccentEmerald else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    0 to "Library",
                    1 to "Ready ($localRomCount)",
                    2 to "Emulators",
                    3 to "Servers"
                )
                tabs.forEach { (idx, label) ->
                    TvNavPill(
                        label = label,
                        selected = currentTab == idx,
                        onClick = { onSelectTab(idx) }
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = AccentCyan)
                }
            }
        }
    }
}

@Composable
private fun TvNavPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "navScale")
    Surface(
        color = when {
            selected -> AccentCyan
            focused -> Color(0xFF1E3A5F)
            else -> BgCard
        },
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Color.White else if (selected) AccentCyan else BorderSubtle,
                shape = RoundedCornerShape(999.dp)
            )
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) BgDeep else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TvControllerHintBar(currentTab: Int) {
    Surface(
        color = Color(0xFF080D1C),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HintChip("A", "Play / Open")
            HintChip("B", "Back")
            if (currentTab == 0) {
                HintChip("LB / RB", "Systems")
                HintChip("X", "Ready Filter")
                HintChip("Y", "Search")
            }
            HintChip("LT / RT", "Switch Tab")
        }
    }
}

@Composable
private fun HintChip(button: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
        ) {
            Text(
                text = button,
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = action, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun LibraryScreen(
    isTvWide: Boolean,
    config: ServerConfig,
    serverProfiles: List<ServerProfile>,
    platforms: List<PlatformItem>,
    collections: List<CollectionItem>,
    selectedPlatformId: Int?,
    selectedCollectionId: Int?,
    roms: List<RomItem>,
    focusedRom: RomItem?,
    searchQuery: String,
    searchFocusRequester: FocusRequester,
    onlyInstalledFilter: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    coverBitmaps: MutableMap<Int, Bitmap>,
    onSearchQueryChange: (String) -> Unit,
    onOpenTvSearch: () -> Unit,
    onTriggerSearch: () -> Unit,
    onSelectPlatform: (Int?) -> Unit,
    onSelectCollection: (Int?) -> Unit,
    onSwitchProfile: (ServerProfile) -> Unit,
    onToggleInstalledOnly: () -> Unit,
    onRefresh: () -> Unit,
    onOpenServerSetup: () -> Unit,
    onFocusRom: (RomItem) -> Unit,
    onSelectRom: (RomItem) -> Unit,
    onQuickPlayRom: (RomItem) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val hPad = if (isTvWide) 24.dp else 14.dp

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = hPad, vertical = 10.dp)) {
        if (!isTvWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CARTRIDGE",
                        color = AccentCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = if (config.isConfigured) {
                            val tag = if (config.isUsingLocalLan) "LAN" else "REMOTE"
                            "$tag • ${config.effectiveUrl}"
                        } else "Not connected",
                        color = if (config.isUsingLocalLan) AccentEmerald else TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = onlyInstalledFilter,
                        onClick = onToggleInstalledOnly,
                        label = { Text("Ready", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = AccentEmerald
                        )
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = AccentCyan)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Server profile quick-switcher (RomM, ROMarr, Friends' Libraries)
        if (serverProfiles.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                serverProfiles.forEach { profile ->
                    val isActive = profile.name == config.activeServerName
                    FilterChip(
                        selected = isActive,
                        onClick = { onSwitchProfile(profile) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet,
                            selectedLabelColor = BgDeep,
                            selectedLeadingIconColor = BgDeep
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Search + Platform bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isTvWide) {
                var searchBtnFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (searchBtnFocused) Color(0xFF1E3A5F) else BgSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.32f)
                        .onFocusChanged { searchBtnFocused = it.isFocused }
                        .border(
                            width = if (searchBtnFocused) 2.5.dp else 1.dp,
                            color = if (searchBtnFocused) AccentCyan else BorderSubtle,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenTvSearch() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Search: $searchQuery" else "Search games (Press Y)",
                            color = if (searchQuery.isNotBlank()) TextPrimary else TextMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester),
                    placeholder = { Text("Search games (e.g. Mario Kart)...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                onSearchQueryChange("")
                                onTriggerSearch()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onTriggerSearch()
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = BgSurface,
                        unfocusedContainerColor = BgSurface
                    )
                )
            }

            if (isTvWide && platforms.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .weight(0.58f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SystemChip(
                        label = "All Systems",
                        selected = selectedPlatformId == null && selectedCollectionId == null,
                        onClick = { onSelectPlatform(null) }
                    )
                    platforms.forEach { plat ->
                        val label = if (plat.romCount > 0) "${plat.name} (${plat.romCount})" else plat.name
                        SystemChip(
                            label = label,
                            selected = selectedPlatformId == plat.id,
                            onClick = { onSelectPlatform(plat.id) }
                        )
                    }
                    collections.forEach { coll ->
                        SystemChip(
                            label = "★ ${coll.name}",
                            selected = selectedCollectionId == coll.id,
                            accent = AccentViolet,
                            onClick = { onSelectCollection(coll.id) }
                        )
                    }
                }
            }
        }

        if (!isTvWide && platforms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SystemChip(
                    label = "All Systems",
                    selected = selectedPlatformId == null && selectedCollectionId == null,
                    onClick = { onSelectPlatform(null) }
                )
                platforms.forEach { plat ->
                    val label = if (plat.romCount > 0) "${plat.name} (${plat.romCount})" else plat.name
                    SystemChip(
                        label = label,
                        selected = selectedPlatformId == plat.id,
                        onClick = { onSelectPlatform(plat.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!config.isConfigured) {
            EmptyLibraryCard(
                title = "Connect Your RomM or ROMarr Server",
                body = "Add your home LAN IP (fast direct Wi-Fi!) and remote URL (fallback away from home) to sync and play your retro library.",
                buttonLabel = "Configure Server",
                onAction = onOpenServerSetup
            )
            return@Column
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Syncing ${config.activeServerName} (${config.effectiveUrl})...", color = TextMuted, fontSize = 14.sp)
                }
            }
            return@Column
        }

        if (errorMessage != null && roms.isEmpty()) {
            EmptyLibraryCard(
                title = "Could Not Load Library",
                body = errorMessage,
                buttonLabel = "Server Settings",
                onAction = onOpenServerSetup
            )
            return@Column
        }

        if (roms.isEmpty()) {
            EmptyLibraryCard(
                title = "No Games Matched",
                body = if (onlyInstalledFilter) {
                    "No games from this filter are downloaded yet. Press X or turn off Ready Filter to browse the full server."
                } else {
                    "Your server returned 0 games for this filter. Clear search or switch systems."
                },
                buttonLabel = "Refresh Library",
                onAction = onRefresh
            )
            return@Column
        }

        // TV Spotlight Hero Banner for currently focused game
        if (isTvWide && focusedRom != null) {
            val isHeroInstalled = RommApiClient.getLocalRomFile(context, focusedRom) != null
            HeroSpotlightBanner(
                rom = focusedRom,
                coverBitmap = coverBitmaps[focusedRom.id],
                isInstalled = isHeroInstalled,
                onPlay = { onQuickPlayRom(focusedRom) },
                onDetails = { onSelectRom(focusedRom) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (isTvWide) 156.dp else 136.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTvWide) 14.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTvWide) 14.dp else 10.dp)
        ) {
            items(roms, key = { it.id }) { rom ->
                val isInstalled = RommApiClient.getLocalRomFile(context, rom) != null
                LaunchedEffect(rom.id, rom.coverUrl) {
                    if (rom.coverUrl.isNotBlank() && !coverBitmaps.containsKey(rom.id)) {
                        val bmp = RommApiClient.loadCoverBitmap(config, rom.coverUrl)
                        if (bmp != null) {
                            coverBitmaps[rom.id] = bmp
                        }
                    }
                }
                RomGridTile(
                    rom = rom,
                    coverBitmap = coverBitmaps[rom.id],
                    isInstalled = isInstalled,
                    onFocused = { onFocusRom(rom) },
                    onClick = { onSelectRom(rom) }
                )
            }
        }
    }
}

@Composable
private fun SystemChip(
    label: String,
    selected: Boolean,
    accent: Color = AccentCyan,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "chipScale")
    Surface(
        color = when {
            selected -> accent
            focused -> Color(0xFF1E3A5F)
            else -> BgSurface
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Color.White else if (selected) accent else BorderSubtle,
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) BgDeep else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun HeroSpotlightBanner(
    rom: RomItem,
    coverBitmap: Bitmap?,
    isInstalled: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF132245), Color(0xFF0E1833), Color(0xFF161336))
                ),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 114.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B1020))
                    .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = rom.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgePill(
                        text = rom.platformName.ifBlank { rom.platformSlug.uppercase() },
                        color = AccentCyan
                    )
                    BadgePill(text = rom.formattedSize, color = TextMuted)
                    if (isInstalled) {
                        BadgePill(text = "READY ON TV", color = AccentEmerald)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rom.name,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (rom.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rom.summary,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDeep),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            rom.shouldStream -> "Stream Now"
                            isInstalled -> "Play Now"
                            else -> "Download & Play"
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                OutlinedButton(
                    onClick = onDetails,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Game Details", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RomGridTile(
    rom: RomItem,
    coverBitmap: Bitmap?,
    isInstalled: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "cardScale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused -> AccentCyan
                    isInstalled -> AccentEmerald.copy(alpha = 0.65f)
                    else -> BorderSubtle
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) Color(0xFF1A2B52) else BgCard
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0B1224))
                        )
                    )
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = rom.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = AccentCyan.copy(alpha = 0.75f),
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rom.platformName.ifBlank { rom.platformSlug.uppercase() },
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Bottom vignette for crisp readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, BgCard.copy(alpha = 0.9f))
                            )
                        )
                )

                Surface(
                    color = BgDeep.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                ) {
                    Text(
                        text = rom.platformSlug.uppercase(),
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (isInstalled) {
                    Surface(
                        color = Color(0xFF052E16).copy(alpha = 0.94f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                    ) {
                        Text(
                            text = "READY",
                            color = AccentEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = rom.name,
                    color = if (focused) Color.White else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    minLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rom.formattedSize,
                    color = if (focused) AccentCyan else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun LocalDownloadsScreen(
    localRoms: List<LocalRomEntry>,
    onPlayLocal: (LocalRomEntry) -> Unit,
    onDeleteLocal: (LocalRomEntry) -> Unit
) {
    val totalBytes = localRoms.sumOf { it.sizeBytes }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            text = "Ready on This Device",
            color = AccentCyan,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "${localRoms.size} game(s) ready for instant play · ${formatBytes(totalBytes)} used",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (localRoms.isEmpty()) {
            EmptyLibraryCard(
                title = "No Local ROMs Yet",
                body = "Pick any game in the Library tab and press Play to cache it locally for instant launch.",
                buttonLabel = null,
                onAction = {}
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(localRoms, key = { it.file.absolutePath }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${entry.platformSlug.uppercase()} · ${entry.formattedSize}",
                                    color = AccentEmerald,
                                    fontSize = 12.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onPlayLocal(entry) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCyan,
                                        contentColor = BgDeep
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { onDeleteLocal(entry) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF87171))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmulatorsScreen(
    emulators: List<InstalledEmulatorStatus>,
    onRefresh: () -> Unit,
    onOpenEmulator: (InstalledEmulatorStatus) -> Unit
) {
    val installedCount = emulators.count { it.isInstalled }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Emulator Fleet",
                    color = AccentCyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "$installedCount of ${emulators.size} emulator families ready on this device",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = AccentCyan)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(emulators, key = { it.target.id }) { status ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (status.isInstalled) AccentEmerald.copy(alpha = 0.55f) else BorderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = status.target.displayName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (status.isInstalled) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = status.target.systemLabel, color = TextMuted, fontSize = 12.sp)
                            if (status.installedPackage != null) {
                                Text(
                                    text = status.installedPackage,
                                    color = AccentEmerald,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        OutlinedButton(onClick = { onOpenEmulator(status) }) {
                            Text(
                                text = if (status.isInstalled) "Open" else "Get App",
                                color = if (status.isInstalled) AccentEmerald else AccentCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerSetupScreen(
    config: ServerConfig,
    profiles: List<ServerProfile>,
    onSwitchProfile: (ServerProfile) -> Unit,
    onDeleteProfile: (ServerProfile) -> Unit,
    onSaveAndConnect: (ServerConfig) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var serverNameInput by remember(config.activeServerName) { mutableStateOf(config.activeServerName) }
    var localUrlInput by remember(config.localUrl) {
        mutableStateOf(config.localUrl.ifBlank { if (RommApiClient.isLanUrl(config.url)) config.url else "" })
    }
    var remoteUrlInput by remember(config.remoteUrl) {
        mutableStateOf(config.remoteUrl.ifBlank { if (!RommApiClient.isLanUrl(config.url)) config.url else "" })
    }
    var authTab by remember { mutableIntStateOf(if (config.authMode == "pair") 1 else if (config.authMode == "token") 2 else 0) }
    var usernameInput by remember(config.username) { mutableStateOf(config.username) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var pairCodeInput by remember { mutableStateOf("") }
    var tokenInput by remember(config.token) { mutableStateOf(config.token) }
    var allowInsecure by remember(config.allowInsecureCerts) { mutableStateOf(config.allowInsecureCerts) }

    var isBusy by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<ProbeResult?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Servers & Libraries",
            color = AccentCyan,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Set both a Local LAN IP (direct Wi-Fi, bypasses Cloudflare) and Remote URL (fallback away from home). Add RomM, ROMarr, or Friends' libraries.",
            color = TextMuted,
            fontSize = 13.sp
        )

        if (profiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("Saved Libraries", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    val isCurrent = profile.name == config.activeServerName
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isCurrent) AccentEmerald else BorderSubtle,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = listOfNotNull(
                                        profile.localUrl.takeIf { it.isNotBlank() }?.let { "LAN: $it" },
                                        profile.remoteUrl.takeIf { it.isNotBlank() }?.let { "Remote: $it" }
                                    ).joinToString("  •  "),
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onSwitchProfile(profile) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCurrent) AccentEmerald else AccentCyan,
                                        contentColor = BgDeep
                                    )
                                ) {
                                    Text(if (isCurrent) "Active" else "Connect", fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { onDeleteProfile(profile) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFF87171))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = BgSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Add / Edit Server Connection",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = serverNameInput,
                    onValueChange = { serverNameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Library Name (e.g. Home RomM, ROMarr, Friend's Server)") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = AccentViolet) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = localUrlInput,
                    onValueChange = { localUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Local LAN URL (e.g. http://192.168.0.94:8080)") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = AccentEmerald) },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.trim()?.let { if (it.isNotBlank()) localUrlInput = it }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Paste", tint = AccentCyan)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remoteUrlInput,
                    onValueChange = { remoteUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Remote Domain URL (e.g. https://romm.moveweight.com)") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AccentCyan) },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.trim()?.let { if (it.isNotBlank()) remoteUrlInput = it }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Paste", tint = AccentCyan)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow self-signed HTTPS certificates", color = TextPrimary, fontSize = 14.sp)
                        Text("Enable for private LAN HTTPS servers", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(checked = allowInsecure, onCheckedChange = { allowInsecure = it })
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        scope.launch {
                            isBusy = true
                            val target = localUrlInput.ifBlank { remoteUrlInput }
                            statusText = "Testing $target..."
                            isStatusError = false
                            val res = RommApiClient.probeServer(target, allowInsecure)
                            probeResult = res
                            statusText = res.message
                            isStatusError = !res.ok
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection", color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = BgSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Authentication",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = authTab == 0, onClick = { authTab = 0 }, label = { Text("Username & Password") })
                    FilterChip(selected = authTab == 1, onClick = { authTab = 1 }, label = { Text("Pair Code") })
                    FilterChip(selected = authTab == 2, onClick = { authTab = 2 }, label = { Text("API Token / Open") })
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (authTab) {
                    0 -> {
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = AccentCyan) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AccentCyan) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Lock else Icons.Default.Info,
                                        contentDescription = "Toggle password",
                                        tint = TextMuted
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    1 -> {
                        OutlinedTextField(
                            value = pairCodeInput,
                            onValueChange = { pairCodeInput = it.uppercase() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Pairing Code (XXXX-XXXX)") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AccentCyan) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    2 -> {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Token (Optional)") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AccentCyan) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        scope.launch {
                            isBusy = true
                            isStatusError = false
                            statusText = "Connecting..."

                            val primaryProbeInput = localUrlInput.ifBlank { remoteUrlInput }
                            val probe = RommApiClient.probeServer(primaryProbeInput, allowInsecure)
                            probeResult = probe
                            if (!probe.ok) {
                                statusText = probe.message
                                isStatusError = true
                                isBusy = false
                                return@launch
                            }

                            val baseConfig = config.copy(
                                url = probe.resolvedUrl,
                                localUrl = localUrlInput.trim().trimEnd('/'),
                                remoteUrl = remoteUrlInput.trim().trimEnd('/'),
                                activeServerName = serverNameInput.trim().ifBlank { "My Library" },
                                isUsingLocalLan = RommApiClient.isLanUrl(probe.resolvedUrl),
                                allowInsecureCerts = allowInsecure
                            )

                            when (authTab) {
                                0 -> {
                                    if (usernameInput.isBlank() && passwordInput.isBlank()) {
                                        onSaveAndConnect(baseConfig.copy(authMode = "open"))
                                    } else {
                                        val authRes = RommApiClient.signInWithPassword(baseConfig, usernameInput.trim(), passwordInput)
                                        authRes.fold(
                                            onSuccess = { onSaveAndConnect(it) },
                                            onFailure = { err ->
                                                statusText = err.message ?: "Sign-in failed"
                                                isStatusError = true
                                            }
                                        )
                                    }
                                }
                                1 -> {
                                    val pairRes = RommApiClient.pairWithCode(baseConfig, pairCodeInput)
                                    pairRes.fold(
                                        onSuccess = { onSaveAndConnect(it) },
                                        onFailure = { err ->
                                            statusText = err.message ?: "Pairing failed"
                                            isStatusError = true
                                        }
                                    )
                                }
                                else -> {
                                    onSaveAndConnect(
                                        baseConfig.copy(
                                            token = tokenInput.trim(),
                                            basicAuth = "",
                                            authMode = "token"
                                        )
                                    )
                                }
                            }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDeep),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BgDeep, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save & Sync Library", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }

                if (statusText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = statusText!!,
                        color = if (isStatusError) Color(0xFFF87171) else AccentEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    title: String,
    body: String,
    buttonLabel: String?,
    onAction: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BgSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = body, color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                if (buttonLabel != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDeep),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(buttonLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    var text by remember(initialQuery) { mutableStateOf(initialQuery) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BgSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, AccentCyan, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Search Games", color = AccentCyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type game name (e.g. Mario Kart)...", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(text.trim()) }),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onSearch(text.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDeep),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Search", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onSearch("") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear", color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back", color = TextMuted)
                    }
                }
            }
        }
    }
}
