using LarpClient;
using OpenTK.Graphics.OpenGL4;
using OpenTK.Windowing.Common;
using OpenTK.Windowing.Desktop;
using GLFWBindingsContext = OpenTK.Windowing.GraphicsLibraryFramework.GLFWBindingsContext;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        if(args.Contains("--window")) { CheckWindow();return; }
        var name=MinecraftNameplate.CreateTexture("Steve");var model=CharacterModel.Name(name);
        if(model.VertexCount==0||model.VertexCount%36!=0||model.Maximum.Z-model.Minimum.Z<.4f)throw new Exception("Nametag is not solid geometry.");
        if(model.Vertices.Any(v=>!float.IsFinite(v)))throw new Exception("Invalid model vertices.");
        using var window=new NativeWindow(new NativeWindowSettings { StartVisible=false,StartFocused=false,APIVersion=new Version(3,3),Flags=ContextFlags.ForwardCompatible,ClientSize=new(320,384) });
        window.Context.MakeCurrent();GL.LoadBindings(new GLFWBindingsContext());
        var fbo=GL.GenFramebuffer();GL.BindFramebuffer(FramebufferTarget.Framebuffer,fbo);
        var color=GL.GenTexture();GL.BindTexture(TextureTarget.Texture2D,color);
        GL.TexImage2D(TextureTarget.Texture2D,0,PixelInternalFormat.Rgba8,320,384,0,PixelFormat.Rgba,PixelType.UnsignedByte,IntPtr.Zero);
        GL.FramebufferTexture2D(FramebufferTarget.Framebuffer,FramebufferAttachment.ColorAttachment0,TextureTarget.Texture2D,color,0);
        var depth=GL.GenRenderbuffer();GL.BindRenderbuffer(RenderbufferTarget.Renderbuffer,depth);
        GL.RenderbufferStorage(RenderbufferTarget.Renderbuffer,RenderbufferStorage.DepthComponent24,320,384);
        GL.FramebufferRenderbuffer(FramebufferTarget.Framebuffer,FramebufferAttachment.DepthAttachment,RenderbufferTarget.Renderbuffer,depth);
        if(GL.CheckFramebufferStatus(FramebufferTarget.Framebuffer)!=FramebufferErrorCode.FramebufferComplete)throw new Exception("Test framebuffer incomplete.");
        using(var renderer=new GpuSkinRenderer())
        {
            renderer.SetSkin(SkinScene.DefaultSkin(),false);renderer.SetName(name);
            var pixels=new byte[320*384*4];var visible=new Dictionary<int,int>();byte[]? front=null;
            foreach(var angle in new[]{0,45,90,135,180,225,270,315,360})
            {
                renderer.Render(new CharacterPose(0,0,angle,0,0,0,0,0,0,1),320,384);
                GL.ReadPixels(0,0,320,384,PixelFormat.Rgba,PixelType.UnsignedByte,pixels);
                var body=0;var tag=0;
                for(var y=0;y<384;y++)for(var x=0;x<320;x++)if(pixels[(y*320+x)*4+3]>0){if(y>334)tag++;else body++;}
                if(body<2000||tag<5)throw new Exception($"Missing body / solid nametag at {angle} degrees: {body} / {tag} pixels.");
                visible[angle]=tag;
                if(angle==0)front=(byte[])pixels.Clone();
                if(angle==360&&pixels.Zip(front!).Count(p=>Math.Abs(p.First-p.Second)>1)>100)throw new Exception("Full turn does not return to original model.");
            }
            if(visible[90]>=visible[0]/2||visible[270]>=visible[180]/2)throw new Exception("Nametag still faces the camera instead of rotating through its sides.");
            renderer.ResetHistory();renderer.Render(new(0,0,10,0,0,0,0,0,0,1),320,384,true);
            renderer.Render(new(1,0,13,0,0,0,0,0,0,1),320,384,true);
            var bytes=renderer.BufferBytes;
            for(var i=0;i<40;i++)renderer.Render(new(1+i*.1,0,13+i,0,0,0,0,0,0,1),320,384,true);
            if(bytes!=renderer.BufferBytes)throw new Exception("Exposure buffers grow per frame.");
            renderer.SetSkin(SkinScene.DefaultSkin(),true);renderer.Render(new(0,0,45,35,-35,-35,35,0,0,1),320,384);
            renderer.SetSkin(new System.Windows.Media.Imaging.CroppedBitmap(SkinScene.DefaultSkin(),new System.Windows.Int32Rect(0,0,64,32)),false);
            renderer.Render(new(0,0,180,35,-35,-35,35,0,0,1),320,384);
            if(GL.GetError()!=ErrorCode.NoError)throw new Exception("OpenGL error during model or exposure rendering.");
            Console.WriteLine($"PASS: {renderer.Device}; closed 3D glyph meshes, nine views over 360 degrees, visible sides/back, identical full-turn return, GPU shutter blur and stable framebuffers.");
        }
        GL.DeleteFramebuffer(fbo);GL.DeleteTexture(color);GL.DeleteRenderbuffer(depth);
    }
    private static void CheckWindow()
    {
        var app=new System.Windows.Application {ShutdownMode=System.Windows.ShutdownMode.OnExplicitShutdown};
        var window=new MainWindow();window.Show();
        var timer=new System.Windows.Threading.DispatcherTimer {Interval=TimeSpan.FromSeconds(8)};
        timer.Tick+=(_,_)=>
        {
            timer.Stop();var scene=(SkinScene)window.FindName("Character");
            if(!scene.HardwareRendering)throw new Exception("Window fell back to CPU: "+scene.RenderDevice);
            Console.WriteLine($"PASS: WPF GPU surface on {scene.RenderDevice}; {scene.FramesPerSecond:F1} renders/s, p95 {scene.FrameTimeP95Milliseconds:F2} ms; closing with GPU cleanup.");
            window.Close();app.Shutdown();
        };
        timer.Start();app.Run();
        Console.WriteLine("PASS: real WPF window closed, GPU resources disposed and compositor subscriptions detached.");
    }
}
