package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

public class PlayerCheckData {
    public final String name;

    public double lastX;
    public double lastY;
    public double lastZ;
    public double x;
    public double y;
    public double z;
    public double deltaX;
    public double deltaY;
    public double deltaZ;
    public double horizontalDelta;
    public double lastHorizontalDelta;

    public float yaw;
    public float pitch;
    public float lastYaw;
    public float lastPitch;
    public float yawDelta;
    public float pitchDelta;

    public boolean onGround;
    public boolean lastOnGround;
    public int groundTicks;
    public int airTicks;
    public int hurtTicks;
    public int sinceHurtTicks = 999;
    public int teleportTicks;
    public int existedTicks;
    public float observedFallDistance;

    public PlayerCheckData(EntityPlayer player) {
        this.name = player.getName();
        this.x = this.lastX = player.posX;
        this.y = this.lastY = player.posY;
        this.z = this.lastZ = player.posZ;
        this.yaw = this.lastYaw = player.rotationYaw;
        this.pitch = this.lastPitch = player.rotationPitch;
        this.onGround = this.lastOnGround = player.onGround;
    }

    public void update(EntityPlayer player) {
        this.existedTicks++;
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
        this.lastOnGround = this.onGround;
        this.lastHorizontalDelta = this.horizontalDelta;

        this.x = player.posX;
        this.y = player.posY;
        this.z = player.posZ;
        this.yaw = player.rotationYaw;
        this.pitch = player.rotationPitch;
        this.onGround = player.onGround;

        this.deltaX = this.x - this.lastX;
        this.deltaY = this.y - this.lastY;
        this.deltaZ = this.z - this.lastZ;
        this.horizontalDelta = Math.hypot(this.deltaX, this.deltaZ);
        this.yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(this.yaw - this.lastYaw));
        this.pitchDelta = Math.abs(this.pitch - this.lastPitch);

        double totalDelta = Math.sqrt(this.deltaX * this.deltaX + this.deltaY * this.deltaY + this.deltaZ * this.deltaZ);
        this.teleportTicks = totalDelta > 8.0D ? 4 : Math.max(0, this.teleportTicks - 1);
        this.groundTicks = this.onGround ? this.groundTicks + 1 : 0;
        this.airTicks = this.onGround ? 0 : this.airTicks + 1;
        this.hurtTicks = player.hurtTime > 0 ? player.hurtTime : Math.max(0, this.hurtTicks - 1);
        this.sinceHurtTicks = player.hurtTime > 0 ? 0 : Math.min(999, this.sinceHurtTicks + 1);

        if (!this.onGround && this.deltaY < 0.0D) {
            this.observedFallDistance += (float) -this.deltaY;
        } else if (this.onGround) {
            this.observedFallDistance = 0.0F;
        }
    }

    public boolean recentlyTeleported() {
        return this.teleportTicks > 0 || this.existedTicks < 5;
    }

    public boolean recentlyHurt() {
        return this.sinceHurtTicks <= 10 || this.hurtTicks > 0;
    }

    public double predictedHorizontalLimit(EntityPlayer player) {
        double limit = this.onGround ? 0.36D : 0.62D;
        if (player.isSprinting()) limit += 0.08D;
        if (player.isSneaking()) limit += 0.02D;
        if (player.isUsingItem()) limit += 0.02D;
        if (player.isPotionActive(Potion.moveSpeed)) {
            int amplifier = player.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1;
            limit += amplifier * 0.075D;
        }
        if (player.isPotionActive(Potion.jump)) limit += 0.04D;
        if (this.recentlyHurt()) limit += 0.35D;
        return limit;
    }
}
