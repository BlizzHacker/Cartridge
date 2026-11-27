package org.moveweight.cartridge.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.moveweight.cartridge.model.DownloadState
import org.moveweight.cartridge.model.InstalledEmulatorStatus
import org.moveweight.cartridge.model.RomItem
import org.moveweight.cartridge.model.formatBytes
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetailDialog(
    rom: RomItem,
    coverBitmap: Bitmap?,
    localFile: File?,
    downloadState: DownloadState?,
    matchingEmulators: List<InstalledEmulatorStatus>,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onPlayExternal: (String?) -> Unit,
    onPlayBuiltInWeb: () -> Unit,
    onDeleteLocal: () -> Unit
) {
    val bestInstalled = matchingEmulators.firstOrNull { it.isInstalled }
    val isDownloading = downloadState?.isRunning == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .border(1.dp, Color(0xFF334B7A), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11192E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 152.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            )
                            .border(1.dp, Color(0xFF334B7A), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap.asImageBitmap(),
                                contentDescription = rom.name,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = rom.platformSlug.uppercase(),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = rom.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BadgePill(text = rom.platformName.ifBlank { rom.platformSlug.uppercase() }, color = Color(0xFF38BDF8))
                            BadgePill(text = rom.formattedSize, color = Color(0xFF94A3B8))
                            if (localFile != null) {
                                BadgePill(text = "INSTALLED ON DEVICE", color = Color(0xFF4ADE80))
                            }
                            rom.regions.take(2).forEach { reg ->
                                BadgePill(text = reg, color = Color(0xFFA78BFA))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = rom.fileName,
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (downloadState != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = Color(0xFF0B1020),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = downloadState.status,
                                color = if (downloadState.error != null) Color(0xFFF87171) else Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (downloadState.isRunning) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadState.progressFraction },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${formatBytes(downloadState.bytesRead)} / ${formatBytes(downloadState.totalBytes)}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Play / Download Actions
                Button(
                    onClick = { onPlayExternal(bestInstalled?.installedPackage) },
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color(0xFF0B1020)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val label = when {
                        localFile == null && bestInstalled != null ->
                            "Download & Play in ${bestInstalled.target.displayName}"
                        localFile != null && bestInstalled != null ->
                            "Play in ${bestInstalled.target.displayName}"
                        localFile == null ->
                            "Download & Launch External Emulator"
                        else ->
                            "Launch in External Emulator"
                    }
                    Text(text = label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (localFile == null) {
                        OutlinedButton(
                            onClick = onDownload,
                            enabled = !isDownloading,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download ROM", color = Color(0xFFF8FAFC))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDeleteLocal,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Local Copy", color = Color(0xFFF87171))
                        }
                    }

                    OutlinedButton(
                        onClick = onPlayBuiltInWeb,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF4ADE80))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Built-In Player", color = Color(0xFFF8FAFC))
                    }
                }

                if (matchingEmulators.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Compatible Android Emulators",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchingEmulators.forEach { emu ->
                            AssistChip(
                                onClick = {
                                    if (emu.isInstalled) {
                                        onPlayExternal(emu.installedPackage)
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (emu.isInstalled) "${emu.target.displayName} (Ready)" else emu.target.displayName,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (emu.isInstalled) {
                                    {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4ADE80),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = if (emu.isInstalled) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }

                if (rom.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Overview",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rom.summary,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }

                if (rom.crcHash.isNotBlank() || rom.md5Hash.isNotBlank() || rom.sha1Hash.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "DAT Verification Hashes",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF0B1020),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (rom.crcHash.isNotBlank()) {
                                HashLine("CRC32", rom.crcHash)
                            }
                            if (rom.md5Hash.isNotBlank()) {
                                HashLine("MD5  ", rom.md5Hash)
                            }
                            if (rom.sha1Hash.isNotBlank()) {
                                HashLine("SHA-1", rom.sha1Hash)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgePill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HashLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
