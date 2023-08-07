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

import java.util.ArrayList;
import java.util.List;

import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributeAPHandler extends AbstractMaplePacketHandler {
	private static Logger log = LoggerFactory.getLogger(DistributeAPHandler.class);
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		List<Pair<MapleStat, Integer>> statupdate = new ArrayList<Pair<MapleStat, Integer>>(2);
		c.getSession().write(MaplePacketCreator.updatePlayerStats(statupdate, true));
		slea.readInt(); // whatever

		int update = slea.readInt();
		if (c.getPlayer().getRemainingAp() > 0) {
			switch (update) {
				case 64: // str
					if (c.getPlayer().getStr() >= 999) {
						return;
					}
					c.getPlayer().setStr(c.getPlayer().getStr() + 1);
					statupdate.add(new Pair<MapleStat, Integer>(MapleStat.STR, c.getPlayer().getStr()));
					break;
				case 128: // dex
					if (c.getPlayer().getDex() >= 999) {
						return;
					}
					c.getPlayer().setDex(c.getPlayer().getDex() + 1);
					statupdate.add(new Pair<MapleStat, Integer>(MapleStat.DEX, c.getPlayer().getDex()));
					break;
				case 256: // int
					if (c.getPlayer().getInt() >= 999) {
						return;
					}
					c.getPlayer().setInt(c.getPlayer().getInt() + 1);
					statupdate.add(new Pair<MapleStat, Integer>(MapleStat.INT, c.getPlayer().getInt()));
					break;
				case 512: // luk
					if (c.getPlayer().getLuk() >= 999) {
						return;
					}
					c.getPlayer().setLuk(c.getPlayer().getLuk() + 1);
					statupdate.add(new Pair<MapleStat, Integer>(MapleStat.LUK, c.getPlayer().getLuk()));
					break;
				case 2048: // hp
                                    int maxhp = c.getPlayer().getMaxHp();
                                    if (maxhp >= 30000) {
                                        c.getSession().write(
                                                MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                        return;
                                    } else {
                                        if (c.getPlayer().getJob().isA(MapleJob.BEGINNER)) {
                                                maxhp += rand(8, 12);
                                        } else if (c.getPlayer().getJob().isA(MapleJob.WARRIOR)) {
                                                ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
                                                int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                                                maxhp += rand(20, 24);
                                                maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getX();
                                        } else if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN)) {
                                                maxhp += rand(6, 10);
                                        } else if (c.getPlayer().getJob().isA(MapleJob.BOWMAN)) {
                                                maxhp += rand(16, 20);
                                        } else if (c.getPlayer().getJob().isA(MapleJob.THIEF)) {
                                                maxhp += rand(16, 20);
                                        }
                                        maxhp = Math.min(30000, maxhp);
                                        c.getPlayer().setMaxHp(maxhp);
                                        c.getPlayer().updateSingleStat(MapleStat.MAXHP, maxhp);
                                        c.getPlayer().updateSingleStat(MapleStat.HP, maxhp);
                                        c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed()+1);
                                        break;
                                    }
				case 8192: // mp
                                    int maxmp = c.getPlayer().getMaxMp();
                                    if (maxmp >= 30000) {
                                        c.getSession().write(
                                                MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                        return;
                                    } else {
                                        if (c.getPlayer().getJob().isA(MapleJob.BEGINNER)) {
                                                maxmp += rand(6, 8);
                                        } else if (c.getPlayer().getJob().isA(MapleJob.WARRIOR)) {
                                                maxmp += rand(2, 4);
                                        } else if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN)) {
                                                ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
                                                int improvingMaxMPLevel = c.getPlayer().getSkillLevel(improvingMaxMP);
                                                maxmp += rand(18, 20);
                                                maxmp += 2 * improvingMaxMP.getEffect(improvingMaxMPLevel).getX();
                                        } else if (c.getPlayer().getJob().isA(MapleJob.BOWMAN)) {
                                                maxmp += rand(10, 12);
                                        } else if (c.getPlayer().getJob().isA(MapleJob.THIEF)) {
                                                maxmp += rand(10, 12);
                                        }
                                        maxmp = Math.min(30000, maxmp);
                                        c.getPlayer().setMaxMp(maxmp);
                                        c.getPlayer().updateSingleStat(MapleStat.MAXMP, maxmp);
                                        c.getPlayer().updateSingleStat(MapleStat.MP, maxmp);
                                        c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed()+1);
                                        break;
                                    }
				default: // TODO: implement hp and mp adding
					c.getSession().write(
						MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
					return;
			}
			c.getPlayer().setRemainingAp(c.getPlayer().getRemainingAp() - 1);
			statupdate.add(new Pair<MapleStat, Integer>(MapleStat.AVAILABLEAP, c.getPlayer().getRemainingAp()));
			c.getSession().write(MaplePacketCreator.updatePlayerStats(statupdate, true));
		} else {
			//AutobanManager.getInstance().addPoints(c, 334, 120000, "Trying to distribute AP to " + update + " that are not availables");
			log.info("[h4x] Player {} is distributing ap to {} without having any", c.getPlayer().getName(), Integer.valueOf(update));
		}
	}
        
        private static int rand(int lbound, int ubound) {
		return (int) ((Math.random() * (ubound - lbound + 1)) + lbound);
	}
}
