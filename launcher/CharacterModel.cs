using System.Numerics;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace LarpClient;

// Model-space geometry built once. Vertices contain position, normal, UV, colour and joint index.
public sealed class CharacterModel
{
    public const int Stride = 13;
    public float[] Vertices { get; }
    public int VertexCount => Vertices.Length / Stride;
    public Vector3 Minimum { get; }
    public Vector3 Maximum { get; }
    private CharacterModel(List<float> values)
    {
        Vertices = values.ToArray();
        var min = new Vector3(float.PositiveInfinity); var max = new Vector3(float.NegativeInfinity);
        for (var i=0;i<Vertices.Length;i+=Stride)
        { var p=new Vector3(Vertices[i],Vertices[i+1],Vertices[i+2]); min=Vector3.Min(min,p); max=Vector3.Max(max,p); }
        Minimum=min; Maximum=max;
    }
    public static CharacterModel Skin(bool slim, bool legacy)
    {
        var b = new Builder(); var arm=slim?3:4;
        void Part(float w,float h,float d,float x,float y,float z,int u,int v,int joint,float inflate=0)
            => b.Box(new(x,y,z),new(w+inflate*2,h+inflate*2,d+inflate*2),joint,Vector4.One,u,v,(int)w,(int)h,(int)d,legacy?32:64);
        Part(8,8,8,0,28,0,0,0,1); Part(8,12,4,0,18,0,16,16,0);
        Part(arm,12,4,-4-arm/2f,18,0,40,16,2); Part(arm,12,4,4+arm/2f,18,0,legacy?40:32,legacy?16:48,3);
        Part(4,12,4,-2,6,0,0,16,4); Part(4,12,4,2,6,0,legacy?0:16,legacy?16:48,5);
        Part(8,8,8,0,28,0,32,0,1,.5f);
        if(!legacy)
        {
            Part(8,12,4,0,18,0,16,32,0,.25f);
            Part(arm,12,4,-4-arm/2f,18,0,40,32,2,.25f); Part(arm,12,4,4+arm/2f,18,0,48,48,3,.25f);
            Part(4,12,4,-2,6,0,0,32,4,.25f); Part(4,12,4,2,6,0,0,48,5,.25f);
        }
        return new(b.Values);
    }
    public static CharacterModel Name(BitmapSource source)
    {
        var image=new FormatConvertedBitmap(source,PixelFormats.Bgra32,null,0);
        var pixels=new byte[image.PixelWidth*image.PixelHeight*4]; image.CopyPixels(pixels,image.PixelWidth*4,0);
        // Return to the logical font grid rather than extruding every enlarged screen pixel.
        var w=(image.PixelWidth+2)/3; var h=(image.PixelHeight+2)/3;
        var mask=new bool[w*h]; var left=w; var right=0; var top=h; var bottom=0;
        for(var y=0;y<h;y++)for(var x=0;x<w;x++)
        {
            for(var yy=y*3;yy<Math.Min(image.PixelHeight,y*3+3);yy++)for(var xx=x*3;xx<Math.Min(image.PixelWidth,x*3+3);xx++)
            {var i=(yy*image.PixelWidth+xx)*4; if(pixels[i+3]>127&&pixels[i]>180)mask[y*w+x]=true;}
            if(mask[y*w+x]) {left=Math.Min(left,x);right=Math.Max(right,x+1);top=Math.Min(top,y);bottom=Math.Max(bottom,y+1);}
        }
        var b=new Builder(); if(left>=right)return new(b.Values);
        var unit=Math.Min(.32f,23f/(right-left));
        // Each horizontal glyph run is a solid closed cuboid with front, back and side faces.
        for(var y=top;y<bottom;y++)for(var x=left;x<right;x++)
        {
            if(!mask[y*w+x])continue;
            var start=x; while(x+1<right&&mask[y*w+x+1])x++;
            var size=new Vector3((x-start+1)*unit,unit,.40f);
            var center=new Vector3(((start+x+1)/2f-(left+right)/2f)*unit,((top+bottom)/2f-y-.5f)*unit,0);
            b.Box(center+new Vector3(.09f,-.10f,-.21f),size with { Z=.42f },0,new(.08f,.08f,.08f,1));
            b.Box(center,size,0,Vector4.One);
        }
        return new(b.Values);
    }
    private sealed class Builder
    {
        public List<float> Values { get; }=[];
        private static readonly int[] Order=[0,1,2,0,2,3];
        public void Box(Vector3 center,Vector3 size,int joint,Vector4 color,int u=-1,int v=-1,int tw=0,int th=0,int td=0,int textureHeight=64)
        {
            var a=size.X/2; var b=size.Y/2; var c=size.Z/2;
            Vector3[] p=[new(-a,-b,c),new(a,-b,c),new(a,b,c),new(-a,b,c),new(a,-b,-c),new(-a,-b,-c),new(-a,b,-c),new(a,b,-c)];
            int[][] faces=[[0,1,2,3],[4,5,6,7],[1,4,7,2],[5,0,3,6],[3,2,7,6],[5,4,1,0]];
            (int X,int Y,int W,int H)[] uv=[(u+td,v+td,tw,th),(u+td+tw+td,v+td,tw,th),(u,v+td,td,th),(u+td+tw,v+td,td,th),(u+td,v,tw,td),(u+td+tw,v,tw,td)];
            for(var f=0;f<6;f++)
            {
                var face=faces[f];var normal=Vector3.Normalize(Vector3.Cross(p[face[1]]-p[face[0]],p[face[2]]-p[face[0]]));
                Vector2[] tex=[new(uv[f].X,uv[f].Y+uv[f].H),new(uv[f].X+uv[f].W,uv[f].Y+uv[f].H),new(uv[f].X+uv[f].W,uv[f].Y),new(uv[f].X,uv[f].Y)];
                foreach(var index in Order)
                {
                    var vertex=p[face[index]]+center;var t=u<0?new Vector2(-1):new(tex[index].X/64f,tex[index].Y/textureHeight);
                    Values.AddRange([vertex.X,vertex.Y,vertex.Z,normal.X,normal.Y,normal.Z,t.X,t.Y,color.X,color.Y,color.Z,color.W,joint]);
                }
            }
        }
    }
}
