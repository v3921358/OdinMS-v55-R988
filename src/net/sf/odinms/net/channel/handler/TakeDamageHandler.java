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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleDisease;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.AutobanManager;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.provider.MapleData;
import net.sf.odinms.provider.MapleDataProviderFactory;
import net.sf.odinms.provider.MapleDataTool;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class TakeDamageHandler extends AbstractMaplePacketHandler {
    
	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TakeDamageHandler.class);
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// damage from map object
		// 26 00 EB F2 2B 01 FE 25 00 00 00 00 00
		// damage from monster
		// 26 00 0F 60 4C 00 FF 48 01 00 00 B5 89 5D 00 CC CC CC CC 00 00 00 00
		MapleCharacter player = c.getPlayer();
		
		slea.readInt();
		int damagefrom = slea.readByte();
		int damage = slea.readInt();
		int oid = 0;
		int monsteridfrom = 0;
		int pgmr = 0;
		int direction = 0;
		int pos_x = 0;
		int pos_y = 0;
		int fake = 0;
		boolean is_pgmr = false;
		boolean is_pg = true;
		boolean loseMP = false;
		int mpattack = 0;
		
		if (damagefrom != -2) {
			monsteridfrom = slea.readInt();
			oid = slea.readInt();
			direction = slea.readByte();
			pgmr = slea.readShort();
			if (pgmr != 0) {
				is_pgmr = true;
				if (slea.readByte() == 0) {
					is_pg = false;
				}
				slea.readInt();
				slea.readByte();
				slea.readInt();
				pos_x = slea.readShort();
				pos_y = slea.readShort();
				if (!is_pg) {
					MapleMonster attacker = (MapleMonster) player.getMap().getMapObject(oid);
					if (attacker != null) {
						player.getMap().damageMonster(player, attacker, (damage * pgmr / 100));
						player.getMap().broadcastMessage(player, MaplePacketCreator.damageMonster(oid, (damage * pgmr / 100)), false, true);
					}
				}
			}
		}

		if (damagefrom != -1 && damagefrom != -2) {
			MapleData mobData = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/Mob.wz")).getData(monsteridfrom + ".img");
			MapleData attackData = mobData.getChildByPath("attack" + (damagefrom + 1) + "/info");
			MapleData deadlyAttack = attackData.getChildByPath("deadlyAttack");
			MapleData mpBurn = attackData.getChildByPath("mpBurn");
			MapleData disease = attackData.getChildByPath("disease");
			if (deadlyAttack != null) {
				loseMP = true;
				mpattack = c.getPlayer().getMp() - 1;
			} else if (mpBurn != null) {
				loseMP = true;
				mpattack = MapleDataTool.getInt(mpBurn);
			}
			if (disease != null) {
				MapleDisease type = null;
				switch (MapleDataTool.getInt(disease)) {
					case 120:
						type = MapleDisease.SEAL;
					case 121:
						type = MapleDisease.DARKNESS;
					case 122:
						type = MapleDisease.WEAKEN;
					case 123:
						type = MapleDisease.STUN;
					case 125:
						type = MapleDisease.POISON;
					case 128:
						type = MapleDisease.SEDUCE;
					default:
						type = null;
				}
				if (type != null) {
					
				}
			}
		}
		

		if (damage == -1) {
			int job = (int) (player.getJob().getId() / 10 - 40);
			fake =  4020002 + (job * 100000);
		}
		
		if (damage < -1 || damage > 60000) {
			AutobanManager.getInstance().addPoints(c, 1000, 60000, "Taking abnormal amounts of damge from " + monsteridfrom + ": " + damage);
			return;
		}
		player.getCheatTracker().checkTakeDamage();
		
		if (damage > 0) {
			player.getCheatTracker().setAttacksWithoutHit(0);	
			player.getCheatTracker().resetHPRegen();
		}
		if (damage > 0 && !player.isHidden()) {
			if (damagefrom == -1) {
				Integer pguard = player.getBuffedValue(MapleBuffStat.POWERGUARD);
				if (pguard != null) {
					// why do we have to do this? -.- the client shows the damage...
					MapleMonster attacker = (MapleMonster) player.getMap().getMapObject(oid);
					if (attacker != null && !attacker.isBoss()) {
						int bouncedamage = (int) (damage * (pguard.doubleValue() / 100));
						bouncedamage = Math.min(bouncedamage, attacker.getMaxHp() / 10);
						player.getMap().damageMonster(player, attacker, bouncedamage);
						damage -= bouncedamage;
						player.getMap().broadcastMessage(player, MaplePacketCreator.damageMonster(oid, bouncedamage), false, true);
					}
				}
				Integer achilles = 0;
				ISkill achilles1 = null;
				switch (player.getJob().getId()) {
					case 112:
						achilles = player.getSkillLevel(SkillFactory.getSkill(1120004));
						achilles1 = SkillFactory.getSkill(1120004);
						break;
					case 122:
						achilles = player.getSkillLevel(SkillFactory.getSkill(1220005));
						achilles1 = SkillFactory.getSkill(1220005);
						break;
					case 132:
						achilles = player.getSkillLevel(SkillFactory.getSkill(1320005));
						achilles1 = SkillFactory.getSkill(1320005);
						break;
				}
				if (achilles != 0) {
					int x = achilles1.getEffect(achilles).getX();
					double multiplier = x / 1000.0;
					int newdamage = (int)(multiplier * damage);
					damage = newdamage;
				}
			}
			Integer mguard = player.getBuffedValue(MapleBuffStat.MAGIC_GUARD);
			Integer mesoguard = player.getBuffedValue(MapleBuffStat.MESOGUARD);
			if (mguard != null && !loseMP) {
				List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>(2);
				int mploss = (int) (damage * (mguard.doubleValue() / 100.0));
				int hploss = damage - mploss;
				if (mploss > player.getMp()) {
					hploss += mploss - player.getMp();
					mploss = player.getMp();
				}
				
				player.setHp(player.getHp() - hploss);
				player.setMp(player.getMp() - mploss);
				stats.add(new Pair<MapleStat, Integer>(MapleStat.HP, player.getHp()));
				stats.add(new Pair<MapleStat, Integer>(MapleStat.MP, player.getMp()));
				c.getSession().write(MaplePacketCreator.updatePlayerStats(stats));
			} else if(mesoguard != null) { 
				damage = (damage % 2 == 0) ? damage / 2 : (damage / 2) + 1;
				int mesoloss = (int) (damage * (mesoguard.doubleValue() / 100.0));
				if(player.getMeso() < mesoloss) {
					player.gainMeso(-player.getMeso(), false);
					player.cancelBuffStats(MapleBuffStat.MESOGUARD);
				} else {
					player.gainMeso(-mesoloss, false);
				}
				if (loseMP) {
					player.addMP(-mpattack);
				}
				player.addHP(-damage);
			} else {
				if (loseMP) {
					player.addMP(-mpattack);
				}
				player.addHP(-damage);
			}
		}
		// player.getMap().broadcastMessage(null, MaplePacketCreator.damagePlayer(oid, 30000, damage));
		if (!player.isHidden()) {
			player.getMap().broadcastMessage(player,
				MaplePacketCreator.damagePlayer(damagefrom, monsteridfrom, player.getId(), damage, fake, direction, is_pgmr, pgmr, is_pg, oid, pos_x, pos_y), false);
		}
	}
}
