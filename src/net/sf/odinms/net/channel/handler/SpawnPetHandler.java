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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MaplePet;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.provider.MapleData;
import net.sf.odinms.provider.MapleDataProviderFactory;
import net.sf.odinms.provider.MapleDataTool;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class SpawnPetHandler extends AbstractMaplePacketHandler {
    
	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpawnPetHandler.class);
	
	/*	TODO:
	 *	1.  If a pet is out, check if the one being clicked is the same pet, or a different one.
	 *	    If it is different, change the pet that is out instead of just putting the pet away.
	 *	2.  Multipet - do check above ^^^ and then if it is different AND less than 3 pets are out,
	 *	    send out another. Needs updates to MaplePet too.
	 *	3.  Move the equpping into a function.
	 */ 
	
	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		slea.readInt();
		byte slot = slea.readByte();

		// Handle dragons
		if (c.getPlayer().getInventory(MapleInventoryType.CASH).getItem(slot).getItemId() == 5000028) {
			c.getSession().write(MaplePacketCreator.enableActions());
			return;
		}

		// New instance of MaplePet - using the item ID and unique pet ID
		MaplePet pet = MaplePet.loadFromDb(c.getPlayer().getInventory(MapleInventoryType.CASH).getItem(slot).getItemId(), slot, c.getPlayer().getInventory(MapleInventoryType.CASH).getItem(slot).getPetId());

		// Assign the pet to the player, set stats
		if (c.getPlayer().getPetIndex(pet) != -1) {
			unequipPet(c, pet, true);
		} else {
			if (c.getPlayer().getNoPets() == 3) {
				MaplePet pet_ = c.getPlayer().getPet(0);
				unequipPet(c, pet_, false);
			}
			c.getPlayer().addPet(pet, c.getPlayer().getNextEmptyPetIndex());
			
			// Broadcast packet to the map...
			c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showPet(c.getPlayer(), pet, false), true);

			// Find the pet's unique ID
			int uniqueid = pet.getUniqueId();

			// Make a new List for the stat update
			List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>();
			stats.add(new Pair<MapleStat, Integer>(MapleStat.PET, Integer.valueOf(uniqueid)));

			// Write the stat update to the player...
			c.getSession().write(MaplePacketCreator.updatePlayerStats(stats, false, true, c.getPlayer().getNoPets()));
			c.getSession().write(MaplePacketCreator.enableActions());

			// Get the data
			MapleData petData = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/Item.wz")).getData("Pet/" + String.valueOf(pet.getItemId()) + ".img");
			MapleData hungerData = petData.getChildByPath("info/hungry");

			// Start the fullness schedule
			c.getPlayer().startFullnessSchedule(MapleDataTool.getInt(hungerData), pet);

		}
	}
	
	public void unequipPet(MapleClient c, MaplePet pet, boolean shift_left) {
		try {

			// Execute statement
			c.getPlayer().cancelFullnessSchedule(pet);

			// Save pet data to the database
			Connection con = DatabaseConnection.getConnection(); // Get a connection to the database
			PreparedStatement ps = con.prepareStatement("UPDATE pets SET " + "name = ?, level = ?, " + "closeness = ?, fullness = ? " + "WHERE petid = ?"); // Prepare statement...
			ps.setString(1, pet.getName()); // Set name
			ps.setInt(2, pet.getLevel()); // Set Level
			ps.setInt(3, pet.getCloseness()); // Set Closeness
			ps.setInt(4, pet.getFullness()); // Set Fullness
			ps.setInt(5, pet.getUniqueId()); // Set ID
			ps.executeUpdate(); // Execute statement
			ps.close();

			// Broadcast the packet to the map - with null instead of MaplePet
			c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showPet(c.getPlayer(), pet, true), true);

			// Make a new list for the stat updates
			List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>();
			stats.add(new Pair<MapleStat, Integer>(MapleStat.PET, Integer.valueOf(0)));

			// Write the stat update to the player...
			c.getSession().write(MaplePacketCreator.updatePlayerStats(stats, false, true, c.getPlayer().getPetIndex(pet)));
			c.getSession().write(MaplePacketCreator.enableActions());
			
			// Un-assign the pet set to the player
			c.getPlayer().removePet(pet, shift_left);
		} catch (SQLException ex) {
			Logger.getLogger(SpawnPetHandler.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
