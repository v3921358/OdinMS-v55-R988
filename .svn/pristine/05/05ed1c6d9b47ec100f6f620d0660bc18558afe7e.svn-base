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

import java.util.Random;

import net.sf.odinms.client.ExpTable;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MaplePet;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class PetFoodHandler extends AbstractMaplePacketHandler {

	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PetFoodHandler.class);
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		// 8B 00 4D CB 1C 00 00 00 00 00 00 19
		MaplePet pet = c.getPlayer().getPet(0);
		MapleCharacter player = c.getPlayer();
		
		if (pet == null) {
			pet = c.getPlayer().getPet(1);
		}
		if (pet == null) {
			pet = c.getPlayer().getPet(2);
		}
		if (pet == null) {
			return;
		}
		
		slea.readInt();
		slea.readShort();
		int itemId = slea.readInt();

		boolean gainCloseness = false;
		
		Random rand = new Random();
		int random = rand.nextInt(101);
		if (random <= 50) {
			gainCloseness = true;
		}
		if (pet.getFullness() < 100) {
			int newFullness = pet.getFullness() + 30;
			if (newFullness > 100) {
				newFullness = 100;
			}
			pet.setFullness(newFullness);
			if (gainCloseness && pet.getCloseness() < 30000) {
				int newCloseness = pet.getCloseness() + 1;
				if (newCloseness > 30000) {
				    newCloseness = 30000;
				}
				pet.setCloseness(newCloseness);
				if (newCloseness >= ExpTable.getClosenessNeededForLevel(pet.getLevel() + 1)) {
					pet.setLevel(pet.getLevel() + 1);
				}
			}
			c.getSession().write(MaplePacketCreator.updatePet(pet, true));
			//log.info("To be sent: {}", MaplePacketCreator.commandResponse(player.getId(), (byte) 1, true, true));
			player.getMap().broadcastMessage(player, MaplePacketCreator.commandResponse(player.getId(), (byte) 1,  true, true), true);
		} else {
			if (gainCloseness) {
				int newCloseness = pet.getCloseness() - 1;
				if (newCloseness < 0) {
				    newCloseness = 0;
				}
				pet.setCloseness(newCloseness);
				if (newCloseness < ExpTable.getClosenessNeededForLevel(pet.getLevel())) {
					pet.setLevel(pet.getLevel() - 1);
				}
			}
			c.getSession().write(MaplePacketCreator.updatePet(pet, true));
			player.getMap().broadcastMessage(player, MaplePacketCreator.commandResponse(player.getId(), (byte) 1, false, true), true);
		}
		MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, itemId, 1, true, false);
	}
}
