package net.sf.odinms.scripting;

import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventory;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleQuestStatus;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MapleParty;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.net.world.guild.MapleGuild;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.quest.MapleQuest;
import net.sf.odinms.tools.MaplePacketCreator;

public class AbstractPlayerInteraction {
	private MapleClient c;
	
	public AbstractPlayerInteraction(MapleClient c) {
		this.c = c;
	}
	
	protected MapleClient getClient() {
		return c;
	}
	
	public MapleCharacter getPlayer() {
		return c.getPlayer();
	}
	
	public void warp(int map) {
		MapleMap target = getWarpMap(map);
		c.getPlayer().changeMap(target, target.getPortal(0));
	}

	public void warp(int map, int portal) {
		MapleMap target = getWarpMap(map);
		c.getPlayer().changeMap(target, target.getPortal(portal));
	}

	public void warp(int map, String portal) {
		MapleMap target = getWarpMap(map);
		c.getPlayer().changeMap(target, target.getPortal(portal));
	}
	
	private MapleMap getWarpMap(int map) {
		MapleMap target;
		if (getPlayer().getEventInstance() == null) {
			target = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(map);
		}
		else {
			target = getPlayer().getEventInstance().getMapInstance(map);
		}
		return target;
	}
	
	public boolean haveItem(int itemid) {
		return haveItem(itemid, 1);
	}
	
	public boolean haveItem(int itemid, int quantity) {
		return haveItem(itemid, quantity, false, true);
	}
	
	public boolean haveItem(int itemid, int quantity, boolean checkEquipped, boolean greaterOrEquals) {
		return c.getPlayer().haveItem(itemid, quantity, checkEquipped, greaterOrEquals);
	}
	
	public MapleQuestStatus.Status getQuestStatus(int id) {
		return c.getPlayer().getQuest(MapleQuest.getInstance(id)).getStatus();
	}
	
	/**
	 * Gives item with the specified id or takes it if the quantity is negative. Note that this does NOT take items from the equipped inventory.
	 * @param id
	 * @param quantity
	 */
	public void gainItem(int id, short quantity) {
		if (quantity >= 0) {
			StringBuilder logInfo = new StringBuilder(c.getPlayer().getName());
			logInfo.append(" received ");
			logInfo.append(quantity);
			logInfo.append(" from a scripted PlayerInteraction (");
			logInfo.append(this.toString());
			logInfo.append(")");
			MapleInventoryManipulator.addById(c, id, quantity, logInfo.toString());
		} else {
			MapleInventoryManipulator.removeById(c, MapleItemInformationProvider.getInstance().getInventoryType(id), id, -quantity, true, false);
		}
		c.getSession().write(MaplePacketCreator.getShowItemGain(id,quantity, true));
	}
	
	public void changeMusic(String songName) {
		getPlayer().getMap().broadcastMessage(MaplePacketCreator.musicChange(songName));
	}

	// default playerMessage and mapMessage to use type 5
	public void playerMessage(String message) {
		playerMessage(5, message);
	}

	public void mapMessage(String message) {
		mapMessage(5, message);
	}
	
	public void guildMessage(String message) {
		guildMessage(5, message);
	}

	public void playerMessage(int type, String message) {
		c.getSession().write(MaplePacketCreator.serverNotice(type, message));
	}

	public void mapMessage(int type, String message) {
		getPlayer().getMap().broadcastMessage(MaplePacketCreator.serverNotice(type, message));
	}
	
	public void guildMessage(int type, String message) {
		MapleGuild guild = getGuild();
		if (guild != null) {
			guild.guildMessage(MaplePacketCreator.serverNotice(type, message));
			//guild.broadcast(MaplePacketCreator.serverNotice(type, message));
		}
	}
	
	public MapleGuild getGuild() {
		try {
			return c.getChannelServer().getWorldInterface().getGuild(getPlayer().getGuildId(), null);
		} catch (RemoteException ex) {
			Logger.getLogger(AbstractPlayerInteraction.class.getName()).log(Level.SEVERE, null, ex);
		}
		return null;
	}
	
	public MapleParty getParty() {
		return (c.getPlayer().getParty());
	}
	
	public boolean isLeader() {
		return (getParty().getLeader().equals(new MaplePartyCharacter(c.getPlayer())));
	}
	
	//PQ methods: give items/exp to all party members
	public void givePartyItems(int id, short quantity, List<MapleCharacter> party) {
		for(MapleCharacter chr : party) {
			MapleClient cl = chr.getClient();
			if (quantity >= 0) {
				StringBuilder logInfo = new StringBuilder(cl.getPlayer().getName());
				logInfo.append(" received ");
				logInfo.append(quantity);
				logInfo.append(" from event ");
				logInfo.append(chr.getEventInstance().getName());
				MapleInventoryManipulator.addById(cl, id, quantity, logInfo.toString());
			} else {
				MapleInventoryManipulator.removeById(cl, MapleItemInformationProvider.getInstance().getInventoryType(id), id, -quantity, true, false);
			}
			cl.getSession().write(MaplePacketCreator.getShowItemGain(id,quantity, true));
		}
	}
	
	//PQ gain EXP: Multiplied by channel rate here to allow global values to be input direct into NPCs
	public void givePartyExp(int amount, List<MapleCharacter> party) {
		for(MapleCharacter chr : party) {
			chr.gainExp(amount * c.getChannelServer().getExpRate(), true, true);
		}
	}
	
	//remove all items of type from party
	//combination of haveItem and gainItem
	public void removeFromParty(int id, List<MapleCharacter> party) {
		for (MapleCharacter chr : party) {
			MapleClient cl = chr.getClient();
			MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(id);
			MapleInventory iv = cl.getPlayer().getInventory(type);
			int possesed = iv.countById(id);
			
			if (possesed > 0) {
				MapleInventoryManipulator.removeById(c, MapleItemInformationProvider.getInstance().getInventoryType(id), id, possesed, true, false);
				cl.getSession().write(MaplePacketCreator.getShowItemGain(id, (short)-possesed, true));
			}
		}
	}
	
	//remove all items of type from character
	//combination of haveItem and gainItem
	public void removeAll(int id) {
		removeAll(id, c);
	}
	
	//remove all items of type from character
	//combination of haveItem and gainItem
	public void removeAll(int id, MapleClient cl) {
		MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(id);
		MapleInventory iv = cl.getPlayer().getInventory(type);
		int possessed = iv.countById(id);

		if (possessed > 0) {
			MapleInventoryManipulator.removeById(cl, MapleItemInformationProvider.getInstance().getInventoryType(id), id, possessed, true, false);
			cl.getSession().write(MaplePacketCreator.getShowItemGain(id, (short)-possessed, true));
		}
		
		/*//if equip, remove any equips matching ID
		if (type == MapleInventoryType.EQUIP) {
			iv = cl.getPlayer().getInventory(MapleInventoryType.EQUIPPED);
			if (iv.findById(id) != null) {
				//MapleInventoryManipulator.removeById(cl, MapleInventoryType.EQUIPPED, id, 1, true, false);
				
				//hack: only GQ earrings for now so only byte -4; find a way to remove any equip
				cl.getPlayer().getInventory(MapleInventoryType.EQUIPPED).removeItem((byte)-4);
				cl.getSession().write(MaplePacketCreator.getShowItemGain(id, (short)-1, true));
				//cl.getSession().write(MaplePacketCreator.clearInventoryItem(MapleInventoryType.EQUIPPED, (byte) -4, false));
				cl.getPlayer().equipChanged();
			}
		}*/
	}
}
