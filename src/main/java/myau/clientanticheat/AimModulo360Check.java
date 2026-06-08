package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;

public class AimModulo360Check {
    private final Map<String, Float> lastDeltaYaw = new HashMap<>();
    private final Map<String, CheckBuffer> buffers = new HashMap<>();

    public void check(EntityPlayer player, ClientAntiCheatContext context) {
        check(player, null, context);
    }

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        String name = player.getName();
        CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());
        float delta = data != null ? data.yaw - data.lastYaw : 0.0F;
        float wrapped = data != null ? data.yawDelta : Math.abs(delta);
        float lastDelta = this.lastDeltaYaw.getOrDefault(name, 0.0F);
        boolean moduloSnap = data != null
                && Math.abs(delta) > 320.0F
                && wrapped < 45.0F
                && Math.abs(lastDelta) < 45.0F
                && !data.recentlyTeleported();
        if (moduloSnap) {
            if (buffer.flag(1.0D, 1.5D)) {
                context.receiveSignal(name, "AimModulo360");
                buffer.reset();
            }
        } else {
            buffer.decay(0.35D);
        }
        this.lastDeltaYaw.put(name, delta);
    }

    public void reset() {
        this.lastDeltaYaw.clear();
        this.buffers.clear();
    }
}
