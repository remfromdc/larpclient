using System.IO;
using System.Text.Json;

namespace LarpClient;

public sealed class GameInstance
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Version { get; set; } = "26.2";
    public bool IsCustom => Id.Length > 0;
    public string Subtitle => $"Minecraft {Version} · Java Edition";
}

public sealed class LauncherState
{
    public List<GameInstance> Instances { get; set; } = [
        new() { Name = "Classique", Version = "1.8.9" },
        new() { Name = "Moderne", Version = "1.21.11" },
        new() { Name = "Récente", Version = "26.2" }
    ];
    public string SelectedVersion { get; set; } = "26.2";
    public string SelectedId { get; set; } = "";
    public bool Animations { get; set; } = true;
    public bool RotateSkin { get; set; } = true;
    public bool TransitionBlur { get; set; } = true;
    public string? SkinPath { get; set; }
    private static string FilePath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "LarpClient", "launcher.json");

    public static LauncherState Load()
    {
        try
        {
            var state = JsonSerializer.Deserialize<LauncherState>(File.ReadAllText(FilePath));
            if (state is null || state.Instances is null || state.Instances.Count == 0) return new();
            state.Instances = state.Instances.Where(i => i is not null && ProfileService.Versions.Contains(i.Version)
                && (i.Id.Length == 0 || Guid.TryParseExact(i.Id, "N", out _)) && !string.IsNullOrWhiteSpace(i.Name)).ToList();
            foreach (var instance in state.Instances.Where(i => !i.IsCustom))
                instance.Name = instance.Name switch { "Classic" => "Classique", "Modern" => "Moderne", "Latest" => "Récente", _ => instance.Name };
            return state.Instances.Count > 0 ? state : new();
        }
        catch { return new(); }
    }

    public void Save()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
        var temporary = FilePath + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        File.Move(temporary, FilePath, true);
    }
}
