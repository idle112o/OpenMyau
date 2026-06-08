package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;

public class NoFallCheck {
    private final Map<String, Float> trackedFallDistance = new HashMap<>();
    private final Map<String, CheckBuffer> landingBuffers = new HashMap<>();

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        String name = player.getName();
        CheckBuffer buffer = this.landingBuffers.computeIfAbsent(name, key -> new CheckBuffer());
        if (isExempt(player, data)) {
            reset(name);
            return;
        }

        if (!data.onGround && data.deltaY < 0.0D) {
            float fall = this.trackedFallDistance.getOrDefault(name, 0.0F) + (float) -data.deltaY;
            this.trackedFallDistance.put(name, Math.max(fall, player.fallDistance));
            buffer.decay(0.1D);
            return;
        }

        float fall = this.trackedFallDistance.getOrDefault(name, 0.0F);
        boolean landed = data.onGround && !data.lastOnGround;
        if (landed && fall > 3.1F) {
            boolean noDamage = player.hurtTime == 0 && player.hurtResistantTime <= 10;
            boolean suspiciousReset = player.fallDistance < 0.5F || fall - player.fallDistance > 2.0F;
            if (noDamage && suspiciousReset) {
                if (buffer.flag(1.0D + Math.min(1.0D, fall / 8.0F), 2.0D)) {
                    context.receiveSignal(name, "NoFall");
                    reset(name);
                    return;
                }
            } else {
                buffer.decay(0.75D);
            }
        } else {
            buffer.decay(0.25D);
        }

        if (data.onGround) {
            this.trackedFallDistance.remove(name);
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

    private void reset(String name) {
        this.trackedFallDistance.remove(name);
        this.landingBuffers.remove(name);
    }

    public void reset() {
        this.trackedFallDistance.clear();
        this.landingBuffers.clear();
    }
}
