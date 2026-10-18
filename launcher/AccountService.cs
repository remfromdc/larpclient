using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Windows.Media.Imaging;

namespace LarpClient;

public record JavaAccount(string Name, string Id);
public record SkinTexture(BitmapSource Image, bool Slim);

public static class AccountService
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(12) };

    // Optional first-run preview only. Login and launch use LarpClient's own Microsoft session.
    public static JavaAccount? ReadAccount(string minecraftDirectory)
    {
        foreach (var name in new[] { "launcher_accounts_microsoft_store.json", "launcher_accounts.json" })
        {
            try
            {
                var file = Path.Combine(minecraftDirectory, name);
                if (!File.Exists(file)) continue;
                using var doc = JsonDocument.Parse(File.ReadAllText(file));
                var root = doc.RootElement;
                if (!root.TryGetProperty("accounts", out var accounts) || accounts.ValueKind != JsonValueKind.Object) continue;
                var active = root.TryGetProperty("activeAccountLocalId", out var id) ? id.GetString() : null;
                var ordered = accounts.EnumerateObject().OrderByDescending(a => a.Name == active);
                foreach (var entry in ordered)
                {
                    if (!entry.Value.TryGetProperty("minecraftProfile", out var profile) || profile.ValueKind != JsonValueKind.Object) continue;
                    if (!profile.TryGetProperty("name", out var n) || !profile.TryGetProperty("id", out var uuid)) continue;
                    if (n.GetString() is { Length: > 0 } displayName && Guid.TryParse(uuid.GetString(), out var parsed))
                        return new(displayName, parsed.ToString("N"));
                }
            }
            catch (Exception ex) when (ex is IOException or JsonException or InvalidOperationException or UnauthorizedAccessException) { }
        }
        return null;
    }

    public static async Task<SkinTexture?> FetchSkinAsync(JavaAccount account, CancellationToken cancellationToken)
    {
        // Public Mojang endpoints only. Never send an access token or an email address.
        using var response = await Http.GetAsync($"https://sessionserver.mojang.com/session/minecraft/profile/{account.Id}", cancellationToken);
        response.EnsureSuccessStatusCode();
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken));
        foreach (var property in doc.RootElement.GetProperty("properties").EnumerateArray())
        {
            if (property.GetProperty("name").GetString() != "textures") continue;
            using var textures = JsonDocument.Parse(Encoding.UTF8.GetString(Convert.FromBase64String(property.GetProperty("value").GetString()!)));
            if (!textures.RootElement.GetProperty("textures").TryGetProperty("SKIN", out var skin)) return null;
            if (!Uri.TryCreate(skin.GetProperty("url").GetString(), UriKind.Absolute, out var uri)
                || uri.Host != "textures.minecraft.net" || !uri.AbsolutePath.StartsWith("/texture/", StringComparison.Ordinal)) return null;
            var secure = new UriBuilder(uri) { Scheme = "https", Port = -1 }.Uri;
            using var result = await Http.GetAsync(secure, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            result.EnsureSuccessStatusCode();
            using var input = await result.Content.ReadAsStreamAsync(cancellationToken);
            using var memory = new MemoryStream();
            var buffer = new byte[8192];
            int read;
            while ((read = await input.ReadAsync(buffer, cancellationToken)) > 0)
            {
                if (memory.Length + read > 1024 * 1024) throw new InvalidDataException("Skin trop volumineux.");
                await memory.WriteAsync(buffer.AsMemory(0, read), cancellationToken);
            }
            memory.Position = 0;
            var bitmap = DecodeSkin(memory);
            var slim = skin.TryGetProperty("metadata", out var meta) && meta.TryGetProperty("model", out var model) && model.GetString() == "slim";
            return new(bitmap, slim);
        }
        return null;
    }

    public static BitmapSource DecodeSkin(Stream stream)
    {
        var decoder = BitmapDecoder.Create(stream, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
        var image = decoder.Frames[0];
        if (image.PixelWidth != 64 || (image.PixelHeight != 32 && image.PixelHeight != 64))
            throw new InvalidDataException("Choisis un skin Minecraft PNG de 64 × 64 ou 64 × 32 pixels.");
        image.Freeze();
        return image;
    }
}
