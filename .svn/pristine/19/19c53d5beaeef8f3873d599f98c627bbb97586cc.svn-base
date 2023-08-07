/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc> 
                       Matthias Butz <matze@odinms.de>
                       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation. You may not use, modify
    or distribute this program under any other version of the
    GNU Affero General Public License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.sf.odinms.net.channel.handler;

import java.util.concurrent.ScheduledFuture;
import net.sf.odinms.client.IItem;
import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleCharacter.CancelCooldownAction;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventory;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.MapleWeaponType;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class RangedAttackHandler extends AbstractDealDamageHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// attack air
		// 24 00 01 01 00 00 00 00 00 97 04 06 78 1E BE 01 05 00 00 00 41 DC FF 5F 00
		// 24 00 01 01 00 00 00 00 00 97 04 06 FA 02 C1 01 04 00 00 00 41 DC FF 5F 00
		// 23 00 03 01 00 00 00 00 00 90 01 04 DB 82 A9 00 FB FC D7 00
		// attack one monster
		// 24 00 01 11 00 00 00 00 00 97 04 06 9C B6 C5 01 04 00 00 00 41 69 00 00 00 06 00 02 01 04 00 5F 00 03 00 5F
		// 00 AD 03 09 0B 00 00 F9 00 5F 00
		// 23 00 03 11 00 00 00 00 00 05 01 04 41 AE 65 00 24 00 00 00 06 81 00 01 04 00 BB FE 04 00 BB FE 89 01 0F 02
		// 00 00 B5 FF 9C FE
		
		
		AttackInfo attack = parseDamage(slea, true);
		MapleCharacter player = c.getPlayer();
		
		MapleInventory equip = player.getInventory(MapleInventoryType.EQUIPPED);
		IItem weapon = equip.getItem((byte) -11);
		MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
		MapleWeaponType type = mii.getWeaponType(weapon.getItemId());
		if (type == MapleWeaponType.NOT_A_WEAPON) {
			throw new RuntimeException("[h4x] Player " + player.getName() +
				" is attacking with something that's not a weapon");
		}
		MapleInventory use = player.getInventory(MapleInventoryType.USE);
		int projectile = 0;
		int bulletCount = 1;
		MapleStatEffect effect = null;
		if (attack.skill != 0) {
			effect = attack.getAttackEffect(c.getPlayer());
			bulletCount = effect.getBulletCount();
			if (effect.getCooldown() > 0) {
				c.getSession().write(MaplePacketCreator.skillCooldown(attack.skill, effect.getCooldown()));
			}
		}
		
		
		boolean hasShadowPartner = player.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null;
		int damageBulletCount = bulletCount;
		if (hasShadowPartner) {
			bulletCount *= 2;
		}
		for (int i = 0; i < 255; i++) { // impose order...
			IItem item = use.getItem((byte) i);
			if (item != null) {
				// TODO mittens THROW arrows -.- WTF but mittens are season claws so we'll just ignore them until
				// christmas 2008 (o.o)
				boolean clawCondition = type == MapleWeaponType.CLAW && mii.isThrowingStar(item.getItemId());
				boolean bowCondition = type == MapleWeaponType.BOW && mii.isArrowForBow(item.getItemId());
				boolean crossbowCondition = type == MapleWeaponType.CROSSBOW &&
					mii.isArrowForCrossBow(item.getItemId());
				if ((clawCondition || bowCondition || crossbowCondition) && item.getQuantity() >= bulletCount) {
					projectile = item.getItemId();
					break;
				}
			}
		}
		boolean soulArrow = player.getBuffedValue(MapleBuffStat.SOULARROW) != null;
		boolean shadowClaw = player.getBuffedValue(MapleBuffStat.SHADOW_CLAW) != null;
		if (!soulArrow && !shadowClaw) {
			int bulletConsume = bulletCount;
			if (effect != null && effect.getBulletConsume() != 0) {
				bulletConsume = effect.getBulletConsume() * (hasShadowPartner ? 2 : 1);
			}
			MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, projectile, bulletConsume, false, true);
		}
		if (projectile != 0 || soulArrow) {
			int visProjectile = projectile; //visible projectile sent to players
			if (mii.isThrowingStar(projectile)) {
				//see if player has cash stars
				MapleInventory cash = player.getInventory(MapleInventoryType.CASH);
				
				for (int i = 0; i < 255; i++) { // impose order...
					IItem item = cash.getItem((byte) i);
					if (item != null) {
						//cash stars have prefix 5021xxx
						if (item.getItemId() / 1000 == 5021) {
							visProjectile = item.getItemId();
							break;
						}
					}
				}
			} else { //bow, crossbow
				if (soulArrow || attack.skill == 3111004 || attack.skill == 3211004) {
					visProjectile = 0; //arrow rain/eruption show no arrows
				}
			}

			MaplePacket packet;
			
			if (attack.skill == 3121004 || attack.skill == 3221001) { // pierce and hurricane
				packet = MaplePacketCreator.rangedAttack(player.getId(), attack.skill, attack.direction, attack.numAttackedAndDamage, visProjectile, attack.allDamage);
			} else {
				packet = MaplePacketCreator.rangedAttack(player.getId(), attack.skill, attack.stance, attack.numAttackedAndDamage, visProjectile, attack.allDamage);
			}

			player.getMap().broadcastMessage(player, packet, false, true);
			// MaplePacket packet = MaplePacketCreator.rangedAttack(30000, attack.skill, attack.stance,
			// attack.numAttackedAndDamage, projectile, attack.allDamage);
			// player.getMap().broadcastMessage(null, packet);

			int basedamage;
			int projectileWatk = 0;
			if (projectile != 0) {
				projectileWatk = mii.getWatkForProjectile(projectile);
			}
			if (attack.skill != 4001344) { // not lucky 7
				if (projectileWatk != 0) {
					basedamage = c.getPlayer().calculateMaxBaseDamage(c.getPlayer().getTotalWatk() + projectileWatk);
				} else {
					basedamage = c.getPlayer().getCurrentMaxBaseDamage();
				}
			} else { // l7 has a different formula :>
				basedamage = (int) (((c.getPlayer().getTotalLuk() * 5.0) / 100.0) * (c.getPlayer().getTotalWatk() + projectileWatk));
			}
			if (attack.skill == 3101005) { //arrowbomb is hardcore like that �.o
				basedamage *= effect.getX() / 100.0;
			}
			int maxdamage = basedamage;
			double critdamagerate = 0.0;
			if (player.getJob().isA(MapleJob.ASSASSIN)) {
				ISkill criticalthrow = SkillFactory.getSkill(4100001);
				int critlevel = player.getSkillLevel(criticalthrow);
				if (critlevel > 0) {
					critdamagerate = (criticalthrow.getEffect(player.getSkillLevel(criticalthrow)).getDamage() / 100.0);
				}
			} else if (player.getJob().isA(MapleJob.BOWMAN)) {
				ISkill criticalshot = SkillFactory.getSkill(3000001);
				int critlevel = player.getSkillLevel(criticalshot);
				if (critlevel > 0) {
					critdamagerate = (criticalshot.getEffect(critlevel).getDamage() / 100.0) - 1.0;
				}
			}
			int critdamage = (int) (basedamage * critdamagerate);
			if (effect != null) {
				maxdamage *= effect.getDamage() / 100.0;
			}
			maxdamage += critdamage;
			maxdamage *= damageBulletCount;
			if (hasShadowPartner) {
				ISkill shadowPartner = SkillFactory.getSkill(4111002);
				int shadowPartnerLevel = player.getSkillLevel(shadowPartner);
				MapleStatEffect shadowPartnerEffect = shadowPartner.getEffect(shadowPartnerLevel);
				if (attack.skill != 0) {
					maxdamage *= (1.0 + shadowPartnerEffect.getY() / 100.0);
				} else {
					maxdamage *= (1.0 + shadowPartnerEffect.getX() / 100.0);
				}
			}
			if (attack.skill == 4111004) {
				maxdamage = 35000;
			}
			//maxdamage = Math.min(maxdamage, 99999);
			
			if (effect != null) {
				int money = effect.getMoneyCon();
				if (money != 0) {
					double moneyMod = money * 0.5;
					money = (int) (money + Math.random() * moneyMod);
					if (money > player.getMeso()) {
						money = player.getMeso();
					}
					player.gainMeso(-money, false);
				}
			}
			if (attack.skill != 0) {
				ISkill skill = SkillFactory.getSkill(attack.skill);
				int skillLevel = c.getPlayer().getSkillLevel(skill);
				MapleStatEffect effect_ = skill.getEffect(skillLevel);
				if (effect_.getCooldown() > 0) {
					c.getSession().write(MaplePacketCreator.skillCooldown(attack.skill, effect_.getCooldown()));
					final MapleClient client = c;
					final int skillId = attack.skill;
					if (effect_.getCooldown() > 0) {
						c.getSession().write(MaplePacketCreator.skillCooldown(attack.skill, effect_.getCooldown()));
						ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(c.getPlayer(), skillId), effect_.getCooldown() * 1000);
						c.getPlayer().addCooldown(attack.skill, System.currentTimeMillis(), effect_.getCooldown() * 1000, timer);
					}				
				}
			}
			applyAttack(attack, player, maxdamage, bulletCount);
		}
	}
}
