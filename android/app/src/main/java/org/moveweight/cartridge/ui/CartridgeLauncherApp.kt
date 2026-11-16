package org.moveweight.cartridge.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.moveweight.cartridge.data.RommApiClient
import org.moveweight.cartridge.emulators.EmulatorLauncher
import org.moveweight.cartridge.model.DownloadState
import org.moveweight.cartridge.model.InstalledEmulatorStatus
import org.moveweight.cartridge.model.LocalRomEntry
import org.moveweight.cartridge.model.PlatformItem
import org.moveweight.cartridge.model.ProbeResult
import org.moveweight.cartridge.model.RomItem
import org.moveweight.cartridge.model.ServerConfig
import org.moveweight.cartridge.model.formatBytes

private val CartridgeDarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0B1020),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0B1020),
    surface = Color(0xFF121B33),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    outline = Color(0xFF334B7A)
)

@Composable
fun CartridgeLauncherApp(
    gamepadEvents: SharedFlow<String>,
    onOpenBuiltInWebPlayer: (ServerConfig, RomItem?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(RommApiClient.loadSavedConfig(context)) }
    var currentTab by remember { mutableIntStateOf(if (config.isConfigured) 0 else 3) }

    var platforms by remember { mutableStateOf<List<PlatformItem>>(emptyList()) }
    var selectedPlatformId by remember { mutableStateOf<Int?>(null) }
    var roms by remember { mutableStateOf<List<RomItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var onlyInstalledFilter by remember { mutableStateOf(false) }
    var isLoadingLibrary by remember { mutableStateOf(false) }
    var libraryError by remember { mutableStateOf<String?>(null) }

    val coverBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var localRoms by remember { mutableStateOf(RommApiClient.listLocalRoms(context)) }
    var installedEmulators by remember { mutableStateOf(EmulatorLauncher.scanInstalledEmulators(context)) }

    var selectedRom by remember { mutableStateOf<RomItem?>(null) }
    var activeDownload by remember { mutableStateOf<DownloadState?>(null) }

    fun refreshLocalState() {
        localRoms = RommApiClient.listLocalRoms(context)
        installedEmulators = EmulatorLauncher.scanInstalledEmulators(context)
    }

    fun syncLibrary(targetConfig: ServerConfig = config, platId: Int? = selectedPlatformId, query: String = searchQuery) {
        if (!targetConfig.isConfigured) return
        scope.launch {
            isLoadingLibrary = true
            libraryError = null
            val platRes = RommApiClient.fetchPlatforms(targetConfig)
            platRes.onSuccess { platforms = it }
            val romRes = RommApiClient.fetchRoms(targetConfig, platId, query)
            romRes.fold(
                onSuccess = { loaded ->
                    roms = loaded
                    refreshLocalState()
                },
                onFailure = { err ->
                    libraryError = err.message ?: "Could not load library from ${targetConfig.url}"
                }
            )
            isLoadingLibrary = false
        }
    }

    LaunchedEffect(config.url, config.token, config.basicAuth) {
        if (config.isConfigured) {
            syncLibrary(config, selectedPlatformId, searchQuery)
        }
    }

    LaunchedEffect(Unit) {
        gamepadEvents.collect { key ->
            when (key) {
                "l1" -> if (platforms.isNotEmpty() && currentTab == 0) {
                    val idx = platforms.indexOfFirst { it.id == selectedPlatformId }
                    selectedPlatformId = if (idx <= 0) null else platforms[idx - 1].id
                    syncLibrary(config, selectedPlatformId, searchQuery)
                }
                "r1" -> if (platforms.isNotEmpty() && currentTab == 0) {
                    val idx = platforms.indexOfFirst { it.id == selectedPlatformId }
                    val nextIdx = (idx + 1).coerceAtMost(platforms.lastIndex)
                    selectedPlatformId = platforms.getOrNull(nextIdx)?.id
                    syncLibrary(config, selectedPlatformId, searchQuery)
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
                        status = "Installed to ${file.name}",
                        isRunning = false
                    )
                    if (triggerLaunchAfter) {
                        val launchRes = EmulatorLauncher.launchRomInExternalEmulator(context, file, launchPackageAfter)
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

    MaterialTheme(colorScheme = CartridgeDarkColors) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1020))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            containerColor = Color(0xFF0B1020),
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0F172A),
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(
                        Triple(0, "Library", Icons.Default.SportsEsports),
                        Triple(1, "On Device (${localRoms.size})", Icons.Default.Folder),
                        Triple(2, "Emulators", Icons.Default.PlayArrow),
                        Triple(3, "Server", Icons.Default.Settings)
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
                                selectedIconColor = Color(0xFF0B1020),
                                selectedTextColor = Color(0xFF38BDF8),
                                indicatorColor = Color(0xFF38BDF8),
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8)
                            )
                        )
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
                        config = config,
                        platforms = platforms,
                        selectedPlatformId = selectedPlatformId,
                        roms = if (onlyInstalledFilter) {
                            roms.filter { RommApiClient.getLocalRomFile(context, it) != null }
                        } else roms,
                        searchQuery = searchQuery,
                        onlyInstalledFilter = onlyInstalledFilter,
                        isLoading = isLoadingLibrary,
                        errorMessage = libraryError,
                        coverBitmaps = coverBitmaps,
                        onSearchQueryChange = { q ->
                            searchQuery = q
                        },
                        onTriggerSearch = {
                            syncLibrary(config, selectedPlatformId, searchQuery)
                        },
                        onSelectPlatform = { pid ->
                            selectedPlatformId = pid
                            syncLibrary(config, pid, searchQuery)
                        },
                        onToggleInstalledOnly = {
                            onlyInstalledFilter = !onlyInstalledFilter
                        },
                        onRefresh = {
                            syncLibrary(config, selectedPlatformId, searchQuery)
                        },
                        onOpenServerSetup = { currentTab = 3 },
                        onSelectRom = { rom -> selectedRom = rom }
                    )

                    1 -> LocalDownloadsScreen(
                        localRoms = localRoms,
                        onPlayLocal = { entry ->
                            val best = EmulatorLauncher.bestInstalledForSlug(context, entry.platformSlug)
                            EmulatorLauncher.launchRomInExternalEmulator(context, entry.file, best?.installedPackage)
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
                        onSaveAndConnect = { updated ->
                            config = updated
                            RommApiClient.saveConfig(context, updated)
                            syncLibrary(updated, null, "")
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
                    onPlayExternal = { targetPkg ->
                        val existing = RommApiClient.getLocalRomFile(context, rom)
                        if (existing != null) {
                            EmulatorLauncher.launchRomInExternalEmulator(context, existing, targetPkg)
                        } else {
                            startRomDownload(rom, launchPackageAfter = targetPkg, triggerLaunchAfter = true)
                        }
                    },
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
        }
    }
}

@Composable
private fun LibraryScreen(
    config: ServerConfig,
    platforms: List<PlatformItem>,
    selectedPlatformId: Int?,
    roms: List<RomItem>,
    searchQuery: String,
    onlyInstalledFilter: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    coverBitmaps: MutableMap<Int, Bitmap>,
    onSearchQueryChange: (String) -> Unit,
    onTriggerSearch: () -> Unit,
    onSelectPlatform: (Int?) -> Unit,
    onToggleInstalledOnly: () -> Unit,
    onRefresh: () -> Unit,
    onOpenServerSetup: () -> Unit,
    onSelectRom: (RomItem) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CARTRIDGE",
                    color = Color(0xFF38BDF8),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (config.isConfigured) config.url else "Not connected to a server",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = onlyInstalledFilter,
                    onClick = onToggleInstalledOnly,
                    label = { Text("On Device", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4ADE80).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF4ADE80)
                    )
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync Library", tint = Color(0xFF38BDF8))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Native Android Search TextField (pops up the phone's real system keyboard!)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search games in your library...", color = Color(0xFF64748B), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        onSearchQueryChange("")
                        onTriggerSearch()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
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
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334B7A),
                focusedContainerColor = Color(0xFF121B33),
                unfocusedContainerColor = Color(0xFF121B33)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Platform filter rail
        if (platforms.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPlatformId == null,
                    onClick = { onSelectPlatform(null) },
                    label = { Text("All Systems", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF38BDF8),
                        selectedLabelColor = Color(0xFF0B1020)
                    )
                )
                platforms.forEach { plat ->
                    val label = if (plat.romCount > 0) "${plat.name} (${plat.romCount})" else plat.name
                    FilterChip(
                        selected = selectedPlatformId == plat.id,
                        onClick = { onSelectPlatform(plat.id) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8),
                            selectedLabelColor = Color(0xFF0B1020)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!config.isConfigured) {
            EmptyLibraryCard(
                title = "Connect Your RomM or ROMarr Server",
                body = "Enter your server URL and sign in with your phone's native keyboard to sync your game library, download ROMs, and launch directly into RetroArch, Dolphin, PPSSPP, DuckStation, or NetherSX2.",
                buttonLabel = "Configure Server Connection",
                onAction = onOpenServerSetup
            )
            return@Column
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Syncing library from ${config.url}...", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            }
            return@Column
        }

        if (errorMessage != null && roms.isEmpty()) {
            EmptyLibraryCard(
                title = "Could Not Load Library",
                body = errorMessage,
                buttonLabel = "Check Server & Auth Settings",
                onAction = onOpenServerSetup
            )
            return@Column
        }

        if (roms.isEmpty()) {
            EmptyLibraryCard(
                title = "No Games Matched",
                body = if (onlyInstalledFilter) {
                    "No games from this filter are downloaded to your phone yet. Turn off 'On Device' or tap a game to download it."
                } else {
                    "Your server returned 0 games for this filter. Try clearing the search bar or switching platforms."
                },
                buttonLabel = "Refresh Library",
                onAction = onRefresh
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    onClick = { onSelectRom(rom) }
                )
            }
        }
    }
}

@Composable
private fun RomGridTile(
    rom: RomItem,
    coverBitmap: Bitmap?,
    isInstalled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (isInstalled) Color(0xFF4ADE80).copy(alpha = 0.6f) else Color(0xFF334B7A),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B33))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
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
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8).copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rom.platformName.ifBlank { rom.platformSlug.uppercase() },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Platform tag top-left
                Surface(
                    color = Color(0xFF0B1020).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        text = rom.platformSlug.uppercase(),
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (isInstalled) {
                    Surface(
                        color = Color(0xFF052E16).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Text(
                            text = "READY",
                            color = Color(0xFF4ADE80),
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
                    color = Color(0xFFF8FAFC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rom.formattedSize,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "On-Device ROM Storage",
            color = Color(0xFF38BDF8),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${localRoms.size} game(s) downloaded · ${formatBytes(totalBytes)} used",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (localRoms.isEmpty()) {
            EmptyLibraryCard(
                title = "No Local ROMs Downloaded Yet",
                body = "Tap any game in the Library tab and press 'Download & Play' to store ROMs locally for offline handheld play.",
                buttonLabel = null,
                onAction = {}
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(localRoms, key = { it.file.absolutePath }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B33)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF334B7A), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    color = Color(0xFFF8FAFC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${entry.platformSlug.uppercase()} · ${entry.formattedSize}",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 12.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onPlayLocal(entry) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF38BDF8),
                                        contentColor = Color(0xFF0B1020)
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Android Emulator Fleet",
                    color = Color(0xFF38BDF8),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$installedCount of ${emulators.size} emulator families installed on this device",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan Emulators", tint = Color(0xFF38BDF8))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(emulators, key = { it.target.id }) { status ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121B33)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (status.isInstalled) Color(0xFF4ADE80).copy(alpha = 0.5f) else Color(0xFF334B7A),
                            RoundedCornerShape(12.dp)
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
                                    color = Color(0xFFF8FAFC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (status.isInstalled) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = status.target.systemLabel,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            if (status.installedPackage != null) {
                                Text(
                                    text = status.installedPackage,
                                    color = Color(0xFF4ADE80),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        OutlinedButton(onClick = { onOpenEmulator(status) }) {
                            Text(
                                text = if (status.isInstalled) "Open" else "Get App",
                                color = if (status.isInstalled) Color(0xFF4ADE80) else Color(0xFF38BDF8),
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
    onSaveAndConnect: (ServerConfig) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var urlInput by remember(config.url) { mutableStateOf(config.url) }
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
            .padding(16.dp)
    ) {
        Text(
            text = "Server & Account Setup",
            color = Color(0xFF38BDF8),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tap any field below to type or paste using your phone's native keyboard.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B33)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF334B7A), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. RomM or ROMarr Server URL",
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server Address (e.g. http://192.168.1.100:6868)") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                clipboard.getText()?.text?.trim()?.let { pasted ->
                                    if (pasted.isNotBlank()) urlInput = pasted
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL", tint = Color(0xFF38BDF8))
                            }
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                                }
                            }
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
                        Text("Allow self-signed HTTPS certificates", color = Color(0xFFF8FAFC), fontSize = 14.sp)
                        Text("Enable for private LAN servers using self-signed TLS", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = allowInsecure, onCheckedChange = { allowInsecure = it })
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        scope.launch {
                            isBusy = true
                            statusText = "Testing connection to $urlInput..."
                            isStatusError = false
                            val res = RommApiClient.probeServer(urlInput, allowInsecure)
                            probeResult = res
                            if (res.ok && res.resolvedUrl.isNotBlank()) {
                                urlInput = res.resolvedUrl
                                statusText = res.message
                                isStatusError = false
                            } else {
                                statusText = res.message
                                isStatusError = true
                            }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Server Connection", color = Color(0xFFF8FAFC))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Authentication Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B33)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF334B7A), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Authentication Method",
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = authTab == 0,
                        onClick = { authTab = 0 },
                        label = { Text("Username & Password") }
                    )
                    FilterChip(
                        selected = authTab == 1,
                        onClick = { authTab = 1 },
                        label = { Text("Pair Code (XXXX-XXXX)") }
                    )
                    FilterChip(
                        selected = authTab == 2,
                        onClick = { authTab = 2 },
                        label = { Text("API Token / Open") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (authTab) {
                    0 -> {
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF38BDF8)) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    1 -> {
                        Text(
                            text = "In RomM or ROMarr, open Settings -> Client API Tokens -> Add -> Pair and type or paste the 8-character code:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pairCodeInput,
                            onValueChange = { pairCodeInput = it.uppercase() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Pairing Code (e.g. ABCD-1234)") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF38BDF8)) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.trim()?.let { pasted ->
                                        if (pasted.isNotBlank()) pairCodeInput = pasted.uppercase()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste Code", tint = Color(0xFF38BDF8))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    2 -> {
                        Text(
                            text = "Paste a Bearer API token, or leave blank if your ROMarr / RomM server allows unauthenticated LAN access:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Token (Optional)") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF38BDF8)) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.trim()?.let { pasted ->
                                        if (pasted.isNotBlank()) tokenInput = pasted
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste Token", tint = Color(0xFF38BDF8))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                            statusText = "Connecting to server..."

                            val probe = RommApiClient.probeServer(urlInput, allowInsecure)
                            probeResult = probe
                            if (!probe.ok) {
                                statusText = probe.message
                                isStatusError = true
                                isBusy = false
                                return@launch
                            }

                            val baseConfig = config.copy(
                                url = probe.resolvedUrl,
                                allowInsecureCerts = allowInsecure
                            )

                            when (authTab) {
                                0 -> {
                                    if (usernameInput.isBlank() && passwordInput.isBlank()) {
                                        onSaveAndConnect(baseConfig.copy(authMode = "open"))
                                    } else {
                                        val authRes = RommApiClient.signInWithPassword(baseConfig, usernameInput.trim(), passwordInput)
                                        authRes.fold(
                                            onSuccess = { signedIn ->
                                                statusText = "Signed in as ${signedIn.username}"
                                                onSaveAndConnect(signedIn)
                                            },
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
                                        onSuccess = { paired ->
                                            statusText = "Device paired!"
                                            onSaveAndConnect(paired)
                                        },
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color(0xFF0B1020)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF0B1020),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save & Sync Game Library", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                if (statusText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = statusText!!,
                        color = if (isStatusError) Color(0xFFF87171) else Color(0xFF4ADE80),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                probeResult?.logs?.takeIf { it.isNotEmpty() }?.let { logs ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF0B1020),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            logs.forEach { line ->
                                Text(
                                    text = line,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B33)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, Color(0xFF334B7A), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    color = Color(0xFFF8FAFC),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = body,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (buttonLabel != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color(0xFF0B1020)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(buttonLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
