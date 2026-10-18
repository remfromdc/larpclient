using System.Diagnostics;
using System.IO;
using System.Net.Http;
using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.Installers;
using CmlLib.Core.ProcessBuilder;

namespace LarpClient;

public record GameProgress(string Message, double? Percent = null);

public sealed class MinecraftGameService(ProfileService profiles)
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(5) };
    // A lock file protects shared libraries / natives against two launcher windows installing at once.
    private FileStream AcquireInstallationLock()
    {
        Directory.CreateDirectory(profiles.DataDirectory);
        try { return new FileStream(Path.Combine(profiles.DataDirectory, "installation.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); }
        catch (IOException) { throw new InvalidOperationException("Une installation est déjà en cours dans une autre fenêtre LarpClient."); }
    }
    private MinecraftLauncher CreateLauncher()
    {
        // Shared real .minecraft resources, isolated saves/settings and Larp-owned natives.
        var path = new MinecraftPath(profiles.MinecraftDirectory);
        var parameters = MinecraftLauncherParameters.CreateDefault(path, Http);
        parameters.GameInstaller = new ParallelGameInstaller(2, 6, 256, Http);
        return new MinecraftLauncher(parameters);
    }
    public async Task InstallAsync(GameInstance instance, IProgress<GameProgress> progress, CancellationToken ct)
    {
        using var installLock = AcquireInstallationLock();
        var folder = profiles.Prepare(instance.Version, instance.Id, instance.Name);
        var launcher = CreateLauncher();
        progress.Report(new($"Préparation de Minecraft {instance.Version}…"));
        // Run checksums and archive work away from the animation/UI thread.
        await Task.Run(async () =>
        {
            var lastReport = Stopwatch.StartNew();
            var fileProgress = new InlineProgress<InstallerProgressChangedEventArgs>(value =>
            {
                if (lastReport.ElapsedMilliseconds < 100 && value.ProgressedTasks < value.TotalTasks) return;
                lastReport.Restart();
                var percent = value.TotalTasks > 0 ? 100d * value.ProgressedTasks / value.TotalTasks : (double?)null;
                progress.Report(new($"Installation de {instance.Version} · {percent.GetValueOrDefault():0} %", percent));
            });
            await launcher.InstallAsync(instance.Version, fileProgress, null, ct);
            ct.ThrowIfCancellationRequested();
            var version = await launcher.GetVersionAsync(instance.Version, ct);
            var java = launcher.GetJavaPath(version) ?? launcher.GetDefaultJavaPath();
            if (string.IsNullOrEmpty(java) || !File.Exists(java)) throw new InvalidOperationException("Le runtime Java requis n’a pas pu être installé. Relance l’installation.");
        }, ct);
        File.WriteAllText(Path.Combine(folder, ".installed"), instance.Version);
        progress.Report(new("Installation terminée", 100));
    }
    public async Task<Process> BuildProcessAsync(GameInstance instance, MSession session, string clientId, CancellationToken ct)
    {
        if (!session.CheckIsValid() || session.AccessToken == "access_token")
            throw new InvalidOperationException("Connecte ton compte Microsoft avant de jouer.");
        var folder = profiles.Prepare(instance.Version, instance.Id, instance.Name);
        return await Task.Run(async () =>
        {
            var launcher = CreateLauncher();
            var version = await launcher.GetVersionAsync(instance.Version, ct);
            // Extract into the instance to avoid deleting the official launcher's natives.
            var nativePath = new MinecraftPath(profiles.MinecraftDirectory) { Versions = Path.Combine(folder, ".versions") };
            var natives = launcher.NativeLibraryExtractor.Extract(nativePath, version, launcher.RulesContext);
            var process = launcher.BuildProcess(version, new MLaunchOption
            {
                Session = session, ClientId = clientId, NativesDirectory = natives,
                MaximumRamMb = 4096, MinimumRamMb = 512, GameLauncherName = "LarpClient", GameLauncherVersion = "1.0",
                ArgumentDictionary = new Dictionary<string, string> { ["game_directory"] = folder }
            });
            process.StartInfo.WorkingDirectory = folder;
            process.StartInfo.UseShellExecute = false;
            process.StartInfo.CreateNoWindow = true;
            // Do not capture command lines / game output; access tokens can appear there.
            ct.ThrowIfCancellationRequested();
            return process;
        }, ct);
    }
    private sealed class InlineProgress<T>(Action<T> report) : IProgress<T>
    { public void Report(T value) => report(value); }
}
