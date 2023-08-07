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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.odinms.client;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.odinms.database.DatabaseConnection;

/**
 *
 * @author Matze
 */
public class MaplePet extends Item {

	private String name;
	private int uniqueid;
	private int closeness = 0;
	private int level = 1;
	private int fullness = 100;
	
	
	private MaplePet(int id, byte position, int uniqueid) {
		super(id, position, (short) 1);
		this.uniqueid = uniqueid;
	}

	public static MaplePet loadFromDb(int itemid, byte position, int petid) {		
		try {
			MaplePet ret = new MaplePet(itemid, position, petid);

			Connection con = DatabaseConnection.getConnection(); // Get a connection to the database
			PreparedStatement ps = con.prepareStatement("SELECT * FROM pets WHERE petid = ?"); // Get pet details..
			ps.setInt(1, petid);

			ResultSet rs = ps.executeQuery();
			rs.next();

			ret.setName(rs.getString("name"));
			ret.setCloseness(rs.getInt("closeness"));
			ret.setLevel(rs.getInt("level"));
			ret.setFullness(rs.getInt("fullness"));

			rs.close();
			ps.close();
			
			return ret;
		} catch (SQLException ex) {
			Logger.getLogger(MaplePet.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public int getUniqueId() {
		return uniqueid;
	}

	public void setUniqueId(int id) {
		this.uniqueid = id;
	}
	
	public int getCloseness() {
		return closeness;
	}

	public void setCloseness(int closeness) {
		this.closeness = closeness;
	}
	
	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public int getFullness() {
		return fullness;
	}

	public void setFullness(int fullness) {
		this.fullness = fullness;
	}
	
}
