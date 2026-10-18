using System.Diagnostics;
using System.Windows;
using System.Windows.Media;

namespace LarpClient;

// No rendering subscription remains attached while hidden, unfocused, disabled or minimized.
public sealed class AnimationLoop : IDisposable
{
    private readonly FrameworkElement element;
    private readonly Action<double> step;
    private Window? owner;
    private bool subscribed;
    private bool enabled=true;
    private double last;
    private TimeSpan lastRenderingTime=TimeSpan.MinValue;
    private bool firstFrame;
    public bool Enabled {get=>enabled;set {enabled=value;Update();}}
    public bool IsRunning=>subscribed;
    public AnimationLoop(FrameworkElement element,Action<double> step)
    {
        this.element=element;this.step=step;
        element.Loaded+=Loaded; element.Unloaded+=Unloaded; element.IsVisibleChanged+=VisibilityChanged;
    }
    private void Loaded(object sender,RoutedEventArgs e)
    {
        AttachOwner(Window.GetWindow(element));Update();
    }
    private void Unloaded(object sender,RoutedEventArgs e){Stop();AttachOwner(null);}
    private void VisibilityChanged(object sender,DependencyPropertyChangedEventArgs e)=>Update();
    private void OwnerChanged(object? sender,EventArgs e)=>Update();
    private void AttachOwner(Window? window)
    {
        if(owner==window)return;
        if(owner is not null){owner.Activated-=OwnerChanged;owner.Deactivated-=OwnerChanged;owner.StateChanged-=OwnerChanged;owner.Closed-=OwnerClosed;}
        owner=window;
        if(owner is not null){owner.Activated+=OwnerChanged;owner.Deactivated+=OwnerChanged;owner.StateChanged+=OwnerChanged;owner.Closed+=OwnerClosed;}
    }
    private void OwnerClosed(object? sender,EventArgs e)=>Dispose();
    private void Update()
    {
        var shouldRun=enabled&&element.IsLoaded&&element.IsVisible&&owner is {IsActive:true,WindowState:not WindowState.Minimized};
        if(!shouldRun){Stop();return;}
        if(subscribed)return;
        last=Stopwatch.GetTimestamp()/(double)Stopwatch.Frequency;lastRenderingTime=TimeSpan.MinValue;firstFrame=true;
        subscribed=true;CompositionTarget.Rendering+=Render;
    }
    private void Stop(){if(!subscribed)return;subscribed=false;CompositionTarget.Rendering-=Render;}
    private void Render(object? sender,EventArgs e)
    {
        if(e is RenderingEventArgs args){if(args.RenderingTime==lastRenderingTime)return;lastRenderingTime=args.RenderingTime;}
        var now=Stopwatch.GetTimestamp()/(double)Stopwatch.Frequency;
        // Follow every distinct compositor frame. Quantizing a 60/144/165 Hz display to
        // 90 ticks caused alternating short/long frame gaps even when the average said 90.
        var delta=now-last;last=now;
        // Resume/sleep stalls never advance the character or flood a catch-up queue.
        var resume=firstFrame;firstFrame=false;
        step(resume||delta>.25?0:Math.Clamp(delta,0,.05));
    }
    public void Dispose()
    {
        Stop();AttachOwner(null);element.Loaded-=Loaded;element.Unloaded-=Unloaded;element.IsVisibleChanged-=VisibilityChanged;
    }
}
