using System.Windows;

namespace LarpClient;
public partial class App : Application
{
    public App()
    {
        var arguments = Environment.GetCommandLineArgs();
        if (arguments.Contains("--software-render"))
            System.Windows.Media.RenderOptions.ProcessRenderMode = System.Windows.Interop.RenderMode.SoftwareOnly;
    }
}
