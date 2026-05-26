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

import dynamicswordskills.DynamicSwordSkills;
import dynamicswordskills.client.DSSKeyHandler;
import dynamicswordskills.ref.Config;
import dynamicswordskills.ref.ModSounds;
import dynamicswordskills.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 
 * DODGE
 * Description: Avoid damage by quickly dodging out of the way
 * Activation: Double-tap left or right to dodge in that direction
 * Exhaustion: 0.1F (flat)
 * Duration: 7 ticks (0.35 seconds) regardless of level
 * Special: Chance to avoid damage is (level * 10%) + 15%, plus a timing bonus of up to (4% * level)
 * 
 */
public class Dodge extends SkillActive
{
	/** Key that was pressed to initiate dodge */
	@SideOnly(Side.CLIENT)
	private KeyBinding keyPressed;

	/** Current number of ticks remaining before dodge will not activate */
	private int ticksTilFail;

	/** Only for double-tap activation; true after the first key press and release */
	private boolean keyReleased;

	/** Trajectory based on player's look vector and Dodge direction */
	private Vec3d trajectory;

	/** Timer during which player may evade incoming attacks */
	private int dodgeTimer = 0;

	/** Entity dodged, since the attack event may fire multiple times in quick succession for mobs like zombies */
	private Entity entityDodged;

	public Dodge(String translationKey) {
		super(translationKey);
	}

	private Dodge(Dodge skill) {
		super(skill);
	}

	@Override
	public Dodge newInstance() {
		return new Dodge(this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(List<String> desc, EntityPlayer player) {
		desc.add(new TextComponentTranslation(getTranslationKey() + ".info.chance", (int)(getBaseDodgeChance(player) * 100)).getUnformattedText());
		desc.add(new TextComponentTranslation(getTranslationKey() + ".info.bonus", level * 4).getUnformattedText());
		desc.add(getTimeLimitDisplay(getDodgeTime()));
		desc.add(getExhaustionDisplay(getExhaustion()));
	}

	@Override
	public boolean isActive() {
		return (dodgeTimer > 0);
	}

	@Override
	protected float getExhaustion() {
		return 0.1F; // 固定消耗 0.1
	}

	/** 基础闪避概率：等级 * 10% + 15%，不再受移动速度加成 */
	private float getBaseDodgeChance(EntityPlayer player) {
		return (level * 0.1F + 0.15F);
	}

	/** 总闪避概率：基础概率 + 时间奖励 */
	private float getDodgeChance(EntityPlayer player) {
		return getBaseDodgeChance(player) + getTimeBonus();
	}

	/** 闪避持续时间：固定 7 刻 (0.35 秒) */
	private int getDodgeTime() {
		return 7;
	}

	/** 时间奖励：早期闪避有额外加成，与剩余时间和等级相关 */
	private float getTimeBonus() {
		return ((dodgeTimer + level - 5) * 0.02F);
	}

	@Override
	public boolean canUse(EntityPlayer player) {
		return super.canUse(player) && !isActive() && !PlayerUtils.isBlocking(player);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean canExecute(EntityPlayer player) {
		return player.onGround && canUse(player) && keyReleased && ticksTilFail > 0;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isKeyListener(Minecraft mc, KeyBinding key, boolean isLockedOn) {
		if (Config.requiresLockOn() && !isLockedOn) {
			return false;
		}
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean keyPressed(Minecraft mc, KeyBinding key, EntityPlayer player) {
		if (key == keyPressed) {
			boolean flag = canExecute(player) && activate(player);
			resetKeyState(flag);
			return flag;
		} else if (Config.allowVanillaControls() && (key == mc.gameSettings.keyBindLeft || key == mc.gameSettings.keyBindRight)) {
			firstKeyPress(mc, key, player);
		} else if (key == DSSKeyHandler.keys[DSSKeyHandler.KEY_LEFT].getKey() || key == DSSKeyHandler.keys[DSSKeyHandler.KEY_RIGHT].getKey()) {
			if (key == DSSKeyHandler.keys[DSSKeyHandler.KEY_LEFT].getKey() && mc.gameSettings.keyBindRight.isKeyDown()) {
				return false;
			} else if (key == DSSKeyHandler.keys[DSSKeyHandler.KEY_RIGHT].getKey() && mc.gameSettings.keyBindLeft.isKeyDown()) {
				return false;
			}
			firstKeyPress(mc, key, player);
			if (!Config.requiresDoubleTap()) {
				keyReleased = true;
				boolean flag = canExecute(player) && activate(player);
				resetKeyState(true);
				return flag;
			}
		} else {
			keyPressed = null;
		}
		return false;
	}

	@SideOnly(Side.CLIENT)
	private void firstKeyPress(Minecraft mc, KeyBinding key, EntityPlayer player) {
		keyPressed = key;
		keyReleased = false;
		ticksTilFail = 6;
	}

	@SideOnly(Side.CLIENT)
	private void resetKeyState(boolean flag) {
		keyReleased = false;
		ticksTilFail = 0;
		if (!flag) {
			keyPressed = null;
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void keyReleased(Minecraft mc, KeyBinding key, EntityPlayer player) {
		keyReleased = (key == keyPressed);
		if (keyReleased && ticksTilFail < 1) {
			keyPressed = null;
		}
	}

	@Override
	public boolean onActivated(World world, EntityPlayer player) {
		dodgeTimer = getDodgeTime();
		entityDodged = null;
		if (player.getEntityWorld().isRemote) {
			trajectory = player.getLookVec();
			if (keyPressed == DSSKeyHandler.keys[DSSKeyHandler.KEY_RIGHT].getKey() || keyPressed == Minecraft.getMinecraft().gameSettings.keyBindRight) {
				trajectory = new Vec3d(-trajectory.z, 0.0D, trajectory.x);
			} else {
				trajectory = new Vec3d(trajectory.z, 0.0D, -trajectory.x);
			}
			keyPressed = null;
		}
		return isActive();
	}

	@Override
	protected void onDeactivated(World world, EntityPlayer player) {
		dodgeTimer = 0;
		entityDodged = null;
	}

	@Override
	public void onUpdate(EntityPlayer player) {
		if (isActive()) {
			--dodgeTimer;
		}
		if (ticksTilFail > 0) {
			--ticksTilFail;
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isAnimating() {
		return (dodgeTimer > level && trajectory != null);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean onRenderTick(EntityPlayer player, float partialTickTime) {
		double speed = player.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
		double fps = (DynamicSwordSkills.BASE_FPS / (float) Minecraft.getDebugFPS()); 
		double d = 1.15D * fps * speed;
		if (player.isInWater() || player.isInLava()) {
			d *= 0.15D;
		}
		player.addVelocity(trajectory.x * d, -0.02D * fps, trajectory.z * d);
		return true;
	}

	@Override
	public boolean onBeingAttacked(EntityPlayer player, DamageSource source) {
		if (dodgeTimer > level) {
			Entity attacker = source.getTrueSource();
			if (attacker != null) {
				return (attacker == entityDodged || dodgeAttack(player, attacker));
			}
		}
		return false;
	}

	/**
	 * Returns true if the attack was dodged and the attack event should be canceled
	 */
	private boolean dodgeAttack(EntityPlayer player, Entity attacker) {
		if (player.getEntityWorld().rand.nextFloat() < getDodgeChance(player)) {
			entityDodged = attacker;
			PlayerUtils.playRandomizedSound(player, ModSounds.SWORD_MISS, SoundCategory.PLAYERS, 0.4F, 0.5F);
			return true;
		}
		return false;
	}
}

	



			
