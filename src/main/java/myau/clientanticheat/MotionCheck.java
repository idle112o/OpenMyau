package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;

public class MotionCheck {
    private final Map<String, CheckBuffer> speedBuffers = new HashMap<>();
    private final Map<String, CheckBuffer> airStallBuffers = new HashMap<>();

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        String name = player.getName();
        CheckBuffer speedBuffer = this.speedBuffers.computeIfAbsent(name, key -> new CheckBuffer());
        CheckBuffer airBuffer = this.airStallBuffers.computeIfAbsent(name, key -> new CheckBuffer());
        if (isExempt(player, data)) {
            speedBuffer.decay(1.0D);
            airBuffer.decay(1.0D);
            return;
        }

        double limit = data.predictedHorizontalLimit(player);
        if (data.horizontalDelta > limit && !player.isCollidedHorizontally) {
            if (speedBuffer.flag(1.0D + Math.min(1.0D, data.horizontalDelta - limit), 4.0D)) {
                context.receiveSignal(name, "Motion");
                speedBuffer.reset();
            }
        } else {
            speedBuffer.decay(0.35D);
        }

        boolean airStall = !data.onGround
                && data.airTicks >= 7
                && Math.abs(data.deltaY) < 0.003D
                && data.horizontalDelta < 0.025D
                && player.fallDistance > 0.0F
                && !data.recentlyHurt();
        if (airStall) {
            if (airBuffer.flag(1.0D, 3.5D)) {
                context.receiveSignal(name, "Motion");
                airBuffer.reset();
            }
        } else {
            airBuffer.decay(0.4D);
        }
    }

    private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
        return player == null
                || data == null
                || player.isDead
                || player.ticksExisted < 20
                || data.recentlyTeleported()
                || player.isInWater()
                || player.isInLava()
                || player.isOnLadder()
                || player.isRiding()
                || player.capabilities.isFlying
                || player.capabilities.disableDamage;
    }

    public void reset() {
        this.speedBuffers.clear();
        this.airStallBuffers.clear();
    }
}
