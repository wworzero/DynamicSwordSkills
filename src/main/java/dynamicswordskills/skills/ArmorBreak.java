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

import dynamicswordskills.api.SkillGroup;
import dynamicswordskills.client.DSSClientEvents;
import dynamicswordskills.entity.DSSPlayerInfo;
import dynamicswordskills.network.PacketDispatcher;
import dynamicswordskills.network.bidirectional.ActivateSkillPacket;
import dynamicswordskills.ref.Config;
import dynamicswordskills.ref.ModSounds;
import dynamicswordskills.util.DamageUtils;
import dynamicswordskills.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ArmorBreak extends SkillActive
{
    private int activeTimer = 0;
    private int charge = 0;
    private boolean wasLockedOn;
    @SideOnly(Side.CLIENT)
    private KeyBinding attackKey;

    public ArmorBreak(String translationKey) { super(translationKey); }
    private ArmorBreak(ArmorBreak skill) { super(skill); }
    @Override public ArmorBreak newInstance() { return new ArmorBreak(this); }
    @Override public boolean displayInGroup(SkillGroup group) { return super.displayInGroup(group) || group == Skills.WEAPON_GROUP; }
    @Override @SideOnly(Side.CLIENT) public void addInformation(List<String> desc, EntityPlayer player) {
        desc.add(getChargeDisplay(getChargeTime(player)));
        desc.add(getExhaustionDisplay(getExhaustion()));
    }
    @Override protected boolean allowUserActivation() { return false; }
    @Override public boolean isActive() { return activeTimer > 0; }
    @Override protected float getExhaustion() { return 4.0F - (0.5F * level); }

    // 蓄力时间：45 - 2*level
    private int getChargeTime(EntityPlayer player) { return 45 - 5 * level; }

    @Override public boolean canUse(EntityPlayer player) { return super.canUse(player) && !isActive() && PlayerUtils.isWeapon(player.getHeldItemMainhand()); }
    @Override @SideOnly(Side.CLIENT) public boolean isKeyListener(Minecraft mc, KeyBinding key, boolean isLockedOn) {
        if (Config.requiresLockOn() && !isLockedOn) return charge > 0 && key == mc.gameSettings.keyBindAttack;
        wasLockedOn = isLockedOn;
        return key == mc.gameSettings.keyBindAttack;
    }
    @Override @SideOnly(Side.CLIENT) public boolean keyPressed(Minecraft mc, KeyBinding key, EntityPlayer player) {
        if (wasLockedOn || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) attackKey = key;
        return false;
    }
    @Override @SideOnly(Side.CLIENT) public void keyReleased(Minecraft mc, KeyBinding key, EntityPlayer player) {
        if (key == attackKey) { attackKey = null; charge = 0; DSSPlayerInfo.get(player).setArmSwingProgress(0.0F, 0.0F); }
    }
    @SideOnly(Side.CLIENT) private void initCharging(EntityPlayer player) {
        if (charge == 0 && attackKey != null && attackKey.isKeyDown() && DSSPlayerInfo.get(player).canInteract()) {
            charge = getChargeTime(player);
            KeyBinding.setKeyBindState(attackKey.getKeyCode(), false);
        }
    }
    @Override protected boolean onActivated(World world, EntityPlayer player) {
        activeTimer = 4;
        if (world.isRemote) { attackKey = null; DSSPlayerInfo.get(player).setArmSwingProgress(0.0F, 0.0F); DSSClientEvents.handlePlayerAttack(Minecraft.getMinecraft()); }
        return true;
    }
    @Override protected void onDeactivated(World world, EntityPlayer player) { activeTimer = 0; charge = 0; DSSPlayerInfo.get(player).setArmSwingProgress(0.0F, 0.0F); }
    @Override public void onUpdate(EntityPlayer player) {
        if (player.getEntityWorld().isRemote) initCharging(player);
        if (isActive()) { --activeTimer; }
        else if (charge > 0) {
            if (PlayerUtils.isWeapon(player.getHeldItemMainhand())) {
                int maxCharge = getChargeTime(player);
                if (charge < maxCharge - 1) {
                    float f = 0.25F + 0.75F * ((float)(maxCharge - charge) / (float) maxCharge);
                    DSSPlayerInfo.get(player).setArmSwingProgress(f, 0.0F);
                }
                --charge;
                if (charge == 0) PacketDispatcher.sendToServer(new ActivateSkillPacket(this, true));
            } else { DSSPlayerInfo.get(player).setArmSwingProgress(0.0F, 0.0F); charge = 0; }
        }
    }
    @Override public boolean onAttack(EntityPlayer player, EntityLivingBase entity, DamageSource source, float amount) {
        activeTimer = 0;
        // 伤害倍率：每级增加30%基础伤害（1级100%，2级130%，3级160%...）
        float multiplier = 1.2F + 0.35F * (level - 1);
        // 使用间接剑技伤害源，便于 NPC 脚本识别
        entity.attackEntityFrom(DamageUtils.causeIndirectComboDamage(player, player), amount * multiplier);
        if (!player.getEntityWorld().isRemote) { 
            PlayerUtils.playSoundAtEntity(player.getEntityWorld(), player, ModSounds.ARMOR_BREAK, SoundCategory.PLAYERS, 0.4F, 0.5F);
        }
        return true;
    }
}
	
		
