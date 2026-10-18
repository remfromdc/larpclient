using System.Text.Json;
using LarpClient;

var root = Path.Combine(Path.GetTempPath(), "LarpClient-checks-" + Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
var service = new ProfileService(Path.Combine(root, "minecraft"), Path.Combine(root, "data"));
void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
try
{
    foreach (var version in ProfileService.Versions)
    {
        var folder = service.Prepare(version);
        Check(Directory.Exists(Path.Combine(folder, "mods")), "Instance not prepared.");
        Check(!Directory.Exists(service.MinecraftDirectory), "Preparation created official launcher data.");
    }
    Directory.CreateDirectory(service.MinecraftDirectory);
    var official = Path.Combine(service.MinecraftDirectory, "launcher_profiles.json");
    File.WriteAllText(official, "broken JSON owned by another application");
    var first = Guid.NewGuid().ToString("N"); var second = Guid.NewGuid().ToString("N");
    var a = service.Prepare("26.2", first, "Test A"); var b = service.Prepare("26.2", second, "Test B");
    Check(a != b, "Instance folders must be isolated.");
    File.WriteAllText(Path.Combine(a, "options.txt"), "user settings");
    Check(service.Prepare("26.2", first, "Nouveau nom") == a, "Repeated preparation changed folder.");
    Check(File.ReadAllText(Path.Combine(a, "options.txt")) == "user settings", "User settings lost.");
    using var json = JsonDocument.Parse(File.ReadAllText(Path.Combine(a, "larp-instance.json")));
    Check(json.RootElement.GetProperty("name").GetString() == "Nouveau nom", "Name not updated.");
    Check(File.ReadAllText(official) == "broken JSON owned by another application", "Official configuration modified.");
    foreach (var invalid in new[] { "../invalid", "1.14" })
    {
        var rejected = false; try { service.Prepare(invalid); } catch (ArgumentException) { rejected = true; }
        Check(rejected, "Invalid version accepted.");
    }
    var invalidId = false;
    try { service.Prepare("26.2", "../outside"); } catch (ArgumentException) { invalidId = true; }
    Check(invalidId, "Unsafe instance ID accepted.");
    Console.WriteLine("PASS: fresh setup without official launcher, three versions, repeat preparation, isolated worlds/settings, invalid paths, untouched official files.");
}
finally
{
    var resolved = Path.GetFullPath(root);
    if (Path.GetDirectoryName(resolved) == Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar)
        && Path.GetFileName(resolved).StartsWith("LarpClient-checks-")) Directory.Delete(resolved, true);
}
