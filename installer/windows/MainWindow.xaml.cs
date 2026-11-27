using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Windows;

namespace MoveWeightTvSetup;

public partial class MainWindow : Window
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(4) };
    private readonly string _workDir = Path.Combine(Path.GetTempPath(), "MoveWeight-TV-Setup");

    public MainWindow()
    {
        InitializeComponent();
        Http.DefaultRequestHeaders.UserAgent.ParseAdd("MoveWeight-TV-Setup/1.0");
        Directory.CreateDirectory(_workDir);
        Loaded += async (_, _) => await ScanLanAsync();
    }

    private void Log(string msg)
    {
        Dispatcher.Invoke(() =>
        {
            LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {msg}{Environment.NewLine}");
            LogBox.ScrollToEnd();
        });
    }

    private void SetProgress(int pct, string status)
    {
        Dispatcher.Invoke(() =>
        {
            ProgressBar.Value = Math.Clamp(pct, 0, 100);
            PercentLabel.Text = $"{Math.Clamp(pct, 0, 100)}%";
            StatusLabel.Text = status;
        });
    }

    private async void ScanButton_Click(object sender, RoutedEventArgs e)
    {
        await ScanLanAsync();
    }

    private async Task ScanLanAsync()
    {
        ScanButton.IsEnabled = false;
        SetProgress(5, "Scanning your Wi-Fi for Android TV / Onn 4K boxes...");
        Log("Scanning local network for Google TV / Android TV boxes...");

        var found = new List<string>();
        try
        {
            var subnet = GetLocalSubnetPrefix();
            if (!string.IsNullOrEmpty(subnet))
            {
                var tasks = Enumerable.Range(1, 254).Select(async i =>
                {
                    var ip = $"{subnet}.{i}";
                    try
                    {
                        using var tcp = new TcpClient();
                        var conn = tcp.ConnectAsync(ip, 8008);
                        if (await Task.WhenAny(conn, Task.Delay(180)) == conn && tcp.Connected)
                        {
                            string name = "Android TV";
                            try
                            {
                                using var cts = new CancellationTokenSource(1200);
                                var json = await Http.GetStringAsync($"http://{ip}:8008/setup/eureka_info", cts.Token);
                                using var doc = JsonDocument.Parse(json);
                                if (doc.RootElement.TryGetProperty("name", out var n) && !string.IsNullOrWhiteSpace(n.GetString()))
                                    name = n.GetString()!;
                            }
                            catch { }
                            lock (found) found.Add($"{ip} — {name}");
                        }
                    }
                    catch { }
                });
                await Task.WhenAll(tasks);
            }
        }
        catch (Exception ex)
        {
            Log($"Scan note: {ex.Message}");
        }

        Dispatcher.Invoke(() =>
        {
            DeviceCombo.Items.Clear();
            foreach (var item in found.OrderBy(x => x))
                DeviceCombo.Items.Add(item);
            if (found.Count > 0)
            {
                DeviceCombo.SelectedIndex = 0;
                Log($"Found {found.Count} TV box(es): {string.Join(", ", found)}");
                SetProgress(0, $"Found {found.Count} TV box(es). Ready to install!");
            }
            else
            {
                Log("No Cast/Google TV boxes auto-detected. Type your TV IP address above.");
                SetProgress(0, "Enter your TV box IP address above and click SET UP MY TV BOX.");
            }
            ScanButton.IsEnabled = true;
        });
    }

    private static string? GetLocalSubnetPrefix()
    {
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up) continue;
            if (ni.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;
            foreach (var ua in ni.GetIPProperties().UnicastAddresses)
            {
                if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                var parts = ua.Address.ToString().Split('.');
                if (parts.Length == 4 && (parts[0] == "192" || parts[0] == "10" || parts[0] == "172"))
                    return $"{parts[0]}.{parts[1]}.{parts[2]}";
            }
        }
        return null;
    }

    private async void StartButton_Click(object sender, RoutedEventArgs e)
    {
        var rawDevice = DeviceCombo.Text.Trim();
        var ipMatch = Regex.Match(rawDevice, @"\b\d{1,3}(?:\.\d{1,3}){3}\b");
        if (!ipMatch.Success)
        {
            MessageBox.Show("Please select a TV box or enter a valid IP address (like 192.168.0.198).",
                "TV IP Needed", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        string deviceIp = ipMatch.Value;
        string rommRemote = RommRemoteBox.Text.Trim().TrimEnd('/');
        string rommLocal = RommLocalBox.Text.Trim().TrimEnd('/');
        string rommUser = RommUserBox.Text.Trim();
        string rommPass = RommPassBox.Password;

        var keepPkgs = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "org.moveweight.cartridge", "com.moveweight.stream", "com.phlox.tvwebbrowser",
            "com.nendo.argosy", "com.swordfish.lemuroid", "com.retroarch.ra32", "com.retroarch.aarch64"
        };
        if (ChkPlex.IsChecked == true) keepPkgs.Add("com.plexapp.android");
        if (ChkJellyfin.IsChecked == true) keepPkgs.Add("org.jellyfin.androidtv");
        if (ChkYouTube.IsChecked == true) keepPkgs.Add("com.google.android.youtube.tv");
        if (ChkNetflix.IsChecked == true) keepPkgs.Add("com.netflix.ninja");
        if (ChkAmazon.IsChecked == true) keepPkgs.Add("com.amazon.amazonvideo.livingroom");
        if (ChkParamount.IsChecked == true) keepPkgs.Add("com.cbs.ott");
        if (ChkDisney.IsChecked == true) keepPkgs.Add("com.disney.disneyplus");
        if (ChkHulu.IsChecked == true) keepPkgs.Add("com.hulu.livingroomplus");
        if (ChkMax.IsChecked == true) keepPkgs.Add("com.wbd.stream");
        if (ChkAppleTv.IsChecked == true) keepPkgs.Add("com.apple.atve.androidtv.appletv");

        bool installJellyfin = ChkJellyfin.IsChecked == true;

        StartButton.IsEnabled = false;
        ScanButton.IsEnabled = false;

        try
        {
            await RunFullSetupAsync(deviceIp, rommLocal, rommRemote, rommUser, rommPass, keepPkgs, installJellyfin);
            SetProgress(100, "ALL DONE! Grab your Xbox controller and enjoy!");
            MessageBox.Show(
                $"Your TV Box ({deviceIp}) is completely ready!\n\n" +
                "• Pair 1–4 Xbox Controllers in TV Settings → Remotes & Accessories\n" +
                "• In RetroArch: click both thumbsticks (L3+R3) to exit back to Cartridge\n" +
                "• Open Cartridge, Argosy, Lemuroid, Yarr.It, Plex, or Jellyfin!",
                "Setup Complete!", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            Log($"ERROR: {ex.Message}");
            SetProgress(0, $"Setup stopped: {ex.Message}");
            MessageBox.Show($"Setup encountered an error:\n\n{ex.Message}", "Setup Error",
                MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally
        {
            StartButton.IsEnabled = true;
            ScanButton.IsEnabled = true;
        }
    }

    private async Task RunFullSetupAsync(
        string deviceIp, string rommLocal, string rommRemote, string rommUser, string rommPass,
        HashSet<string> keepPkgs, bool installJellyfin)
    {
        // 1. Ensure ADB
        SetProgress(8, "Checking Android Platform Tools (ADB)...");
        string adb = await EnsureAdbAsync();
        string target = $"{deviceIp}:5555";

        // 2. Connect ADB
        SetProgress(14, $"Connecting to {target}...");
        Log($"Connecting ADB to {target}...");
        await RunProcessAsync(adb, $"connect {target}");
        await Task.Delay(1500);

        var state = (await RunProcessAsync(adb, $"-s {target} get-state")).Trim();
        if (!state.Contains("device"))
        {
            Dispatcher.Invoke(() => MessageBox.Show(
                "Look at your TV screen now!\n\nSelect 'Always allow from this computer' → 'Allow', then click OK here.",
                "Allow Connection on TV", MessageBoxButton.OK, MessageBoxImage.Information));
            await RunProcessAsync(adb, $"connect {target}");
            await Task.Delay(1500);
            state = (await RunProcessAsync(adb, $"-s {target} get-state")).Trim();
            if (!state.Contains("device"))
                throw new Exception("Could not connect to the TV box. Make sure USB Debugging is enabled in Developer Options.");
        }

        string model = (await RunProcessAsync(adb, $"-s {target} shell getprop ro.product.model")).Trim();
        string abi = (await RunProcessAsync(adb, $"-s {target} shell getprop ro.product.cpu.abilist")).Trim();
        bool is64 = abi.Contains("arm64-v8a", StringComparison.OrdinalIgnoreCase);
        Log($"Connected to {model} (ABI: {abi}, 64-bit: {is64})");

        // Disable Android 14 Play Protect ADB block
        await RunProcessAsync(adb, $"-s {target} shell \"settings put global verifier_verify_adb_installs 0; settings put global package_verifier_enable 0\"");

        // 3. Debloat
        SetProgress(24, "Removing bloatware & speeding up TV animations...");
        string[] bloatList =
        [
            "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv",
            "com.bamnetworks.mobile.android.gameday.atbat", "com.cbs.ott",
            "com.crunchyroll.crunchyroid", "com.discovery.discoveryplus.mobile",
            "com.disney.disneyplus", "com.espn.score_center", "com.gotv.nflgamecenter.us.lite",
            "com.hulu.livingroomplus", "com.nbaimd.gametime.nba2011", "com.netflix.ninja",
            "com.netflix.tokenmanager", "com.peacocktv.peacockandroid", "com.spotify.tv.android",
            "com.tubitv", "com.wbd.stream", "com.google.android.youtube.tvunplugged",
            "com.google.android.youtube.tvmusic", "com.google.android.play.games",
            "com.google.android.feedback", "com.google.android.marvin.talkback",
            "com.google.android.partnersetup", "com.android.tv.feedbackconsent",
            "com.android.adservices.api", "com.android.ondevicepersonalization.services",
            "com.android.federatedcompute.services", "com.android.dreams.basic",
            "com.android.printspooler", "com.onn.updatenotification", "com.google.android.tvrecommendations"
        ];
        var installedOut = await RunProcessAsync(adb, $"-s {target} shell pm list packages");
        var installed = installedOut.Split('\n').Select(l => l.Trim().Replace("package:", "")).ToHashSet();
        int removed = 0;
        foreach (var pkg in bloatList)
        {
            if (keepPkgs.Contains(pkg) || !installed.Contains(pkg)) continue;
            await RunProcessAsync(adb, $"-s {target} shell pm uninstall --user 0 {pkg}");
            removed++;
        }
        await RunProcessAsync(adb, $"-s {target} shell \"settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5\"");
        Log($"Debloated {removed} bloat packages and sped up animations.");

        // 4. Sideload Apps
        SetProgress(35, "Installing Yarr.It TV & TV Bro browser...");
        await DownloadAndInstallApkAsync(adb, target, "Yarr.It TV", "https://github.com/BlizzHacker/yarr-it/releases/download/v1.0.1/Yarr.It-tv-1.0.1.apk", "yarrit-tv.apk");

        var tvbroUrl = await GetLatestGitHubApkAsync("truefedex/tv-bro", @"\.apk$");
        if (tvbroUrl != null) await DownloadAndInstallApkAsync(adb, target, "TV Bro Browser", tvbroUrl, "tvbro.apk");

        if (installJellyfin)
        {
            SetProgress(46, "Installing Jellyfin Android TV...");
            var jfUrl = await GetLatestGitHubApkAsync("jellyfin/jellyfin-androidtv", @"release\.apk$");
            if (jfUrl != null) await DownloadAndInstallApkAsync(adb, target, "Jellyfin TV", jfUrl, "jellyfin-tv.apk");
        }

        SetProgress(56, "Installing Argosy Launcher & Lemuroid (Zero-Core Emulator)...");
        var argosyUrl = await GetLatestGitHubApkAsync("rommapp/argosy-launcher", is64 ? @"arm64\.apk$" : @"arm32\.apk$");
        if (argosyUrl != null) await DownloadAndInstallApkAsync(adb, target, "Argosy Launcher", argosyUrl, "argosy.apk");

        var lemUrl = await GetLatestGitHubApkAsync("Swordfish90/Lemuroid", @"\.apk$");
        if (lemUrl != null) await DownloadAndInstallApkAsync(adb, target, "Lemuroid (Zero-Core)", lemUrl, "lemuroid.apk");

        var mlUrl = await GetLatestGitHubApkAsync("moonlight-stream/moonlight-android", @"\.apk$");
        if (mlUrl != null) await DownloadAndInstallApkAsync(adb, target, "Moonlight Game Streaming", mlUrl, "moonlight.apk");

        SetProgress(68, "Installing Cartridge for Android TV...");
        var cartUrl = await GetLatestGitHubApkAsync("BlizzHacker/Cartridge", @"\.apk$");
        if (cartUrl != null) await DownloadAndInstallApkAsync(adb, target, "Cartridge", cartUrl, "cartridge.apk");

        SetProgress(78, "Installing RetroArch (4P Xbox + Netplay)...");
        string raPkg = is64 ? "com.retroarch.aarch64" : "com.retroarch.ra32";
        string raApk = is64 ? "RetroArch_aarch64.apk" : "RetroArch_ra32.apk";
        var raHtml = await Http.GetStringAsync("https://buildbot.libretro.com/stable/");
        var raVer = Regex.Matches(raHtml, @"href=""/stable/(\d+\.\d+\.\d+)/""")
            .Select(m => Version.Parse(m.Groups[1].Value))
            .OrderByDescending(v => v).First().ToString();
        await DownloadAndInstallApkAsync(adb, target, $"RetroArch {raVer}", $"https://buildbot.libretro.com/stable/{raVer}/android/{raApk}", raApk);

        // 5. Deploy 4P Xbox + Netplay RetroArch config
        SetProgress(88, "Configuring 4-Player Xbox Controllers & Netplay...");
        string cfgPath = Path.Combine(_workDir, "retroarch.cfg");
        File.WriteAllText(cfgPath, DefaultRetroArchConfig(is64));
        await RunProcessAsync(adb, $"-s {target} shell \"appops set {raPkg} MANAGE_EXTERNAL_STORAGE allow; mkdir -p /sdcard/Android/data/{raPkg}/files /sdcard/Download/Cartridge\"");
        await RunProcessAsync(adb, $"-s {target} push \"{cfgPath}\" /sdcard/Android/data/{raPkg}/files/retroarch.cfg");
        Log("4-Player Xbox Controller + Netplay config deployed.");

        // 6. Optional RomM Sign-in & Auto-Provision Cartridge
        SetProgress(94, "Connecting Cartridge to your RomM library...");
        string token = "";
        if (!string.IsNullOrWhiteSpace(rommUser) && !string.IsNullOrWhiteSpace(rommPass))
        {
            string authBase = !string.IsNullOrWhiteSpace(rommLocal) ? rommLocal : rommRemote;
            token = await TrySignInRommAsync(authBase, rommUser, rommPass)
                 ?? await TrySignInRommAsync(rommRemote, rommUser, rommPass)
                 ?? "";
        }

        if (!string.IsNullOrWhiteSpace(rommLocal) || !string.IsNullOrWhiteSpace(rommRemote))
        {
            var profiles = JsonSerializer.Serialize(new[]
            {
                new { id = "home_romm", name = "Home RomM", localUrl = rommLocal, remoteUrl = rommRemote, token, authMode = string.IsNullOrEmpty(token) ? "password" : "token" }
            });
            string amCmd = $"am force-stop org.moveweight.cartridge; am start -n org.moveweight.cartridge/.MainActivity " +
                           $"--es server_name \"Home RomM\" --es local_url \"{rommLocal}\" --es remote_url \"{rommRemote}\" " +
                           $"--es auth_token \"{token}\" --es profiles_json '{profiles}'";
            await RunProcessAsync(adb, $"-s {target} shell \"{amCmd.Replace("\"", "\\\"")}\"");
            Log("Cartridge launched and connected to your RomM library!");
        }
    }

    private async Task<string?> TrySignInRommAsync(string baseUrl, string user, string pass)
    {
        if (string.IsNullOrWhiteSpace(baseUrl)) return null;
        try
        {
            using var content = new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["grant_type"] = "password",
                ["username"] = user,
                ["password"] = pass,
                ["scope"] = "me.read roms.read platforms.read assets.read assets.write collections.read"
            });
            var resp = await Http.PostAsync($"{baseUrl}/api/token", content);
            if (!resp.IsSuccessStatusCode) return null;
            using var doc = JsonDocument.Parse(await resp.Content.ReadAsStringAsync());
            if (doc.RootElement.TryGetProperty("access_token", out var tok))
            {
                Log($"Signed in to RomM ({baseUrl}) as {user}!");
                return tok.GetString();
            }
        }
        catch { }
        return null;
    }

    private async Task<string?> GetLatestGitHubApkAsync(string repo, string regexPattern)
    {
        var json = await Http.GetStringAsync($"https://api.github.com/repos/{repo}/releases/latest");
        using var doc = JsonDocument.Parse(json);
        foreach (var asset in doc.RootElement.GetProperty("assets").EnumerateArray())
        {
            var name = asset.GetProperty("name").GetString() ?? "";
            if (Regex.IsMatch(name, regexPattern, RegexOptions.IgnoreCase))
                return asset.GetProperty("browser_download_url").GetString();
        }
        return null;
    }

    private async Task DownloadAndInstallApkAsync(string adb, string target, string label, string url, string fileName)
    {
        string localPath = Path.Combine(_workDir, fileName);
        if (!File.Exists(localPath))
        {
            Log($"Downloading {label}...");
            var bytes = await Http.GetByteArrayAsync(url);
            await File.WriteAllBytesAsync(localPath, bytes);
        }
        Log($"Installing {label} onto TV...");
        await RunProcessAsync(adb, $"-s {target} push \"{localPath}\" /data/local/tmp/{fileName}");
        var res = await RunProcessAsync(adb, $"-s {target} shell \"pm install -r --bypass-low-target-sdk-block /data/local/tmp/{fileName} && rm -f /data/local/tmp/{fileName}\"");
        Log($"  {label}: {(res.Contains("Success") ? "Installed!" : res.Trim())}");
    }

    private async Task<string> EnsureAdbAsync()
    {
        string[] candidates =
        [
            @"C:\Android\sdk\platform-tools\adb.exe",
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Android\Sdk\platform-tools\adb.exe"),
            Path.Combine(_workDir, @"platform-tools\adb.exe")
        ];
        foreach (var c in candidates)
            if (File.Exists(c)) return c;

        Log("Downloading official Android Platform Tools (ADB)...");
        string zipPath = Path.Combine(_workDir, "platform-tools.zip");
        var bytes = await Http.GetByteArrayAsync("https://dl.google.com/android/repository/platform-tools-latest-windows.zip");
        await File.WriteAllBytesAsync(zipPath, bytes);
        ZipFile.ExtractToDirectory(zipPath, _workDir, overwriteFiles: true);
        File.Delete(zipPath);
        return Path.Combine(_workDir, @"platform-tools\adb.exe");
    }

    private static async Task<string> RunProcessAsync(string fileName, string args)
    {
        var psi = new ProcessStartInfo(fileName, args)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        using var proc = Process.Start(psi) ?? throw new Exception($"Failed to start {fileName}");
        var stdout = await proc.StandardOutput.ReadToEndAsync();
        var stderr = await proc.StandardError.ReadToEndAsync();
        await proc.WaitForExitAsync();
        return stdout + stderr;
    }

    private static string DefaultRetroArchConfig(bool is64)
    {
        string arch = is64 ? "arm64-v8a" : "armeabi-v7a";
        return $"""
            video_driver = "gl"
            video_threaded = "true"
            video_vsync = "true"
            video_fullscreen = "true"
            menu_driver = "ozone"
            ozone_menu_color_theme = "1"
            audio_driver = "opensl"
            input_driver = "android"
            input_joypad_driver = "android"
            input_autodetect_enable = "true"
            input_max_users = "4"
            all_users_control_menu = "true"
            input_menu_toggle_gamepad_combo = "4"
            input_quit_gamepad_combo = "2"
            quit_press_twice = "false"
            input_player1_analog_dpad_mode = "1"
            input_player2_analog_dpad_mode = "1"
            input_player3_analog_dpad_mode = "1"
            input_player4_analog_dpad_mode = "1"
            input_overlay_enable = "false"
            netplay_use_mitm_server = "true"
            netplay_mitm_server = "nyc"
            netplay_nat_traversal = "true"
            netplay_allow_slaves = "true"
            core_updater_buildbot_cores_url = "https://buildbot.libretro.com/nightly/android/latest/{arch}/"
            """;
    }
}
