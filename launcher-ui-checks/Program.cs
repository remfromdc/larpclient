using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Media.Imaging;
using LarpClient;
using System.Diagnostics;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        var app = new Application();
        // Exercise actual control templates: XAML can compile yet fail when a template is first loaded.
        var window = new MainWindow();
        var content = (FrameworkElement)window.Content;
        content.Measure(new Size(1180, 790));
        content.Arrange(new Rect(0, 0, 1180, 790));
        content.UpdateLayout();
        var rendered = new RenderTargetBitmap(1180, 790, 96, 96, System.Windows.Media.PixelFormats.Pbgra32);
        rendered.Render(content);
        var instanceList=(System.Windows.Controls.ListBox)window.FindName("InstanceList");
        instanceList.IsEnabled=false;content.UpdateLayout();
        var disabledRender=new RenderTargetBitmap(1180,790,96,96,System.Windows.Media.PixelFormats.Pbgra32);disabledRender.Render(content);
        var point=instanceList.TranslatePoint(new Point(8,instanceList.ActualHeight-8),content);
        var sample=new byte[4];disabledRender.CopyPixels(new Int32Rect((int)point.X,(int)point.Y,1,1),sample,4,0);
        if(sample[0]>100||sample[1]>100||sample[2]>100)throw new Exception("Disabled instance list reverted to a white system background.");
        instanceList.IsEnabled=true;
        typeof(MainWindow).GetMethod("SetStatus",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance)!.Invoke(window,["Test de connexion : autorisation requise",true]);
        content.UpdateLayout();
        if(((FrameworkElement)window.FindName("StatusOverlay")).Visibility!=Visibility.Visible||((FrameworkElement)window.FindName("StatusApprovalButton")).Visibility!=Visibility.Visible)throw new Exception("Dark approval error overlay missing.");
        window.Close();
        Console.WriteLine("PASS: window layout and actual control templates instantiate.");
        CheckChoreography();
        CheckRasterizer();
        CheckLongRunningChoreography();
        var scene = new SkinScene();
        var texture = SkinScene.DefaultSkin();
        scene.SetTexture(texture, false);
        scene.SetTexture(texture, true);
        var legacy = new CroppedBitmap(texture, new Int32Rect(0, 0, 64, 32));
        scene.SetTexture(legacy, false);
        using var stream = new MemoryStream();
        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(legacy)); encoder.Save(stream); stream.Position = 0;
        if (AccountService.DecodeSkin(stream).PixelHeight != 32) throw new Exception("Legacy skin decode failed.");
        var root = Path.Combine(Path.GetTempPath(), "LarpClient-ui-checks-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(root);
        try
        {
            var data = new { activeAccountLocalId = "active", accounts = new Dictionary<string, object> {
                ["other"] = new { minecraftProfile = new { name = "Other", id = "00000000000000000000000000000001" } },
                ["active"] = new { minecraftProfile = new { name = "Selected", id = "00000000000000000000000000000002" } }
            }};
            File.WriteAllText(Path.Combine(root, "launcher_accounts_microsoft_store.json"), JsonSerializer.Serialize(data));
            var account = AccountService.ReadAccount(root);
            if (account?.Name != "Selected") throw new Exception("Wrong account selected.");
            File.WriteAllText(Path.Combine(root, "launcher_accounts_microsoft_store.json"), "{}");
            if (AccountService.ReadAccount(root) is not null) throw new Exception("Absent profile must remain absent.");
        }
        finally { if (Path.GetFileName(root).StartsWith("LarpClient-ui-checks-")) Directory.Delete(root, true); }
        Console.WriteLine("PASS: classic, slim, legacy 3D skin meshes; PNG decode; active public profile selection; absent account fallback.");
        app.Shutdown();
    }

    private static void CheckRasterizer()
    {
        var renderer = new PixelSkinRenderer(320, 384);
        var texture = SkinScene.DefaultSkin();
        renderer.SetTexture(texture, false);
        renderer.SetNameTexture(MinecraftNameplate.CreateTexture("Steve"));
        var idle = new CharacterPose(0, 0, -20, 0, 0, 0, 0, 0, 0, 1);
        var image = renderer.Render(idle, false);
        if (image.Take(320 * 50).Count(p => (p >> 24) > 0) < 50) throw new Exception("3D nametag missing above the head.");
        var tagPixels = image.Take(320 * 50).ToArray();
        renderer.Render(idle with { X = 8 }, false);
        if (tagPixels.SequenceEqual(image.Take(320 * 50))) throw new Exception("Nametag did not follow character in world space.");
        if (image.Count(p => (p >> 24) > 0) < 5000) throw new Exception("Textured 3D body is missing from rendered frame.");
        renderer.SetTexture(texture, true); renderer.Render(idle, true);
        renderer.SetTexture(new CroppedBitmap(texture, new Int32Rect(0, 0, 64, 32)), false); renderer.Render(idle, true);
        renderer.SetTexture(texture, false);
        for (var i = 0; i < 30; i++) renderer.Render(idle, true);
        var allocated = GC.GetAllocatedBytesForCurrentThread();
        var watch = Stopwatch.StartNew();
        var frames = 1200;
        for (var i = 0; i < frames; i++)
        {
            var gait = Math.Sin(i / 8d) * 35;
            var pose = idle with { Yaw = -20 + i * .3, LeftArm = -gait, RightArm = gait, LeftLeg = gait, RightLeg = -gait };
            if (!ReferenceEquals(image, renderer.Render(pose, true))) throw new Exception("Frame buffer was reallocated.");
        }
        watch.Stop();
        var allocation = GC.GetAllocatedBytesForCurrentThread() - allocated;
        if (allocation > 65536) throw new Exception($"Unbounded render allocation: {allocation} bytes.");
        Console.WriteLine($"PASS: textured classic/slim/legacy rasterization, motion history and stable buffers; {watch.Elapsed.TotalMilliseconds / frames:F2} ms/render, {allocation} bytes allocated over {frames} frames.");
    }

    private static void CheckLongRunningChoreography()
    {
        var director = new CharacterChoreography();
        for (var frame = 0; frame < 90 * 60 * 60 * 6; frame++)
        {
            var pose = director.Advance(1d / 90, true);
            if (!double.IsFinite(pose.Yaw) || !double.IsFinite(pose.X) || Math.Abs(pose.X) > 35)
                throw new Exception("Choreography became unstable during six-hour simulation.");
        }
        var loopScene = new SkinScene();
        if (loopScene.IsRenderLoopRunning) throw new Exception("Unloaded control must not subscribe to rendering.");
        Console.WriteLine("PASS: six hours of simulated animation at 90 updates/s; no invalid transforms or unloaded render subscriptions.");
    }

    private static void CheckChoreography()
    {
        var director = new CharacterChoreography();
        var phases = new HashSet<CharacterPhase>();
        var previous = director.Pose;
        var crossedLeft = false; var crossedRight = false;
        for (var frame = 0; frame < 60 * 13; frame++)
        {
            if (frame == 60 * 4) director.QueueRun();
            var pose = director.Advance(1d / 60, true);
            phases.Add(director.Phase);
            var values = new[] { pose.X, pose.Y, pose.Yaw, pose.LeftArm, pose.RightArm, pose.LeftLeg, pose.RightLeg, pose.Lean, pose.Head, pose.ScaleY };
            if (values.Any(v => !double.IsFinite(v))) throw new Exception("Non-finite animation transform.");
            if (Math.Abs(pose.X - previous.X) > 1.4) throw new Exception("Discontinuous horizontal motion.");
            if (Math.Abs(pose.LeftLeg + pose.RightLeg) > .001 || Math.Abs(pose.LeftArm + pose.RightArm) > .001)
                throw new Exception("Limb gait is not opposed.");
            if (pose.ScaleY < .9 || pose.ScaleY > 1.1) throw new Exception("Invalid landing scale.");
            if (director.Phase == CharacterPhase.SprintAcross) { crossedLeft |= pose.X < -20; crossedRight |= pose.X > 20; }
            previous = pose;
        }
        if (phases.Count != 6 || !crossedLeft || !crossedRight || director.Phase != CharacterPhase.Idle || Math.Abs(director.Pose.X) > .01)
            throw new Exception("Entrance/run/return sequence did not complete.");
        director.ReplayEntrance(); director.Advance(10, true);
        if (director.PhaseTime > .051) throw new Exception("Long frame skipped choreography.");
        var before = director.Pose;
        director.SetManualYaw(42); var manual = director.Advance(1d / 60, false, interacting: true);
        if (Math.Abs(manual.X - before.X) > 4 || manual.Yaw != 42) throw new Exception("Manual interaction snapped.");
        var still = director.Advance(1, true, reducedMotion: true);
        if (still.X != 0 || still.LeftLeg != 0 || still.RightArm != 0 || still.ScaleY != 1) throw new Exception("Reduced motion failed.");
        Console.WriteLine("PASS: complete walk/run choreography, continuity, opposed limbs, long-frame recovery, drag control and reduced motion.");
    }
}
