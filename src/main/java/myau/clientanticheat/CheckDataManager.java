package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class CheckDataManager {
    private final Map<String, PlayerCheckData> data = new HashMap<>();

    public void update(World world) {
        Set<String> seen = new HashSet<>();
        for (EntityPlayer player : world.playerEntities) {
            if (player.getName() == null) continue;
            seen.add(player.getName());
            PlayerCheckData playerData = this.data.computeIfAbsent(player.getName(), key -> new PlayerCheckData(player));
            playerData.update(player);
        }
        Iterator<String> iterator = this.data.keySet().iterator();
        while (iterator.hasNext()) {
            if (!seen.contains(iterator.next())) {
                iterator.remove();
            }
        }
    }

    public PlayerCheckData get(EntityPlayer player) {
        if (player == null || player.getName() == null) return null;
        return this.data.get(player.getName());
    }

    public boolean isMovementExempt(EntityPlayer player, PlayerCheckData data) {
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
        this.data.clear();
    }
}
