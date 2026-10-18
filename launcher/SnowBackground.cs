using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace LarpClient;

// Frozen raster tiles: no path tessellation or per-flake objects during animation.
public sealed class SnowBackground : FrameworkElement
{
    private const int TileSize=512;
    private const int FlakesPerLayer=48;
    private readonly ImageBrush[] layers=new ImageBrush[3];
    private readonly TranslateTransform[] offsets=new TranslateTransform[3];
    private readonly AnimationLoop loop;
    private double activeTime;
    private readonly double[] driftX=new double[3];
    private readonly double[] driftY=new double[3];
    private double windPhase;
    public bool Animate { get=>loop.Enabled;set=>loop.Enabled=value; }
    public double ActiveTime=>activeTime;
    public bool IsRenderLoopRunning=>loop.IsRunning;
    public int ParticleCount=>(int)Math.Ceiling(3*FlakesPerLayer*ActualWidth*ActualHeight/(TileSize*TileSize));
    public SnowBackground()
    {
        IsHitTestVisible=false;ClipToBounds=true;
        var random=new Random(7401);
        for(var layer=0;layer<3;layer++)
        {
            var bytes=new byte[TileSize*TileSize*4];
            for(var n=0;n<FlakesPerLayer;n++)
            {
                var cx=6+random.NextDouble()*(TileSize-12);var cy=6+random.NextDouble()*(TileSize-12);
                var radius=.65+layer*.4+random.NextDouble()*.8;var opacity=45+layer*36+random.Next(30);
                for(var y=(int)(cy-radius-1);y<=cy+radius+1;y++)for(var x=(int)(cx-radius-1);x<=cx+radius+1;x++)
                {
                    var coverage=Math.Clamp(radius+.6-Math.Sqrt((x+.5-cx)*(x+.5-cx)+(y+.5-cy)*(y+.5-cy)),0,1);
                    var alpha=(byte)(opacity*coverage);var i=(y*TileSize+x)*4;
                    bytes[i]=bytes[i+1]=bytes[i+2]=bytes[i+3]=alpha;
                }
            }
            var tile=BitmapSource.Create(TileSize,TileSize,96,96,PixelFormats.Pbgra32,null,bytes,TileSize*4);tile.Freeze();
            offsets[layer]=new TranslateTransform();
            layers[layer]=new ImageBrush(tile){TileMode=TileMode.Tile,Stretch=Stretch.Fill,ViewportUnits=BrushMappingMode.Absolute,
                Viewport=new Rect(0,0,TileSize,TileSize),Transform=offsets[layer]};
        }
        loop=new AnimationLoop(this,Render);
        SizeChanged+=(_,_)=>InvalidateVisual();
    }
    private void Render(double delta)
    {
        activeTime+=delta;
        windPhase=(windPhase+delta*.45)%Math.Tau;
        for(var i=0;i<3;i++)
        {
            driftX[i]=(driftX[i]+delta*(5+i*3))%TileSize;
            driftY[i]=(driftY[i]+delta*(22+i*24))%TileSize;
            offsets[i].X=driftX[i]+Math.Sin(windPhase+i)*(6+i*3);
            offsets[i].Y=driftY[i];
        }
    }
    protected override void OnRender(DrawingContext dc)
    {
        base.OnRender(dc);if(ActualWidth<1||ActualHeight<1)return;
        dc.PushClip(new RectangleGeometry(new Rect(RenderSize),21,21));
        foreach(var layer in layers)dc.DrawRectangle(layer,null,new Rect(RenderSize));dc.Pop();
    }
}
