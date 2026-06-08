package myau.clientanticheat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScaffoldCheck {
    private final Map<UUID, CheckBuffer> supportBuffers = new HashMap<>();
    private final Map<UUID, CheckBuffer> rotationBuffers = new HashMap<>();
    private final Map<UUID, CheckBuffer> pitchBuffers = new HashMap<>();
    private final Map<UUID, Long> lastFlag = new HashMap<>();

    public void check(EntityPlayer player, World world, ClientAntiCheatContext context) {
        check(player, world, null, context);
    }

    public void check(EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
        UUID uuid = player.getUniqueID();
        ItemStack held = player.getHeldItem();
        boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
        CheckBuffer supportBuffer = this.supportBuffers.computeIfAbsent(uuid, key -> new CheckBuffer());
        CheckBuffer rotationBuffer = this.rotationBuffers.computeIfAbsent(uuid, key -> new CheckBuffer());
        CheckBuffer pitchBuffer = this.pitchBuffers.computeIfAbsent(uuid, key -> new CheckBuffer());
        if (!holdingBlock || player.isInWater() || player.isInLava() || player.isOnLadder() || data != null && data.recentlyTeleported()) {
            supportBuffer.decay(0.5D);
            rotationBuffer.decay(0.5D);
            pitchBuffer.decay(0.5D);
            return;
        }

        double horizontalSpeed = data != null ? data.horizontalDelta : Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        boolean movingFast = horizontalSpeed > 0.15D;
        boolean airborne = data != null ? !data.onGround : !player.onGround;
        boolean falling = data != null ? data.deltaY < -0.08D : player.motionY < -0.08D;
        boolean supportBelow = this.hasSupportBelow(player, world);

        if (airborne && falling && movingFast && supportBelow) {
            supportBuffer.flag(1.4D, 999.0D);
        } else {
            supportBuffer.decay(0.35D);
        }

        float yawDiff = data != null ? data.yawDelta : 0.0F;
        float pitchDiff = data != null ? data.pitchDelta : 0.0F;
        if (movingFast && (yawDiff > 105.0F || pitchDiff > 32.0F)) {
            rotationBuffer.flag(1.5D, 999.0D);
        } else {
            rotationBuffer.decay(0.35D);
        }

        if (movingFast && player.rotationPitch > 65.0F && pitchDiff < 1.0F && supportBelow) {
            pitchBuffer.flag(1.0D, 999.0D);
        } else {
            pitchBuffer.decay(0.25D);
        }

        if ((supportBuffer.get() > 7.0D && rotationBuffer.get() > 2.5D) || (supportBuffer.get() > 8.0D && pitchBuffer.get() > 6.0D)) {
            long now = System.currentTimeMillis();
            long last = this.lastFlag.getOrDefault(uuid, 0L);
            if (now - last > 3000L) {
                context.receiveSignal(player.getName(), "Scaffold");
                this.lastFlag.put(uuid, now);
                supportBuffer.reset();
                rotationBuffer.reset();
                pitchBuffer.reset();
            }
        }
    }

    private boolean hasSupportBelow(EntityPlayer player, World world) {
        for (double xOffset = -0.3D; xOffset <= 0.3D; xOffset += 0.3D) {
            for (double zOffset = -0.3D; zOffset <= 0.3D; zOffset += 0.3D) {
                BlockPos below = new BlockPos(
                        MathHelper.floor_double(player.posX + xOffset),
                        MathHelper.floor_double(player.posY - 1.0D),
                        MathHelper.floor_double(player.posZ + zOffset)
                );
                Block block = world.getBlockState(below).getBlock();
                if (!(block instanceof BlockAir)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void reset() {
        this.supportBuffers.clear();
        this.rotationBuffers.clear();
        this.pitchBuffers.clear();
        this.lastFlag.clear();
    }
}
