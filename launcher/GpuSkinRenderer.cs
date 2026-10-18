using System.Windows.Media;
using System.Windows.Media.Imaging;
using OpenTK.Graphics.OpenGL4;
using OpenTK.Mathematics;

namespace LarpClient;

// Static meshes and skin atlas live on the GPU; only joint matrices change each frame.
public sealed class GpuSkinRenderer : IDisposable
{
    private int shader, texture, bodyVao, bodyVbo, bodyCount, nameVao, nameVbo, nameCount;
    private int transformLocation, cameraLocation;
    private int sceneFbo, resolvedFbo, sceneColor, sceneDepth, resolvedColor, composeShader, composeVao, composeVbo;
    private int bufferWidth,bufferHeight;
    private bool hasPrevious;
    private CharacterPose previousPose;
    public void ResetHistory()=>hasPrevious=false;
    private readonly Matrix4[] joints=new Matrix4[6];
    private float armPivot=6;
    public string Device { get; private set; }="";
    public int NametagVertices => nameCount;
    private long meshBytes;
    public long BufferBytes => meshBytes+(long)bufferWidth*bufferHeight*36;
    private static float Rad(double value)=>(float)(value*Math.PI/180);
    public GpuSkinRenderer()
    {
        Device=GL.GetString(StringName.Renderer);
        const string vertex="""
            #version 330 core
            layout(location=0) in vec3 position;
            layout(location=1) in vec3 normal;
            layout(location=2) in vec2 uv;
            layout(location=3) in vec4 color;
            layout(location=4) in float joint;
            uniform mat4 transforms[6];
            uniform float aspect;
            out vec2 texcoord; out vec4 tint;
            void main() {
                mat4 model=transforms[int(joint)];
                vec3 p=(model*vec4(position,1)).xyz;
                vec3 n=normalize(mat3(model)*normal);
                float light=.65+.35*max(0.,dot(n,normalize(vec3(-.4,.65,1))));
                float depth=66.-p.z;
                gl_Position=vec4(p.x*3./aspect,(p.y-18.5)*3.,(depth-1.)*1.01-1.01,depth);
                texcoord=uv; tint=vec4(color.rgb*light,color.a);
            }
            """;
        const string fragment="""
            #version 330 core
            in vec2 texcoord; in vec4 tint;
            uniform sampler2D skin;
            out vec4 outputColor;
            void main() {
                vec4 pixel=texcoord.x<0.?vec4(1):texture(skin,texcoord);
                if(pixel.a<.03)discard;
                outputColor=vec4(pixel.rgb*tint.rgb*pixel.a,pixel.a*tint.a);
            }
            """;
        var vs=Compile(ShaderType.VertexShader,vertex); var fs=Compile(ShaderType.FragmentShader,fragment);
        shader=GL.CreateProgram();GL.AttachShader(shader,vs);GL.AttachShader(shader,fs);GL.LinkProgram(shader);
        GL.GetProgram(shader,GetProgramParameterName.LinkStatus,out var linked);
        GL.DeleteShader(vs);GL.DeleteShader(fs);
        if(linked==0)throw new InvalidOperationException(GL.GetProgramInfoLog(shader));
        transformLocation=GL.GetUniformLocation(shader,"transforms[0]");cameraLocation=GL.GetUniformLocation(shader,"aspect");
        texture=GL.GenTexture();GL.BindTexture(TextureTarget.Texture2D,texture);
        GL.TexParameter(TextureTarget.Texture2D,TextureParameterName.TextureMinFilter,(int)TextureMinFilter.Nearest);
        GL.TexParameter(TextureTarget.Texture2D,TextureParameterName.TextureMagFilter,(int)TextureMagFilter.Nearest);
        GL.TexParameter(TextureTarget.Texture2D,TextureParameterName.TextureWrapS,(int)TextureWrapMode.ClampToEdge);
        GL.TexParameter(TextureTarget.Texture2D,TextureParameterName.TextureWrapT,(int)TextureWrapMode.ClampToEdge);
    }
    private static int Compile(ShaderType type,string source)
    {
        var id=GL.CreateShader(type);GL.ShaderSource(id,source);GL.CompileShader(id);GL.GetShader(id,ShaderParameter.CompileStatus,out var compiled);
        if(compiled==0){var log=GL.GetShaderInfoLog(id);GL.DeleteShader(id);throw new InvalidOperationException(log);}return id;
    }
    private static void Upload(CharacterModel model,ref int vao,ref int vbo,ref int count)
    {
        if(vao==0){vao=GL.GenVertexArray();vbo=GL.GenBuffer();}
        GL.BindVertexArray(vao);GL.BindBuffer(BufferTarget.ArrayBuffer,vbo);
        GL.BufferData(BufferTarget.ArrayBuffer,model.Vertices.Length*sizeof(float),model.Vertices,BufferUsageHint.StaticDraw);
        var sizes=new[]{3,3,2,4,1};var offset=0;
        for(var i=0;i<sizes.Length;i++){GL.EnableVertexAttribArray(i);GL.VertexAttribPointer(i,sizes[i],VertexAttribPointerType.Float,false,CharacterModel.Stride*sizeof(float),offset*sizeof(float));offset+=sizes[i];}
        count=model.VertexCount;
    }
    public void SetSkin(BitmapSource source,bool slim)
    {
        armPivot=slim?5.5f:6;
        var pixels=new byte[source.PixelWidth*source.PixelHeight*4];
        new FormatConvertedBitmap(source,PixelFormats.Bgra32,null,0).CopyPixels(pixels,source.PixelWidth*4,0);
        GL.BindTexture(TextureTarget.Texture2D,texture);
        GL.TexImage2D(TextureTarget.Texture2D,0,PixelInternalFormat.Rgba,source.PixelWidth,source.PixelHeight,0,OpenTK.Graphics.OpenGL4.PixelFormat.Bgra,PixelType.UnsignedByte,pixels);
        Upload(CharacterModel.Skin(slim,source.PixelHeight==32),ref bodyVao,ref bodyVbo,ref bodyCount);
        UpdateMemory(source.PixelHeight);
    }
    public void SetName(BitmapSource? source)
    {
        if(source is null){nameCount=0;return;}
        Upload(CharacterModel.Name(source),ref nameVao,ref nameVbo,ref nameCount);UpdateMemory(64);
    }
    private void UpdateMemory(int textureHeight)=>meshBytes=(bodyCount+nameCount)*CharacterModel.Stride*sizeof(float)+64L*textureHeight*4;
    private static Matrix4 Pivot(Vector3 pivot,double angle,bool yaw=false)=>Matrix4.CreateTranslation(-pivot)
        *(yaw?Matrix4.CreateRotationY(Rad(angle)):Matrix4.CreateRotationX(Rad(angle)))*Matrix4.CreateTranslation(pivot);
    public void Render(CharacterPose pose,int width,int height,bool motionBlur=false)
    {
        if(width<1||height<1)return;
        var movement=Math.Abs(pose.X-previousPose.X)+Math.Abs(Math.IEEERemainder(pose.Yaw-previousPose.Yaw,360))*.08;
        if(motionBlur&&hasPrevious&&movement>.06&&movement<8)
        {
            GL.GetInteger(GetPName.FramebufferBinding,out var target);
            EnsureExposureBuffers(width,height);
            GL.BindFramebuffer(FramebufferTarget.Framebuffer,target);GL.ClearColor(0,0,0,0);GL.Clear(ClearBufferMask.ColorBufferBit|ClearBufferMask.DepthBufferBit);
            for(var sample=0;sample<3;sample++)
            {
                var t=sample==0?.2:sample==1?.65:1;
                var poseSample=Interpolate(previousPose,pose,t);
                GL.BindFramebuffer(FramebufferTarget.Framebuffer,sceneFbo);RenderScene(poseSample,width,height);
                GL.BindFramebuffer(FramebufferTarget.ReadFramebuffer,sceneFbo);GL.BindFramebuffer(FramebufferTarget.DrawFramebuffer,resolvedFbo);
                GL.BlitFramebuffer(0,0,width,height,0,0,width,height,ClearBufferMask.ColorBufferBit,BlitFramebufferFilter.Nearest);
                GL.BindFramebuffer(FramebufferTarget.Framebuffer,target);GL.Disable(EnableCap.DepthTest);GL.Disable(EnableCap.CullFace);
                GL.Enable(EnableCap.Blend);GL.BlendFunc(BlendingFactor.One,BlendingFactor.One);
                GL.UseProgram(composeShader);GL.Uniform1(GL.GetUniformLocation(composeShader,"weight"),sample==0?.18f:sample==1?.30f:.52f);
                GL.ActiveTexture(TextureUnit.Texture0);GL.BindTexture(TextureTarget.Texture2D,resolvedColor);GL.BindVertexArray(composeVao);
                GL.DrawArrays(PrimitiveType.Triangles,0,6);
            }
            GL.BindVertexArray(0);GL.UseProgram(0);GL.Enable(EnableCap.DepthTest);
            GL.BlendFunc(BlendingFactor.One,BlendingFactor.OneMinusSrcAlpha);
        }
        else RenderScene(pose,width,height);
        previousPose=pose;hasPrevious=true;
    }
    private static CharacterPose Interpolate(CharacterPose a,CharacterPose b,double t)
    {
        static double Mix(double x,double y,double t)=>x+(y-x)*t;
        return new(Mix(a.X,b.X,t),Mix(a.Y,b.Y,t),a.Yaw+Math.IEEERemainder(b.Yaw-a.Yaw,360)*t,
            Mix(a.LeftArm,b.LeftArm,t),Mix(a.RightArm,b.RightArm,t),Mix(a.LeftLeg,b.LeftLeg,t),Mix(a.RightLeg,b.RightLeg,t),
            Mix(a.Lean,b.Lean,t),Mix(a.Head,b.Head,t),Mix(a.ScaleY,b.ScaleY,t));
    }
    private void EnsureExposureBuffers(int width,int height)
    {
        if(sceneFbo!=0&&bufferWidth==width&&bufferHeight==height)return;
        ReleaseExposureBuffers();bufferWidth=width;bufferHeight=height;
        sceneFbo=GL.GenFramebuffer();GL.BindFramebuffer(FramebufferTarget.Framebuffer,sceneFbo);
        sceneColor=GL.GenRenderbuffer();GL.BindRenderbuffer(RenderbufferTarget.Renderbuffer,sceneColor);
        GL.RenderbufferStorageMultisample(RenderbufferTarget.Renderbuffer,4,RenderbufferStorage.Rgba8,width,height);
        GL.FramebufferRenderbuffer(FramebufferTarget.Framebuffer,FramebufferAttachment.ColorAttachment0,RenderbufferTarget.Renderbuffer,sceneColor);
        sceneDepth=GL.GenRenderbuffer();GL.BindRenderbuffer(RenderbufferTarget.Renderbuffer,sceneDepth);
        GL.RenderbufferStorageMultisample(RenderbufferTarget.Renderbuffer,4,RenderbufferStorage.DepthComponent24,width,height);
        GL.FramebufferRenderbuffer(FramebufferTarget.Framebuffer,FramebufferAttachment.DepthAttachment,RenderbufferTarget.Renderbuffer,sceneDepth);
        if(GL.CheckFramebufferStatus(FramebufferTarget.Framebuffer)!=FramebufferErrorCode.FramebufferComplete)throw new InvalidOperationException("Framebuffer d’exposition incomplet.");
        resolvedFbo=GL.GenFramebuffer();GL.BindFramebuffer(FramebufferTarget.Framebuffer,resolvedFbo);
        resolvedColor=GL.GenTexture();GL.BindTexture(TextureTarget.Texture2D,resolvedColor);
        GL.TexImage2D(TextureTarget.Texture2D,0,PixelInternalFormat.Rgba8,width,height,0,OpenTK.Graphics.OpenGL4.PixelFormat.Rgba,PixelType.UnsignedByte,IntPtr.Zero);
        GL.TexParameter(TextureTarget.Texture2D,TextureParameterName.TextureMinFilter,(int)TextureMinFilter.Linear);
        GL.TexParameter(TextureTarget.Texture2D,TextureParameterName.TextureMagFilter,(int)TextureMagFilter.Linear);
        GL.FramebufferTexture2D(FramebufferTarget.Framebuffer,FramebufferAttachment.ColorAttachment0,TextureTarget.Texture2D,resolvedColor,0);
        if(GL.CheckFramebufferStatus(FramebufferTarget.Framebuffer)!=FramebufferErrorCode.FramebufferComplete)throw new InvalidOperationException("Framebuffer de résolution incomplet.");
        if(composeShader!=0)return;
        var vs=Compile(ShaderType.VertexShader,"#version 330 core\nlayout(location=0) in vec2 position; layout(location=1) in vec2 uv; out vec2 t; void main(){t=uv;gl_Position=vec4(position,0,1);}");
        var fs=Compile(ShaderType.FragmentShader,"#version 330 core\nin vec2 t; uniform sampler2D scene; uniform float weight; out vec4 c; void main(){c=texture(scene,t)*weight;}");
        composeShader=GL.CreateProgram();GL.AttachShader(composeShader,vs);GL.AttachShader(composeShader,fs);GL.LinkProgram(composeShader);GL.DeleteShader(vs);GL.DeleteShader(fs);
        GL.GetProgram(composeShader,GetProgramParameterName.LinkStatus,out var linked);if(linked==0)throw new InvalidOperationException(GL.GetProgramInfoLog(composeShader));
        float[] quad=[-1,-1,0,0,1,-1,1,0,1,1,1,1,-1,-1,0,0,1,1,1,1,-1,1,0,1];
        composeVao=GL.GenVertexArray();composeVbo=GL.GenBuffer();GL.BindVertexArray(composeVao);GL.BindBuffer(BufferTarget.ArrayBuffer,composeVbo);
        GL.BufferData(BufferTarget.ArrayBuffer,quad.Length*sizeof(float),quad,BufferUsageHint.StaticDraw);
        GL.EnableVertexAttribArray(0);GL.VertexAttribPointer(0,2,VertexAttribPointerType.Float,false,4*sizeof(float),0);
        GL.EnableVertexAttribArray(1);GL.VertexAttribPointer(1,2,VertexAttribPointerType.Float,false,4*sizeof(float),2*sizeof(float));
    }
    private void ReleaseExposureBuffers()
    {
        if(sceneFbo!=0)GL.DeleteFramebuffer(sceneFbo);if(resolvedFbo!=0)GL.DeleteFramebuffer(resolvedFbo);
        if(sceneColor!=0)GL.DeleteRenderbuffer(sceneColor);if(sceneDepth!=0)GL.DeleteRenderbuffer(sceneDepth);if(resolvedColor!=0)GL.DeleteTexture(resolvedColor);
        sceneFbo=resolvedFbo=sceneColor=sceneDepth=resolvedColor=0;
    }
    private void RenderScene(CharacterPose pose,int width,int height)
    {
        GL.Viewport(0,0,width,height);GL.ClearColor(0,0,0,0);GL.Clear(ClearBufferMask.ColorBufferBit|ClearBufferMask.DepthBufferBit);
        GL.Enable(EnableCap.DepthTest);GL.DepthFunc(DepthFunction.Less);GL.Enable(EnableCap.CullFace);GL.CullFace(TriangleFace.Back);
        GL.Enable(EnableCap.Blend);GL.BlendFunc(BlendingFactor.One,BlendingFactor.OneMinusSrcAlpha);
        GL.UseProgram(shader);GL.Uniform1(cameraLocation,width/(float)height);
        GL.ActiveTexture(TextureUnit.Texture0);GL.BindTexture(TextureTarget.Texture2D,texture);
        var global=Matrix4.CreateScale(1,(float)pose.ScaleY,1)*Pivot(new(0,12,0),pose.Lean)
            *Matrix4.CreateRotationY(Rad(pose.Yaw))*Matrix4.CreateTranslation((float)pose.X,(float)pose.Y,0);
        joints[0]=global;joints[1]=Pivot(new(0,24,0),pose.Head,true)*global;
        joints[2]=Pivot(new(-armPivot,23,0),pose.LeftArm)*global;joints[3]=Pivot(new(armPivot,23,0),pose.RightArm)*global;
        joints[4]=Pivot(new(-2,12,0),pose.LeftLeg)*global;joints[5]=Pivot(new(2,12,0),pose.RightLeg)*global;
        for(var i=0;i<6;i++)GL.UniformMatrix4(transformLocation+i,false,ref joints[i]);
        GL.BindVertexArray(bodyVao);GL.DrawArrays(PrimitiveType.Triangles,0,bodyCount);
        if(nameCount>0)
        {
            // The entire solid nametag shares the character's complete 360-degree yaw.
            var tag=Matrix4.CreateRotationY(Rad(pose.Yaw))*Matrix4.CreateTranslation((float)pose.X,36.4f+(float)pose.Y,0);
            GL.UniformMatrix4(transformLocation,false,ref tag);
            GL.BindVertexArray(nameVao);GL.DrawArrays(PrimitiveType.Triangles,0,nameCount);
        }
        GL.BindVertexArray(0);GL.UseProgram(0);
    }
    public void Dispose()
    {
        ReleaseExposureBuffers();
        if(composeShader!=0)GL.DeleteProgram(composeShader);if(composeVao!=0)GL.DeleteVertexArray(composeVao);if(composeVbo!=0)GL.DeleteBuffer(composeVbo);
        if(bodyVao!=0)GL.DeleteVertexArray(bodyVao);if(nameVao!=0)GL.DeleteVertexArray(nameVao);
        if(bodyVbo!=0)GL.DeleteBuffer(bodyVbo);if(nameVbo!=0)GL.DeleteBuffer(nameVbo);
        if(texture!=0)GL.DeleteTexture(texture);if(shader!=0)GL.DeleteProgram(shader);
    }
}
