package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import java.util.HashMap;
import java.util.Map;

public class BadPacketsCheck {
    private final Map<String, CheckBuffer> rotationBuffers = new HashMap<>();
    private final Map<String, CheckBuffer> movementBuffers = new HashMap<>();

    public void check(EntityPlayer player, ClientAntiCheatContext context) {
        check(player, null, context);
    }

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        String name = player.getName();
        CheckBuffer rotationBuffer = this.rotationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
        CheckBuffer movementBuffer = this.movementBuffers.computeIfAbsent(name, key -> new CheckBuffer());

        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        boolean invalidPitch = pitch > 90.0F || pitch < -90.0F || Float.isNaN(pitch) || Float.isInfinite(pitch);
        boolean invalidYaw = Float.isNaN(yaw) || Float.isInfinite(yaw);
        boolean hugeRotation = false;
        boolean duplicateImpossibleLook = false;
        if (data != null && !data.recentlyTeleported()) {
            hugeRotation = data.yawDelta > 170.0F && data.pitchDelta > 65.0F;
            duplicateImpossibleLook = data.yawDelta == 0.0F && data.pitchDelta == 0.0F && data.horizontalDelta > 0.25D && player.swingProgress > 0.0F;
        }

        if (invalidPitch || invalidYaw || hugeRotation || duplicateImpossibleLook) {
            if (rotationBuffer.flag(invalidPitch || invalidYaw ? 2.0D : 1.0D, 2.5D)) {
                context.receiveSignal(name, invalidPitch ? "BadPacketsPitch" : "BadPacketsRotation");
                rotationBuffer.reset();
            }
        } else {
            rotationBuffer.decay(0.45D);
        }

        if (data != null && !data.recentlyTeleported()) {
            double delta = Math.sqrt(data.deltaX * data.deltaX + data.deltaY * data.deltaY + data.deltaZ * data.deltaZ);
            boolean impossibleDelta = delta > 6.0D && !player.capabilities.isFlying && !player.isRiding();
            boolean groundSpoofPattern = data.onGround && data.lastOnGround && Math.abs(data.deltaY) > 0.42D;
            if (impossibleDelta || groundSpoofPattern) {
                if (movementBuffer.flag(1.0D, 2.0D)) {
                    context.receiveSignal(name, "BadPacketsMove");
                    movementBuffer.reset();
                }
            } else {
                movementBuffer.decay(0.35D);
            }
        }
    }

    public void reset() {
        this.rotationBuffers.clear();
        this.movementBuffers.clear();
    }
}
