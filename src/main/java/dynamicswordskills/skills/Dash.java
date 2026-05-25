
/**
    Copyright (C) <2017> <coolAlias>

    This file is part of coolAlias' Dynamic Sword Skills Minecraft Mod; as such,
    you can redistribute it and/or modify it under the terms of the GNU
    General Public License as published by the Free Software Foundation,
    either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package dynamicswordskills.skills;

import java.util.List;

import dynamicswordskills.entity.DSSPlayerInfo;
import dynamicswordskills.network.PacketDispatcher;
import dynamicswordskills.network.server.DashImpactPacket;
import dynamicswordskills.ref.Config;
import dynamicswordskills.ref.ModSounds;
import dynamicswordskills.util.DamageUtils;
import dynamicswordskills.util.PlayerUtils;
import dynamicswordskills.util.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import swordskillsapi.api.item.IDashItem;

public class Dash extends SkillActive
{
    public static final double BASE_MOVE = 0.10000000149011612D;
    private boolean isActive = false;
    private int activeTime;
    private Vec3d trajectory;
    private Vec3d initialPosition;
    private Entity target;
    private int impactTime;

    public Dash(String translationKey) { super(translationKey); }
    private Dash(Dash skill) { super(skill); }
    @Override public Dash newInstance() { return new Dash(this); }

    @Override @SideOnly(Side.CLIENT) public void addInformation(List<String> desc, EntityPlayer player) {
        desc.add(new TextComponentTranslation(getTranslationKey() + ".info.damage").getUnformattedText());
        desc.add(getRangeDisplay(getRange()));
        desc.add(getExhaustionDisplay(getExhaustion()));
    }
    @Override public boolean isActive() { return isActive || impactTime > 0; }
    protected int getMaxActiveTime() { return 14 + (2 * level); }
    private int getBlockCooldown() { return (50 - (3 * level)); }
    @Override protected float getExhaustion() { return 12.0F; }
    private float getKnockback() { return 0.4F * level; }

    // 最大冲刺距离：6.0 + level*2
    private double getRange() { return (6.0D + 2 * level); }
    private double getMinDistance() { return 2.0D - (0.2D * level); }

    @Override public boolean canUse(EntityPlayer player) {
        boolean flag = PlayerUtils.isBlocking(player);
        for (EnumHand hand : EnumHand.values()) { if (canItemDash(player, hand)) { flag = true; break; } }
        return (flag && super.canUse(player) && !isActive());
    }
    private boolean canItemDash(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() instanceof IDashItem) return ((IDashItem) stack.getItem()).canDash(stack, player, hand);
        return false;
    }
    @Override @SideOnly(Side.CLIENT) public boolean canExecute(EntityPlayer player) {
        return player.onGround && canUse(player) && Minecraft.getMinecraft().gameSettings.keyBindForward.isKeyDown();
    }
    @Override @SideOnly(Side.CLIENT) public boolean isKeyListener(Minecraft mc, KeyBinding key, boolean isLockedOn) {
        if (Config.requiresLockOn() && !isLockedOn) return false;
        return key == mc.gameSettings.keyBindAttack;
    }
    @Override @SideOnly(Side.CLIENT) public boolean keyPressed(Minecraft mc, KeyBinding key, EntityPlayer player) {
        return canExecute(player) && activate(player);
    }
    @Override protected boolean onActivated(World world, EntityPlayer player) {
        isActive = true; activeTime = 0; player.setSprinting(true);
        trajectory = player.getLookVec(); initialPosition = new Vec3d(player.posX, player.posY, player.posZ);
        return isActive();
    }
    @Override protected void onDeactivated(World world, EntityPlayer player) { initialPosition = null; impactTime = 0; setNotDashing(player); }
    @Override public void onUpdate(EntityPlayer player) {
        if (impactTime > 0) { --impactTime; if (impactTime == 0) target = null; }
        if (isActive) {
            player.setSprinting(true);
            if (!PlayerUtils.isBlocking(player)) { if (!player.getEntityWorld().isRemote) deactivate(player); }
            else if (player.getEntityWorld().isRemote) {
                if (trajectory != null) {
                    double bonus = 1.0D + (0.15D * level);
                    double speed = bonus * player.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
                    if (player.isInWater() || player.isInLava()) speed *= 0.15D;
                    if (player.onGround) trajectory = player.getLookVec();
                    player.addVelocity(trajectory.x * speed, -0.02D, trajectory.z * speed);
                }
                RayTraceResult result = TargetUtils.checkForImpact(player.getEntityWorld(), player, player, 0.5D, false);
                if (result != null || player.collidedHorizontally) {
                    PacketDispatcher.sendToServer(new DashImpactPacket(player, result));
                    player.resetCooldown(); DSSPlayerInfo.get(player).setUseItemCooldown(getBlockCooldown());
                    KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode(), false);
                    impactTime = 5; if (result != null && result.typeOfHit == RayTraceResult.Type.ENTITY) target = result.entityHit;
                    double d = (player.onGround ? 2.0D : 0.5D); double dy = (player.onGround ? 0.3D : -0.15D);
                    player.setVelocity(-player.motionX * d, dy, -player.motionZ * d); setNotDashing(player);
                } else if (initialPosition == null || player.getDistance(initialPosition.x, initialPosition.y, initialPosition.z) > getRange()) {
                    player.addVelocity(-player.motionX * 0.5D, -0.02D, -player.motionZ * 0.5D); deactivate(player);
                } else if (!Minecraft.getMinecraft().gameSettings.keyBindForward.isKeyDown()) { deactivate(player); }
            }
        }
        if (isActive) { ++activeTime; if (activeTime > getMaxActiveTime()) { if (!player.getEntityWorld().isRemote) deactivate(player); } }
    }
    public void onImpact(World world, EntityPlayer player, RayTraceResult result) {
        if (result != null && result.typeOfHit == RayTraceResult.Type.ENTITY) {
            target = result.entityHit;
            double distance = target.getDistance(initialPosition.x, initialPosition.y, initialPosition.z);
            double bbMod = (target.width / 2.0F) + (player.width / 2.0F);
            double speed = player.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
            double sf = (1.0D + (speed - BASE_MOVE)); if (player.isInWater() || player.isInLava()) sf *= 0.3D;
            if (speed > 0.075D && (distance - bbMod) > getMinDistance() && distance < (getRange() + 1.0D) && player.getDistanceSq(target) < 6.0D) {
                double attackDamage = player.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
                // 伤害公式：200*level + level * (攻击力 * 0.8)
                float dmg = (float)(200.0F * level + level * (attackDamage * 0.6F));
                impactTime = 5;
                target.attackEntityFrom(DamageUtils.causeIndirectComboDamage(player, player), dmg);
                if (target instanceof EntityLivingBase) {
                    float db = 0.15F * (float)(distance - getMinDistance());
                    float k = (float)sf * Math.min(db + getKnockback(), 3.0F);
                    TargetUtils.knockTargetBack((EntityLivingBase) target, player, 0.5F * k);
                }
                if (target instanceof EntityPlayerMP && !player.getEntityWorld().isRemote) ((EntityPlayerMP) target).connection.sendPacket(new SPacketEntityVelocity(target));
            }
        }
        DSSPlayerInfo.get(player).setUseItemCooldown(getBlockCooldown());
        PlayerUtils.playSoundAtEntity(player.getEntityWorld(), player, ModSounds.SLAM, SoundCategory.PLAYERS, 0.4F, 0.5F);
        setNotDashing(player);
    }
    @Override @SideOnly(Side.CLIENT) public boolean isAnimating() { return isActive; }
    @Override @SideOnly(Side.CLIENT) public boolean onRenderTick(EntityPlayer player, float partialTickTime) { player.setSprinting(true); return false; }
    @Override public boolean onBeingAttacked(EntityPlayer player, DamageSource source) {
        if (impactTime > 0 && source.getTrueSource() == target) return true;
        else if (source.damageType.equals("mob") && source.getTrueSource() != null && player.getDistanceSq(source.getTrueSource()) < 6.0D) return true;
        return false;
    }
    private void setNotDashing(EntityPlayer player) { isActive = false; player.setSprinting(false); trajectory = null; if (!isActive()) target = null; }
}
        
