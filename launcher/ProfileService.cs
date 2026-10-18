using System.IO;
using System.Text.Json;

namespace LarpClient;

public sealed class ProfileService
{
    public static readonly IReadOnlyList<string> Versions = Array.AsReadOnly(new[] { "1.8.9", "1.21.11", "26.2" });
    public string MinecraftDirectory { get; }
    public string DataDirectory { get; }

    public ProfileService(string? minecraftDirectory = null, string? dataDirectory = null)
    {
        MinecraftDirectory = minecraftDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
        DataDirectory = dataDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "LarpClient");
    }

    public string InstanceDirectory(string version, string? instanceId = null)
    {
        if (!Versions.Contains(version)) throw new ArgumentException("Version non prise en charge.");
        if (!string.IsNullOrEmpty(instanceId))
        {
            if (!Guid.TryParseExact(instanceId, "N", out _)) throw new ArgumentException("Identifiant d’instance invalide.");
            return Path.Combine(DataDirectory, "instances", version, instanceId);
        }
        return Path.Combine(DataDirectory, "instances", version);
    }

    public string Prepare(string version, string? instanceId = null, string? displayName = null)
    {
        var instance = InstanceDirectory(version, instanceId);
        Directory.CreateDirectory(instance);
        Directory.CreateDirectory(Path.Combine(instance, "mods"));
        var file = Path.Combine(instance, "larp-instance.json");
        var temporary = file + ".tmp";
        try
        {
            File.WriteAllText(temporary, JsonSerializer.Serialize(new { version, name = displayName ?? version }, new JsonSerializerOptions { WriteIndented = true }));
            File.Move(temporary, file, true);
        }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
        return instance;
    }
}
