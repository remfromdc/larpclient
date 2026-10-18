using System.Numerics;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace LarpClient;

// Small textured 3D rasterizer. Fixed buffers and no dependency on the WPF software 3D pipeline.
public sealed class PixelSkinRenderer
{
    private readonly record struct Part(float W, float H, float D, Vector3 Center, int U, int V, int Joint, float Inflate);
    private readonly record struct Face(int A, int B, int C, int D, int U, int V, int W, int H);
    private readonly record struct Vertex(float X, float Y, float InvZ);
    private readonly List<(Part Part, Face[] Faces)> parts = [];
    private readonly Vector3[] vertices = new Vector3[8];
    private readonly Vertex[] projected = new Vertex[8];
    private uint[] texture = new uint[64 * 64];
    private readonly uint[] frame;
    private readonly uint[] previous;
    private readonly uint[] output;
    private readonly float[] depth;
    private CharacterPose previousPose;
    private bool hasPrevious;
    private CharacterModel? nameModel;
    private static readonly uint[] WhitePixel = [0xFFFFFFFF];
    public void SetNameTexture(BitmapSource? source)
    {
        nameModel=source is null?null:CharacterModel.Name(source);
        ResetHistory();
    }
    public int Width { get; }
    public int Height { get; }
    public long BufferBytes => (long)(frame.Length + previous.Length + output.Length + depth.Length) * 4 + (texture.Length + (nameModel?.Vertices.Length ?? 0)) * 4L;
    public PixelSkinRenderer(int width, int height)
    {
        Width = width; Height = height;
        frame = new uint[width * height]; previous = new uint[frame.Length]; output = new uint[frame.Length]; depth = new float[frame.Length];
    }
    public void ResetHistory() { hasPrevious = false; Array.Clear(previous); }
    public void SetTexture(BitmapSource source, bool slim)
    {
        var converted = new FormatConvertedBitmap(source, PixelFormats.Bgra32, null, 0);
        texture = new uint[source.PixelWidth * source.PixelHeight]; converted.CopyPixels(texture, 64 * 4, 0);
        parts.Clear();
        var modern = source.PixelHeight == 64;
        var arm = slim ? 3 : 4;
        Add(8,8,8,0,28,0,0,0,1);
        Add(8,12,4,0,18,0,16,16,0);
        Add(arm,12,4,-4-arm/2f,18,0,40,16,2);
        Add(arm,12,4,4+arm/2f,18,0,modern?32:40,modern?48:16,3);
        Add(4,12,4,-2,6,0,0,16,4);
        Add(4,12,4,2,6,0,modern?16:0,modern?48:16,5);
        Add(8,8,8,0,28,0,32,0,1,.5f);
        if (modern)
        {
            Add(8,12,4,0,18,0,16,32,0,.25f);
            Add(arm,12,4,-4-arm/2f,18,0,40,32,2,.25f);
            Add(arm,12,4,4+arm/2f,18,0,48,48,3,.25f);
            Add(4,12,4,-2,6,0,0,32,4,.25f);
            Add(4,12,4,2,6,0,0,48,5,.25f);
        }
        ResetHistory();
    }
    private void Add(float w,float h,float d,float x,float y,float z,int u,int v,int joint,float inflate=0)
    {
        var iw=(int)w; var ih=(int)h; var id=(int)d;
        parts.Add((new(w,h,d,new(x,y,z),u,v,joint,inflate), [
            new(0,1,2,3,u+id,v+id,iw,ih), new(4,5,6,7,u+id+iw+id,v+id,iw,ih),
            new(1,4,7,2,u,v+id,id,ih), new(5,0,3,6,u+id+iw,v+id,id,ih),
            new(3,2,7,6,u+id,v,iw,id), new(5,4,1,0,u+id+iw,v,iw,id)]));
    }
    private static float Radians(double degrees) => (float)(degrees * Math.PI / 180);
    public uint[] Render(CharacterPose pose, bool motionBlur)
    {
        Array.Clear(frame); Array.Fill(depth, float.PositiveInfinity);
        var global = Matrix4x4.CreateScale(1,(float)pose.ScaleY,1)
            * Matrix4x4.CreateTranslation(0,-12,0) * Matrix4x4.CreateRotationX(Radians(pose.Lean)) * Matrix4x4.CreateTranslation(0,12,0)
            * Matrix4x4.CreateRotationY(Radians(pose.Yaw)) * Matrix4x4.CreateTranslation((float)pose.X,(float)pose.Y,0);
        foreach (var (part, faces) in parts)
        {
            var a=part.W/2+part.Inflate; var b=part.H/2+part.Inflate; var c=part.D/2+part.Inflate;
            vertices[0]=new(-a,-b,c); vertices[1]=new(a,-b,c); vertices[2]=new(a,b,c); vertices[3]=new(-a,b,c);
            vertices[4]=new(a,-b,-c); vertices[5]=new(-a,-b,-c); vertices[6]=new(-a,b,-c); vertices[7]=new(a,b,-c);
            var angle = part.Joint switch {1=>pose.Head,2=>pose.LeftArm,3=>pose.RightArm,4=>pose.LeftLeg,5=>pose.RightLeg,_=>0};
            var pivot = part.Joint switch {1=>new Vector3(0,24,0),2=>new Vector3(part.Center.X,23,0),3=>new Vector3(part.Center.X,23,0),4=>new Vector3(-2,12,0),5=>new Vector3(2,12,0),_=>Vector3.Zero};
            var joint = Matrix4x4.CreateTranslation(-pivot)
                * (part.Joint==1?Matrix4x4.CreateRotationY(Radians(angle)):Matrix4x4.CreateRotationX(Radians(angle)))
                * Matrix4x4.CreateTranslation(pivot) * global;
            for(var i=0;i<8;i++)
            {
                var v=Vector3.Transform(vertices[i]+part.Center,joint); vertices[i]=v;
                var inv=1f/(66-v.Z); var scale=Height*66/44f;
                projected[i]=new(Width/2f+v.X*inv*scale,Height/2f-(v.Y-18.5f)*inv*scale,inv);
            }
            foreach(var face in faces)
            {
                var normal=Vector3.Cross(vertices[face.B]-vertices[face.A],vertices[face.C]-vertices[face.A]);
                if(Vector3.Dot(normal,new Vector3(0,18.5f,66)-vertices[face.A])<=0)continue;
                normal=Vector3.Normalize(normal);
                var light=.67f+.33f*Math.Max(0,Vector3.Dot(normal,Vector3.Normalize(new Vector3(-.4f,.65f,1))));
                var u0=face.U+.001f; var u1=face.U+face.W-.001f;
                var v0=face.V+.001f; var v1=face.V+face.H-.001f;
                Triangle(projected[face.A],projected[face.B],projected[face.C],u0,v1,u1,v1,u1,v0,light);
                Triangle(projected[face.A],projected[face.C],projected[face.D],u0,v1,u1,v0,u0,v0,light);
            }
        }
        DrawName(pose);
        var velocity=Math.Abs(pose.X-previousPose.X)*9 + Math.Abs(Math.IEEERemainder(pose.Yaw-previousPose.Yaw,360))*.15;
        var weight=motionBlur&&hasPrevious&&velocity>.9 ? (int)Math.Clamp(velocity*15,0,85) : 0;
        if(weight>0)
            for(var i=0;i<frame.Length;i++) output[i]=Blend(frame[i],previous[i],weight);
        else Array.Copy(frame,output,frame.Length);
        Array.Copy(frame,previous,frame.Length); hasPrevious=true; previousPose=pose;
        return output;
    }
    private void DrawName(CharacterPose pose)
    {
        if (nameModel is null) return;
        var transform = Matrix4x4.CreateRotationY(Radians(pose.Yaw))
            * Matrix4x4.CreateTranslation((float)pose.X, 36.4f + (float)pose.Y, 0);
        var data=nameModel.Vertices;
        for(var i=0;i<data.Length;i+=CharacterModel.Stride*3)
        {
            var a=Vector3.Transform(new(data[i],data[i+1],data[i+2]),transform);
            var b=Vector3.Transform(new(data[i+13],data[i+14],data[i+15]),transform);
            var c=Vector3.Transform(new(data[i+26],data[i+27],data[i+28]),transform);
            var normal=Vector3.TransformNormal(new(data[i+3],data[i+4],data[i+5]),transform);
            if(Vector3.Dot(normal,new Vector3(0,18.5f,66)-a)<=0)continue;
            var light=(.65f+.35f*Math.Max(0,Vector3.Dot(normal,Vector3.Normalize(new Vector3(-.4f,.65f,1)))))*data[i+8];
            Triangle(ProjectName(a),ProjectName(b),ProjectName(c),0,0,0,0,0,0,light,WhitePixel,1,1);
        }
    }
    private Vertex ProjectName(Vector3 v)
    {
        var inv = 1f / (66-v.Z); var scale = Height*66/44f;
        return new(Width/2f+v.X*inv*scale, Height/2f-(v.Y-18.5f)*inv*scale, inv);
    }
    private static uint Blend(uint current,uint old,int weight)
    {
        var inverse=256-weight;
        var a=((current>>24)*(uint)inverse+(old>>24)*(uint)weight)>>8;
        var r=(((current>>16)&255)*(uint)inverse+((old>>16)&255)*(uint)weight)>>8;
        var g=(((current>>8)&255)*(uint)inverse+((old>>8)&255)*(uint)weight)>>8;
        var b=((current&255)*(uint)inverse+(old&255)*(uint)weight)>>8;
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
    private void Triangle(Vertex a,Vertex b,Vertex c,float au,float av,float bu,float bv,float cu,float cv,float light, uint[]? pixels=null, int textureWidth=64, int textureHeight=0)
    {
        pixels ??= texture; if(textureHeight==0)textureHeight=pixels.Length/textureWidth;
        var denominator=(b.Y-c.Y)*(a.X-c.X)+(c.X-b.X)*(a.Y-c.Y);
        if(Math.Abs(denominator)<.0001f)return;
        var inverse=1/denominator;
        var minX=Math.Max(0,(int)MathF.Floor(Math.Min(a.X,Math.Min(b.X,c.X))));
        var maxX=Math.Min(Width-1,(int)MathF.Ceiling(Math.Max(a.X,Math.Max(b.X,c.X))));
        var minY=Math.Max(0,(int)MathF.Floor(Math.Min(a.Y,Math.Min(b.Y,c.Y))));
        var maxY=Math.Min(Height-1,(int)MathF.Ceiling(Math.Max(a.Y,Math.Max(b.Y,c.Y))));
        au*=a.InvZ; av*=a.InvZ; bu*=b.InvZ; bv*=b.InvZ; cu*=c.InvZ; cv*=c.InvZ;
        for(var y=minY;y<=maxY;y++)for(var x=minX;x<=maxX;x++)
        {
            var wa=((b.Y-c.Y)*(x+.5f-c.X)+(c.X-b.X)*(y+.5f-c.Y))*inverse;
            var wb=((c.Y-a.Y)*(x+.5f-c.X)+(a.X-c.X)*(y+.5f-c.Y))*inverse;
            var wc=1-wa-wb;
            if(wa<-.00001f||wb<-.00001f||wc<-.00001f)continue;
            var z=1/(wa*a.InvZ+wb*b.InvZ+wc*c.InvZ); var index=y*Width+x;
            if(z>=depth[index])continue;
            var u=Math.Clamp((int)((wa*au+wb*bu+wc*cu)*z),0,textureWidth-1);
            var v=Math.Clamp((int)((wa*av+wb*bv+wc*cv)*z),0,textureHeight-1);
            var pixel=pixels[v*textureWidth+u]; var alpha=(int)(pixel>>24);
            if(alpha<8)continue;
            var red=(uint)(((pixel>>16)&255)*light*alpha/255);
            var green=(uint)(((pixel>>8)&255)*light*alpha/255);
            var blue=(uint)((pixel&255)*light*alpha/255);
            var source=((uint)alpha<<24)|(red<<16)|(green<<8)|blue;
            if(alpha<255)
            {
                var destination=frame[index]; var remain=(uint)(255-alpha);
                var da=Math.Min(255,(uint)alpha+((destination>>24)*remain/255));
                red+=((destination>>16)&255)*remain/255; green+=((destination>>8)&255)*remain/255; blue+=(destination&255)*remain/255;
                source=(da<<24)|(red<<16)|(green<<8)|blue;
            }
            frame[index]=source; depth[index]=z;
        }
    }
}
