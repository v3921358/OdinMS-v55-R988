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

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

/**
*
* @author Penguins (Acrylic)
*/
public class EnterCashShopHandler extends AbstractMaplePacketHandler {
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		SpawnPetHandler petHandler = new SpawnPetHandler();
		if (c.getPlayer().getPet() != null) {
			petHandler.unequipPet(c);
		}
		c.getPlayer().cancelAllBuffs();
		c.getPlayer().getMap().removePlayer(c.getPlayer());
		c.getSession().write(MaplePacketCreator.warpCS(c));
		c.getPlayer().setInCS(true);
		//c.getSession().write(HexTool.getByteArrayFromHexString("0A 00 00 00 00 00 00"));
		//c.getSession().write(HexTool.getByteArrayFromHexString("EF 00 2C 00 00 04 00"));
		//c.getSession().write(HexTool.getByteArrayFromHexString("EF 00 2E 00 00"));
		//c.getSession().write(HexTool.getByteArrayFromHexString("EF 00 30 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"));
		c.getSession().write(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
		c.getSession().write(MaplePacketCreator.enableCSUse0());
		c.getSession().write(MaplePacketCreator.enableCSUse1());
		c.getSession().write(MaplePacketCreator.enableCSUse2());
		c.getSession().write(MaplePacketCreator.enableCSUse3());
		c.getPlayer().saveToDB(true);
		//c.getSession().write(HexTool.getByteArrayFromHexString("14 00 00 00"));
		//c.getSession().write(HexTool.getByteArrayFromHexString("10 00 02 C7 94 F7 2F"));
	}
}
