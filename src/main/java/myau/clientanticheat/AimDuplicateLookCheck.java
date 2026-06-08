package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class AimDuplicateLookCheck {
    private final Map<String, CheckBuffer> duplicateBuffers = new HashMap<>();

    public void check(EntityPlayer player, World world, ClientAntiCheatContext context) {
        check(player, world, null, context);
    }

    public void check(EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
        if (data == null || data.recentlyTeleported()) return;
        String name = player.getName();
        CheckBuffer buffer = this.duplicateBuffers.computeIfAbsent(name, key -> new CheckBuffer());
        boolean duplicate = data.yawDelta == 0.0F && data.pitchDelta == 0.0F;
        boolean combat = player.swingProgress > 0.0F && this.hasNearbyTarget(player, world);
        boolean suspicious = duplicate && combat && data.horizontalDelta > 0.03D && player.ticksExisted > 40;
        if (suspicious) {
            if (buffer.flag(1.0D, 7.0D)) {
                context.receiveSignal(name, "AimDuplicateLook");
                buffer.reset();
            }
        } else {
            buffer.decay(0.45D);
        }
    }

    private boolean hasNearbyTarget(EntityPlayer player, World world) {
        for (EntityPlayer target : world.playerEntities) {
            if (target != player && !target.isDead && player.getDistanceToEntity(target) < 6.0F) return true;
        }
        return false;
    }

    public void reset() {
        this.duplicateBuffers.clear();
    }
}
