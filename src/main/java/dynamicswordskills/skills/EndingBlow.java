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
import dynamicswordskills.client.DSSKeyHandler;
import dynamicswordskills.entity.DSSPlayerInfo;
import dynamicswordskills.entity.DirtyEntityAccessor;
import dynamicswordskills.network.PacketDispatcher;
import dynamicswordskills.network.bidirectional.ActionTimePacket;
import dynamicswordskills.network.client.EndingBlowPacket;
import dynamicswordskills.ref.Config;
import dynamicswordskills.ref.ModSounds;
import dynamicswordskills.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EndingBlow extends SkillActive
{
	private int activeTimer = 0;

	@SideOnly(Side.CLIENT)
	private int ticksTilFail;

	@SideOnly(Side.CLIENT)
	private int keyPressed;

	@SideOnly(Side.CLIENT)
	private boolean keyReleased;

	@SideOnly(Side.CLIENT)
	private long lastActivationTime;

	public byte skillResult;

	private int lastNumHits;

	private EntityLivingBase entityHit;

	private int xp;

	public EndingBlow(String translationKey) {
		super(translationKey);
	}

	private EndingBlow(EndingBlow skill) {
		super(skill);
	}

	@Override
	public EndingBlow newInstance() {
		return new EndingBlow(this);
	}

	@Override
	public boolean displayInGroup(SkillGroup group) {
		return super.displayInGroup(group) || group == Skills.WEAPON_GROUP || group == Skills.TARGETED_GROUP;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(List<String> desc, EntityPlayer player) {
		desc.add(getDamageDisplay(level * 20, true) + "%");
		desc.add(getDurationDisplay(getDuration(), true));
		desc.add(getExhaustionDisplay(getExhaustion()));
	}

	@Override
	public boolean isActive() {
		return activeTimer > 0;
	}

	@Override
	protected float getExhaustion() {
		return 8.0F;
	}

	/** 失败惩罚冷却：60 - level*5 刻，超时时长翻倍 */
	public int getDuration() {
		return 60 - (level * 5);
	}

	@SideOnly(Side.CLIENT)
	public long getLastActivationTime() {
		return this.lastActivationTime;
	}

	@Override
	public boolean canUse(EntityPlayer player) {
		if (!isActive() && super.canUse(player) && PlayerUtils.isWeapon(player.getHeldItemMainhand())) {
			IComboSkill combo = DSSPlayerInfo.get(player).getComboSkill();
			if (combo != null && combo.isComboInProgress()) {
				ILockOnTarget lock = DSSPlayerInfo.get(player).getTargetingSkill();
				if (lock == null || (lock.isLockedOn() && lock.getCurrentTarget() != combo.getCombo().getLastEntityHit())) {
					return false;
				} else if (lastNumHits > 0) {
					return combo.getCombo().getConsecutiveHits() > 2 && combo.getCombo().getNumHits() > lastNumHits + 2;
				} else {
					return combo.getCombo().getConsecutiveHits() > 2;
				}
			}
		}
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean canExecute(EntityPlayer player) {
		return ticksTilFail > 0 && keyPressed > 1 && keyReleased && canUse(player);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isKeyListener(Minecraft mc, KeyBinding key, boolean isLockedOn) {
		if (Config.requiresLockOn() && !isLockedOn) {
			return false;
		}
		return (key == mc.gameSettings.keyBindAttack || key == DSSKeyHandler.keys[DSSKeyHandler.KEY_FORWARD].getKey()
				|| (Config.allowVanillaControls() && key == mc.gameSettings.keyBindForward));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean keyPressed(Minecraft mc, KeyBinding key, EntityPlayer player) {
		if (key == DSSKeyHandler.keys[DSSKeyHandler.KEY_FORWARD].getKey() || (Config.allowVanillaControls() && key == mc.gameSettings.keyBindForward)) {
			if (ticksTilFail == 0) {
				ticksTilFail = 6;
			}
			++keyPressed;
		} else if (canExecute(player)) {
			ticksTilFail = 0;
			keyPressed = 0;
			keyReleased = false;
			return activate(player);
		}
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void keyReleased(Minecraft mc, KeyBinding key, EntityPlayer player) {
		if (key == DSSKeyHandler.keys[DSSKeyHandler.KEY_FORWARD].getKey() || (Config.allowVanillaControls() && key == mc.gameSettings.keyBindForward)) {
			keyReleased = (keyPressed > 0 && ticksTilFail > 0);
		}
	}

	@Override
	protected boolean onActivated(World world, EntityPlayer player) {
		activeTimer = 3;
		entityHit = null;
		IComboSkill skill = DSSPlayerInfo.get(player).getComboSkill();
		if (skill != null && skill.getCombo() != null) {
			lastNumHits = skill.getCombo().getNumHits();
		}
		if (world.isRemote) {
			DSSClientEvents.handlePlayerAttack(Minecraft.getMinecraft());
			this.lastActivationTime = Minecraft.getSystemTime();
			this.skillResult = 0;
		}
		return isActive();
	}

	@Override
	protected void onDeactivated(World world, EntityPlayer player) {
		activeTimer = 0;
		entityHit = null;
		xp = 0;
		if (world.isRemote) {
			keyPressed = 0;
			keyReleased = false;
			ticksTilFail = 0;
		}
	}

	@Override
	public void onUpdate(EntityPlayer player) {
		if (player.getEntityWorld().isRemote && ticksTilFail > 0) {
			--ticksTilFail;
			if (ticksTilFail == 0) {
				keyPressed = 0;
				keyReleased = false;
			}
		}
		if (lastNumHits > 0) {
			if (entityHit != null && xp > 0) {
				updateEntityState(player);
			}
			IComboSkill skill = DSSPlayerInfo.get(player).getComboSkill();
			if (skill == null || !skill.isComboInProgress()) {
				lastNumHits = 0;
			}
		}
		if (isActive()) {
			--activeTimer;
			if (activeTimer == 0 && !player.getEntityWorld().isRemote) {
				onFail(player, true);
			}
		}
	}

	private void updateEntityState(EntityPlayer player) {
		if (!player.getEntityWorld().isRemote) {
			if (entityHit.getHealth() <= 0.0F) {
				if (entityHit instanceof EntityLiving) {
					DirtyEntityAccessor.setLivingXp((EntityLiving) entityHit, xp, true);
				} else {
					PlayerUtils.spawnXPOrbsWithRandom(player.getEntityWorld(), player.getEntityWorld().rand, entityHit.getPosition(), xp);
				}
				PacketDispatcher.sendTo(new EndingBlowPacket((byte) 1), (EntityPlayerMP) player);
			} else {
				onFail(player, false);
			}
		}
		entityHit = null;
		xp = 0;
	}

	@Override
	public float onImpact(EntityPlayer player, EntityLivingBase entity, float amount) {
		IComboSkill combo = DSSPlayerInfo.get(player).getComboSkill();
		if (combo != null && combo.isComboInProgress() && entity == combo.getCombo().getLastEntityHit() && combo.getCombo().getConsecutiveHits() > 1) {
			// 新伤害公式：2.0 + level * 0.5
			amount *= (2.0F + (level * 0.5F));
			PlayerUtils.playSoundAtEntity(player.getEntityWorld(), player, ModSounds.MORTAL_DRAW, SoundCategory.PLAYERS, 0.4F, 0.5F);
			entityHit = entity;
		} else if (!player.getEntityWorld().isRemote) {
			onFail(player, false);
		}
		return amount;
	}

	@Override
	public void postImpact(EntityPlayer player, EntityLivingBase entity, float amount) {
		activeTimer = 0;
		if (entityHit != null) {
			xp = level + 1 + player.getEntityWorld().rand.nextInt(Math.max(2, MathHelper.ceil(entity.getHealth())));
		}
	}

	private void onFail(EntityPlayer player, boolean timedOut) {
		if (!player.capabilities.isCreativeMode) {
			DSSPlayerInfo skills = DSSPlayerInfo.get(player);
			int t = getDuration() * (timedOut ? 2 : 1);
			skills.setAttackCooldown(t);
			PacketDispatcher.sendTo(new ActionTimePacket(skills.getAttackTime(), true), (EntityPlayerMP) player);
		}
		if (!timedOut) {
			PlayerUtils.playSoundAtEntity(player.getEntityWorld(), player, ModSounds.HURT_FLESH, SoundCategory.PLAYERS, 0.3F, 0.8F);
		}
		PacketDispatcher.sendTo(new EndingBlowPacket((byte)-1), (EntityPlayerMP) player);
	}
}

	

	
		
				
