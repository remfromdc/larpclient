using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Threading;
using Microsoft.Win32;

namespace LarpClient;

public partial class MainWindow : Window
{
    private readonly ProfileService profiles = new();
    private readonly LauncherState preferences = LauncherState.Load();
    private readonly CancellationTokenSource lifetime = new();
    private readonly List<Storyboard> ambient = [];
    private readonly MicrosoftLoginService login;
    private readonly MinecraftGameService game;
    private CancellationTokenSource? operation;
    private bool gameRunning;
    private GameInstance? selected;
    private JavaAccount? account;
    private SkinTexture? accountSkin;
    private bool drawerOpen = true;
    private bool navOpen = true;
    private bool refreshing;
    private bool ready;
    private int currentPage;

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    public MainWindow()
    {
        InitializeComponent();
        login = new MicrosoftLoginService(profiles.DataDirectory);
        game = new MinecraftGameService(profiles);
        if (Environment.GetCommandLineArgs().Contains("--disable-snow")) Snow.Visibility = Visibility.Collapsed;
        if (Environment.GetCommandLineArgs().Contains("--disable-wordmark")) Wordmark.Visibility = Visibility.Collapsed;
        if (Environment.GetCommandLineArgs().Contains("--disable-character")) Character.Visibility = Visibility.Collapsed;
        SourceInitialized += (_, _) =>
        {
            var handle = new WindowInteropHelper(this).Handle;
            var rounded = 2; var dark = 1;
            DwmSetWindowAttribute(handle, 33, ref rounded, sizeof(int));
            DwmSetWindowAttribute(handle, 20, ref dark, sizeof(int));
        };
        StateChanged += (_, _) => { OuterBorder.CornerRadius = new CornerRadius(WindowState == WindowState.Maximized ? 0 : 22); UpdateMotion(); };
        Activated += (_, _) => UpdateMotion();
        Deactivated += (_, _) => { InstancePopup.IsOpen = false; UpdateMotion(); };
        Closed += (_, _) => { operation?.Cancel(); lifetime.Cancel(); };
        AnimationToggle.IsChecked = preferences.Animations;
        RotationToggle.IsChecked = preferences.RotateSkin;
        BlurToggle.IsChecked = preferences.TransitionBlur;
        selected = preferences.Instances.FirstOrDefault(i => i.Version == preferences.SelectedVersion && i.Id == preferences.SelectedId) ?? preferences.Instances[0];
        RefreshInstances();
        account = login.ReadProfile() ?? (login.AllowLegacyPreview ? AccountService.ReadAccount(profiles.MinecraftDirectory) : null);
        Character.SetPlayerName(account?.Name);
        if (login.ReadProfile() is not null) LoginButton.Content = "Changer de compte Microsoft";
    }

    private async void WindowLoaded(object sender, RoutedEventArgs e)
    {
        ready = true;
        StartAmbient();
        UpdateMotion();
        Animate(PlayPage, OpacityProperty, 1, .4, 0);
        MonitorStartupRendering();
        if (Environment.GetCommandLineArgs().Any(a => a is "--measure-animation" or "--profile-session")) MeasureAnimation();
        if (preferences.SkinPath is { } path && File.Exists(path))
        {
            try { using var file = File.OpenRead(path); Character.SetTexture(AccountService.DecodeSkin(file), false); }
            catch { preferences.SkinPath = null; }
        }
        if (account is not null)
        {
            try
            {
                accountSkin = await AccountService.FetchSkinAsync(account, lifetime.Token);
                if (preferences.SkinPath is null && accountSkin is not null)
                {
                    Character.SetTexture(accountSkin.Image, accountSkin.Slim);
                }
            }
            catch (OperationCanceledException) { }
            catch { }
        }
    }

    private void RefreshInstances()
    {
        refreshing = true;
        InstanceList.ItemsSource = null;
        PopupInstanceList.ItemsSource = null;
        InstanceList.ItemsSource = preferences.Instances;
        PopupInstanceList.ItemsSource = preferences.Instances;
        InstanceList.SelectedItem = selected;
        PopupInstanceList.SelectedItem = selected;
        refreshing = false;
        UpdateSelection();
    }

    private void UpdateSelection()
    {
        if (selected is null) { LaunchButton.IsEnabled = false; return; }
        LaunchButton.IsEnabled = !gameRunning;
        SelectedInstanceLabel.Text = $"{selected.Name} · {selected.Version}";
        SelectedInstanceLabel.ToolTip = selected.Subtitle;
        preferences.SelectedId = selected.Id;
        preferences.SelectedVersion = selected.Version;
    }

    private void InstanceSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (refreshing || InstanceList.SelectedItem is not GameInstance instance) return;
        selected = instance;
        refreshing = true;
        PopupInstanceList.SelectedItem = selected;
        refreshing = false;
        UpdateSelection();
        SavePreferences();
        if (ready) Character.RunAcross();
    }

    private void PopupSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (refreshing || PopupInstanceList.SelectedItem is not GameInstance instance) return;
        InstanceList.SelectedItem = instance;
        InstancePopup.IsOpen = false;
    }

    private void InstancePickerClick(object sender, RoutedEventArgs e) => InstancePopup.IsOpen = !InstancePopup.IsOpen;
    private void PopupItemClick(object sender, MouseButtonEventArgs e)
    {
        if (ItemsControl.ContainerFromElement(PopupInstanceList, e.OriginalSource as DependencyObject) is ListBoxItem)
            InstancePopup.IsOpen = false;
    }
    private void PopupKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key is Key.Enter or Key.Escape) { InstancePopup.IsOpen = false; InstancePicker.Focus(); e.Handled = true; }
    }
    private void InstancePopupOpened(object? sender, EventArgs e)
    {
        PopupInstanceList.SelectedItem = selected;
        if (!preferences.Animations) return;
        Animate(PopupMotion, TranslateTransform.YProperty, 0, .3, 14);
        Reveal(PopupSurface);
    }

    private void NavigateClick(object sender, RoutedEventArgs e)
    {
        var page = int.Parse((string)((Button)sender).Tag);
        if (page == currentPage) { if (page == 2 && !drawerOpen) ToggleDrawer(); return; }
        var previousPage = currentPage;
        currentPage = page;
        Animate(NavMotion, TranslateTransform.YProperty, page * 58, .42);
        SettingsPage.Visibility = page == 3 ? Visibility.Visible : Visibility.Collapsed;
        PlayPage.Visibility = page == 3 ? Visibility.Collapsed : Visibility.Visible;
        AppearanceBar.Visibility = page == 1 ? Visibility.Visible : Visibility.Collapsed;
        PlayActions.Visibility = page == 1 ? Visibility.Collapsed : Visibility.Visible;


        if (page == 2 && !drawerOpen) ToggleDrawer();
        // Changing navigation should not restart or blur the entire 3D character scene.
        if (page == 3) Reveal(SettingsPage);
        else if (previousPage == 3) Animate(PlayPage, OpacityProperty, 1, .3, .6);
        if (page == 1) Reveal(AppearanceBar);
    }

    private void ToggleNavClick(object sender, RoutedEventArgs e)
    {
        navOpen = !navOpen;
        Animate(NavigationRail, WidthProperty, navOpen ? 186 : 70, .4);
        Animate(NavIndicator, WidthProperty, navOpen ? 168 : 50, .4, NavIndicator.ActualWidth);

        ApplyTransitionBlur(NavigationRail);
    }

    private void ToggleDrawerClick(object sender, RoutedEventArgs e) => ToggleDrawer();
    private void ToggleDrawer()
    {
        drawerOpen = !drawerOpen;
        Animate(DrawerHost, WidthProperty, drawerOpen ? 304 : 0, .45);
        Animate(DrawerMotion, TranslateTransform.XProperty, drawerOpen ? 0 : 80, .45);
        Animate(Drawer, OpacityProperty, drawerOpen ? 1 : 0, .3);
        ApplyTransitionBlur(Drawer);
    }

    private void CreateInstanceClick(object sender, RoutedEventArgs e)
    {
        CreateForm.Visibility = CreateForm.Visibility == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible;
        if (CreateForm.Visibility == Visibility.Visible) { Reveal(CreateForm); NewInstanceName.Focus(); NewInstanceName.SelectAll(); }
    }

    private void ConfirmCreateClick(object sender, RoutedEventArgs e)
    {
        var name = NewInstanceName.Text.Trim();
        if (name.Length == 0) { SetStatus("Donne un nom à ton instance."); return; }
        var version = CreateVersion189.IsChecked == true ? "1.8.9" : CreateVersion121.IsChecked == true ? "1.21.11" : "26.2";
        selected = new GameInstance { Id = Guid.NewGuid().ToString("N"), Name = name, Version = version };
        preferences.Instances.Add(selected);
        RefreshInstances();
        SavePreferences();
        CreateForm.Visibility = Visibility.Collapsed;
    }

    private void PreferencesChanged(object sender, RoutedEventArgs e)
    {
        preferences.Animations = AnimationToggle.IsChecked == true;
        preferences.RotateSkin = RotationToggle.IsChecked == true;
        preferences.TransitionBlur = BlurToggle.IsChecked == true;
        SavePreferences();
        UpdateMotion();
    }

    private void SavePreferences()
    {
        try { preferences.Save(); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { SetStatus("Impossible d’enregistrer les préférences : " + ex.Message); }
    }

    private void StartAmbient()
    {
        if (ambient.Count > 0) return;
        void Loop(DependencyObject target, DependencyProperty property, double from, double to, double seconds)
        {
            var motion = new DoubleAnimation(from, to, TimeSpan.FromSeconds(seconds)) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever, EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut } };
            Storyboard.SetTarget(motion, target);
            Storyboard.SetTargetProperty(motion, new PropertyPath(property));
            var storyboard = new Storyboard(); storyboard.Children.Add(motion);
            storyboard.Begin(this, true); ambient.Add(storyboard);
        }
        Loop(((TransformGroup)SparkOne.RenderTransform).Children[1], TranslateTransform.YProperty, -8, 9, 3.3);
        Loop(SparkTwo.RenderTransform, TranslateTransform.YProperty, 9, -11, 4.1);
        Loop(Orbit, OpacityProperty, .18, .42, 4.5);
    }

    private void UpdateMotion()
    {
        if (!ready) return;
        Character.Animate = preferences.Animations;
        Character.AutoRotate = preferences.RotateSkin;
        Character.MotionBlur = preferences.TransitionBlur;
        Snow.Animate = preferences.Animations;
        ConfigureButtonMotion(this);
        foreach (var animation in ambient)
        {
            if (preferences.Animations && IsActive && WindowState != WindowState.Minimized) animation.Resume(this);
            else animation.Pause(this);
        }
    }

    private void ButtonLoaded(object sender, RoutedEventArgs e) => ConfigureButtonMotion((DependencyObject)sender);
    private void ConfigureButtonMotion(DependencyObject root)
    {
        if (root is FrameworkElement element)
            foreach (VisualStateGroup group in VisualStateManager.GetVisualStateGroups(element))
                foreach (VisualTransition transition in group.Transitions)
                    transition.GeneratedDuration = new Duration(TimeSpan.FromSeconds(preferences.Animations ? .14 : 0));
        for (var i = 0; i < VisualTreeHelper.GetChildrenCount(root); i++)
            ConfigureButtonMotion(VisualTreeHelper.GetChild(root, i));
    }

    private void MeasureAnimation()
    {
        var profileSession = Environment.GetCommandLineArgs().Contains("--profile-session");
        var samples = new List<double>();
        var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        timer.Tick += (_, _) =>
        {
            var active = IsActive && WindowState != WindowState.Minimized;
            if (active && Character.IsVisible && Character.FramesPerSecond > 0)
            { samples.Add(Character.FramesPerSecond); if (samples.Count > 60) samples.RemoveAt(0); }
            if (!profileSession && samples.Count < 10) return;
            if (!profileSession) timer.Stop();
            using var sampleProcess = Process.GetCurrentProcess();
            var metrics = new { Samples = samples, AverageFps = samples.Count > 2 ? samples.Skip(2).Average() : 0, Scheduling = "Every compositor frame",
                CharacterRenderMilliseconds = Character.AverageRenderMilliseconds, RenderBufferBytes = Character.RenderBufferBytes,
                CharacterGpu = Character.HardwareRendering, CharacterDevice = Character.RenderDevice, FrameTimeP95Milliseconds = Character.FrameTimeP95Milliseconds,
                Active = active, CharacterLoopRunning = Character.IsRenderLoopRunning, SnowLoopRunning = Snow.IsRenderLoopRunning,
                AnimationPhase = Character.AnimationPhase.ToString(), CharacterX = Character.CurrentPose.X, SnowTime = Snow.ActiveTime,
                ManagedMemoryBytes = GC.GetTotalMemory(false), WorkingSetBytes = sampleProcess.WorkingSet64,
                Renderer = Character.HardwareRendering ? "OpenGL 3.3 / GPU mesh" : "CPU rasterizer", RenderTier = RenderCapability.Tier >> 16, SnowParticles = Snow.ParticleCount, RenderingMode = RenderOptions.ProcessRenderMode.ToString(),
                Note = "Completed character renders while active; not a measurement of monitor-presented frames." };
            try
            {
                var file=Path.Combine(AppContext.BaseDirectory,"animation-metrics.json");
                File.WriteAllText(file+".tmp",System.Text.Json.JsonSerializer.Serialize(metrics,new System.Text.Json.JsonSerializerOptions { WriteIndented=true }));
                File.Move(file+".tmp",file,true);
            }
            catch (IOException) { }
        };
        Closed += (_, _) => timer.Stop();
        timer.Start();
    }

    private void MonitorStartupRendering()
    {
        // Some driver sessions fail to present even a plain WPF window. Recover without changing Windows settings.
        if (RenderOptions.ProcessRenderMode == RenderMode.SoftwareOnly) return;
        var activeSeconds = 0d;
        var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(.5) };
        timer.Tick += (_, _) =>
        {
            if (!IsActive || WindowState == WindowState.Minimized) return;
            activeSeconds += .5;
            if (activeSeconds < 4) return;
            if (!Character.HardwareRendering && Character.FramesPerSecond is > 0 and < 9)
            {
                timer.Stop();
                RenderOptions.ProcessRenderMode = RenderMode.SoftwareOnly;
                if (PresentationSource.FromVisual(this) is HwndSource source && source.CompositionTarget is { } target)
                    target.RenderMode = RenderMode.SoftwareOnly;
                OuterBorder.InvalidateVisual();
                try
                {
                    Directory.CreateDirectory(profiles.DataDirectory);
                    File.WriteAllText(Path.Combine(profiles.DataDirectory, "graphics-diagnostics.json"),
                        System.Text.Json.JsonSerializer.Serialize(new { Mode = "SoftwareOnly", HardwareUpdatesPerSecond = Character.FramesPerSecond,
                            Reason = "Low hardware presentation cadence at startup", Date = DateTimeOffset.UtcNow }));
                }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
            }
            else if (activeSeconds >= 8)
            {
                timer.Stop();
                try
                {
                    Directory.CreateDirectory(profiles.DataDirectory);
                    File.WriteAllText(Path.Combine(profiles.DataDirectory, "graphics-diagnostics.json"),
                        System.Text.Json.JsonSerializer.Serialize(new { Mode = "Default", Renderer = "PixelSkinRenderer",
                            ObservedUpdatesPerSecond = Character.FramesPerSecond, Date = DateTimeOffset.UtcNow }));
                }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
            }
        };
        Closed += (_, _) => timer.Stop();
        timer.Start();
    }

    private void Animate(DependencyObject target, DependencyProperty property, double to, double seconds, double? from = null)
    {
        if (target is not IAnimatable animated) return;
        if (!preferences.Animations) { animated.BeginAnimation(property, null); target.SetValue(property, to); return; }
        var animation = new DoubleAnimation { To = to, Duration = TimeSpan.FromSeconds(seconds), EasingFunction = new QuarticEase { EasingMode = EasingMode.EaseOut } };
        if (from is { } initial) animation.From = initial;
        animated.BeginAnimation(property, animation, HandoffBehavior.SnapshotAndReplace);
    }

    private void Reveal(UIElement element)
    {
        Animate(element, OpacityProperty, 1, .42, .3);
        if (element.RenderTransform is TranslateTransform transform) Animate(transform, TranslateTransform.YProperty, 0, .42, 12);
        ApplyTransitionBlur(element);
    }

    private void ApplyTransitionBlur(UIElement element)
    {
        if (!preferences.Animations || !preferences.TransitionBlur || RenderOptions.ProcessRenderMode == RenderMode.SoftwareOnly) { element.Effect = null; return; }
        var effect = new BlurEffect { Radius = 0, RenderingBias = RenderingBias.Performance };
        element.Effect = effect;
        var animation = new DoubleAnimation(5, 0, TimeSpan.FromSeconds(.36)) { EasingFunction = new QuarticEase { EasingMode = EasingMode.EaseOut } };
        animation.Completed += (_, _) => { if (ReferenceEquals(element.Effect, effect)) element.Effect = null; };
        effect.BeginAnimation(BlurEffect.RadiusProperty, animation);
    }

    private void ImportSkinClick(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog { Filter = "Skin Minecraft (*.png)|*.png", Title = "Choisir un skin Minecraft", CheckFileExists = true };
        if (dialog.ShowDialog(this) != true) return;
        try
        {
            if (new FileInfo(dialog.FileName).Length > 1024 * 1024) throw new InvalidDataException("Le skin doit peser moins de 1 Mo.");
            using var source = File.OpenRead(dialog.FileName);
            var bitmap = AccountService.DecodeSkin(source);
            var destination = Path.Combine(profiles.DataDirectory, "skin-local.png");
            Directory.CreateDirectory(profiles.DataDirectory);
            var encoder = new System.Windows.Media.Imaging.PngBitmapEncoder();
            encoder.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bitmap));
            using (var output = File.Create(destination)) encoder.Save(output);
            preferences.SkinPath = destination;
            Character.SetTexture(bitmap, false);

            SavePreferences();
        }
        catch (Exception ex) { SetStatus(ex.Message); }
    }

    private void ResetSkinClick(object sender, RoutedEventArgs e)
    {
        preferences.SkinPath = null;
        Character.SetTexture(accountSkin?.Image ?? SkinScene.DefaultSkin(), accountSkin?.Slim ?? false);

        SavePreferences();
    }

    private void SetStatus(string message,bool approvalRequired=false)
    {
        InstancePopup.IsOpen=false;
        StatusMessage.Text=message;
        StatusApprovalButton.Visibility=approvalRequired?Visibility.Visible:Visibility.Collapsed;
        StatusOverlay.Visibility=Visibility.Visible;
        StatusDismissButton.Focus();
    }
    private void ShowOperationError(Exception ex)=>SetStatus(ex.Message,ex is MinecraftAuthenticationException {Failure:AuthenticationFailure.ApplicationNotApproved});
    private void DismissStatusClick(object sender,RoutedEventArgs e) { StatusOverlay.Visibility=Visibility.Collapsed;LaunchButton.Focus(); }
    private void OpenApprovalClick(object sender,RoutedEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo(MicrosoftLoginService.ApplicationApprovalUrl) {UseShellExecute=true}); }
        catch { StatusMessage.Text="Le navigateur n’a pas pu être ouvert. Le lien de la demande d’autorisation est dans MICROSOFT-SETUP.md."; }
    }

    private void SetOperation(bool busy)
    {
        InstancePicker.IsEnabled = InstanceList.IsEnabled = InstallButton.IsEnabled = LoginButton.IsEnabled = LogoutButton.IsEnabled = !busy;
        LaunchLabel.Text = busy ? "ANNULER" : "JOUER";
        LaunchButton.IsEnabled = !gameRunning;
        OperationPanel.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
        CancelOperationButton.IsEnabled = busy;
        if (busy && !drawerOpen) ToggleDrawer();
    }
    private void ReportProgress(GameProgress value)
    {
        InstallationStatus.Text = value.Message;
        InstallationProgress.IsIndeterminate = value.Percent is null;
        InstallationProgress.Value = value.Percent ?? 0;
    }
    private void CancelOperationClick(object sender, RoutedEventArgs e) { if (!gameRunning) operation?.Cancel(); }
    private async Task ApplyAccount(CmlLib.Core.Auth.MSession session, CancellationToken ct)
    {
        account = new(session.Username!, session.UUID!);
        Character.SetPlayerName(account.Name);
        LoginButton.Content = "Changer de compte Microsoft";
        try
        {
            accountSkin = await AccountService.FetchSkinAsync(account, ct);
            if (preferences.SkinPath is null && accountSkin is not null) Character.SetTexture(accountSkin.Image, accountSkin.Slim);
        }
        catch (OperationCanceledException) { throw; }
        catch { /* A skin lookup must not prevent an authenticated game launch. */ }
    }
    private async void LaunchClick(object sender, RoutedEventArgs e)
    {
        if (operation is not null) { if (!gameRunning) operation.Cancel(); return; }
        if (selected is null) return;
        var instance = selected;
        operation = CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token);
        var ct = operation.Token;
        SetOperation(true);
        try
        {
            // Install first: downloading Minecraft never requires the official launcher or a configured login.
            await game.InstallAsync(instance, new Progress<GameProgress>(ReportProgress), ct);
            ReportProgress(new("Connexion Microsoft…"));
            var session = await login.AuthenticateAsync(false, ct);
            await ApplyAccount(session, ct);
            ReportProgress(new("Lancement de Minecraft…"));
            using var process = await game.BuildProcessAsync(instance, session, login.ClientId, ct);
            ct.ThrowIfCancellationRequested();
            if (!process.Start()) throw new InvalidOperationException("Minecraft n’a pas pu démarrer.");
            gameRunning = true; LaunchButton.IsEnabled = false; LaunchLabel.Text = "EN JEU";
            CancelOperationButton.IsEnabled = false;
            ReportProgress(new("Minecraft est lancé", 100));
            WindowState = WindowState.Minimized;
            await process.WaitForExitAsync(ct);
            if (process.ExitCode != 0 && !ct.IsCancellationRequested)
            {
                WindowState = WindowState.Normal;
                SetStatus($"Minecraft s’est fermé avec le code {process.ExitCode}. Le journal est disponible dans le dossier de l’instance : logs/latest.log.");
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { if (!ct.IsCancellationRequested) ShowOperationError(ex); }
        finally { gameRunning = false; operation.Dispose(); operation = null; SetOperation(false); }
    }
    private async void InstallClick(object sender, RoutedEventArgs e)
    {
        if (operation is not null || selected is null) return;
        operation = CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token);
        var ct = operation.Token; SetOperation(true);
        try
        {
            await game.InstallAsync(selected, new Progress<GameProgress>(ReportProgress), ct);
            Character.SetPlayerName(account?.Name); // Reload the local Minecraft font after a fresh install.
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { if (!ct.IsCancellationRequested) ShowOperationError(ex); }
        finally { operation.Dispose(); operation = null; SetOperation(false); }
    }
    private async void LoginClick(object sender, RoutedEventArgs e)
    {
        if (operation is not null) return;
        operation = CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token);
        var ct = operation.Token; SetOperation(true); ReportProgress(new("Connexion Microsoft…"));
        try { await ApplyAccount(await login.AuthenticateAsync(login.ReadProfile() is not null, ct), ct); }
        catch (OperationCanceledException) { }
        catch (Exception ex) { if (!ct.IsCancellationRequested) ShowOperationError(ex); }
        finally { operation.Dispose(); operation = null; SetOperation(false); }
    }
    private async void LogoutClick(object sender, RoutedEventArgs e)
    {
        if (operation is not null) return;
        try
        {
            await login.SignOutAsync(lifetime.Token);
            account = null; accountSkin = null; Character.SetPlayerName(null);
            LoginButton.Content = "Se connecter à Microsoft";
            if (preferences.SkinPath is null) Character.SetTexture(SkinScene.DefaultSkin(), false);
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { SetStatus(ex.Message); }
    }

    private void OpenFolderClick(object sender, RoutedEventArgs e)
    {
        if (selected is null) return;
        try
        {
            var folder = profiles.InstanceDirectory(selected.Version, selected.Id);
            Directory.CreateDirectory(folder);
            Process.Start(new ProcessStartInfo(folder) { UseShellExecute = true });
        }
        catch (Exception ex) { SetStatus(ex.Message); }
    }

    private void MinimizeClick(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;
    private void MaximizeClick(object sender, RoutedEventArgs e) => WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    private void CloseClick(object sender, RoutedEventArgs e) => Close();
    private void ReplayCharacterClick(object sender, RoutedEventArgs e) => Character.ReplayEntrance();
    private void WindowKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape) { StatusOverlay.Visibility=Visibility.Collapsed;InstancePopup.IsOpen = false; CreateForm.Visibility = Visibility.Collapsed; }
    }
}
