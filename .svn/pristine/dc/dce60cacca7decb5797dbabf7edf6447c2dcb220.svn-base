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

import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MaplePet;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.MapleServerHandler;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;
import net.sf.odinms.server.CashItemFactory;
import net.sf.odinms.server.CashItemInfo;

/**
*
* @author Penguins (Acrylic)
*/
public class BuyCSItemHandler extends AbstractMaplePacketHandler {
	private final static Logger log = LoggerFactory.getLogger(MapleServerHandler.class);
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
                log.info(slea.toString());
                int mode = slea.readByte();
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		slea.skip(1);
		int snCS = slea.readInt();
		CashItemInfo item = CashItemFactory.getItem(snCS);
		
		if (item.getId() >= 5000000 && item.getId() <= 5000045) {
			try {
				Connection con = DatabaseConnection.getConnection();
				PreparedStatement ps = con.prepareStatement("INSERT INTO pets (name, level, closeness, fullness) VALUES (?, ?, ?, ?)");
				ps.setString(1, ii.getName(item.getId()));
				ps.setInt(2, 1);
				ps.setInt(3, 0);
				ps.setInt(4, 100);
				ps.executeUpdate();
				ResultSet rs = ps.getGeneratedKeys();
				rs.next();
				//c.getPlayer().equipChanged();
				MapleInventoryManipulator.addById(c, item.getId(), (short) item.getCount(), "Cash Item was purchased.", null, rs.getInt(1));
				rs.close();
				ps.close();
			} catch (SQLException ex) {
			    java.util.logging.Logger.getLogger(BuyCSItemHandler.class.getName()).log(Level.SEVERE, null, ex);
			}
		} else {
			MapleInventoryManipulator.addById(c, item.getId(), (short) item.getCount(), "Cash Item was purchased.");
		}
		c.getSession().write(MaplePacketCreator.showBoughtCSItem(item.getId()));
		c.getPlayer().modifyCSPoints(0, -item.getPrice());
		c.getSession().write(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
		c.getSession().write(MaplePacketCreator.enableCSUse0());
		c.getSession().write(MaplePacketCreator.enableCSUse1());
		c.getSession().write(MaplePacketCreator.enableCSUse2());
		c.getSession().write(MaplePacketCreator.enableCSUse3());

	}
}
