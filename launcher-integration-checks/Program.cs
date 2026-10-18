using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using CmlLib.Core.Auth;
using LarpClient;

if(args.Contains("--diagnose-cached-auth"))
{
    var storedLogin=new MicrosoftLoginService(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),"LarpClient"));
    try
    {
        var restored=await storedLogin.TryAuthenticateSilentlyAsync(CancellationToken.None);
        Console.WriteLine(restored is null?"Diagnostic : une connexion interactive est nécessaire ; aucun navigateur n’a été ouvert.":"Diagnostic : connexion Minecraft réussie avec la session propre à LarpClient.");
    }
    catch(MinecraftAuthenticationException ex) { Console.WriteLine(JsonSerializer.Serialize(new {Stage=ex.Stage.ToString(),Failure=ex.Failure.ToString(),HttpStatus=(int)ex.Status})); }
    catch(InvalidOperationException ex) { Console.WriteLine(ex.Message); }
    return;
}

void Check(bool value, string message) { if (!value) throw new Exception(message); }
var root = Path.Combine(Path.GetTempPath(), "LarpClient-integration-" + Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
try
{
    var handler = new FakeMicrosoft();
    var login = new MicrosoftLoginService(root, new HttpClient(handler));
    var session = await login.ExchangeAsync("fixture-microsoft-token", CancellationToken.None);
    Check(session.Username == "FixturePlayer" && session.UserType == "msa" && session.Xuid == "42", "Invalid authenticated session.");
    Check(handler.Calls == 4, "Authentication exchange incomplete.");
    Check(!Directory.GetFiles(root).Any(), "Exchange persisted unprotected tokens.");
    handler.Reject = HttpStatusCode.Forbidden;handler.RejectAt="/authentication/login_with_xbox";handler.InvalidApp=true;
    try { await login.ExchangeAsync("fixture-microsoft-token", CancellationToken.None); throw new Exception("403 accepted."); }
    catch (MinecraftAuthenticationException ex) { Check(ex.Failure==AuthenticationFailure.ApplicationNotApproved&&ex.Stage==AuthenticationStage.MinecraftLogin&&ex.Message.Contains("Client ID")&&!ex.Message.Contains("fixture-"), "App approval failure not classified safely."); }
    handler.RejectAt="/minecraft/profile";handler.InvalidApp=false;
    try { await login.ExchangeAsync("fixture-microsoft-token",CancellationToken.None); throw new Exception("Profile 403 accepted."); }
    catch(MinecraftAuthenticationException ex) {Check(ex.Failure==AuthenticationFailure.AccessDenied&&ex.Stage==AuthenticationStage.JavaProfile&&!ex.Message.Contains("Client ID"),"Profile rejection falsely classified as app approval.");}
    handler.RejectAt="/user/authenticate";
    try { await login.ExchangeAsync("fixture-microsoft-token",CancellationToken.None); throw new Exception("Xbox 403 accepted."); }
    catch(MinecraftAuthenticationException ex) {Check(ex.Stage==AuthenticationStage.XboxLive&&!ex.Message.Contains("Client ID"),"Xbox rejection falsely classified as app approval.");}
    handler.RejectAt="/minecraft/profile";
    handler.Reject = HttpStatusCode.NotFound;
    try { await login.ExchangeAsync("fixture-microsoft-token", CancellationToken.None); throw new Exception("Account without Java accepted."); }
    catch (InvalidOperationException ex) { Check(ex.Message.Contains("Java"), "Ownership failure not explained."); }
    using var canceled = new CancellationTokenSource(); canceled.Cancel();
    try { await login.ExchangeAsync("fixture-token", canceled.Token); throw new Exception("Cancellation ignored."); }
    catch (OperationCanceledException) { }
    var diagnostic=File.ReadAllText(Path.Combine(root,"authentication-diagnostic.json"));
    Check(!diagnostic.Contains("fixture-")&&!diagnostic.Contains("access_token")&&!diagnostic.Contains("identityToken"),"Authentication diagnostics exposed sensitive data.");
    Console.WriteLine("PASS: Microsoft -> Xbox -> XSTS -> Java profile, ownership failure, forbidden client, cancellation, no token persistence or exposure.");
    var profiles = new ProfileService(Path.Combine(root, "minecraft"), Path.Combine(root, "data"));
    var service = new MinecraftGameService(profiles);
    var instance = new GameInstance { Name = "Test propre", Version = "1.8.9" };
    try { await service.BuildProcessAsync(instance, MSession.CreateOfflineSession("NoAuth"), "fixture", CancellationToken.None); throw new Exception("Offline session accepted."); }
    catch (InvalidOperationException) { }
    Console.WriteLine("PASS: direct launch refuses placeholder / unauthenticated sessions.");
    if (args.Contains("--fresh-install"))
    {
        var progress = new Progress<GameProgress>(p => { if (p.Percent is null || p.Percent >= 100) Console.WriteLine(p.Message); });
        await service.InstallAsync(instance, progress, CancellationToken.None);
        var jar = Path.Combine(profiles.MinecraftDirectory, "versions", "1.8.9", "1.8.9.jar");
        Check(File.Exists(jar), "Client JAR missing after clean installation.");
        var originalHash = SHA256.HashData(File.ReadAllBytes(jar));
        File.WriteAllText(jar, "deliberately damaged test fixture");
        await service.InstallAsync(instance, progress, CancellationToken.None);
        Check(originalHash.SequenceEqual(SHA256.HashData(File.ReadAllBytes(jar))), "Damaged client was not repaired.");
        using var process = await service.BuildProcessAsync(instance, new MSession("FixturePlayer", "fixture-not-a-real-token", "00000000000000000000000000000042") { UserType = "msa" }, "00000000-0000-0000-0000-000000000042", CancellationToken.None);
        Check(File.Exists(process.StartInfo.FileName), "Java not installed.");
        Check(process.StartInfo.WorkingDirectory == profiles.InstanceDirectory("1.8.9"), "Wrong game directory.");
        Check(!File.Exists(Path.Combine(profiles.MinecraftDirectory, "launcher_profiles.json")), "Official launcher file created.");
        Check(!process.StartInfo.Arguments.Contains("${"), "Unresolved game argument.");
        Console.WriteLine("PASS: real fresh 1.8.9 download, Java runtime, corruption repair, direct process preparation, no official launcher files. Game not started with fixture credentials.");
    }
    if (args.Contains("--installed-versions"))
    {
        var shared = new ProfileService(dataDirectory: Path.Combine(root, "shared-data"));
        var sharedGame = new MinecraftGameService(shared);
        foreach (var version in ProfileService.Versions)
        {
            var test = new GameInstance { Version = version, Name = "Vérification" };
            await sharedGame.InstallAsync(test, new Progress<GameProgress>(), CancellationToken.None);
            using var process = await sharedGame.BuildProcessAsync(test, session, "00000000-0000-0000-0000-000000000042", CancellationToken.None);
            Check(File.Exists(process.StartInfo.FileName) && !process.StartInfo.Arguments.Contains("${"), "Invalid direct launch preparation.");
            Console.WriteLine($"PASS: Minecraft {version}, shared resources verified, installed runtime and native process prepared; game not started.");
        }
    }
}
finally
{
    var resolved = Path.GetFullPath(root);
    if (Path.GetDirectoryName(resolved) == Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar)
        && Path.GetFileName(resolved).StartsWith("LarpClient-integration-")) Directory.Delete(resolved, true);
}

sealed class FakeMicrosoft : HttpMessageHandler
{
    public int Calls; public HttpStatusCode? Reject; public string RejectAt="/minecraft/profile";public bool InvalidApp;
    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
    {
        ct.ThrowIfCancellationRequested(); Calls++;
        var url = request.RequestUri!.AbsoluteUri;
        if (Reject is not null && url.EndsWith(RejectAt))
            return new(Reject.Value) { RequestMessage = request, Content = JsonContent.Create(new { errorMessage = InvalidApp?"Invalid app registration, see application approval.":"fixture-secret-not-to-display" }) };
        object result;
        if (url.Contains("user.auth.xboxlive.com"))
        {
            using var json = JsonDocument.Parse(await request.Content!.ReadAsStringAsync(ct));
            if (json.RootElement.GetProperty("Properties").GetProperty("RpsTicket").GetString() != "d=fixture-microsoft-token") throw new Exception("Invalid Xbox ticket format.");
            result = new { Token = "fixture-xbox-token" };
        }
        else if (url.Contains("xsts.auth.xboxlive.com")) result = new { Token = "fixture-xsts-token", DisplayClaims = new { xui = new[] { new { uhs = "fixture-hash", xid = "42" } } } };
        else if (url.EndsWith("/login_with_xbox")) result = new { access_token = "fixture-minecraft-token" };
        else
        {
            if (request.Headers.Authorization?.Parameter != "fixture-minecraft-token") throw new Exception("Profile missing bearer token.");
            result = new { name = "FixturePlayer", id = "00000000000000000000000000000042" };
        }
        return new(HttpStatusCode.OK) { RequestMessage = request, Content = JsonContent.Create(result, options: new JsonSerializerOptions { PropertyNamingPolicy = null }) };
    }
}
