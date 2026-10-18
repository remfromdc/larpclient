using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using OpenTK.Wpf;

namespace LarpClient;

public sealed class SkinScene : Grid
{
    private readonly Image image = new() { Stretch = Stretch.Fill, IsHitTestVisible = false };
    private readonly CharacterChoreography choreography = new();
    private readonly AnimationLoop loop;
    private PixelSkinRenderer? renderer;
    private WriteableBitmap? surface;
    private GLWpfControl? gpuControl;
    private GpuSkinRenderer? gpu;
    private bool skinDirty=true, nameDirty=true, gpuFailed;
    private Window? owner;
    public string RenderDevice => gpu?.Device ?? (gpuFailed ? "CPU (secours)" : "Initialisation");
    public bool HardwareRendering => gpu is not null;
    private BitmapSource texture;
    private bool slim;
    private BitmapSource? nameTexture;
    public void SetPlayerName(string? name)
    {
        nameTexture = string.IsNullOrWhiteSpace(name) ? null : MinecraftNameplate.CreateTexture(name);
        System.Windows.Automation.AutomationProperties.SetName(image, name is null ? "Personnage Minecraft en 3D" : $"Personnage et nametag 3D de {name}");
        nameDirty=true; if(gpuControl is null)renderer?.SetNameTexture(nameTexture); Draw(choreography.Pose);
    }
    private bool animate = true;
    private bool motionBlur = true;
    private Point? drag;
    private double interactionRemaining;
    private double sampleTime;
    private int sampleFrames;
    private double sampleRenderMs;
    private long sampleStarted;
    private long lastCompleted;
    private readonly double[] intervals=new double[240];
    private int intervalCount, intervalCursor;
    public double FrameTimeP95Milliseconds { get; private set; }
    public bool Animate { get => animate; set { animate = value; loop.Enabled = value; renderer?.ResetHistory(); if (!value) Draw(choreography.Advance(0, false, reducedMotion: true)); } }
    public bool MotionBlur { get => motionBlur; set { if(motionBlur==value)return;motionBlur = value; renderer?.ResetHistory();gpu?.ResetHistory(); } }
    public bool AutoRotate { get; set; } = true;
    public double FramesPerSecond { get; private set; }
    public double AverageRenderMilliseconds { get; private set; }
    public long RenderBufferBytes => gpu?.BufferBytes ?? renderer?.BufferBytes ?? 0;
    public bool IsRenderLoopRunning => loop.IsRunning;
    public CharacterPhase AnimationPhase => choreography.Phase;
    public CharacterPose CurrentPose => choreography.Pose;
    public void ReplayEntrance() { choreography.ReplayEntrance(); renderer?.ResetHistory(); }
    public void RunAcross() => choreography.QueueRun();
    public SkinScene()
    {
        Background = Brushes.Transparent; ClipToBounds = true; Cursor = Cursors.Hand;
        texture = DefaultSkin(); Children.Add(image);
        RenderOptions.SetBitmapScalingMode(image, BitmapScalingMode.LowQuality);
        loop = new AnimationLoop(this, Frame);
        SizeChanged += (_, _) => RecreateSurface();
        Loaded += (_, _) =>
        {
            RecreateSurface(); StartGpu();
            if(owner is null && Window.GetWindow(this) is { } window) {owner=window;owner.Closed+=(_,_)=>ReleaseGpu();}
        };
        IsVisibleChanged += (_, _) => { renderer?.ResetHistory(); sampleTime = 0; sampleFrames = 0; sampleRenderMs = 0; };
        MouseLeftButtonDown += (_, e) =>
        {
            if (e.ClickCount == 2) { drag = null; ReleaseMouseCapture(); interactionRemaining = 0; choreography.QueueRun(); return; }
            choreography.SetManualYaw(choreography.Pose.Yaw); interactionRemaining = 4;
            drag = e.GetPosition(this); CaptureMouse();
        };
        MouseMove += (_, e) =>
        {
            if (drag is not { } previous) return;
            var current = e.GetPosition(this);
            choreography.SetManualYaw(choreography.Pose.Yaw + (current.X - previous.X) * .65);
            drag = current; interactionRemaining = 4;
            if (!animate) Draw(choreography.Advance(0, false, reducedMotion: true));
        };
        MouseLeftButtonUp += (_, _) => { drag = null; ReleaseMouseCapture(); };
        LostMouseCapture += (_, _) => drag = null;
    }
    private void StartGpu()
    {
        if(gpuControl is not null || gpuFailed || Environment.GetCommandLineArgs().Contains("--cpu-character"))return;
        try
        {
            gpuControl=new GLWpfControl { IsHitTestVisible=false, Focusable=false };
            gpuControl.Start(new GLWpfControlSettings { MajorVersion=3,MinorVersion=3,TransparentBackground=true,Samples=4,RenderContinuously=false });
            gpuControl.Render+=DrawGpu;
            Children.Add(gpuControl); image.Visibility=Visibility.Collapsed;
            renderer=null;surface=null;image.Source=null;
            gpuControl.InvalidateVisual();
        }
        catch(Exception ex) { UseCpuFallback(ex); }
    }
    private void UseCpuFallback(Exception ex)
    {
        gpuFailed=true; ReleaseGpu(); image.Visibility=Visibility.Visible;RecreateSurface();
        try
        {
            var directory=System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),"LarpClient");
            System.IO.Directory.CreateDirectory(directory);
            System.IO.File.WriteAllText(System.IO.Path.Combine(directory,"gpu-renderer-error.txt"),ex.ToString());
        } catch { }
    }
    private void ReleaseGpu()
    {
        if(gpuControl is null)return;
        gpuControl.Visibility=Visibility.Collapsed;
        gpuControl.Render-=DrawGpu;
        gpuControl.Context?.MakeCurrent();gpu?.Dispose();gpu=null;
        Children.Remove(gpuControl);gpuControl.Dispose();gpuControl=null;
    }
    private void DrawGpu(TimeSpan ignored)
    {
        if(gpuControl is null||gpuControl.FrameBufferWidth<1||gpuControl.FrameBufferHeight<1)return;
        try
        {
            gpu ??= new GpuSkinRenderer();
            if(skinDirty){gpu.SetSkin(texture,slim);skinDirty=false;}
            if(nameDirty){gpu.SetName(nameTexture);nameDirty=false;}
            var started=Stopwatch.GetTimestamp();
            gpu.Render(choreography.Pose,gpuControl!.FrameBufferWidth,gpuControl.FrameBufferHeight,motionBlur&&animate);
            RecordFrame(started);
        }
        catch(Exception ex)
        {
            // Defer teardown until the current interop render callback has released its surface.
            Dispatcher.BeginInvoke(()=>UseCpuFallback(ex));
        }
    }
    private void RecordFrame(long started)
    {
        var now=Stopwatch.GetTimestamp();
        if(sampleStarted==0)sampleStarted=now;
        if(lastCompleted!=0)
        {
            var ms=Stopwatch.GetElapsedTime(lastCompleted,now).TotalMilliseconds;
            if(ms<250){intervals[intervalCursor++%intervals.Length]=ms;intervalCount=Math.Min(intervalCount+1,intervals.Length);}
        }
        lastCompleted=now;
        sampleRenderMs+=Stopwatch.GetElapsedTime(started,now).TotalMilliseconds;sampleFrames++;
        sampleTime=Stopwatch.GetElapsedTime(sampleStarted).TotalSeconds;
        if(sampleTime>=1)
        {
            FramesPerSecond=sampleFrames/sampleTime;AverageRenderMilliseconds=sampleRenderMs/sampleFrames;
            if(intervalCount>0){var sorted=intervals.Take(intervalCount).Order().ToArray();FrameTimeP95Milliseconds=sorted[(int)((sorted.Length-1)*.95)];}
            sampleFrames=0;sampleTime=0;sampleRenderMs=0;sampleStarted=now;
        }
    }
    private void RecreateSurface()
    {
        if(gpuControl is not null)return;
        if (ActualWidth < 1 || ActualHeight < 1) return;
        const int height = 384;
        var width = Math.Clamp((int)Math.Round(height * ActualWidth / ActualHeight), 128, 640);
        if (renderer?.Width == width) return;
        renderer = new PixelSkinRenderer(width, height); renderer.SetTexture(texture, slim);
        renderer.SetNameTexture(nameTexture);
        surface = new WriteableBitmap(width, height, 96, 96, PixelFormats.Pbgra32, null); image.Source = surface;
        Draw(choreography.Pose);
    }
    private void Frame(double delta)
    {
        if (renderer is null&&gpuControl is null) return;
        if (delta == 0 || sampleStarted == 0) { renderer?.ResetHistory(); gpu?.ResetHistory();sampleTime = 0; sampleFrames = 0; sampleRenderMs = 0; lastCompleted=0; sampleStarted = Stopwatch.GetTimestamp(); }
        interactionRemaining = Math.Max(0, interactionRemaining - delta);
        var pose = choreography.Advance(delta, AutoRotate && interactionRemaining == 0, false, drag is not null || interactionRemaining > 0);
        var started = Stopwatch.GetTimestamp(); Draw(pose);
        if(gpuControl is null)RecordFrame(started);
    }
    private void Draw(CharacterPose pose)
    {
        if(gpuControl is not null){gpuControl.InvalidateVisual();return;}
        if (renderer is null || surface is null) return;
        var pixels = renderer.Render(pose, motionBlur && animate);
        surface.WritePixels(new Int32Rect(0, 0, renderer.Width, renderer.Height), pixels, renderer.Width * 4, 0);
    }
    public void SetTexture(BitmapSource source, bool isSlim)
    {
        texture = source; slim = isSlim; skinDirty=true; if(gpuControl is null)renderer?.SetTexture(source, isSlim); Draw(choreography.Pose);
    }
    public static BitmapSource DefaultSkin()
    {
        // Original monochrome preview, visibly labelled as a fallback by the UI.
        var pixels = new byte[64 * 64 * 4];
        void Rect(int x, int y, int w, int h, byte r, byte g, byte b)
        {
            for (var yy = y; yy < y + h; yy++) for (var xx = x; xx < x + w; xx++)
            { var i = (yy * 64 + xx) * 4; pixels[i] = b; pixels[i+1] = g; pixels[i+2] = r; pixels[i+3] = 255; }
        }
        Rect(0,0,32,16,189,175,159); Rect(0,0,32,8,53,49,46); Rect(8,8,8,2,53,49,46);
        Rect(9,11,2,1,240,240,240); Rect(13,11,2,1,240,240,240); Rect(10,11,1,1,64,64,64); Rect(13,11,1,1,64,64,64);
        Rect(11,14,2,1,108,89,79);
        Rect(16,16,24,16,54,57,62); Rect(20,20,8,2,77,80,85); Rect(23,23,2,5,224,226,230);
        Rect(40,16,16,16,60,63,68); Rect(32,48,16,16,60,63,68);
        Rect(44,29,4,3,189,175,159); Rect(36,61,4,3,189,175,159);
        Rect(0,16,16,16,32,33,37); Rect(16,48,16,16,32,33,37);
        Rect(4,30,4,2,210,210,214); Rect(20,62,4,2,210,210,214);
        var image = BitmapSource.Create(64,64,96,96,PixelFormats.Bgra32,null,pixels,64*4); image.Freeze(); return image;
    }
}
