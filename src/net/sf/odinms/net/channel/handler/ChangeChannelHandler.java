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

package net.sf.odinms.net.channel.handler;

import java.net.InetAddress;
import java.rmi.RemoteException;

import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MapleMessengerCharacter;
import net.sf.odinms.net.world.remote.WorldChannelInterface;
import net.sf.odinms.server.MapleTrade;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author Matze
 */
public class ChangeChannelHandler extends AbstractMaplePacketHandler {

	private static final Logger log = LoggerFactory.getLogger(ChangeChannelHandler.class);
    
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
		int channel = slea.readByte() + 1; // there is some int after it...but...wtf?
		String ip = ChannelServer.getInstance(c.getChannel()).getIP(channel);
		// ip = "127.0.0.1:7575";
		// System.out.println("Changing channel towards " + ip);
		String[] socket = ip.split(":");
		if (c.getPlayer().getTrade() != null) {
			MapleTrade.cancelTrade(c.getPlayer());
		}
		c.getPlayer().cancelMagicDoor();
		if (c.getPlayer().getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
			c.getPlayer().cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
		}
		if (c.getPlayer().getBuffedValue(MapleBuffStat.PUPPET) != null) {
			c.getPlayer().cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
		}
		try {
			WorldChannelInterface wci = ChannelServer.getInstance(c.getChannel()).getWorldInterface();
			wci.addBuffsToStorage(c.getPlayer().getId(), c.getPlayer().getAllBuffs());
			wci.addCooldownsToStorage(c.getPlayer().getId(), c.getPlayer().getAllCooldowns());
		} catch (RemoteException e) {
			log.info("RemoteException: {}", e);
			c.getChannelServer().reconnectWorld();
		}
		c.getPlayer().saveToDB(true);
		if (c.getPlayer().getCheatTracker() != null)
			c.getPlayer().getCheatTracker().dispose();
                
                if (c.getPlayer().getMessenger() != null) {
                        MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(c.getPlayer());
                        try {
				WorldChannelInterface wci = ChannelServer.getInstance(c.getChannel()).getWorldInterface();
                                wci.silentLeaveMessenger(c.getPlayer().getMessenger().getId(), messengerplayer);
                        } 
                        catch (RemoteException e) {
                                c.getChannelServer().reconnectWorld();
                        }
                }
                
		c.getPlayer().getMap().removePlayer(c.getPlayer());
		ChannelServer.getInstance(c.getChannel()).removePlayer(c.getPlayer());
		c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION);
		try {
			MaplePacket packet = MaplePacketCreator.getChannelChange(
				InetAddress.getByName(socket[0]), Integer.parseInt(socket[1]));
			c.getSession().write(packet);
			// c.getSession().close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
