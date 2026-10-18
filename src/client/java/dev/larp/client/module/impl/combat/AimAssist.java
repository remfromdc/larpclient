package dev.larp.client.module.impl.combat;

import dev.larp.client.LarpClient;
import dev.larp.client.event.Priority;
import dev.larp.client.event.events.TickEvent;
import dev.larp.client.module.Module;
import dev.larp.client.rotation.Bone;
import dev.larp.client.rotation.Rotation;
import dev.larp.client.rotation.RotationConfig;
import dev.larp.client.rotation.RotationTarget;
import dev.larp.client.rotation.Smoothing;
import dev.larp.client.setting.BoolSetting;
import dev.larp.client.setting.EnumSetting;
import dev.larp.client.setting.Measure;
import dev.larp.client.setting.MultiSetting;
import dev.larp.client.setting.SliderSetting;
import dev.larp.client.setting.TargetSettings;
import dev.larp.client.util.Entities;
import dev.larp.client.util.Game;
import dev.larp.client.util.Maths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class AimAssist extends Module {
    private static final String[] MODES = {"Regular", "Silent"};
    private static final int SILENT = 1;
    private static final String[] PRIORITIES = {"Closest to FOV", "Lowest health"};
    private static final int LOWEST_HEALTH = 1;
    private static final double RANGE = 6.0;

    private record Aim(LocalPlayer player, LivingEntity entity, Bone bone) implements RotationTarget {
        @Override
        public Rotation at(float partialTick) {
            return Rotation.toward(player.getEyePosition(partialTick), bone.point(player, entity, partialTick));
        }
    }

    private final SliderSetting fov = add(new SliderSetting("fov", 80, 10, 360, Measure.DEGREES));
    private final EnumSetting mode = add(new EnumSetting("mode", 0, MODES));
    private final EnumSetting rotation = add(new EnumSetting("rotation", 0, Smoothing.NAMES));
    private final SliderSetting smoothness = add(new SliderSetting("smoothness", 50, 0, 100, Measure.PERCENT));
    private final BoolSetting onHold = add(new BoolSetting("onHold", false));
    private final BoolSetting weaponsOnly = add(new BoolSetting("weaponsOnly", false));
    private final EnumSetting target = add(new EnumSetting("target", 0, PRIORITIES));
    private final TargetSettings targets = add(new TargetSettings());
    private final MultiSetting bones = add(new MultiSetting("bones", Bone.NAMES, Bone.HEAD.ordinal()));
    private boolean targeting;
    private LivingEntity current;

    public AimAssist() {
        super("aimAssist");
        bones.optionVisibleWhen(option -> option == Bone.MULTIPOINT.ordinal() || !bones.get().contains(Bone.MULTIPOINT.ordinal()));
    }

    @Override
    public String info() {
        return mode.selected();
    }

    public boolean blocksBreaking() {
        return isEnabled() && targeting;
    }

    public LivingEntity target() {
        return isEnabled() ? current : null;
    }

    @Override
    protected void onEnable() {
        listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        targeting = false;
        current = null;
    }

    private void onTick(TickEvent event) {
        targeting = false;
        current = null;
        if (onHold.get() && !event.client().options.keyAttack.isDown()) return;
        Aim aim = aim(event.client());
        if (aim == null) return;
        targeting = true;
        current = aim.entity();
        Smoothing smoothing = Smoothing.values()[rotation.get()];
        RotationConfig config = mode.get() == SILENT
                ? RotationConfig.silent(smoothness.get() / 100f, smoothing)
                : RotationConfig.visible(smoothness.get() / 100f, smoothing);
        LarpClient.ROTATIONS.aim(aim, Priority.NORMAL, config);
    }

    private Aim aim(Minecraft client) {
        LocalPlayer player = client.player;
        if (!Game.playing(client)) return null;
        if (weaponsOnly.get() && !Entities.holdsWeapon(player)) return null;
        Aim best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE))) {
            if (!targeted(player, entity)) continue;
            Bone closest = null;
            double closestAngle = fov.get() * 0.5;
            for (Bone bone : Bone.ALL) {
                if (!bones.has(bone.ordinal())) continue;
                double angle = Maths.angle(player.getLookAngle(), bone.point(player, entity, 1f).subtract(player.getEyePosition()));
                if (angle >= closestAngle) continue;
                closest = bone;
                closestAngle = angle;
            }
            if (closest == null) continue;
            double score = target.get() == LOWEST_HEALTH ? entity.getHealth() : closestAngle;
            if (score >= bestScore) continue;
            best = new Aim(player, entity, closest);
            bestScore = score;
        }
        return best;
    }

    private boolean targeted(LocalPlayer player, LivingEntity entity) {
        return targets.accepts(player, entity) && player.distanceToSqr(entity) <= RANGE * RANGE && player.hasLineOfSight(entity);
    }
}
