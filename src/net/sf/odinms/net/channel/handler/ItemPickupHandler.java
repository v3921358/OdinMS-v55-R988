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

/*
 * ItemPickupHandler.java
 *
 * Created on 29. November 2007, 13:39
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.odinms.net.channel.handler;

import java.util.logging.Level;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.anticheat.CheatingOffense;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.maps.MapleMapItem;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

/**
 *
 * @author Matze
 */
public class ItemPickupHandler extends AbstractMaplePacketHandler {
	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ItemPickupHandler.class);
	
	/** Creates a new instance of ItemPickupHandler */
	public ItemPickupHandler() {
	}

	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		if (c.getPlayer().getName().equalsIgnoreCase("Danny")) {
			log.info("Pickup packet: {}", slea.toString());
		}
		@SuppressWarnings("unused")
		byte mode = slea.readByte(); // or something like that...but better ignore it if you want
					     // mapchange to work! o.o!
		slea.readInt(); //?
		slea.readInt(); // position, but we dont need it o.o
		int oid = slea.readInt();
		MapleMapObject ob = c.getPlayer().getMap().getMapObject(oid);
		if (ob == null) {
			c.getSession().write(MaplePacketCreator.getInventoryFull());
			c.getSession().write(MaplePacketCreator.getShowInventoryFull());
			return;
		}
		if (ob instanceof MapleMapItem) {
			MapleMapItem mapitem = (MapleMapItem)ob;
			synchronized (mapitem) {
				if (mapitem.isPickedUp()) {
					c.getSession().write(MaplePacketCreator.getInventoryFull());
					c.getSession().write(MaplePacketCreator.getShowInventoryFull());
					return;
				}
				double distance = c.getPlayer().getPosition().distanceSq(mapitem.getPosition());
				c.getPlayer().getCheatTracker().checkPickupAgain();
				if (distance > 90000.0) { // 300^2, 550 is approximatly the range of ultis
					// AutobanManager.getInstance().addPoints(c, 100, 300000, "Itemvac");
					c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.ITEMVAC);
					// Double.valueOf(Math.sqrt(distance))
				} else if (distance > 30000.0) {
					// log.warn("[h4x] Player {} is picking up an item that's fairly far away: {}", c.getPlayer().getName(), Double.valueOf(Math.sqrt(distance)));
					c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.SHORT_ITEMVAC);
				}
				if (mapitem.getMeso() > 0) {
					c.getPlayer().gainMeso(mapitem.getMeso(), true, true);
					c.getPlayer().getMap().broadcastMessage(
						MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 2, c.getPlayer().getId()),
						mapitem.getPosition());
					c.getPlayer().getCheatTracker().pickupComplete();
					c.getPlayer().getMap().removeMapObject(ob);
				} else {
					StringBuilder logInfo = new StringBuilder("Picked up by ");
					logInfo.append(c.getPlayer().getName());
					if (mapitem.getItem().getItemId() >= 5000000 && mapitem.getItem().getItemId() <= 5000045) {
						try {
							MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
							Connection con = DatabaseConnection.getConnection();
							PreparedStatement ps = con.prepareStatement("INSERT INTO pets (name, level, closeness, fullness) VALUES (?, ?, ?, ?)");
							ps.setString(1, ii.getName(mapitem.getItem().getItemId()));
							ps.setInt(2, 1);
							ps.setInt(3, 0);
							ps.setInt(4, 100);
							ps.executeUpdate();
							ResultSet rs = ps.getGeneratedKeys();
							rs.next();
							//c.getPlayer().equipChanged();
							MapleInventoryManipulator.addById(c, mapitem.getItem().getItemId(), mapitem.getItem().getQuantity(), "Cash Item was purchased.", null, rs.getInt(1));
							rs.close();
							ps.close();
							c.getPlayer().getMap().broadcastMessage(
								MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 2, c.getPlayer().getId()),
								mapitem.getPosition());
							c.getPlayer().getCheatTracker().pickupComplete();
							c.getPlayer().getMap().removeMapObject(ob);
						} catch (SQLException ex) {
							java.util.logging.Logger.getLogger(BuyCSItemHandler.class.getName()).log(Level.SEVERE, null, ex);
						}
					} else {
						if (MapleInventoryManipulator.addFromDrop(c, mapitem.getItem(), logInfo.toString())) {
							c.getPlayer().getMap().broadcastMessage(
								MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 2, c.getPlayer().getId()),
								mapitem.getPosition());
							c.getPlayer().getCheatTracker().pickupComplete();
							c.getPlayer().getMap().removeMapObject(ob);
						} else {
							c.getPlayer().getCheatTracker().pickupComplete();
							return;
						}
					}
				}
				mapitem.setPickedUp(true);
			}
		}
	}
	
}
