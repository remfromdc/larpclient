using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using CmlLib.Core.Auth;
using Microsoft.Identity.Client;

namespace LarpClient;

public sealed class MicrosoftLoginService
{
    public const string ApplicationApprovalUrl="https://forms.cloud.microsoft/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR-ajEQ1td1ROpz00KtS8Gd5UNVpPTkVLNFVROVQxNkdRMEtXVjNQQjdXVC4u";
    private readonly string directory;
    private readonly HttpClient http;
    private IPublicClientApplication? application;
    private readonly SemaphoreSlim gate = new(1, 1);
    private static readonly string[] Scopes = ["XboxLive.signin"];
    private static readonly JsonSerializerOptions XboxJson = new() { PropertyNamingPolicy = null };
    public MicrosoftLoginService(string directory, HttpClient? http = null)
    {
        this.directory = directory;
        this.http = http ?? new HttpClient { Timeout = TimeSpan.FromSeconds(45) };
    }
    public string ClientId
    {
        get
        {
            var value = Environment.GetEnvironmentVariable("LARPCLIENT_MICROSOFT_CLIENT_ID");
            foreach (var folder in new[] { AppContext.BaseDirectory, directory })
            {
                if (!string.IsNullOrWhiteSpace(value)) break;
                var path = Path.Combine(folder, "larpclient.auth.json");
                if (!File.Exists(path)) continue;
                using var json = JsonDocument.Parse(File.ReadAllText(path));
                value = json.RootElement.GetProperty("clientId").GetString();
            }
            if (string.IsNullOrWhiteSpace(value))
            {
                using var preset = typeof(MicrosoftLoginService).Assembly.GetManifestResourceStream("LarpClient.larpclient.auth.json");
                if (preset is not null)
                {
                    using var json = JsonDocument.Parse(preset);
                    value = json.RootElement.GetProperty("clientId").GetString();
                }
            }
            if (!Guid.TryParse(value, out var id) || id == Guid.Empty)
                throw new InvalidOperationException("La connexion Microsoft de LarpClient n’est pas encore configurée. Le développeur doit renseigner son Client ID dans larpclient.auth.json (voir MICROSOFT-SETUP.md).");
            return id.ToString();
        }
    }
    public JavaAccount? ReadProfile()
    {
        try { return JsonSerializer.Deserialize<JavaAccount>(File.ReadAllText(Path.Combine(directory, "java-profile.json"))); }
        catch (Exception ex) when (ex is IOException or JsonException or UnauthorizedAccessException) { return null; }
    }
    public bool AllowLegacyPreview => !File.Exists(Path.Combine(directory, "signed-out"));
    private IPublicClientApplication Application()
    {
        if (application is not null) return application;
        application = PublicClientApplicationBuilder.Create(ClientId)
            .WithAuthority("https://login.microsoftonline.com/consumers")
            .WithRedirectUri("http://localhost").Build();
        var cache = Path.Combine(directory, "microsoft-session.bin");
        application.UserTokenCache.SetBeforeAccess(args =>
        {
            if (!File.Exists(cache)) return;
            try { args.TokenCache.DeserializeMsalV3(ProtectedData.Unprotect(File.ReadAllBytes(cache), null, DataProtectionScope.CurrentUser)); }
            catch (CryptographicException) { /* A cache from another Windows account cannot be reused. */ }
        });
        application.UserTokenCache.SetAfterAccess(args =>
        {
            if (!args.HasStateChanged) return;
            Directory.CreateDirectory(directory);
            var encrypted = ProtectedData.Protect(args.TokenCache.SerializeMsalV3(), null, DataProtectionScope.CurrentUser);
            var temporary = cache + ".tmp";
            try { File.WriteAllBytes(temporary, encrypted); File.Move(temporary, cache, true); }
            finally { if (File.Exists(temporary)) File.Delete(temporary); }
        });
        return application;
    }
    public async Task<MSession> AuthenticateAsync(bool switchAccount, CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            var app = Application();
            AuthenticationResult? result = null;
            var account = (await app.GetAccountsAsync()).FirstOrDefault();
            if (!switchAccount && account is not null)
            {
                try { result = await app.AcquireTokenSilent(Scopes, account).ExecuteAsync(ct); }
                catch (MsalUiRequiredException) { }
            }
            result ??= await app.AcquireTokenInteractive(Scopes).WithUseEmbeddedWebView(false)
                .WithPrompt(Prompt.SelectAccount).ExecuteAsync(ct);
            var session = await ExchangeAsync(result.AccessToken, ct);
            Directory.CreateDirectory(directory);
            var signedOut = Path.Combine(directory, "signed-out");
            if (File.Exists(signedOut)) File.Delete(signedOut);
            var path = Path.Combine(directory, "java-profile.json");
            var temporary = path + ".tmp";
            try
            {
                File.WriteAllText(temporary, JsonSerializer.Serialize(new JavaAccount(session.Username!, session.UUID!)));
                File.Move(temporary, path, true);
            }
            finally { if (File.Exists(temporary)) File.Delete(temporary); }
            return session;
        }
        catch (MsalException ex) when (ex.ErrorCode is "authentication_canceled" or "access_denied")
        { throw new OperationCanceledException("Connexion annulée.", ex, ct); }
        catch (MsalException) { throw new InvalidOperationException("La connexion Microsoft a échoué. Vérifie la connexion réseau et la configuration de l’application LarpClient."); }
        finally { gate.Release(); }
    }
    // For diagnosis / session restoration only. This never opens a browser or falls back to interactive sign-in.
    public async Task<MSession?> TryAuthenticateSilentlyAsync(CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            var app=Application();var cached=(await app.GetAccountsAsync()).FirstOrDefault();
            if(cached is null)return null;
            AuthenticationResult result;
            try { result=await app.AcquireTokenSilent(Scopes,cached).ExecuteAsync(ct); }
            catch(MsalUiRequiredException) { return null; }
            return await ExchangeAsync(result.AccessToken,ct);
        }
        catch(MsalException) { throw new InvalidOperationException("La session Microsoft enregistrée ne peut pas être restaurée sans une nouvelle connexion."); }
        finally { gate.Release(); }
    }
    // Kept separate from the browser flow so the Xbox / Minecraft exchange can be verified with HTTP fixtures.
    public async Task<MSession> ExchangeAsync(string microsoftToken, CancellationToken ct)
    {
        using var xbox = await Post("https://user.auth.xboxlive.com/user/authenticate", new
        {
            Properties = new { AuthMethod = "RPS", SiteName = "user.auth.xboxlive.com", RpsTicket = "d=" + microsoftToken },
            RelyingParty = "http://auth.xboxlive.com", TokenType = "JWT"
        }, ct);
        using var xsts = await Post("https://xsts.auth.xboxlive.com/xsts/authorize", new
        {
            Properties = new { SandboxId = "RETAIL", UserTokens = new[] { xbox.RootElement.GetProperty("Token").GetString() } },
            RelyingParty = "rp://api.minecraftservices.com/", TokenType = "JWT"
        }, ct);
        var claim = xsts.RootElement.GetProperty("DisplayClaims").GetProperty("xui")[0];
        var hash = claim.GetProperty("uhs").GetString();
        using var minecraft = await Post("https://api.minecraftservices.com/authentication/login_with_xbox", new
        { identityToken = $"XBL3.0 x={hash};{xsts.RootElement.GetProperty("Token").GetString()}" }, ct);
        var token = minecraft.RootElement.GetProperty("access_token").GetString();
        if (string.IsNullOrWhiteSpace(token)) throw new InvalidDataException("Session Minecraft invalide.");
        using var request = new HttpRequestMessage(HttpMethod.Get, "https://api.minecraftservices.com/minecraft/profile");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        using var response = await http.SendAsync(request, ct);
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            SaveFailure(AuthenticationStage.JavaProfile,AuthenticationFailure.JavaProfileMissing,response.StatusCode);
            throw new MinecraftAuthenticationException(AuthenticationStage.JavaProfile,AuthenticationFailure.JavaProfileMissing,response.StatusCode,
                "Ce compte Microsoft ne possède pas de profil Minecraft Java actif.");
        }
        await RequireSuccess(response, ct);
        using var profile = await JsonDocument.ParseAsync(await response.Content.ReadAsStreamAsync(ct), cancellationToken: ct);
        var name = profile.RootElement.GetProperty("name").GetString();
        var id = profile.RootElement.GetProperty("id").GetString();
        if (string.IsNullOrWhiteSpace(name) || !Guid.TryParse(id, out var uuid)) throw new InvalidDataException("Profil Minecraft invalide.");
        return new MSession(name, token, uuid.ToString("N")) { UserType = "msa", Xuid = claim.TryGetProperty("xid", out var xid) ? xid.GetString() : null };
    }
    private async Task<JsonDocument> Post(string url, object body, CancellationToken ct)
    {
        using var response = await http.PostAsJsonAsync(url, body, XboxJson, ct);
        await RequireSuccess(response, ct);
        return await JsonDocument.ParseAsync(await response.Content.ReadAsStreamAsync(ct), cancellationToken: ct);
    }
    private void SaveFailure(AuthenticationStage stage,AuthenticationFailure failure,HttpStatusCode status)
    {
        try
        {
            Directory.CreateDirectory(directory);
            var path=Path.Combine(directory,"authentication-diagnostic.json");
            // Deliberately allowlist metadata. Never save tokens, request headers, raw bodies or account details.
            File.WriteAllText(path+".tmp",JsonSerializer.Serialize(new {Stage=stage.ToString(),Failure=failure.ToString(),HttpStatus=(int)status,Date=DateTimeOffset.UtcNow},new JsonSerializerOptions {WriteIndented=true}));
            File.Move(path+".tmp",path,true);
        }
        catch(Exception ex)when(ex is IOException or UnauthorizedAccessException) { }
    }
    private async Task RequireSuccess(HttpResponseMessage response, CancellationToken ct)
    {
        if (response.IsSuccessStatusCode) return;
        var endpoint=response.RequestMessage?.RequestUri;
        var stage=endpoint?.Host switch
        {
            "user.auth.xboxlive.com"=>AuthenticationStage.XboxLive,
            "xsts.auth.xboxlive.com"=>AuthenticationStage.XboxSecurity,
            _=>endpoint?.AbsolutePath=="/authentication/login_with_xbox"?AuthenticationStage.MinecraftLogin:AuthenticationStage.JavaProfile
        };
        SaveFailure(stage,AuthenticationFailure.AccessDenied,response.StatusCode);
        // Never display response bodies: they may contain tokens or account details.
        if (response.RequestMessage?.RequestUri?.Host == "xsts.auth.xboxlive.com")
        {
            using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(ct));
            var code = json.RootElement.TryGetProperty("XErr", out var error) ? error.GetInt64() : 0;
            throw new InvalidOperationException(code switch
            {
                2148916233 => "Crée d’abord un profil Xbox pour ce compte Microsoft.",
                2148916238 => "Le compte enfant doit être autorisé par son groupe familial Xbox.",
                _ => "Xbox a refusé la connexion. Vérifie les autorisations de ton compte."
            });
        }
        if (response.StatusCode == HttpStatusCode.Forbidden)
        {
            var invalidApplication=false;
            if(stage==AuthenticationStage.MinecraftLogin)
            {
                try
                {
                    var body=await response.Content.ReadAsStringAsync(ct);
                    if(body.Length<=16384)
                    {
                        using var json=JsonDocument.Parse(body);
                        invalidApplication=json.RootElement.TryGetProperty("errorMessage",out var error)
                            && error.ValueKind==JsonValueKind.String
                            && (error.GetString()?.Contains("Invalid app registration",StringComparison.OrdinalIgnoreCase)??false);
                    }
                }
                catch(JsonException) { }
            }
            if(invalidApplication)
            {
                SaveFailure(stage,AuthenticationFailure.ApplicationNotApproved,response.StatusCode);
                throw new MinecraftAuthenticationException(stage,AuthenticationFailure.ApplicationNotApproved,response.StatusCode,
                    "La connexion Microsoft et Xbox a réussi. Minecraft n’a pas encore autorisé l’application LarpClient. Son créateur doit demander l’approbation du Client ID auprès de Minecraft. Réessaie après confirmation de leur équipe.");
            }
            var message=stage switch
            {
                AuthenticationStage.XboxLive=>"Xbox Live a refusé la connexion à ton compte. Vérifie les restrictions de ton compte Microsoft / Xbox.",
                AuthenticationStage.JavaProfile=>"Minecraft a refusé la lecture de ton profil Java. Reconnecte ton compte puis réessaie.",
                _=>"Le service de connexion Minecraft a refusé l’accès. Le diagnostic local indique l’étape concernée ; ce refus ne suffit pas à confirmer un problème de Client ID."
            };
            throw new MinecraftAuthenticationException(stage,AuthenticationFailure.AccessDenied,response.StatusCode,message);
        }
        SaveFailure(stage,AuthenticationFailure.ServiceUnavailable,response.StatusCode);
        throw new MinecraftAuthenticationException(stage,AuthenticationFailure.ServiceUnavailable,response.StatusCode,$"Connexion Microsoft / Minecraft indisponible (HTTP {(int)response.StatusCode}).");
    }
    public async Task SignOutAsync(CancellationToken ct)
    {
        await gate.WaitAsync(ct);
        try
        {
            if (application is not null)
                foreach (var account in await application.GetAccountsAsync()) await application.RemoveAsync(account);
            foreach (var file in new[] { "microsoft-session.bin", "java-profile.json" })
            {
                var path = Path.Combine(directory, file); if (File.Exists(path)) File.Delete(path);
            }
            application = null;
            Directory.CreateDirectory(directory);
            File.WriteAllText(Path.Combine(directory, "signed-out"), "");
        }
        finally { gate.Release(); }
    }
}
