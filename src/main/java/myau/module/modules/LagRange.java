package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.ItemUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;

public class LagRange extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int activeDelayTicks = 0;
    private int jitterTicks = 0;
    private long delayCounter = 0L;
    private boolean hasTarget = false;
    private boolean aggressiveHitUsed = false;
    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;

    public final IntProperty delay = new IntProperty("delay", 220, 0, 1000);
    public final IntProperty maxDelay = new IntProperty("max-delay", 450, 50, 1000);
    public final IntProperty jitter = new IntProperty("jitter", 125, 0, 300);
    public final FloatProperty range = new FloatProperty("range", 12.0F, 3.0F, 100.0F);
    public final FloatProperty attackRange = new FloatProperty("attack-range", 3.35F, 3.0F, 6.0F);
    public final BooleanProperty dynamicDelay = new BooleanProperty("dynamic-delay", true);
    public final BooleanProperty aggressive = new BooleanProperty("aggressive", true);
    public final IntProperty aggressiveDelayMin = new IntProperty("aggressive-delay-min", 120, 0, 1000, this.aggressive::getValue);
    public final IntProperty aggressiveDelayMax = new IntProperty("aggressive-delay-max", 260, 50, 1000, this.aggressive::getValue);
    public final BooleanProperty predict = new BooleanProperty("predict", true);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty botCheck = new BooleanProperty("bot-check", true);
    public final BooleanProperty teams = new BooleanProperty("teams", true);
    public final ModeProperty showPosition = new ModeProperty("show-position", 0, new String[]{"NONE", "DEFAULT", "HUD"});

    public LagRange() {
        super("LagRange", false);
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer == mc.thePlayer || entityPlayer == mc.thePlayer.ridingEntity) {
            return false;
        }
        if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
            return false;
        }
        if (entityPlayer.deathTime > 0 || TeamUtil.isFriend(entityPlayer)) {
            return false;
        }
        return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer))
                && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
    }

    private boolean canRun() {
        BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
        return (!bedNuker.isEnabled() || !bedNuker.isReady())
                && !((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                && (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool());
    }

    private boolean shouldHardReset(Packet<?> packet) {
        if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        }
        return false;
    }

    private double getHorizontalSpeed(EntityPlayer player) {
        double motionX = player.posX - player.lastTickPosX;
        double motionZ = player.posZ - player.lastTickPosZ;
        return Math.sqrt(motionX * motionX + motionZ * motionZ);
    }

    private double getTargetScore(EntityPlayer player, Vec3 realEyePosition, Vec3 fakeEyePosition) {
        double currentDistance = RotationUtil.distanceToBox(player, realEyePosition);
        if (currentDistance > (double) this.range.getValue()) {
            return -1.0;
        }

        double fakeDistance = RotationUtil.distanceToBox(player, fakeEyePosition);
        double lastDistance = RotationUtil.distanceToBox(
                player,
                new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + mc.thePlayer.getEyeHeight(), mc.thePlayer.lastTickPosZ)
        );
        double desyncAdvantage = Math.max(0.0, fakeDistance - currentDistance);
        double closingAdvantage = Math.max(0.0, lastDistance - currentDistance);
        double reachWindow = Math.max(0.0, (double) this.attackRange.getValue() - currentDistance);
        double angleWeight = Math.max(0.0, 180.0 - (double) RotationUtil.angleToEntity(player)) / 180.0;
        double enemySpeed = this.getHorizontalSpeed(player);

        if ((!this.aggressive.getValue() || this.aggressiveHitUsed) && reachWindow <= 0.0 && closingAdvantage <= 0.01 && desyncAdvantage <= 0.03) {
            return -1.0;
        }

        return reachWindow * 7.0 + desyncAdvantage * 5.0 + closingAdvantage * 3.0 + angleWeight * 2.0 + enemySpeed * 3.0;
    }

    private EntityPlayer getBestTarget(Vec3 realEyePosition, Vec3 fakeEyePosition) {
        EntityPlayer bestTarget = null;
        double bestScore = -1.0;
        for (Object entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (this.isValidTarget(player)) {
                    double score = this.getTargetScore(player, realEyePosition, fakeEyePosition);
                    if (score > bestScore) {
                        bestScore = score;
                        bestTarget = player;
                    }
                }
            }
        }
        return bestTarget;
    }

    private int msToRollingTicks(int ms) {
        int ticks = 0;
        for (this.delayCounter += (long) ms; this.delayCounter > 0L; this.delayCounter -= 50L) {
            ticks++;
        }
        return Math.max(1, ticks);
    }

    private void rollJitter() {
        int maxJitter = this.jitter.getValue();
        if (maxJitter <= 0) {
            this.jitterTicks = 0;
        } else {
            this.jitterTicks = (int) Math.round((double) ThreadLocalRandom.current().nextInt(-maxJitter, maxJitter + 1) / 50.0);
        }
    }

    private int getAggressiveDelayMs() {
        int min = this.aggressiveDelayMin.getValue();
        int max = Math.max(min, this.aggressiveDelayMax.getValue());
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private int calculateDelayTicks(EntityPlayer target, Vec3 realEyePosition) {
        int baseTicks = this.msToRollingTicks(this.delay.getValue());
        if (!this.dynamicDelay.getValue() || target == null) {
            return Math.max(1, baseTicks + this.jitterTicks);
        }

        double distance = RotationUtil.distanceToBox(target, realEyePosition);
        double pressure = Math.max(0.0, Math.min(1.0,
                ((double) this.range.getValue() - distance)
                        / Math.max(0.1, (double) this.range.getValue() - (double) this.attackRange.getValue())));
        int maxTicks = Math.max(baseTicks, (int) Math.ceil((double) this.maxDelay.getValue() / 50.0));
        return Math.max(1, baseTicks + (int) Math.round((double) (maxTicks - baseTicks) * pressure) + this.jitterTicks);
    }

    private void hardReset() {
        Myau.lagManager.setDelay(0);
        this.activeDelayTicks = 0;
        this.jitterTicks = 0;
        this.hasTarget = false;
        this.aggressiveHitUsed = false;
    }

    private boolean shouldPredictReset(Packet<?> packet) {
        if (!this.predict.getValue()
                || !(packet instanceof S0BPacketAnimation)
                || ((S0BPacketAnimation) packet).getAnimationType() != 0
                || mc.theWorld == null) {
            return false;
        }

        Object entity = mc.theWorld.getEntityByID(((S0BPacketAnimation) packet).getEntityID());
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }

        EntityPlayer player = (EntityPlayer) entity;
        if (!this.isValidTarget(player)) {
            return false;
        }

        Vec3 fakeEyePosition = Myau.lagManager.getLastPosition().addVector(0.0, mc.thePlayer.getEyeHeight(), 0.0);
        return RotationUtil.distanceToBox(player, fakeEyePosition) <= (double) this.attackRange.getValue();
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        switch (event.getType()) {
            case PRE:
                Myau.lagManager.setDelay(0);
                this.hasTarget = false;

                if (!this.canRun()) {
                    this.hardReset();
                    break;
                }

                double height = mc.thePlayer.getEyeHeight();
                Vec3 fakeEyePosition = Myau.lagManager.getLastPosition().addVector(0.0, height, 0.0);
                Vec3 realEyePosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
                EntityPlayer target = this.getBestTarget(realEyePosition, fakeEyePosition);

                if (target == null) {
                    this.hardReset();
                    break;
                }

                this.hasTarget = true;

                int wantedDelay = this.calculateDelayTicks(target, realEyePosition);
                if (this.activeDelayTicks <= 0 || this.dynamicDelay.getValue()) {
                    this.activeDelayTicks = wantedDelay;
                }
                Myau.lagManager.setDelay(this.activeDelayTicks);
                break;
            case POST:
                Vec3 savedPosition = Myau.lagManager.getLastPosition();
                if (this.currentPosition == null) {
                    this.lastPosition = savedPosition;
                } else {
                    this.lastPosition = this.currentPosition;
                }
                this.currentPosition = savedPosition;
                break;
            default:
                break;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.RECEIVE) {
            if (!event.isCancelled() && this.shouldPredictReset(packet)) {
                this.hardReset();
            }
            return;
        }
        if (event.getType() != EventType.SEND) {
            return;
        }

        if (packet instanceof C02PacketUseEntity) {
            this.rollJitter();
            if (this.aggressive.getValue() && !this.aggressiveHitUsed) {
                int aggressiveDelay = Math.max(1, this.msToRollingTicks(this.getAggressiveDelayMs()) + this.jitterTicks);
                this.activeDelayTicks = Math.max(this.activeDelayTicks, aggressiveDelay);
                this.aggressiveHitUsed = true;
            } else if (this.activeDelayTicks <= 0) {
                this.activeDelayTicks = Math.max(1, this.msToRollingTicks(this.delay.getValue()) + this.jitterTicks);
            }
        } else if (this.shouldHardReset(packet)) {
            this.hardReset();
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showPosition.getValue() != 0
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                Color color = new Color(-1);
                switch (this.showPosition.getValue()) {
                    case 1:
                        color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
                        break;
                    case 2:
                        color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
                double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
                float size = mc.thePlayer.getCollisionBorderSize();
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) mc.thePlayer.width / 2.0,
                        y,
                        z - (double) mc.thePlayer.width / 2.0,
                        x + (double) mc.thePlayer.width / 2.0,
                        y + (double) mc.thePlayer.height,
                        z + (double) mc.thePlayer.width / 2.0
                )
                        .expand(size, size, size)
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onDisabled() {
        this.hardReset();
        this.delayCounter = 0L;
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%d-%dms", this.delay.getValue(), this.maxDelay.getValue())};
    }
}
