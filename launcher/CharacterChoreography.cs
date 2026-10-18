namespace LarpClient;

public enum CharacterPhase { Entrance, Settle, Idle, ExitLeft, SprintAcross, Return }

public readonly record struct CharacterPose(double X, double Y, double Yaw, double LeftArm, double RightArm,
    double LeftLeg, double RightLeg, double Lean, double Head, double ScaleY);

// Uses active animation time only: switching windows never skips a step of the sequence.
public sealed class CharacterChoreography
{
    public CharacterPhase Phase { get; private set; } = CharacterPhase.Entrance;
    public double PhaseTime { get; private set; }
    private double time;
    private double idleYaw = -20;
    private bool runQueued;
    private double settleYaw = -20;
    public CharacterPose Pose { get; private set; } = new(-32, 0, 90, 0, 0, 0, 0, 0, 0, 1);

    private static double Smooth(double p) { p = Math.Clamp(p, 0, 1); return p * p * p * (p * (p * 6 - 15) + 10); }
    private static double Mix(double a, double b, double p) => a + (b - a) * p;
    private static double MixAngle(double a, double b, double p) => a + Math.IEEERemainder(b - a, 360) * p;
    private void Enter(CharacterPhase phase) { Phase = phase; PhaseTime = 0; }
    public void QueueRun() => runQueued = true;
    public void ReplayEntrance() { runQueued = false; Enter(CharacterPhase.Entrance); }
    public void SetManualYaw(double yaw)
    {
        idleYaw = yaw;
        runQueued = false;
        Enter(CharacterPhase.Idle);
        Pose = Pose with { Yaw = yaw };
    }

    public CharacterPose Advance(double delta, bool autoRotate, bool reducedMotion = false, bool interacting = false)
    {
        if (reducedMotion)
        {
            Enter(CharacterPhase.Idle);
            runQueued = false;
            return Pose = new(0, 0, idleYaw, 0, 0, 0, 0, 0, 0, 1);
        }
        delta = Math.Clamp(delta, 0, .05);
        time += delta;
        PhaseTime += delta;
        var x = 0d; var y = 0d; var yaw = idleYaw;
        var gait = 0d; var amplitude = 0d; var lean = 0d; var scale = 1d;
        switch (Phase)
        {
            case CharacterPhase.Entrance:
                var p = Math.Clamp(PhaseTime / 2.8, 0, 1);
                x = Mix(-32, 0, Smooth(p));
                yaw = Mix(90, -20, Smooth((p - .76) / .24));
                amplitude = 30 * Math.Min(p * 8, 1) * (1 - Smooth((p - .76) / .24));
                gait = Math.Sin(PhaseTime * 9);
                y = Math.Abs(gait) * .5 * amplitude / 30;
                if (p >= 1) { settleYaw = -20; Enter(CharacterPhase.Settle); }
                break;
            case CharacterPhase.Settle:
                var settle = Math.Clamp(PhaseTime / .65, 0, 1);
                yaw = Mix(settleYaw, -20, Smooth(settle));
                scale = 1 - Math.Sin(settle * Math.PI) * .035;
                if (settle >= 1) { idleYaw = -20; Enter(CharacterPhase.Idle); }
                break;
            case CharacterPhase.Idle:
                // Ease back to the pedestal if the player grabbed the model mid-stride.
                x = Pose.X * Math.Exp(-delta * 7);
                if (Math.Abs(x) < .001) x = 0;
                if (autoRotate && !interacting) idleYaw += delta * 12;
                yaw = idleYaw;
                y = Math.Sin(time * 1.9) * .14;
                amplitude = 4;
                gait = Math.Sin(time * 1.9);
                if (!interacting && (runQueued || PhaseTime > 34)) { runQueued = false; Enter(CharacterPhase.ExitLeft); }
                break;
            case CharacterPhase.ExitLeft:
                var exit = Math.Clamp(PhaseTime / 1.05, 0, 1);
                x = Mix(0, -34, Smooth(exit));
                yaw = MixAngle(idleYaw, -90, Smooth(exit * 4));
                amplitude = 52 * Math.Min(exit * 5, 1);
                gait = Math.Sin(PhaseTime * 17);
                y = Math.Abs(gait) * 1.1; lean = 10 * Math.Min(exit * 4, 1);
                if (exit >= 1) Enter(CharacterPhase.SprintAcross);
                break;
            case CharacterPhase.SprintAcross:
                var sprint = Math.Clamp(PhaseTime / 2.35, 0, 1);
                x = Mix(-34, 34, sprint);
                yaw = 90;
                amplitude = 58;
                gait = Math.Sin(PhaseTime * 18);
                y = Math.Abs(gait) * 1.2; lean = 12;
                if (sprint >= 1) Enter(CharacterPhase.Return);
                break;
            case CharacterPhase.Return:
                var back = Math.Clamp(PhaseTime / 1.8, 0, 1);
                x = Mix(34, 0, Smooth(back));
                yaw = Mix(-90, -20, Smooth((back - .7) / .3));
                amplitude = 36 * (1 - Smooth((back - .6) / .4));
                gait = Math.Sin(PhaseTime * 10);
                y = Math.Abs(gait) * .6 * amplitude / 36;
                if (back >= 1) { settleYaw = -20; Enter(CharacterPhase.Settle); }
                break;
        }
        return Pose = new(x, y, yaw, -gait * amplitude * .85, gait * amplitude * .85,
            gait * amplitude, -gait * amplitude, lean, Math.Sin(time * .8) * 5, scale);
    }
}
