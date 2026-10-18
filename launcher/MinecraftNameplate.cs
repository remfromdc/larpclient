using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace LarpClient;

public sealed class MinecraftNameplate : FrameworkElement
{
    public static readonly DependencyProperty TextProperty=DependencyProperty.Register(nameof(Text),typeof(string),typeof(MinecraftNameplate),
        new FrameworkPropertyMetadata("",FrameworkPropertyMetadataOptions.AffectsMeasure|FrameworkPropertyMetadataOptions.AffectsRender));
    public string Text {get=>(string)GetValue(TextProperty);set=>SetValue(TextProperty,value);}
    private readonly BitmapSource?[] glyphs=new BitmapSource?[128];
    private readonly BitmapSource?[] shadows=new BitmapSource?[128];
    private readonly int[] widths=new int[128];
    public bool UsesGameFont { get; }
    // Generated once per account change; the scene projects this texture in world space.
    public static BitmapSource CreateTexture(string name)
    {
        var label = new MinecraftNameplate { Text = name };
        label.Measure(new Size(400, 30));
        var width = Math.Max(1, (int)Math.Ceiling(label.DesiredSize.Width));
        label.Arrange(new Rect(0, 0, width, 30));
        var bitmap = new RenderTargetBitmap(width, 30, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(label); bitmap.Freeze(); return bitmap;
    }
    public MinecraftNameplate()
    {
        IsHitTestVisible=false; RenderOptions.SetBitmapScalingMode(this,BitmapScalingMode.NearestNeighbor);
        try
        {
            var jar=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),".minecraft","versions","1.8.9","1.8.9.jar");
            using var zip=ZipFile.OpenRead(jar); var entry=zip.GetEntry("assets/minecraft/textures/font/ascii.png");
            if(entry is null)return;
            using var source=entry.Open(); using var stream=new MemoryStream(); source.CopyTo(stream); stream.Position=0;
            var decoder=BitmapDecoder.Create(stream,BitmapCreateOptions.PreservePixelFormat,BitmapCacheOption.OnLoad);
            var atlas=new FormatConvertedBitmap(decoder.Frames[0],PixelFormats.Bgra32,null,0);
            var cell=atlas.PixelWidth/16;
            if(cell<1 || atlas.PixelHeight<cell*8)return;
            var atlasBytes=new byte[atlas.PixelWidth*atlas.PixelHeight*4];
            atlas.CopyPixels(atlasBytes,atlas.PixelWidth*4,0);
            for(var code=32;code<127;code++)
            {
                var bytes=new byte[cell*cell*4]; var rect=new Int32Rect(code%16*cell,code/16*cell,cell,cell);
                for(var row=0;row<cell;row++)
                    Array.Copy(atlasBytes,((rect.Y+row)*atlas.PixelWidth+rect.X)*4,bytes,row*cell*4,cell*4);
                var right=0;
                for(var y=0;y<cell;y++)for(var x=0;x<cell;x++)if(bytes[(y*cell+x)*4+3]>16)right=Math.Max(right,x+1);
                widths[code]=code==32?4:Math.Max(1,right+1);
                var white=new byte[bytes.Length]; var black=new byte[bytes.Length];
                for(var i=0;i<bytes.Length;i+=4){white[i]=white[i+1]=white[i+2]=255;white[i+3]=bytes[i+3];black[i]=black[i+1]=black[i+2]=40;black[i+3]=bytes[i+3];}
                glyphs[code]=BitmapSource.Create(cell,cell,96,96,PixelFormats.Bgra32,null,white,cell*4);glyphs[code]!.Freeze();
                shadows[code]=BitmapSource.Create(cell,cell,96,96,PixelFormats.Bgra32,null,black,cell*4);shadows[code]!.Freeze();
            }
            UsesGameFont=true;
        }
        catch(Exception ex)when(ex is IOException or UnauthorizedAccessException or NotSupportedException){ }
    }
    protected override Size MeasureOverride(Size availableSize)=>new(Math.Min(330,UsesGameFont?Text.Sum(c=>c<128?widths[c]*3:18)+3:Text.Length*14),30);
    protected override void OnRender(DrawingContext dc)
    {
        if(!UsesGameFont)
        {
            dc.DrawText(new FormattedText(Text,CultureInfo.GetCultureInfo("fr-FR"),FlowDirection.LeftToRight,new Typeface("Consolas"),23,Brushes.White,VisualTreeHelper.GetDpi(this).PixelsPerDip),new Point());return;
        }
        var x=0d;
        foreach(var c in Text)
        {
            var code=c<128?c:'?'; if(glyphs[code] is not { } glyph)continue;
            dc.DrawImage(shadows[code],new Rect(x+2,2,24,24));dc.DrawImage(glyph,new Rect(x,0,24,24));x+=widths[code]*3;
        }
    }
}
