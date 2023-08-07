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
package net.sf.odinms.tools;

import java.awt.Point;
import java.awt.Rectangle;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.odinms.client.BuddylistEntry;
import net.sf.odinms.client.IEquip;
import net.sf.odinms.client.IItem;
import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.Item;
import net.sf.odinms.client.MapleBuffStat;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventory;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleKeyBinding;
import net.sf.odinms.client.MaplePet;
import net.sf.odinms.client.MapleQuestStatus;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.client.IEquip.ScrollResult;
import net.sf.odinms.client.status.MonsterStatus;
import net.sf.odinms.net.ByteArrayMaplePacket;
import net.sf.odinms.net.LongValueHolder;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.SendPacketOpcode;
import net.sf.odinms.net.channel.handler.SummonDamageHandler.SummonAttackEntry;
import net.sf.odinms.net.world.MapleParty;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.net.world.PartyOperation;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MaplePlayerShop;
import net.sf.odinms.server.MaplePlayerShopItem;
import net.sf.odinms.server.MapleShopItem;
import net.sf.odinms.server.MapleTrade;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.life.MapleNPC;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleReactor;
import net.sf.odinms.server.maps.SummonMovementType;
import net.sf.odinms.server.movement.LifeMovementFragment;
import net.sf.odinms.tools.data.output.LittleEndianWriter;
import net.sf.odinms.tools.data.output.MaplePacketLittleEndianWriter;
import net.sf.odinms.net.world.guild.*;


/**
 * Provides all MapleStory packets needed in one place.
 * 
 * @author Frz
 * @since Revision 259
 * @version 1.0
 */
public class MaplePacketCreator {
	private static Logger log = LoggerFactory.getLogger(MaplePacketCreator.class);

	private final static byte[] CHAR_INFO_MAGIC = new byte[] { (byte) 0xff, (byte) 0xc9, (byte) 0x9a, 0x3b };
	private final static byte[] ITEM_MAGIC = new byte[] { (byte) 0x80, 5 };
	public static final List<Pair<MapleStat, Integer>> EMPTY_STATUPDATE = Collections.emptyList();

	//credit goes to Simon for this function, copied (with slight variation) out of his tempban
	//convert to EDT
	private final static long FT_UT_OFFSET = 116444592000000000L;

	private static long getKoreanTimestamp(long realTimestamp) {
		long time = (realTimestamp / 1000 / 60); //convert to minutes
		return ((time * 600000000) + FT_UT_OFFSET);
	}
	
	/**
	 * Sends a hello packet.
	 * 
	 * @param mapleVersion The maple client version.
	 * @param sendIv the IV used by the server for sending
	 * @param recvIv the IV used by the server for receiving
	 */
	public static MaplePacket getHello(short mapleVersion, byte[] sendIv, byte[] recvIv, boolean testServer) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(0x0d);
		mplew.writeShort(mapleVersion);
		mplew.write(new byte[] { 0, 0 });
		mplew.write(recvIv);
		mplew.write(sendIv);
		mplew.write(testServer ? 5 : 8);
		return mplew.getPacket();
	}

	/**
	 * Sends a ping packet.
	 * 
	 * @return The packet.
	 */
	public static MaplePacket getPing() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(SendPacketOpcode.PING.getValue());
		return mplew.getPacket();
	}

	/**
	 * Gets a login failed packet.
	 * 
	 * Possible values for <code>reason</code>:<br>
	 * 3: ID deleted or blocked<br>
	 * 4: Incorrect password<br>
	 * 5: Not a registered id<br>
	 * 6: System error<br>
	 * 7: Already logged in<br>
	 * 8: System error<br>
	 * 9: System error<br>
	 * 10: Cannot process so many connections<br>
	 * 11: Only users older than 20 can use this channel
	 * 
	 * @param reason The reason logging in failed.
	 * @return The login failed packet.
	 */
	public static MaplePacket getLoginFailed(int reason) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.writeInt(reason);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	public static MaplePacket getPermBan(byte reason) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
		// Response.WriteHexString("00 00 02 00 01 01 01 01 01 00");
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.writeShort(0x02); // Account is banned
		mplew.write(0x0);
		mplew.write(reason);
		mplew.write(HexTool.getByteArrayFromHexString("01 01 01 01 00"));
		return mplew.getPacket();
	}

	public static MaplePacket getTempBan(long timestampTill, byte reason) {

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(17);
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.write(0x02);
		mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00")); // Account is banned
		mplew.write(reason);
		mplew.writeLong(timestampTill); // Tempban date is handled as a 64-bit long, number of 100NS intervals since
										// 1/1/1601. Lulz.
		return mplew.getPacket();
	}

	/**
	 * Gets a successful authentication and PIN Request packet.
	 * 
	 * @param account The account name.
	 * @return The PIN request packet.
	 */
	public static MaplePacket getAuthSuccessRequestPin(String account) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
		mplew.write(new byte[] { 0, 0, 0, 0, 0, 0, (byte) 0xFF, 0x6A, 1, 0, 0, 0, 0x4E });
		mplew.writeMapleAsciiString(account);
		mplew
			.write(new byte[] { 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xDC, 0x3D, 0x0B, 0x28, 0x64, (byte) 0xC5, 1, 8, 0, 0, 0 });
		return mplew.getPacket();
	}

	/**
	 * Gets a packet detailing a PIN operation.
	 * 
	 * Possible values for <code>mode</code>:<br>
	 * 0 - PIN was accepted<br>
	 * 1 - Register a new PIN<br>
	 * 2 - Invalid pin / Reenter<br>
	 * 3 - Connection failed due to system error<br>
	 * 4 - Enter the pin
	 * 
	 * @param mode The mode.
	 */
	public static MaplePacket pinOperation(byte mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);
		mplew.writeShort(SendPacketOpcode.PIN_OPERATION.getValue());
		mplew.write(mode);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet requesting the client enter a PIN.
	 * 
	 * @return The request PIN packet.
	 */
	public static MaplePacket requestPin() {
		return pinOperation((byte) 4);
	}

	/**
	 * Gets a packet requesting the PIN after a failed attempt.
	 * 
	 * @return The failed PIN packet.
	 */
	public static MaplePacket requestPinAfterFailure() {
		return pinOperation((byte) 2);
	}

	/**
	 * Gets a packet saying the PIN has been accepted.
	 * 
	 * @return The PIN accepted packet.
	 */
	public static MaplePacket pinAccepted() {
		return pinOperation((byte) 0);
	}

	/**
	 * Gets a packet detailing a server and its channels.
	 * 
	 * @param serverIndex The index of the server to create information about.
	 * @param serverName The name of the server.
	 * @param channelLoad Load of the channel - 1200 seems to be max.
	 * @return The server info packet.
	 */
	public static MaplePacket getServerList(int serverIndex, String serverName, Map<Integer, Integer> channelLoad) {
		/*
		 * 0B 00 00 06 00 53 63 61 6E 69 61 00 00 00 64 00 64 00 00 13 08 00 53 63 61 6E 69 61 2D 31 5E 04 00 00 00 00
		 * 00 08 00 53 63 61 6E 69 61 2D 32 25 01 00 00 00 01 00 08 00 53 63 61 6E 69 61 2D 33 F6 00 00 00 00 02 00 08
		 * 00 53 63 61 6E 69 61 2D 34 BC 00 00 00 00 03 00 08 00 53 63 61 6E 69 61 2D 35 E7 00 00 00 00 04 00 08 00 53
		 * 63 61 6E 69 61 2D 36 BC 00 00 00 00 05 00 08 00 53 63 61 6E 69 61 2D 37 C2 00 00 00 00 06 00 08 00 53 63 61
		 * 6E 69 61 2D 38 BB 00 00 00 00 07 00 08 00 53 63 61 6E 69 61 2D 39 C0 00 00 00 00 08 00 09 00 53 63 61 6E 69
		 * 61 2D 31 30 C3 00 00 00 00 09 00 09 00 53 63 61 6E 69 61 2D 31 31 BB 00 00 00 00 0A 00 09 00 53 63 61 6E 69
		 * 61 2D 31 32 AB 00 00 00 00 0B 00 09 00 53 63 61 6E 69 61 2D 31 33 C7 00 00 00 00 0C 00 09 00 53 63 61 6E 69
		 * 61 2D 31 34 B9 00 00 00 00 0D 00 09 00 53 63 61 6E 69 61 2D 31 35 AE 00 00 00 00 0E 00 09 00 53 63 61 6E 69
		 * 61 2D 31 36 B6 00 00 00 00 0F 00 09 00 53 63 61 6E 69 61 2D 31 37 DB 00 00 00 00 10 00 09 00 53 63 61 6E 69
		 * 61 2D 31 38 C7 00 00 00 00 11 00 09 00 53 63 61 6E 69 61 2D 31 39 EF 00 00 00 00 12 00
		 */

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
		mplew.write(serverIndex);
		mplew.writeMapleAsciiString(serverName);
		mplew.write(2); // 1: E 2: N 3: H
		// mplew.writeShort(0);

		mplew.writeMapleAsciiString("");
		mplew.write(0x64); // rate modifier, don't ask O.O!

		mplew.write(0x0); // event xp * 2.6 O.O!

		mplew.write(0x64); // rate modifier, don't ask O.O!

		mplew.write(0x0); // drop rate * 2.6

		mplew.write(0x0);
		int lastChannel = 1;
		Set<Integer> channels = channelLoad.keySet();
		for (int i = 30; i > 0; i--) {
			if (channels.contains(i)) {
				lastChannel = i;
				break;
			}
		}
		mplew.write(lastChannel);

		int load;
		for (int i = 1; i <= lastChannel; i++) {
			if (channels.contains(i)) {
				load = channelLoad.get(i);
			} else {
				load = 1200;
			}
			mplew.writeMapleAsciiString(serverName + "-" + i);
			mplew.writeInt(load);
			mplew.write(serverIndex);
			mplew.writeShort(i - 1);
		}

		return mplew.getPacket();
	}

	/**
	 * Gets a packet saying that the server list is over.
	 * 
	 * @return The end of server list packet.
	 */
	public static MaplePacket getEndOfServerList() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
		mplew.write(0xFF);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet detailing a server status message.
	 * 
	 * Possible values for <code>status</code>:<br>
	 * 0 - Normal<br>
	 * 1 - Highly populated<br>
	 * 2 - Full
	 * 
	 * @param status The server status.
	 * @return The server status packet.
	 */
	public static MaplePacket getServerStatus(int status) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SERVERSTATUS.getValue());
		mplew.writeShort(status);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client the IP of the channel server.
	 * 
	 * @param inetAddr The InetAddress of the requested channel server.
	 * @param port The port the channel is on.
	 * @param clientId The ID of the client.
	 * @return The server IP packet.
	 */
	public static MaplePacket getServerIP(InetAddress inetAddr, int port, int clientId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVER_IP.getValue());
		mplew.writeShort(0);
		byte[] addr = inetAddr.getAddress();
		mplew.write(addr);
		mplew.writeShort(port);
		// 0x13 = numchannels?
		mplew.writeInt(clientId); // this gets repeated to the channel server
		// leos.write(new byte[] { (byte) 0x13, (byte) 0x37, 0x42, 1, 0, 0, 0,
		// 0, 0 });

		mplew.write(new byte[] { 0, 0, 0, 0, 0 });
		// 0D 00 00 00 3F FB D9 0D 8A 21 CB A8 13 00 00 00 00 00 00
		// ....?....!.........
		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client the IP of the new channel.
	 * 
	 * @param inetAddr The InetAddress of the requested channel server.
	 * @param port The port the channel is on.
	 * @return The server IP packet.
	 */
	public static MaplePacket getChannelChange(InetAddress inetAddr, int port) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHANGE_CHANNEL.getValue());
		mplew.write(1);
		byte[] addr = inetAddr.getAddress();
		mplew.write(addr);
		mplew.writeShort(port);
		return mplew.getPacket();
	}

	/**
	 * Gets a packet with a list of characters.
	 * 
	 * @param c The MapleClient to load characters of.
	 * @param serverId The ID of the server requested.
	 * @return The character list packet.
	 */
	public static MaplePacket getCharList(MapleClient c, int serverId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CHARLIST.getValue());
		mplew.write(0);
		List<MapleCharacter> chars = c.loadCharacters(serverId);
		mplew.write((byte) chars.size());

		for (MapleCharacter chr : chars) {
			addCharEntry(mplew, chr);
		}

		//System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	/**
	 * Adds character stats to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add the stats of.
	 */
	private static void addCharStats(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		mplew.writeInt(chr.getId()); // character id

		mplew.writeAsciiString(chr.getName());
		for (int x = chr.getName().length(); x < 13; x++) { // fill to maximum
			// name length

			mplew.write(0);
		}

		mplew.write(chr.getGender()); // gender (0 = male, 1 = female)

		mplew.write(chr.getSkinColor().getId()); // skin color

		mplew.writeInt(chr.getFace()); // face

		mplew.writeInt(chr.getHair()); // hair
                
                mplew.writeInt(0);
                mplew.writeInt(0);
                mplew.writeLong(0);
                mplew.writeLong(0);

		mplew.write(chr.getLevel()); // level

		mplew.writeShort(chr.getJob().getId()); // job
		// mplew.writeShort(422);

		mplew.writeShort(chr.getStr()); // str

		mplew.writeShort(chr.getDex()); // dex

		mplew.writeShort(chr.getInt()); // int

		mplew.writeShort(chr.getLuk()); // luk

		mplew.writeShort(chr.getHp()); // hp (?)

		mplew.writeShort(chr.getMaxHp()); // maxhp

		mplew.writeShort(chr.getMp()); // mp (?)

		mplew.writeShort(chr.getMaxMp()); // maxmp

		mplew.writeShort(chr.getRemainingAp()); // remaining ap

		mplew.writeShort(chr.getRemainingSp()); // remaining sp

		mplew.writeInt(chr.getExp()); // current exp

		mplew.writeShort(chr.getFame()); // fame

		mplew.writeInt(chr.getMapId()); // current map id

		mplew.write(chr.getInitialSpawnpoint()); // spawnpoint

	}

	/**
	 * Adds the aesthetic aspects of a character to an existing
	 * MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add the looks of.
	 * @param mega Unknown
	 */
	private static void addCharLook(MaplePacketLittleEndianWriter mplew, MapleCharacter chr, boolean mega) {
		mplew.write(chr.getGender());
		mplew.write(chr.getSkinColor().getId()); // skin color

		mplew.writeInt(chr.getFace()); // face
		// variable length

		mplew.write(mega ? 0 : 1);
		mplew.writeInt(chr.getHair()); // hair

		MapleInventory equip = chr.getInventory(MapleInventoryType.EQUIPPED);
		// Map<Integer, Integer> equipped = new LinkedHashMap<Integer,
		// Integer>();
		Map<Byte, Integer> myEquip = new LinkedHashMap<Byte, Integer>();
		Map<Byte, Integer> maskedEquip = new LinkedHashMap<Byte, Integer>();
		for (IItem item : equip.list()) {
			byte pos = (byte) (item.getPosition() * -1);
			if (pos < 100 && myEquip.get(pos) == null) {
				myEquip.put(pos, item.getItemId());
			} else if (pos > 100 && pos != 111) { // don't ask. o.o

				pos -= 100;
				if (myEquip.get(pos) != null) {
					maskedEquip.put(pos, myEquip.get(pos));
				}
				myEquip.put(pos, item.getItemId());
			} else if (myEquip.get(pos) != null) {
				maskedEquip.put(pos, item.getItemId());
			}
		}
		for (Entry<Byte, Integer> entry : myEquip.entrySet()) {
			mplew.write(entry.getKey());
			mplew.writeInt(entry.getValue());
		}
		mplew.write(0xFF); // end of visible itens
		// masked itens

		for (Entry<Byte, Integer> entry : maskedEquip.entrySet()) {
			mplew.write(entry.getKey());
			mplew.writeInt(entry.getValue());
		}
		/*
		 * for (IItem item : equip.list()) { byte pos = (byte)(item.getPosition() * -1); if (pos > 100) {
		 * mplew.write(pos - 100); mplew.writeInt(item.getItemId()); } }
		 */
		// ending markers
		mplew.write(0xFF);
		IItem cWeapon = equip.getItem((byte) -111);
		if (cWeapon != null) {
			mplew.writeInt(cWeapon.getItemId());
		} else {
			mplew.writeInt(0); // cashweapon

		}
		if (chr.getNoPets() > 0) {
			for (MaplePet pet : chr.getPets()) {
				mplew.writeInt(pet.getItemId());
			}
		}
		for (int i = chr.getNoPets(); i < 3; i++) {
			mplew.writeInt(0);
		}
	}

	/**
	 * Adds an entry for a character to an existing
	 * MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add.
	 */
	private static void addCharEntry(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		addCharStats(mplew, chr);
		addCharLook(mplew, chr, false);

		mplew.write(1); // world rank enabled (next 4 ints are not sent if
						// disabled)
		mplew.writeInt(chr.getRank()); // world rank
		mplew.writeInt(chr.getRankMove()); // move (negative is downwards)
		mplew.writeInt(chr.getJobRank()); // job rank
		mplew.writeInt(chr.getJobRankMove()); // move (negative is downwards)

	}

	/**
	 * Adds a quest info entry for a character to an existing
	 * MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWrite instance to write the stats
	 *            to.
	 * @param chr The character to add quest info about.
	 */
	private static void addQuestInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
		List<MapleQuestStatus> started = chr.getStartedQuests();
		mplew.writeShort(started.size());
		for (MapleQuestStatus q : started) {
			mplew.writeInt(q.getQuest().getId());
		}
		List<MapleQuestStatus> completed = chr.getCompletedQuests();
		mplew.writeShort(completed.size());
		for (MapleQuestStatus q : completed) {
			mplew.writeShort(q.getQuest().getId());
			// maybe start time? no effect.
			mplew.writeInt(KoreanDateUtil.getQuestTimestamp(q.getCompletionTime()));
			// completion time - don't ask about the time format
			mplew.writeInt(KoreanDateUtil.getQuestTimestamp(q.getCompletionTime()));
			// mplew.write(HexTool.getByteArrayFromHexString("80 DF BA C5"));
			// mplew.writeInt(KoreanDateUtil.getKoreanTimestamp(System.currentTimeMillis()));
			// mplew.writeInt((int) (System.currentTimeMillis() / 1000 / 60));
			// 10/19/2006 15:00 == 29815695
			// 10/23/2006 11:00 == 29816466
			// 08/05/2008 01:00 == 29947542
			// mplew.write(HexTool.getByteArrayFromHexString("80 DF BA C5 8B F3
			// C6 01"));
			// mplew.writeInt(29816466 - (int) ((365 * 6 + 10 * 30) * 24 *
			// 8.3804347826086956521739130434783));
			// mplew.writeInt(29816466 + (int) (5 * 24 *
			// 8.3804347826086956521739130434783));
			// mplew.writeInt((int) (134774 * 24 * 8.381905));
			// mplew.writeInt((int) (134774 * 24 * 8.381905));
			// mplew.writeLong(0);
			// mplew.write(HexTool.getByteArrayFromHexString("80 DF BA C5 96 F6
			// C8 01"));
		}
	}

	/**
	 * Gets character info for a character.
	 * 
	 * @param chr The character to get info about.
	 * @return The character info packet.
	 */
	public static MaplePacket getCharInfo(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49

		mplew.writeInt(chr.getClient().getChannel() - 1);
		mplew.write(1);
		mplew.write(1);
		mplew.writeInt(new Random().nextInt()); // seed the maplestory rng with
		// a random number <3

		mplew.write(HexTool.getByteArrayFromHexString("F4 83 6B 3D BA 9A 4F A1 FF FF"));
		addCharStats(mplew, chr);

		mplew.write(chr.getBuddylist().getCapacity()); // buddylist capacity

		mplew.writeInt(chr.getMeso()); // mesos

		mplew.write(100); // equip slots

		mplew.write(100); // use slots

		mplew.write(100); // set-up slots

		mplew.write(100); // etc slots

		mplew.write(100); // cash slots

		MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
		Collection<IItem> equippedC = iv.list();
		List<Item> equipped = new ArrayList<Item>(equippedC.size());
		for (IItem item : equippedC) {
			equipped.add((Item) item);
		}
		Collections.sort(equipped);

		for (Item item : equipped) {
			addItemInfo(mplew, item);
		}
		mplew.writeShort(0); // start of equip inventory

		iv = chr.getInventory(MapleInventoryType.EQUIP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of use inventory
		// addItemInfo(mplew, new Item(2020028, (byte) 8, (short) 1));

		iv = chr.getInventory(MapleInventoryType.USE);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of set-up inventory

		iv = chr.getInventory(MapleInventoryType.SETUP);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of etc inventory

		iv = chr.getInventory(MapleInventoryType.ETC);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of cash inventory

		iv = chr.getInventory(MapleInventoryType.CASH);
		for (IItem item : iv.list()) {
			addItemInfo(mplew, item);
		}
		mplew.write(0); // start of skills

		Map<ISkill, MapleCharacter.SkillEntry> skills = chr.getSkills();
		mplew.writeShort(skills.size());
		for (Entry<ISkill, MapleCharacter.SkillEntry> skill : skills.entrySet()) {
			mplew.writeInt(skill.getKey().getId());
			mplew.writeInt(skill.getValue().skillevel);
			if (skill.getKey().isFourthJob()) {
				mplew.writeInt(skill.getValue().masterlevel);
			}
		}

		mplew.writeShort(0);
		addQuestInfo(mplew, chr);

		mplew.write(new byte[8]);
		for (int x = 0; x < 15; x++) {
			mplew.write(CHAR_INFO_MAGIC);
		}
		mplew.write(HexTool.getByteArrayFromHexString("90 63 3A 0D C5 5D C8 01"));

		return mplew.getPacket();
	}

	/**
	 * Gets an empty stat update.
	 * 
	 * @return The empy stat update packet.
	 */
	public static MaplePacket enableActions() {
		return updatePlayerStats(EMPTY_STATUPDATE, true);
	}

	/**
	 * Gets an update for specified stats.
	 * 
	 * @param stats The stats to update.
	 * @return The stat update packet.
	 */
	public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats) {
		return updatePlayerStats(stats, false);
	}

	/**
	 * Gets an update for specified stats.
	 * 
	 * @param stats The list of stats to update.
	 * @param itemReaction Result of an item reaction(?)
	 * @return The stat update packet.
	 */
	public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats, boolean itemReaction) {
		return updatePlayerStats(stats, itemReaction, false, 0);
	}
	
	/**
	 * Gets an update for specified stats.
	 * 
	 * @param stats The list of stats to update.
	 * @param itemReaction Result of an item reaction(?)
	 * @param pet Result of spawning a pet(?)
	 * @return The stat update packet.
	 */
	public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats, boolean itemReaction, boolean pet, int no_pets) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
		if (itemReaction) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		//if (!pet) {
			mplew.write(0);
		//
		int updateMask = 0;
		for (Pair<MapleStat, Integer> statupdate : stats) {
			updateMask |= statupdate.getLeft().getValue();
		}
		List<Pair<MapleStat, Integer>> mystats = stats;
		if (mystats.size() > 1) {
			Collections.sort(mystats, new Comparator<Pair<MapleStat, Integer>>() {

				@Override
				public int compare(Pair<MapleStat, Integer> o1, Pair<MapleStat, Integer> o2) {
					int val1 = o1.getLeft().getValue();
					int val2 = o2.getLeft().getValue();
					return (val1 < val2 ? -1 : (val1 == val2 ? 0 : 1));
				}
			});
		}
		mplew.writeInt(updateMask);
		for (Pair<MapleStat, Integer> statupdate : mystats) {
			if (statupdate.getLeft().getValue() >= 1) {
				if (statupdate.getLeft().getValue() == 0x1) {
					mplew.writeShort(statupdate.getRight().shortValue());
				} else if (statupdate.getLeft().getValue() <= 0x4) {
					mplew.writeInt(statupdate.getRight());
				} else if (statupdate.getLeft().getValue() == 0x8) {
					mplew.writeLong(statupdate.getRight());
					mplew.write(no_pets);
				} else if (statupdate.getLeft().getValue() < 0x20) {
					mplew.write(statupdate.getRight().shortValue());
				} else if (statupdate.getLeft().getValue() < 0xFFFF) {
					mplew.writeShort(statupdate.getRight().shortValue());
				} else {
					mplew.writeInt(statupdate.getRight().intValue());
				}
			}
		}

		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to change maps.
	 * 
	 * @param to The <code>MapleMap</code> to warp to.
	 * @param spawnPoint The spawn portal number to spawn at.
	 * @param chr The character warping to <code>to</code>
	 * @return The map change packet.
	 */
	public static MaplePacket getWarpToMap(MapleMap to, int spawnPoint, MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49

		mplew.writeInt(chr.getClient().getChannel() - 1);
		mplew.writeShort(0x2);
		mplew.writeInt(to.getId());
		mplew.write(spawnPoint);
		mplew.writeShort(chr.getHp()); // hp (???)

		mplew.write(0);
		long questMask = 0x1ffffffffffffffL;
		mplew.writeLong(questMask);

		return mplew.getPacket();
	}

	/**
	 * Gets a packet to spawn a portal.
	 * 
	 * @param townId The ID of the town the portal goes to.
	 * @param targetId The ID of the target.
	 * @param pos Where to put the portal.
	 * @return The portal spawn packet.
	 */
	public static MaplePacket spawnPortal(int townId, int targetId, Point pos) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
		mplew.writeInt(townId);
		mplew.writeInt(targetId);
		if (pos != null) {
			mplew.writeShort(pos.x);
			mplew.writeShort(pos.y);
		}
		// System.out.println("ssp: " +
		// HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	/**
	 * Gets a packet to spawn a door.
	 * 
	 * @param oid The door's object ID.
	 * @param pos The position of the door.
	 * @param town
	 * @return The remove door packet.
	 */
	public static MaplePacket spawnDoor(int oid, Point pos, boolean town) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_DOOR.getValue());
		// B9 00 00 47 1E 00 00
		mplew.write(town ? 1 : 0);
		mplew.writeInt(oid);
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		// System.out.println("doorspawn: " +
		// HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	/**
	 * Gets a packet to remove a door.
	 * 
	 * @param oid The door's ID.
	 * @param town
	 * @return The remove door packet.
	 */
	public static MaplePacket removeDoor(int oid, boolean town) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		if (town) {
			mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
			mplew.writeInt(999999999);
			mplew.writeInt(999999999);
		} else {
			mplew.writeShort(SendPacketOpcode.REMOVE_DOOR.getValue());
			mplew.write(/*town ? 1 : */0);
			mplew.writeInt(oid);
		}
		// System.out.println("doorremove: " +
		// HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	/**
	 * Gets a packet to spawn a special map object.
	 * 
	 * @param chr The MapleCharacter who spawned the object.
	 * @param skill The skill used.
	 * @param skillLevel The level of the skill used.
	 * @param pos Where the object was spawned.
	 * @param movementType Movement type of the object.
	 * @param animated Animated spawn?
	 * @return The spawn packet for the map object.
	 */
	public static MaplePacket spawnSpecialMapObject(MapleCharacter chr, int skill, int skillLevel, Point pos,
													SummonMovementType movementType, boolean animated) {
		// 72 00 29 1D 02 00 FD FE 30 00 19 7D FF BA 00 04 01 00 03 01 00

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SPAWN_SPECIAL_MAPOBJECT.getValue());

		mplew.writeInt(chr.getId());
		mplew.writeInt(skill);
		mplew.write(skillLevel);
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		// mplew.writeInt(oid);
		mplew.write(0); // ?

		mplew.write(0); // ?

		mplew.write(0);

		mplew.write(movementType.getValue()); // 0 = don't move, 1 = follow
												// (4th mage summons?), 2/4 =
												// only tele follow, 3 = bird
												// follow

		mplew.write(1); // 0 and the summon can't attack - but puppets don't
						// attack with 1 either ^.-

		mplew.write(animated ? 0 : 1);

		// System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		// 72 00 B3 94 00 00 FD FE 30 00 19 FC 00 B4 00 00 00 00 03 01 00 -
		// fukos bird
		// 72 00 30 75 00 00 FD FE 30 00 00 FC 00 B4 00 00 00 00 03 01 00 -
		// faeks bird
		return mplew.getPacket();
	}

	/**
	 * Gets a packet to remove a special map object.
	 * 
	 * @param chr The MapleCharacter who removed the object.
	 * @param skill The skill used to create the object.
	 * @param animated Animated removal?
	 * @return The packet removing the object.
	 */
	public static MaplePacket removeSpecialMapObject(MapleCharacter chr, int skill, boolean animated) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_SPECIAL_MAPOBJECT.getValue());

		mplew.writeInt(chr.getId());
		mplew.writeInt(skill);

		mplew.write(animated ? 4 : 1); // ?

		// System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	/**
	 * Adds info about an item to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to write to.
	 * @param item The item to write info about.
	 */
	protected static void addItemInfo(MaplePacketLittleEndianWriter mplew, IItem item) {
		addItemInfo(mplew, item, false, false);
	}

	/**
	 * Adds expiration time info to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to write to.
	 * @param time The expiration time.
	 * @param showexpirationtime Show the expiration time?
	 */
	private static void addExpirationTime(MaplePacketLittleEndianWriter mplew, long time, boolean showexpirationtime) {
		mplew.writeInt(KoreanDateUtil.getItemTimestamp(time));
		mplew.write(showexpirationtime ? 1 : 2);
	}

	/**
	 * Adds item info to existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to write to.
	 * @param item The item to add info about.
	 * @param zeroPosition Is the position zero?
	 * @param leaveOut Leave out the item if position is zero?
	 */
	private static void addItemInfo(MaplePacketLittleEndianWriter mplew, IItem item, boolean zeroPosition,
									boolean leaveOut) {
	    
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		byte pos = item.getPosition();
		boolean masking = false;
		if (zeroPosition) {
			if (!leaveOut) {
				mplew.write(0);
			}
		} else if (pos <= (byte) -1) {
			pos *= -1;
			if (pos > 100) {
				masking = true;
				mplew.write(0);
				mplew.write(pos - 100);
			} else {
				mplew.write(pos);
			}
		} else {
			mplew.write(item.getPosition());
		}

		if (item.getItemId() >= 5000000 && item.getItemId() <= 5000045) {
			mplew.write(3);
		} else {
			mplew.write(item.getType());
		}
		
		mplew.writeInt(item.getItemId());
		
		// 01 03 - 01 = slot, 03 = type/section
		// 4b 4b 4c 00 - Pet
		// 01 - ?
		// 01 00 00 00 - PetID
		// 00 00 00 00 - ?
		// 00 80 05 bb 46 e6 17 02 - INV stuff
		// 42 61 63 6f 6e 00 00 00 ea 05 b4 35 00 - pet name padded to 13 bytes (the padding can just be 00's I'm just showing how gMS produces it)
		// 11 - pet level
		// 86 00 - closeness
		// 39 - fitness
		// 00 b8 d5 60 00 ce c8 01 00 00 00 00 - pet is alive(probably some other data included too(time until expire I think. I haven't looked at it in much detail), make it
		// 00 80 05 bb 46 e6 17 02 00 00 00 00 for the pet to be dried up)
                
		// 01 03
		// 57 4B 4C 00
		// 01
		// 4D CB 1C 00
		// 00 00 00 00
		// 00 80 05 BB 46 E6 17 02
		// 43 72 75 73 68 00 6F 2D 6F 00 00 00 00
		// 0F
		// 71 06
		// 32
		// 00 74 A8 73 11 2A C9 01 01 00 00 00 
		
		if (item.getItemId() >= 5000000 && item.getItemId() <= 5000045) {
			MaplePet pet = MaplePet.loadFromDb(item.getItemId(), item.getPosition(), item.getPetId());
			String petname = pet.getName();
			mplew.write(1);
			mplew.writeInt(item.getPetId());
			mplew.writeInt(0);
			mplew.write(HexTool.getByteArrayFromHexString("00 80 05 BB 46 E6 17 02"));
			if (petname.length() > 13) {
				petname = petname.substring(0, 13);
			}
			mplew.writeAsciiString(petname);
			for (int i = petname.length(); i < 13; i++) {
				mplew.write(0);
			}
			mplew.write(pet.getLevel());
			mplew.writeShort(pet.getCloseness());
			mplew.write(pet.getFullness());
			// 01 63 7B B4 3E 00
			// 01 F6 85 57 D0
			mplew.writeLong(getKoreanTimestamp((long) (System.currentTimeMillis() * 1.5)));
			mplew.writeInt(0);
			//mplew.write(HexTool.getByteArrayFromHexString("00 74 A8 73 11 2A C9 01 01 00 00 00"));
			//log.info("To be sent: {}", mplew.toString());00 74 A8 73 11 2A CC 01 00 00 00 00
			return;
			//mplew.write(HexTool.getByteArrayFromHexString("01 01 00 00 00 00 00 00 00 00 80 05 bb 46 e6 17 02 42 61 63 6f 6e 00 00 00 ea 05 b4 35 00 11 86 00 39 00 b8 d5 60 00 ce c8 01 00 00 00 00"));
                }
		
		if (masking) {
			// 07.03.2008 06:49... o.o
			mplew.write(HexTool.getByteArrayFromHexString("01 41 B4 38 00 00 00 00 00 80 20 6F"));
		} else {
			mplew.writeShort(0);
			mplew.write(ITEM_MAGIC);
		}
		// TODO: Item.getExpirationTime
		addExpirationTime(mplew, 0, false);

		if (item.getType() == IItem.EQUIP) {
			IEquip equip = (IEquip) item;
			mplew.write(equip.getUpgradeSlots());
			mplew.write(equip.getLevel());
			mplew.writeShort(equip.getStr()); // str

			mplew.writeShort(equip.getDex()); // dex

			mplew.writeShort(equip.getInt()); // int

			mplew.writeShort(equip.getLuk()); // luk

			mplew.writeShort(equip.getHp()); // hp

			mplew.writeShort(equip.getMp()); // mp

			mplew.writeShort(equip.getWatk()); // watk

			mplew.writeShort(equip.getMatk()); // matk

			mplew.writeShort(equip.getWdef()); // wdef

			mplew.writeShort(equip.getMdef()); // mdef

			mplew.writeShort(equip.getAcc()); // accuracy

			mplew.writeShort(equip.getAvoid()); // avoid

			mplew.writeShort(equip.getHands()); // hands

			mplew.writeShort(equip.getSpeed()); // speed

			mplew.writeShort(equip.getJump()); // jump

			mplew.writeMapleAsciiString(equip.getOwner());
			// 0 normal; 1 locked
			mplew.write(0);
			if (!masking) {
				mplew.write(0);
				mplew.writeInt(0); // values of these don't seem to matter at
				// all

				mplew.writeInt(0);
			}
		} else {
			mplew.writeShort(item.getQuantity());
			mplew.writeMapleAsciiString(item.getOwner());
			mplew.writeShort(0); // this seems to end the item entry
			// but only if its not a THROWING STAR :))9 O.O!

			if (ii.isThrowingStar(item.getItemId())) {
				// mplew.write(HexTool.getByteArrayFromHexString("A8 3A 00 00 41
				// 00 00 20"));
				mplew.write(HexTool.getByteArrayFromHexString("A1 6D 05 01 00 00 00 7D"));
			}
		}
	}

	/**
	 * Gets the response to a relog request.
	 * 
	 * @return The relog response packet.
	 */
	public static MaplePacket getRelogResponse() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);
		mplew.writeShort(SendPacketOpcode.RELOG_RESPONSE.getValue());
		mplew.write(1);
		return mplew.getPacket();
	}

	/**
	 * Gets a server message packet.
	 * 
	 * @param message The message to convey.
	 * @return The server message packet.
	 */
	public static MaplePacket serverMessage(String message) {
		return serverMessage(4, 0, message, true);
	}

	/**
	 * Gets a server notice packet.
	 * 
	 * Possible values for <code>type</code>:<br>
	 * 0: [Notice]<br>
	 * 1: Popup<br>
	 * 2: Light blue background and lolwhut<br>
	 * 4: Scrolling message at top<br>
	 * 5: Pink Text<br>
	 * 6: Lightblue Text
	 * 
	 * @param type The type of the notice.
	 * @param message The message to convey.
	 * @return The server notice packet.
	 */
	public static MaplePacket serverNotice(int type, String message) {
		return serverMessage(type, 0, message, false);
	}

	/**
	 * Gets a server notice packet.
	 * 
	 * Possible values for <code>type</code>:<br>
	 * 0: [Notice]<br>
	 * 1: Popup<br>
	 * 2: Light blue background and lolwhut<br>
	 * 4: Scrolling message at top<br>
	 * 5: Pink Text<br>
	 * 6: Lightblue Text
	 * 
	 * @param type The type of the notice.
	 * @param channel The channel this notice was sent on.
	 * @param message The message to convey.
	 * @return The server notice packet.
	 */
	public static MaplePacket serverNotice(int type, int channel, String message) {
		return serverMessage(type, channel, message, false);
	}

	/**
	 * Gets a server message packet.
	 * 
	 * Possible values for <code>type</code>:<br>
	 * 0: [Notice]<br>
	 * 1: Popup<br>
	 * 2: Light blue background and lolwhut<br>
	 * 4: Scrolling message at top<br>
	 * 5: Pink Text<br>
	 * 6: Lightblue Text
	 * 
	 * @param type The type of the notice.
	 * @param channel The channel this notice was sent on.
	 * @param message The message to convey.
	 * @param servermessage Is this a scrolling ticker?
	 * @return The server notice packet.
	 */
	private static MaplePacket serverMessage(int type, int channel, String message, boolean servermessage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue()); // 0.47:
		// 0x37,
		// unchanged

		mplew.write(type);
		if (servermessage) {
			mplew.write(1);
		}
		mplew.writeMapleAsciiString(message);

		if (type == 3) {
			mplew.write(channel - 1); // channel

			mplew.write(0); // 0 = graues ohr, 1 = lulz?

		}

		return mplew.getPacket();
	}

	/**
	 * Gets an avatar megaphone packet.
	 * 
	 * @param chr The character using the avatar megaphone.
	 * @param channel The channel the character is on.
	 * @param itemId The ID of the avatar-mega.
	 * @param message The message that is sent.
	 * @return The avatar mega packet.
	 */
	public static MaplePacket getAvatarMega(MapleCharacter chr, int channel, int itemId, List<String> message) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.AVATAR_MEGA.getValue());
		mplew.writeInt(itemId);
		mplew.writeMapleAsciiString(chr.getName());
		for (String s : message) {
			mplew.writeMapleAsciiString(s);
		}
		mplew.writeInt(channel - 1); // channel

		mplew.write(0);
		addCharLook(mplew, chr, true);

		return mplew.getPacket();
	}

	/**
	 * Gets a NPC spawn packet.
	 * 
	 * @param life The NPC to spawn.
	 * @param requestController Does the NPC want a controller?
	 * @return The NPC spawn packet.
	 */
	public static MaplePacket spawnNPC(MapleNPC life, boolean requestController) {
		// B1 00 01 04 00 00 00 34 08 00 00 99 FF 35 00 01 0B 00 67 FF CB FF
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		if (requestController) {
			mplew.writeShort(SendPacketOpcode.SPAWN_NPC_REQUEST_CONTROLLER.getValue());
			mplew.write(1); // ?

		} else {
			mplew.writeShort(SendPacketOpcode.SPAWN_NPC.getValue());
		}
		mplew.writeInt(life.getObjectId());
		mplew.writeInt(life.getId());
		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getCy());
		mplew.write(1); // type ?

		mplew.writeShort(life.getFh());
		mplew.writeShort(life.getRx0());
		mplew.writeShort(life.getRx1());

		return mplew.getPacket();
	}

	/**
	 * Gets a spawn monster packet.
	 * 
	 * @param life The monster to spawn.
	 * @param newSpawn Is it a new spawn?
	 * @return The spawn monster packet.
	 */
	public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn) {
		return spawnMonsterInternal(life, false, newSpawn, false, 0);
	}

	/**
	 * Gets a spawn monster packet.
	 * 
	 * @param life The monster to spawn.
	 * @param newSpawn Is it a new spawn?
	 * @param effect The spawn effect.
	 * @return The spawn monster packet.
	 */
	public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn, int effect) {
		return spawnMonsterInternal(life, false, newSpawn, false, effect);
	}

	/**
	 * Gets a control monster packet.
	 * 
	 * @param life The monster to give control to.
	 * @param newSpawn Is it a new spawn?
	 * @param aggro Aggressive monster?
	 * @return The monster control packet.
	 */
	public static MaplePacket controlMonster(MapleMonster life, boolean newSpawn, boolean aggro) {
		return spawnMonsterInternal(life, true, newSpawn, aggro, 0);
	}

	/**
	 * Internal function to handler monster spawning and controlling.
	 * 
	 * @param life The mob to perform operations with.
	 * @param requestController Requesting control of mob?
	 * @param newSpawn New spawn (fade in?)
	 * @param aggro Aggressive mob?
	 * @param effect The spawn effect to use.
	 * @return The spawn/control packet.
	 */
	private static MaplePacket spawnMonsterInternal(MapleMonster life, boolean requestController, boolean newSpawn,
													boolean aggro, int effect) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// 95 00 DA 33 37 00 01 58 CC 6C 00 00 00 00 00 B7 FF F3 FB 02 1A 00 1A
		// 00 02 0E 06 00 00 FF
		// OP OBJID MOBID NULL PX PY ST 00 00 FH
		// 95 00 7A 00 00 00 01 58 CC 6C 00 00 00 00 00 56 FF 3D FA 05 00 00 00
		// 00 FE FF

		if (requestController) {
			mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
			// mplew.writeShort(0xA0); // 47 9e
			if (life.getName().equalsIgnoreCase("Zakum1")) {
				mplew.write(-1);
			} else {
				if (aggro) {
					mplew.write(2);
				} else {
					mplew.write(1);
				}
			}
		} else {
			mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());
			// mplew.writeShort(0x9E); // 47 9c
		}
		mplew.writeInt(life.getObjectId());
		mplew.write(5); // ????!? either 5 or 1?

		mplew.writeInt(life.getId());
		mplew.writeInt(0); // if nonnull client crashes (?)

		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getPosition().y);
		// System.out.println(life.getPosition().x);
		// System.out.println(life.getPosition().y);
		// mplew.writeShort(life.getCy());
		mplew.write(life.getStance()); // or 5? o.O"

		mplew.writeShort(0); // ??

		mplew.writeShort(life.getFh()); // seems to be left and right
										// restriction...

		if (effect > 0) {
			mplew.write(effect);
			mplew.write(0x00);
			mplew.writeShort(0x00); // ?
		}

		if (newSpawn) {
			mplew.writeShort(-2);
		} else {
			mplew.writeShort(-1);
		}

		// System.out.println(mplew.toString());
		return mplew.getPacket();
	}

	/**
	 * Gets a stop control monster packet.
	 * 
	 * @param oid The ObjectID of the monster to stop controlling.
	 * @return The stop control monster packet.
	 */
	public static MaplePacket stopControllingMonster(int oid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
		mplew.write(0);
		mplew.writeInt(oid);

		return mplew.getPacket();
	}

	/**
	 * Gets a response to a move monster packet.
	 * 
	 * @param objectid The ObjectID of the monster being moved.
	 * @param moveid The movement ID.
	 * @param currentMp The current MP of the monster.
	 * @param useSkills Can the monster use skills?
	 * @return The move response packet.
	 */
	public static MaplePacket moveMonsterResponse(int objectid, short moveid, int currentMp, boolean useSkills) {
		// A1 00 18 DC 41 00 01 00 00 1E 00 00 00
		// A1 00 22 22 22 22 01 00 00 00 00 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_MONSTER_RESPONSE.getValue());
		mplew.writeInt(objectid);
		mplew.writeShort(moveid);
		mplew.write(useSkills ? 1 : 0);
		mplew.writeShort(currentMp);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	/**
	 * Gets a general chat packet.
	 * 
	 * @param cidfrom The character ID who sent the chat.
	 * @param text The text of the chat.
	 * @return The general chat packet.
	 */
	public static MaplePacket getChatText(int cidfrom, String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CHATTEXT.getValue());
		// mplew.writeShort(0x67); // 47 65
		mplew.writeInt(cidfrom);
		mplew.write(0); // gms have this set to != 0, gives them white
		// background text

		mplew.writeMapleAsciiString(text);

		return mplew.getPacket();
	}

	/**
	 * For testing only! Gets a packet from a hexadecimal string.
	 * 
	 * @param hex The hexadecimal packet to create.
	 * @return The MaplePacket representing the hex string.
	 */
	public static MaplePacket getPacketFromHexString(String hex) {
		byte[] b = HexTool.getByteArrayFromHexString(hex);
		return new ByteArrayMaplePacket(b);
	}

	/**
	 * Gets a packet telling the client to show an EXP increase.
	 * 
	 * @param gain The amount of EXP gained.
	 * @param inChat In the chat box?
	 * @param white White text or yellow?
	 * @return The exp gained packet.
	 */
	public static MaplePacket getShowExpGain(int gain, boolean inChat, boolean white) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// 20 00 03 01 0A 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(3); // 3 = exp, 4 = fame, 5 = mesos, 6 = guildpoints

		mplew.write(white ? 1 : 0);
		mplew.writeInt(gain);
		mplew.write(inChat ? 1 : 0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to show a meso gain.
	 * 
	 * @param gain How many mesos gained.
	 * @return The meso gain packet.
	 */
	public static MaplePacket getShowMesoGain(int gain) {
		return getShowMesoGain(gain, false);
	}

	/**
	 * Gets a packet telling the client to show a meso gain.
	 * 
	 * @param gain How many mesos gained.
	 * @param inChat Show in the chat window?
	 * @return The meso gain packet.
	 */
	public static MaplePacket getShowMesoGain(int gain, boolean inChat) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		if (!inChat) {
			mplew.write(0);
			mplew.write(1);
		} else {
			mplew.write(5);
		}
		mplew.writeInt(gain);
		mplew.writeShort(0); // inet cafe meso gain ?.o

		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to show a item gain.
	 * 
	 * @param itemId The ID of the item gained.
	 * @param quantity How many items gained.
	 * @return The item gain packet.
	 */
	public static MaplePacket getShowItemGain(int itemId, short quantity) {
		return getShowItemGain(itemId, quantity, false);
	}

	/**
	 * Gets a packet telling the client to show an item gain.
	 * 
	 * @param itemId The ID of the item gained.
	 * @param quantity The number of items gained.
	 * @param inChat Show in the chat window?
	 * @return The item gain packet.
	 */
	public static MaplePacket getShowItemGain(int itemId, short quantity, boolean inChat) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		if (inChat) {
			// mplew.writeShort(0x92); // 47 90
			mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
			mplew.write(3);
			mplew.write(1);
			mplew.writeInt(itemId);
			mplew.writeInt(quantity);
		} else {
			mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
			// mplew.writeShort(0x21);
			mplew.writeShort(0);
			mplew.writeInt(itemId);
			mplew.writeInt(quantity);
			mplew.writeInt(0);
			mplew.writeInt(0);
		}
		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client that a monster was killed.
	 * 
	 * @param oid The objectID of the killed monster.
	 * @param animation Show killed animation?
	 * @return The kill monster packet.
	 */
	public static MaplePacket killMonster(int oid, boolean animation) {
		// 9D 00 45 2B 67 00 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.KILL_MONSTER.getValue());
		// mplew.writeShort(0x9f); // 47 9d
		mplew.writeInt(oid);
		if (animation) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}

		return mplew.getPacket();
	}

	/**
	 * Gets a packet telling the client to show mesos coming out of a map
	 * object.
	 * 
	 * @param amount The amount of mesos.
	 * @param itemoid The ObjectID of the dropped mesos.
	 * @param dropperoid The OID of the dropper.
	 * @param ownerid The ID of the drop owner.
	 * @param dropfrom Where to drop from.
	 * @param dropto Where the drop lands.
	 * @param mod ?
	 * @return The drop mesos packet.
	 */
	public static MaplePacket dropMesoFromMapObject(int amount, int itemoid, int dropperoid, int ownerid,
													Point dropfrom, Point dropto, byte mod) {
		return dropItemFromMapObjectInternal(amount, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, true);
	}

	/**
	 * Gets a packet telling the client to show an item coming out of a map
	 * object.
	 * 
	 * @param itemid The ID of the dropped item.
	 * @param itemoid The ObjectID of the dropped item.
	 * @param dropperoid The OID of the dropper.
	 * @param ownerid The ID of the drop owner.
	 * @param dropfrom Where to drop from.
	 * @param dropto Where the drop lands.
	 * @param mod ?
	 * @return The drop mesos packet.
	 */
	public static MaplePacket dropItemFromMapObject(int itemid, int itemoid, int dropperoid, int ownerid,
													Point dropfrom, Point dropto, byte mod) {
		return dropItemFromMapObjectInternal(itemid, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, false);
	}

	/**
	 * Internal function to get a packet to tell the client to drop an item onto
	 * the map.
	 * 
	 * @param itemid The ID of the item to drop.
	 * @param itemoid The ObjectID of the dropped item.
	 * @param dropperoid The OID of the dropper.
	 * @param ownerid The ID of the drop owner.
	 * @param dropfrom Where to drop from.
	 * @param dropto Where the drop lands.
	 * @param mod ?
	 * @param mesos Is the drop mesos?
	 * @return The item drop packet.
	 */
	public static MaplePacket dropItemFromMapObjectInternal(int itemid, int itemoid, int dropperoid, int ownerid,
															Point dropfrom, Point dropto, byte mod, boolean mesos) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// dropping mesos
		// BF 00 01 01 00 00 00 01 0A 00 00 00 24 46 32 00 00 84 FF 70 00 00 00
		// 00 00 84 FF 70 00 00 00 00
		// dropping maple stars
		// BF 00 00 02 00 00 00 00 FB 95 1F 00 24 46 32 00 00 84 FF 70 00 00 00
		// 00 00 84 FF 70 00 00 00 00 80 05 BB 46 E6 17 02 00
		// killing monster (0F 2C 67 00)
		// BF 00 01 2C 03 00 00 00 6D 09 3D 00 24 46 32 00 00 A3 02 6C FF 0F 2C
		// 67 00 A3 02 94 FF 89 01 00 80 05 BB 46 E6 17 02 01

		// 4000109
		mplew.writeShort(SendPacketOpcode.DROP_ITEM_FROM_MAPOBJECT.getValue());
		// mplew.writeShort(0xC1); // 47 bf
		// mplew.write(1); // 1 with animation, 2 without o.o
		mplew.write(mod);
		mplew.writeInt(itemoid);
		mplew.write(mesos ? 1 : 0); // 1 = mesos, 0 =item

		mplew.writeInt(itemid);
		mplew.writeInt(ownerid); // owner charid

		mplew.write(0);
		mplew.writeShort(dropto.x);
		mplew.writeShort(dropto.y);
		if (mod != 2) {
			mplew.writeInt(ownerid);
			mplew.writeShort(dropfrom.x);
			mplew.writeShort(dropfrom.y);
		} else {
			mplew.writeInt(dropperoid);
		}
		mplew.write(0);
		if (mod != 2) {
			mplew.writeShort(0);
		}
		if (!mesos) {
			mplew.write(ITEM_MAGIC);
			// TODO getTheExpirationTimeFromSomewhere o.o
			addExpirationTime(mplew, System.currentTimeMillis(), false);
			// mplew.write(1);
			mplew.write(0);
		}

		return mplew.getPacket();
	}

	/* (non-javadoc)
	 * TODO: make MapleCharacter a mapobject, remove the need for passing oid
	 * here.
	 */
	/**
	 * Gets a packet spawning a player as a mapobject to other clients.
	 * 
	 * @param chr The character to spawn to other clients.
	 * @return The spawn player packet.
	 */
	public static MaplePacket spawnPlayerMapobject(MapleCharacter chr) {
		// 62 00 24 46 32 00 05 00 42 65 79 61 6E 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 20 4E 00 00 00 44 75 00 00 01 2A 4A 0F 00 04
		// 60 BF 0F 00 05 A2 05 10 00 07 2B 5C 10 00 09 E7 D0 10 00 0B 39 53 14
		// 00 FF FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
		// DE 01 73 FF 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00
	    
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_PLAYER.getValue());
		// mplew.writeInt(chr.getId());
		mplew.writeInt(chr.getId());
		mplew.writeMapleAsciiString(chr.getName());

		if (chr.getGuildId() <= 0)
		{
			mplew.writeMapleAsciiString("");
			mplew.write(new byte[6]);
		}
		else
		{
			MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(
					chr.getGuildId());
			
			if (gs != null)
			{
				mplew.writeMapleAsciiString(gs.getName());
				mplew.writeShort(gs.getLogoBG());
				mplew.write(gs.getLogoBGColor());
				mplew.writeShort(gs.getLogo());
				mplew.write(gs.getLogoColor());
			}
			
			else
			{
				mplew.writeMapleAsciiString("");
				mplew.write(new byte[6]);
			}
		}

		long buffmask = 0;
		Integer buffvalue = null;

		if (chr.getBuffedValue(MapleBuffStat.DARKSIGHT) != null && !chr.isHidden()) {
			buffmask |= MapleBuffStat.DARKSIGHT.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.COMBO) != null) {
			buffmask |= MapleBuffStat.COMBO.getValue();
			buffvalue = Integer.valueOf(chr.getBuffedValue(MapleBuffStat.COMBO).intValue());
		}
		if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
			buffmask |= MapleBuffStat.MONSTER_RIDING.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null) {
			buffmask |= MapleBuffStat.SHADOWPARTNER.getValue();
		}
		if (chr.getBuffedValue(MapleBuffStat.SOULARROW) != null) {
			buffmask |= MapleBuffStat.SOULARROW.getValue();
		}
		mplew.writeLong(buffmask);

		if (buffvalue != null) {
			mplew.write(buffvalue.byteValue());
		}
		addCharLook(mplew, chr, false);
		mplew.writeInt(0);
		mplew.writeInt(chr.getItemEffect());
		mplew.writeInt(chr.getChair());
		mplew.writeShort(chr.getPosition().x);
		mplew.writeShort(chr.getPosition().y);
		mplew.write(chr.getStance());
		// 04 34 00 00
		// mplew.writeInt(1); // dunno p00 (?)
		/*if (chr.getPet() != null) {
			mplew.writeInt(chr.getPet().getUniqueId());
			mplew.writeInt(chr.getPet().getItemId());
			mplew.writeMapleAsciiString(chr.getPet().getName());
			// 38 EVTL. Y
			// mplew.write(HexTool.getByteArrayFromHexString("72 FB 38 00 00 00
			// 00 00 09 03 04 00 18 34 00 00 00 01 00
			// 00 00"));
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00"));
			mplew.writeShort(chr.getPosition().x);
			mplew.writeShort(chr.getPosition().y);
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00 00"));
		} else {*/
			mplew.writeInt(0);
			mplew.writeInt(1);
		//}

		mplew.writeLong(0);
		if (chr.getPlayerShop() != null && chr.getPlayerShop().isOwner(chr)) {
			addAnnounceBox(mplew, chr.getPlayerShop());
		} else {
			mplew.write(0);
		}
		mplew.writeShort(0);
		mplew.writeShort(0);
        
		mplew.write(0);
		//System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	/**
	 * Adds a announcement box to an existing MaplePacketLittleEndianWriter.
	 * 
	 * @param mplew The MaplePacketLittleEndianWriter to add an announcement box
	 *            to.
	 * @param shop The shop to announce.
	 */
	private static void addAnnounceBox(MaplePacketLittleEndianWriter mplew, MaplePlayerShop shop) {
		// 00: no game
		// 01: omok game
		// 02: card game
		// 04: shop
		mplew.write(4);
		mplew.writeInt(shop.getObjectId()); // gameid/shopid

		mplew.writeMapleAsciiString(shop.getDescription()); // desc
		// 00: public
		// 01: private

		mplew.write(0);
		// 00: red 4x3
		// 01: green 5x4
		// 02: blue 6x5
		// omok:
		// 00: normal
		mplew.write(0);
		// first slot: 1/2/3/4
		// second slot: 1/2/3/4
		mplew.write(1);
		mplew.write(4);
		// 0: open
		// 1: in progress
		mplew.write(0);
	}

	public static MaplePacket facialExpression(MapleCharacter from, int expression) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FACIAL_EXPRESSION.getValue());
		// mplew.writeShort(0x85); // 47 83
		mplew.writeInt(from.getId());
		mplew.writeInt(expression);
		return mplew.getPacket();
	}

	private static void serializeMovementList(LittleEndianWriter lew, List<LifeMovementFragment> moves) {
		lew.write(moves.size());
		for (LifeMovementFragment move : moves) {
			move.serialize(lew);
		}
	}

	public static MaplePacket movePlayer(int cid, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		/*
		 * 7C 00 #10 27 00 00# 24 00# 3F FD 03 00# 00 00 Y#00 00 AF 00 00 00 C2 00 01 B4 00 01 AF 00 56 FD 06 00 00 00
		 * X00 00 Y00 00 AF 00 E9 FF 00 00 06 4A 01
		 */

		mplew.writeShort(SendPacketOpcode.MOVE_PLAYER.getValue());
		mplew.writeInt(cid);
		// mplew.write(HexTool.getByteArrayFromHexString("24 00 3F FD")); //?
		mplew.writeInt(0);

		serializeMovementList(mplew, moves);
		// dance ;)
		/*
		 * mplew .write(HexTool .getByteArrayFromHexString("0B 00 EC 00 4F 01 12 00 00 00 42 00 03 1E 00 00 EA 00 4F 01
		 * BE FF 00 00 42 00 03 3C 00 00 E9 00 4F 01 E8 FF 00 00 42 00 02 1E 00 00 EA 00 4F 01 3C 00 00 00 42 00 02 3C
		 * 00 00 EB 00 4F 01 12 00 00 00 42 00 03 1E 00 00 EB 00 4F 01 E8 FF 00 00 42 00 03 1E 00 00 EF 00 4F 01 66 00
		 * 00 00 42 00 02 5A 00 00 F2 00 4F 01 12 00 00 00 42 00 03 3C 00 00 F2 00 4F 01 00 00 00 00 42 00 0B 1E 00 00
		 * F5 00 4F 01 54 00 00 00 42 00 02 3C 00 00 F8 00 4F 01 3C 00 00 00 42 00 04 1E 00 11 88 58 55 88 55 85 18 55
		 * 00 E9 00 4F 01 F8 00 4F 01"));
		 */
		// mplew.write(1); //num commands (?) (alternative: command type)
		// mplew.write(0); //action to perform (0 = walk, 1 = jump, 4 = tele, 6
		// = fj)
		// mplew.writeShort(0); //x target
		// mplew.writeShort(0); //y target
		// mplew.writeShort(100); //x wobbliness
		// mplew.writeShort(100); //y wobbliness
		// mplew.writeShort(0);
		// mplew.write(1); //state after command
		// mplew.writeShort(1000); //time for this command in ms
		return mplew.getPacket();
	}

	public static MaplePacket moveSummon(int cid, int summonSkill, Point startPos, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_SUMMON.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(summonSkill);
		mplew.writeShort(startPos.x);
		mplew.writeShort(startPos.y);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}

	public static MaplePacket moveMonster(int useskill, int skill, int skill_1, int skill_2, int skill_3, int oid, Point startPos,
											List<LifeMovementFragment> moves) {
		/*
		 * A0 00 C8 00 00 00 00 FF 00 00 00 00 48 02 7D FE 02 00 1C 02 7D FE 9C FF 00 00 2A 00 03 BD 01 00 DC 01 7D FE
		 * 9C FF 00 00 2B 00 03 7B 02
		 */
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_MONSTER.getValue());
		// mplew.writeShort(0xA2); // 47 a0
		mplew.writeInt(oid);
		mplew.write(useskill);
		mplew.write(skill);
		mplew.write(skill_1);
		mplew.write(skill_2);
		mplew.write(skill_3);
		mplew.write(0);
		mplew.writeShort(startPos.x);
		mplew.writeShort(startPos.y);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}

	public static MaplePacket summonAttack(int cid, int summonSkillId, int newStance, List<SummonAttackEntry> allDamage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SUMMON_ATTACK.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(summonSkillId);
		mplew.write(newStance);
		mplew.write(allDamage.size());
		for (SummonAttackEntry attackEntry : allDamage) {
			mplew.writeInt(attackEntry.getMonsterOid()); // oid

			mplew.write(6); // who knows

			mplew.writeInt(attackEntry.getDamage()); // damage

		}

		return mplew.getPacket();
	}

	public static MaplePacket closeRangeAttack(int cid, int skill, int stance, int numAttackedAndDamage,
												List<Pair<Integer, List<Integer>>> damage) {
		// 7D 00 #30 75 00 00# 12 00 06 02 0A 00 00 00 00 01 00 00 00 00 97 02
		// 00 00 97 02 00 00
		// 7D 00 #30 75 00 00# 11 00 06 02 0A 00 00 00 00 20 00 00 00 49 06 00
		// 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CLOSE_RANGE_ATTACK.getValue());
		// mplew.writeShort(0x7F); // 47 7D
		if (skill == 4211006) // meso explosion
		{
			addMesoExplosion(mplew, cid, skill, stance, numAttackedAndDamage, 0, damage);
		} else {
			addAttackBody(mplew, cid, skill, stance, numAttackedAndDamage, 0, damage);
		}
		return mplew.getPacket();
	}

	public static MaplePacket rangedAttack(int cid, int skill, int stance, int numAttackedAndDamage, int projectile,
											List<Pair<Integer, List<Integer>>> damage) {
		// 7E 00 30 75 00 00 01 00 97 04 0A CB 72 1F 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.RANGED_ATTACK.getValue());
		// mplew.writeShort(0x80); // 47 7E
		addAttackBody(mplew, cid, skill, stance, numAttackedAndDamage, projectile, damage);

		return mplew.getPacket();
	}

	public static MaplePacket magicAttack(int cid, int skill, int stance, int numAttackedAndDamage,
											List<Pair<Integer, List<Integer>>> damage) {
		return magicAttack(cid, skill, stance, numAttackedAndDamage, damage, -1);
	}

	public static MaplePacket magicAttack(int cid, int skill, int stance, int numAttackedAndDamage,
											List<Pair<Integer, List<Integer>>> damage, int charge) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MAGIC_ATTACK.getValue());
		// mplew.writeShort(0x81);
		addAttackBody(mplew, cid, skill, stance, numAttackedAndDamage, 0, damage);
		if (charge != -1) {
			mplew.writeInt(charge);
		}

		return mplew.getPacket();
	}

	private static void addAttackBody(LittleEndianWriter lew, int cid, int skill, int stance, int numAttackedAndDamage,
										int projectile, List<Pair<Integer, List<Integer>>> damage) {
		lew.writeInt(cid);
		lew.write(numAttackedAndDamage);
		if (skill > 0) {
			lew.write(0xFF); // too low and some skills don't work (?)

			lew.writeInt(skill);
		} else {
			lew.write(0);
		}
		lew.write(stance);
		lew.write(HexTool.getByteArrayFromHexString("02 0A"));
		lew.writeInt(projectile);

		for (Pair<Integer, List<Integer>> oned : damage) {
			if (oned.getRight() != null) {
				lew.writeInt(oned.getLeft().intValue());
				lew.write(0xFF);
				for (Integer eachd : oned.getRight()) {
					// highest bit set = crit
					lew.writeInt(eachd.intValue());
				}
			}
		}
	}

	private static void addMesoExplosion(LittleEndianWriter lew, int cid, int skill, int stance,
											int numAttackedAndDamage, int projectile,
											List<Pair<Integer, List<Integer>>> damage) {
		// 7A 00 6B F4 0C 00 22 1E 3E 41 40 00 38 04 0A 00 00 00 00 44 B0 04 00
		// 06 02 E6 00 00 00 D0 00 00 00 F2 46 0E 00 06 02 D3 00 00 00 3B 01 00
		// 00
		// 7A 00 6B F4 0C 00 00 1E 3E 41 40 00 38 04 0A 00 00 00 00
		lew.writeInt(cid);
		lew.write(numAttackedAndDamage);
		lew.write(0x1E);
		lew.writeInt(skill);
		lew.write(stance);
		lew.write(HexTool.getByteArrayFromHexString("04 0A"));
		lew.writeInt(projectile);

		for (Pair<Integer, List<Integer>> oned : damage) {
			if (oned.getRight() != null) {
				lew.writeInt(oned.getLeft().intValue());
				lew.write(0xFF);
				lew.write(oned.getRight().size());
				for (Integer eachd : oned.getRight()) {
					lew.writeInt(eachd.intValue());
				}
			}
		}

	}

	public static MaplePacket getNPCShop(int sid, List<MapleShopItem> items) {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_NPC_SHOP.getValue());
		mplew.writeInt(sid);
		mplew.writeShort(items.size()); // item count

		for (MapleShopItem item : items) {
			mplew.writeInt(item.getItemId());
			mplew.writeInt(item.getPrice());
			if (!ii.isThrowingStar(item.getItemId())) {
				mplew.writeShort(1); // stacksize o.o

				mplew.writeShort(item.getBuyable());
			} else {
				mplew.writeShort(0);
				mplew.writeInt(0);
				// o.O getPrice sometimes returns the unitPrice not the price
				mplew.writeShort(BitTools.doubleToShortBits(ii.getPrice(item.getItemId())));
				mplew.writeShort(ii.getSlotMax(item.getItemId()));
			}
		}

		return mplew.getPacket();
	}

	/**
	 * code (8 = sell, 0 = buy, 0x20 = due to an error the trade did not happen
	 * o.o)
	 * 
	 * @param code
	 * @return
	 */
	public static MaplePacket confirmShopTransaction(byte code) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CONFIRM_SHOP_TRANSACTION.getValue());
		// mplew.writeShort(0xE6); // 47 E4
		mplew.write(code); // recharge == 8?

		return mplew.getPacket();
	}

	/*
	 * 19 reference 00 01 00 = new while adding 01 01 00 = add from drop 00 01 01 = update count 00 01 03 = clear slot
	 * 01 01 02 = move to empty slot 01 02 03 = move and merge 01 02 01 = move and merge with rest
	 */
	public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item) {
		return addInventorySlot(type, item, false);
	}

	public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		// mplew.writeShort(0x19);
		if (fromDrop) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(HexTool.getByteArrayFromHexString("01 00")); // add mode

		mplew.write(type.getType()); // iv type

		mplew.write(item.getPosition()); // slot id

		addItemInfo(mplew, item, true, false);
		return mplew.getPacket();
	}

	public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item) {
		return updateInventorySlot(type, item, false);
	}

	public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		if (fromDrop) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(HexTool.getByteArrayFromHexString("01 01")); // update
		// mode

		mplew.write(type.getType()); // iv type

		mplew.write(item.getPosition()); // slot id

		mplew.write(0); // ?

		mplew.writeShort(item.getQuantity());
		return mplew.getPacket();
	}

	public static MaplePacket moveInventoryItem(MapleInventoryType type, byte src, byte dst) {
		return moveInventoryItem(type, src, dst, (byte) -1);
	}

	public static MaplePacket moveInventoryItem(MapleInventoryType type, byte src, byte dst, byte equipIndicator) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 01 02"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.writeShort(dst);
		if (equipIndicator != -1) {
			mplew.write(equipIndicator);
		}
		return mplew.getPacket();
	}

	public static MaplePacket moveAndMergeInventoryItem(MapleInventoryType type, byte src, byte dst, short total) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 02 03"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.write(1); // merge mode?

		mplew.write(type.getType());
		mplew.writeShort(dst);
		mplew.writeShort(total);
		return mplew.getPacket();
	}

	public static MaplePacket moveAndMergeWithRestInventoryItem(MapleInventoryType type, byte src, byte dst,
																short srcQ, short dstQ) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 02 01"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		mplew.writeShort(srcQ);
		mplew.write(HexTool.getByteArrayFromHexString("01"));
		mplew.write(type.getType());
		mplew.writeShort(dst);
		mplew.writeShort(dstQ);
		return mplew.getPacket();
	}

	public static MaplePacket clearInventoryItem(MapleInventoryType type, byte slot, boolean fromDrop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(fromDrop ? 1 : 0);
		mplew.write(HexTool.getByteArrayFromHexString("01 03"));
		mplew.write(type.getType());
		mplew.writeShort(slot);
		return mplew.getPacket();
	}

	public static MaplePacket scrolledItem(IItem scroll, IItem item, boolean destroyed) {
		// 18 00 01 02 03 02 08 00 03 01 F7 FF 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(1); // fromdrop always true

		if (destroyed) {
			mplew.write(2);
		} else {
			mplew.write(3);
		}
		if (scroll.getQuantity() > 0) {
			mplew.write(1);
		} else {
			mplew.write(3);
		}
		mplew.write(MapleInventoryType.USE.getType());
		mplew.writeShort(scroll.getPosition());
		if (scroll.getQuantity() > 0) {
			mplew.writeShort(scroll.getQuantity());
		}
		mplew.write(3);
		if (!destroyed) {
			mplew.write(MapleInventoryType.EQUIP.getType());
			mplew.writeShort(item.getPosition());
			mplew.write(0);
		}
		mplew.write(MapleInventoryType.EQUIP.getType());
		mplew.writeShort(item.getPosition());
		if (!destroyed) {
			addItemInfo(mplew, item, true, true);
		}
		mplew.write(1);
		return mplew.getPacket();
	}

	public static MaplePacket getScrollEffect(int chr, ScrollResult scrollSuccess, boolean legendarySpirit) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_SCROLL_EFFECT.getValue());
		mplew.writeInt(chr);
		switch (scrollSuccess) {
			case SUCCESS:
				mplew.writeShort(1);
				if (legendarySpirit) {
					mplew.writeShort(1);
				} else {
					mplew.writeShort(0);
				}
				break;
			case FAIL:
				mplew.writeShort(0);
				if (legendarySpirit) {
					mplew.writeShort(1);
				} else {
					mplew.writeShort(0);
				}
				break;
			case CURSE:
				mplew.write(0);
				mplew.write(1);
				if (legendarySpirit) {
					mplew.writeShort(1);
				} else {
					mplew.writeShort(0);
				}
				break;
			default:
				throw new IllegalArgumentException("effect in illegal range");
		}

		return mplew.getPacket();
	}

	public static MaplePacket removePlayerFromMap(int cid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_PLAYER_FROM_MAP.getValue());
		// mplew.writeShort(0x65); // 47 63
		mplew.writeInt(cid);
		return mplew.getPacket();
	}

	/**
	 * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
	 * 4 - explode<br/> cid is ignored for 0 and 1
	 * 
	 * @param oid
	 * @param animation
	 * @param cid
	 * @return
	 */
	public static MaplePacket removeItemFromMap(int oid, int animation, int cid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.REMOVE_ITEM_FROM_MAP.getValue());
		mplew.write(animation); // expire

		mplew.writeInt(oid);
		if (animation >= 2) {
			mplew.writeInt(cid);
		}
		return mplew.getPacket();
	}

	public static MaplePacket updateCharLook(MapleCharacter chr) {
		// 88 00 80 74 03 00 01 00 00 19 50 00 00 00 67 75 00 00 02 34 71 0F 00
		// 04 59 BF 0F 00 05 AB 05 10 00 07 8C 5B
		// 10 00 08 F4 82 10 00 09 E7 D0 10 00 0A BE A9 10 00 0B 0C 05 14 00 FF
		// FF 00 00 00 00 00 00 00 00 00 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_LOOK.getValue());
		mplew.writeInt(chr.getId());
		mplew.write(1);
		addCharLook(mplew, chr, false);
		mplew.writeShort(0);
        
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket dropInventoryItem(MapleInventoryType type, short src) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		// mplew.writeShort(0x19);
		mplew.write(HexTool.getByteArrayFromHexString("01 01 03"));
		mplew.write(type.getType());
		mplew.writeShort(src);
		if (src < 0) {
			mplew.write(1);
		}
		return mplew.getPacket();
	}

	public static MaplePacket dropInventoryItemUpdate(MapleInventoryType type, IItem item) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("01 01 01"));
		mplew.write(type.getType());
		mplew.writeShort(item.getPosition());
		mplew.writeShort(item.getQuantity());
		return mplew.getPacket();
	}

	public static MaplePacket damagePlayer(int skill, int monsteridfrom, int cid, int damage, int fake, int direction, boolean pgmr, int pgmr_1, boolean is_pg, int oid, int pos_x, int pos_y) {
		// 82 00 30 C0 23 00 FF 00 00 00 00 B4 34 03 00 01 00 00 00 00 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.DAMAGE_PLAYER.getValue());
		// mplew.writeShort(0x84); // 47 82
		mplew.writeInt(cid);
		mplew.write(skill);
		mplew.writeInt(damage);
		mplew.writeInt(monsteridfrom);
		mplew.write(direction);
		if (pgmr) {
			mplew.write(pgmr_1);
			if (is_pg) {
				mplew.write(1);
			} else { 
				mplew.write(0);
			}
			mplew.writeInt(oid);
			mplew.write(6);
			mplew.writeShort(pos_x);
			mplew.writeShort(pos_y);
			mplew.write(0);
		} else {
			mplew.writeShort(0);
		}

		mplew.writeInt(damage);

		if (fake > 0) {
			mplew.writeInt(fake);
		}
		
		return mplew.getPacket();
	}

	public static MaplePacket charNameResponse(String charname, boolean nameUsed) {
		// 0D 00 0C 00 42 6C 61 62 6C 75 62 62 31 32 33 34 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHAR_NAME_RESPONSE.getValue());
		// mplew.writeShort(0xd);
		mplew.writeMapleAsciiString(charname);
		mplew.write(nameUsed ? 1 : 0);

		return mplew.getPacket();
	}

	public static MaplePacket addNewCharEntry(MapleCharacter chr, boolean worked) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.ADD_NEW_CHAR_ENTRY.getValue());

		mplew.write(worked ? 0 : 1);

		addCharEntry(mplew, chr);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket startQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// mplew.writeShort(0x21);
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.writeShort(1);
		mplew.write(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * state 0 = del ok state 12 = invalid bday
	 * 
	 * @param cid
	 * @param state
	 * @return
	 */
	public static MaplePacket deleteCharResponse(int cid, int state) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.DELETE_CHAR_RESPONSE.getValue());
		mplew.writeInt(cid);
		mplew.write(state);
		return mplew.getPacket();
	}

	public static MaplePacket charInfo(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CHAR_INFO.getValue());
		// mplew.writeShort(0x31);
		mplew.writeInt(chr.getId());
		mplew.write(chr.getLevel());
		mplew.writeShort(chr.getJob().getId());
		mplew.writeShort(chr.getFame());
		if (chr.getName().equalsIgnoreCase("Danny")) {
			mplew.write(1);
		} else {
			mplew.write(0); // heart red or gray
		}
		
		if (chr.getGuildId() <= 0)
			mplew.writeMapleAsciiString(""); // guild
		else
		{
			MapleGuildSummary gs = null;
			
			gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
			if (gs != null) {
				mplew.writeMapleAsciiString(gs.getName());
			} else {
				mplew.writeMapleAsciiString(""); // guild
			}
		}

		if (chr.getNoPets() != -1) { // got pet
			for (MaplePet pet : chr.getPets()) {
				mplew.write(pet.getUniqueId());
				mplew.writeInt(pet.getItemId()); // petid

				mplew.writeMapleAsciiString(pet.getName());
				mplew.write(pet.getLevel()); // pet level

				mplew.writeShort(pet.getCloseness()); // pet closeness

				mplew.write(pet.getFullness()); // pet fullness

				mplew.writeInt(0); // ??
				mplew.writeShort(0);
			}
		}
		mplew.write(new byte[3]);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket forfeitQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.writeShort(0);
		mplew.write(0);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @return
	 */
	public static MaplePacket completeQuest(MapleCharacter c, short quest) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(quest);
		mplew.write(HexTool.getByteArrayFromHexString("02 A0 67 B9 DA 69 3A C8 01"));
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param quest
	 * @param npc
	 * @param progress
	 * @return
	 */
	// frz note, 0.52 transition: this is only used when starting a quest and
	// seems to have no effect, is it needed?
	public static MaplePacket updateQuestInfo(MapleCharacter c, short quest, int npc, byte progress) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_QUEST_INFO.getValue());
		mplew.write(progress);
		mplew.writeShort(quest);
		mplew.writeInt(npc);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	private static <E extends LongValueHolder> long getLongMask(List<Pair<E, Integer>> statups) {
		long mask = 0;
		for (Pair<E, Integer> statup : statups) {
			mask |= statup.getLeft().getValue();
		}
		return mask;
	}

	private static <E extends LongValueHolder> long getLongMaskFromList(List<E> statups) {
		long mask = 0;
		for (E statup : statups) {
			mask |= statup.getValue();
		}
		return mask;
	}

	/**
	 * It is important that statups is in the correct order (see decleration
	 * order in MapleBuffStat) since this method doesn't do automagical
	 * reordering.
	 * 
	 * @param buffid
	 * @param bufflength
	 * @param statups
	 * @return
	 */
	public static MaplePacket giveBuff(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
		return giveBuff(buffid, bufflength, statups, false, 0, 0);
	}
	
	/**
	 * It is important that statups is in the correct order (see decleration
	 * order in MapleBuffStat) since this method doesn't do automagical
	 * reordering.
	 * 
	 * @param buffid
	 * @param bufflength
	 * @param statups
	 * @return
	 */
	public static MaplePacket giveBuff(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups, boolean isStatus, int monsSkill, int monsSkillLevel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		// darksight
		// 1C 00 80 04 00 00 00 00 00 00 F4 FF EB 0C 3D 00 C8 00 01 00 EB 0C 3D
		// 00 C8 00 00 00 01
		// fire charge
		// 1C 00 04 00 40 00 00 00 00 00 26 00 7B 7A 12 00 90 01 01 00 7B 7A 12
		// 00 90 01 58 02
		// ice charge
		// 1C 00 04 00 40 00 00 00 00 00 07 00 7D 7A 12 00 26 00 01 00 7D 7A 12
		// 00 26 00 58 02
		// thunder charge
		// 1C 00 04 00 40 00 00 00 00 00 0B 00 7F 7A 12 00 18 00 01 00 7F 7A 12
		// 00 18 00 58 02

		// incincible 0.49
		// 1B 00 00 80 00 00 00 00 00 00 0F 00 4B 1C 23 00 F8 24 01 00 00 00
		// mguard 0.49
		// 1B 00 00 02 00 00 00 00 00 00 50 00 6A 88 1E 00 C0 27 09 00 00 00
		// bless 0.49

		// 1B 00 3A 00 00 00 00 00 00 00 14 00 4C 1C 23 00 3F 0D 03 00 14 00 4C
		// 1C 23 00 3F 0D 03 00 14 00 4C 1C 23 00 3F 0D 03 00 14 00 4C 1C 23 00
		// 3F 0D 03 00 00 00

		// combo
		// 1B 00 00 00 20 00 00 00 00 00 01 00 DA F3 10 00 C0 D4 01 00 58 02
		// 1B 00 00 00 20 00 00 00 00 00 02 00 DA F3 10 00 57 B7 01 00 00 00
		// 1B 00 00 00 20 00 00 00 00 00 03 00 DA F3 10 00 51 A7 01 00 00 00
		
		// 01 00
		// 79 00 - monster skill
		// 01 00
		// B4 78 00 00
		// 00 00
		// 84 03
		
		long mask = getLongMask(statups);

		mplew.writeLong(mask);
		for (Pair<MapleBuffStat, Integer> statup : statups) {
			mplew.writeShort(statup.getRight().shortValue());
			if (isStatus) {
				mplew.writeShort(monsSkill);
				mplew.writeShort(monsSkillLevel);
				mplew.writeInt(30900);
			} else {
				mplew.writeInt(buffid);
				mplew.writeInt(bufflength);
			}
		}

		mplew.writeShort(0); // ??? wk charges have 600 here o.o
		if (isStatus) {
			mplew.writeShort(900);
		} else {
			mplew.write(0); // combo 600, too
		}

		return mplew.getPacket();
	}

	public static MaplePacket giveForeignBuff(int cid, List<Pair<MapleBuffStat, Integer>> statups) {
		// 8A 00 24 46 32 00 80 04 00 00 00 00 00 00 F4 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());

		mplew.writeInt(cid);
		long mask = getLongMask(statups);
		mplew.writeLong(mask);
		// TODO write the values somehow? only one byte per value?
		// seems to work
		for (Pair<MapleBuffStat, Integer> statup : statups) {
			mplew.writeShort(statup.getRight().byteValue());
		}
		mplew.writeShort(0); // same as give_buff

		return mplew.getPacket();
	}

	public static MaplePacket cancelForeignBuff(int cid, List<MapleBuffStat> statups) {
		// 8A 00 24 46 32 00 80 04 00 00 00 00 00 00 F4 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());

		mplew.writeInt(cid);
		mplew.writeLong(getLongMaskFromList(statups));

		return mplew.getPacket();
	}

	public static MaplePacket cancelBuff(List<MapleBuffStat> statups) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
		mplew.writeLong(getLongMaskFromList(statups));
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopChat(MapleCharacter c, String chat, boolean owner) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("06 08"));
		mplew.write(owner ? 0 : 1);
		mplew.writeMapleAsciiString(c.getName() + " : " + chat);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopNewVisitor(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("04 02"));
		addCharLook(mplew, c, false);
		mplew.writeMapleAsciiString(c.getName());
		return mplew.getPacket();
	}

	public static MaplePacket getTradePartnerAdd(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("04 01"));// 00 04 88 4E
																// 00"));

		addCharLook(mplew, c, false);
		mplew.writeMapleAsciiString(c.getName());
		return mplew.getPacket();
	}

	public static MaplePacket getTradeInvite(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("02 03"));
		mplew.writeMapleAsciiString(c.getName());
		mplew.write(HexTool.getByteArrayFromHexString("B7 50 00 00"));
		return mplew.getPacket();
	}

	public static MaplePacket getTradeMesoSet(byte number, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xE);
		mplew.write(number);
		mplew.writeInt(meso);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeItemAdd(byte number, IItem item) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xD);
		mplew.write(number);
		// mplew.write(1);
		addItemInfo(mplew, item);
		return mplew.getPacket();
	}

	public static MaplePacket getPlayerShopItemUpdate(MaplePlayerShop shop) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0x16);
		mplew.write(shop.getItems().size());
		for (MaplePlayerShopItem item : shop.getItems()) {
			mplew.writeShort(item.getBundles());
			mplew.writeShort(item.getItem().getQuantity());
			mplew.writeInt(item.getPrice());
			addItemInfo(mplew, item.getItem(), true, true);
		}
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param c
	 * @param shop
	 * @param owner
	 * @return
	 */
	public static MaplePacket getPlayerShop(MapleClient c, MaplePlayerShop shop, boolean owner) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("05 04 04"));
		mplew.write(owner ? 0 : 1);
		mplew.write(0);
		addCharLook(mplew, shop.getOwner(), false);
		mplew.writeMapleAsciiString(shop.getOwner().getName());

		MapleCharacter[] visitors = shop.getVisitors();
		for (int i = 0; i < visitors.length; i++) {
			if (visitors[i] != null) {
				mplew.write(i + 1);
				addCharLook(mplew, visitors[i], false);
				mplew.writeMapleAsciiString(visitors[i].getName());
			}
		}
		mplew.write(0xFF);
		mplew.writeMapleAsciiString(shop.getDescription());
		List<MaplePlayerShopItem> items = shop.getItems();
		mplew.write(0x10);
		mplew.write(items.size());
		for (MaplePlayerShopItem item : items) {
			mplew.writeShort(item.getBundles());
			mplew.writeShort(item.getItem().getQuantity());
			mplew.writeInt(item.getPrice());
			addItemInfo(mplew, item.getItem(), true, true);
		}
		// mplew.write(HexTool.getByteArrayFromHexString("01 60 BF 0F 00 00 00
		// 80 05 BB 46 E6 17 02 05 00 00 00 00 00 00
		// 00 00 00 1D 00 16 00 00 00 00 00 00 00 02 00 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 1B 7F 00 00 0D 00 00
		// 40 01 00 01 00 FF 34 0C 00 01 E6 D0 10 00 00 00 80 05 BB 46 E6 17 02
		// 04 01 00 00 00 00 00 00 00 00 0A 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 0F 00 00 00 00 00 00 00 00 00 00 00
		// 63 CF 07 01 00 00 00 7C 01 00 01 00 5F
		// AE 0A 00 01 79 16 15 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00
		// 00 00 00 00 66 00 00 00 21 00 2F 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 A4 82 7A 01 00 00
		// 00 7C 01 00 01 00 5F AE 0A 00 01 79 16
		// 15 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00 00 00 00 00 66 00
		// 00 00 23 00 2C 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 FE AD 88 01 00 00 00 7C 01 00 01 00
		// DF 67 35 00 01 E5 D0 10 00 00 00 80 05
		// BB 46 E6 17 02 01 03 00 00 00 00 07 00 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 0A 00 00 00 00 00 00
		// 00 00 00 00 00 CE D4 F1 00 00 00 00 7C 01 00 01 00 7F 1A 06 00 01 4C
		// BF 0F 00 00 00 80 05 BB 46 E6 17 02 05
		// 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1D 00 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 00 38
		// CE AF 00 00 00 00 7C 01 00 01 00 BF 27 09 00 01 07 76 16 00 00 00 80
		// 05 BB 46 E6 17 02 00 07 00 00 00 00 00
		// 00 00 00 00 00 00 00 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 7C 02 00 00 1E 00 00
		// 48 01 00 01 00 5F E3 16 00 01 11 05 14 00 00 00 80 05 BB 46 E6 17 02
		// 07 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 21 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
		// 1C 8A 00 00 39 00 00 10 01 00 01 00 7F
		// 84 1E 00 01 05 DE 13 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 00 0C 00 00 00 00 00 00 00 00 E5 07 01 00 00
		// 00 7C 2B 00 01 00 AF B3 00 00 02 FC 0C
		// 3D 00 00 00 80 05 BB 46 E6 17 02 2B 00 00 00 00 00 00 00 01 00 0F 27
		// 00 00 02 D1 ED 2D 00 00 00 80 05 BB 46
		// E6 17 02 01 00 00 00 00 00 0A 00 01 00 9F 0F 00 00 02 84 84 1E 00 00
		// 00 80 05 BB 46 E6 17 02 0A 00 00 00 00
		// 00 01 00 01 00 FF 08 3D 00 01 02 05 14 00 00 00 80 05 BB 46 E6 17 02
		// 07 00 00 00 00 00 00 00 00 00 00 00 00
		// 00 25 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
		// 78 36 00 00 1D 00 00 14 01 00 01 00 9F
		// 25 26 00 01 2B 2C 14 00 00 00 80 05 BB 46 E6 17 02 07 00 00 00 00 00
		// 00 00 00 00 00 00 00 00 34 00 00 00 06
		// 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 E8 76 00 00 1F 00
		// 00 24 01 00 01 00 BF 0E 16 02 01 D9 D0
		// 10 00 00 00 80 05 BB 46 E6 17 02 00 04 00 00 00 00 00 00 07 00 00 00
		// 00 00 02 00 00 00 06 00 08 00 00 00 00
		// 00 00 00 00 00 00 00 00 00 00 00 23 02 00 00 1C 00 00 1C 5A 00 01 00
		// 0F 27 00 00 02 B8 14 3D 00 00 00 80 05
		// BB 46 E6 17 02 5A 00 00 00 00 00"));
		/*
		 * 10 10 01 00 01 00 3F 42 0F 00 01 60 BF 0F 00 00 00 80 05 BB /* ||||||||||| OMG ITS THE PRICE ||||| PROBABLY
		 * THA QUANTITY ||||||||||| itemid
		 * 
		 */
		// mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeStart(MapleClient c, MapleTrade trade, byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(HexTool.getByteArrayFromHexString("05 03 02"));
		mplew.write(number);
		if (number == 1) {
			mplew.write(0);
			addCharLook(mplew, trade.getPartner().getChr(), false);
			mplew.writeMapleAsciiString(trade.getPartner().getChr().getName());
		}
		mplew.write(number);
		/*if (number == 1) {
		mplew.write(0);
		mplew.writeInt(c.getPlayer().getId());
		}*/
		addCharLook(mplew, c.getPlayer(), false);
		mplew.writeMapleAsciiString(c.getPlayer().getName());
		mplew.write(0xFF);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeConfirmation() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xF);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeCompletion(byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xA);
		mplew.write(number);
		mplew.write(6);
		return mplew.getPacket();
	}

	public static MaplePacket getTradeCancel(byte number) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
		mplew.write(0xA);
		mplew.write(number);
		mplew.write(2);
		return mplew.getPacket();
	}

	public static MaplePacket updateCharBox(MapleCharacter c) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
		mplew.writeInt(c.getId());
		if (c.getPlayerShop() != null) {
			addAnnounceBox(mplew, c.getPlayerShop());
		} else {
			mplew.write(0);
		}
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalk(int npc, byte msgType, String talk, String endBytes) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(msgType);
		mplew.writeMapleAsciiString(talk);
		mplew.write(HexTool.getByteArrayFromHexString(endBytes));
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalkStyle(int npc, String talk, int styles[]) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(7);
		mplew.writeMapleAsciiString(talk);
		mplew.write(styles.length);
		for (int i = 0; i < styles.length; i++) {
			mplew.writeInt(styles[i]);
		}
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalkNum(int npc, String talk, int def, int min, int max) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(4);
		mplew.writeMapleAsciiString(talk);
		mplew.writeInt(def);
		mplew.writeInt(min);
		mplew.writeInt(max);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getNPCTalkText(int npc, String talk) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
		mplew.write(4); // ?

		mplew.writeInt(npc);
		mplew.write(3);
		mplew.writeMapleAsciiString(talk);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket showLevelup(int cid) {
		return showForeignEffect(cid, 0);
	}

	public static MaplePacket showJobChange(int cid) {
		return showForeignEffect(cid, 8);
	}

	public static MaplePacket showForeignEffect(int cid, int effect) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
		mplew.writeInt(cid); // ?

		mplew.write(effect);
		return mplew.getPacket();
	}

	public static MaplePacket showBuffeffect(int cid, int skillid, int effectid) {
		return showBuffeffect(cid, skillid, effectid, (byte) 3);
	}

	public static MaplePacket showBuffeffect(int cid, int skillid, int effectid, byte direction) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
		mplew.writeInt(cid); // ?

		mplew.write(effectid);
		mplew.writeInt(skillid);
		mplew.write(1); // probably buff level but we don't know it and it
		// doesn't really matter
		if (direction != (byte) 3) {
			mplew.write(direction);
		}

		return mplew.getPacket();
	}

	public static MaplePacket showOwnBuffEffect(int skillid, int effectid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
		mplew.write(effectid);
		mplew.writeInt(skillid);
		mplew.write(1); // probably buff level but we don't know it and it
		// doesn't really matter

		return mplew.getPacket();
	}

	public static MaplePacket updateSkill(int skillid, int level, int masterlevel) {
		// 1E 00 01 01 00 E9 03 00 00 01 00 00 00 00 00 00 00 01
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.UPDATE_SKILLS.getValue());
		mplew.write(1);
		mplew.writeShort(1);
		mplew.writeInt(skillid);
		mplew.writeInt(level);
		mplew.writeInt(masterlevel);
		mplew.write(1);
		return mplew.getPacket();
	}

	public static MaplePacket updateQuestMobKills(MapleQuestStatus status) {
		// 21 00 01 FB 03 01 03 00 30 30 31
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(1);
		mplew.writeShort(status.getQuest().getId());
		mplew.write(1);
		String killStr = "";
		for (int kills : status.getMobKills().values()) {
			killStr += StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3);
		}
		mplew.writeMapleAsciiString(killStr);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getShowQuestCompletion(int id) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_QUEST_COMPLETION.getValue());
		mplew.writeShort(id);
		return mplew.getPacket();
	}

	public static MaplePacket getKeymap(Map<Integer, MapleKeyBinding> keybindings) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.KEYMAP.getValue());
		mplew.write(0);

		for (int x = 0; x < 90; x++) {
			MapleKeyBinding binding = keybindings.get(Integer.valueOf(x));
			if (binding != null) {
				mplew.write(binding.getType());
				mplew.writeInt(binding.getAction());
			} else {
				mplew.write(0);
				mplew.writeInt(0);
			}
		}

		return mplew.getPacket();
	}

	public static MaplePacket getWhisper(String sender, int channel, String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(0x12);
		mplew.writeMapleAsciiString(sender);
		mplew.writeShort(channel - 1); // I guess this is the channel

		mplew.writeMapleAsciiString(text);
		return mplew.getPacket();
	}

	/**
	 * 
	 * @param target name of the target character
	 * @param reply error code: 0x0 = cannot find char, 0x1 = success
	 * @return the MaplePacket
	 */
	public static MaplePacket getWhisperReply(String target, byte reply) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(0x0A); // whisper?

		mplew.writeMapleAsciiString(target);
		mplew.write(reply);
		// System.out.println(HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	public static MaplePacket getFindReplyWithMap(String target, int mapid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(1);
		mplew.writeInt(mapid);
		// ?? official doesn't send zeros here but whatever
		mplew.write(new byte[8]);
		return mplew.getPacket();
	}

	public static MaplePacket getFindReply(String target, int channel) {
		// Received UNKNOWN (1205941596.79689): (25)
		// 54 00 09 07 00 64 61 76 74 73 61 69 01 86 7F 3D 36 D5 02 00 00 22 00
		// 00 00
		// T....davtsai..=6...."...
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(3);
		mplew.writeInt(channel - 1);
		return mplew.getPacket();
	}

	public static MaplePacket getInventoryFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
		mplew.write(1);
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getShowInventoryFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
		mplew.write(0);
		mplew.write(0xFF);
		mplew.writeInt(0);
		mplew.writeInt(0);
		return mplew.getPacket();
	}

	public static MaplePacket getStorage(int npcId, byte slots, Collection<IItem> items, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x13);
		mplew.writeInt(npcId);
		mplew.write(slots);
		mplew.writeShort(0x7E);
		mplew.writeInt(meso);
		mplew.write(HexTool.getByteArrayFromHexString("00 00 00"));
		mplew.write((byte) items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true);
		}
		mplew.write(0);
		return mplew.getPacket();
	}

	public static MaplePacket getStorageFull() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0xE);
		return mplew.getPacket();
	}

	public static MaplePacket mesoStorage(byte slots, int meso) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x10);
		mplew.write(slots);
		mplew.writeShort(2);
		mplew.writeInt(meso);
		return mplew.getPacket();
	}

	public static MaplePacket storeStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0xB);
		mplew.write(slots);
		mplew.writeShort(type.getBitfieldEncoding());
		mplew.write(items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true);
			// mplew.write(0);
		}

		return mplew.getPacket();
	}

	/*public static MaplePacket takeOutStorage(byte slots, byte slot) {
	MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
	mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
	mplew.write(8);
	mplew.write(slots);
	mplew.write(4 * slot);
	mplew.writeShort(0);
	return mplew.getPacket();
	}*/
	public static MaplePacket takeOutStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
		mplew.write(0x8);
		mplew.write(slots);
		mplew.writeShort(type.getBitfieldEncoding());
		mplew.write(items.size());
		for (IItem item : items) {
			addItemInfo(mplew, item, true, true);
			// mplew.write(0);
		}

		return mplew.getPacket();
	}

	/**
	 * 
	 * @param oid
	 * @param remhp in %
	 * @return
	 */
	public static MaplePacket showMonsterHP(int oid, int remhppercentage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SHOW_MONSTER_HP.getValue());
		mplew.writeInt(oid);
		mplew.write(remhppercentage);

		return mplew.getPacket();
	}

	public static MaplePacket showBossHP(int oid, int currHP, int maxHP, byte tagColor, byte tagBgColor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
                
		//53 00 05 21 B3 81 00 46 F2 5E 01 C0 F3 5E 01 04 01
		//00 81 B3 21 = 8500001 = Pap monster ID
		//01 5E F3 C0 = 23,000,000 = Pap max HP
		//04, 01 - boss bar color/background color as provided in WZ

		mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
		mplew.write(5);
		mplew.writeInt(oid);
		mplew.writeInt(currHP);
		mplew.writeInt(maxHP);
		mplew.write(tagColor);
		mplew.write(tagBgColor);

		return mplew.getPacket();
	}

	public static MaplePacket giveFameResponse(int mode, String charname, int newfame) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());

		mplew.write(0);
		mplew.writeMapleAsciiString(charname);
		mplew.write(mode);
		mplew.writeShort(newfame);
		mplew.writeShort(0);

		return mplew.getPacket();
	}

	/**
	 * status can be: <br>
	 * 0: ok, use giveFameResponse<br>
	 * 1: the username is incorrectly entered<br>
	 * 2: users under level 15 are unable to toggle with fame.<br>
	 * 3: can't raise or drop fame anymore today.<br>
	 * 4: can't raise or drop fame for this character for this month anymore.<br>
	 * 5: received fame, use receiveFame()<br>
	 * 6: level of fame neither has been raised nor dropped due to an unexpected
	 * error
	 * 
	 * @param status
	 * @param mode
	 * @param charname
	 * @param newfame
	 * @return
	 */
	public static MaplePacket giveFameErrorResponse(int status) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());

		mplew.write(status);

		return mplew.getPacket();
	}

	public static MaplePacket receiveFame(int mode, String charnameFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
		mplew.write(5);
		mplew.writeMapleAsciiString(charnameFrom);
		mplew.write(mode);

		return mplew.getPacket();
	}

	public static MaplePacket partyCreated() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(8);
		mplew.writeShort(0x8b);
		mplew.writeShort(2);
		mplew.write(CHAR_INFO_MAGIC);
		mplew.write(CHAR_INFO_MAGIC);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	public static MaplePacket partyInvite(MapleCharacter from) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(4);
		mplew.writeInt(from.getParty().getId());
		mplew.writeMapleAsciiString(from.getName());

		return mplew.getPacket();
	}

	/**
	 * 10: a beginner can't create a party<br>
	 * 11/14/19: your request for a party didn't work due to an unexpected error<br>
	 * 13: you have yet to join a party<br>
	 * 16: already have joined a party<br>
	 * 17: the party you are trying to join is already at full capacity<br>
	 * 18: unable to find the requested character in this channel<br>
	 * 
	 * @param message
	 * @return
	 */
	public static MaplePacket partyStatusMessage(int message) {
		// 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(message);

		return mplew.getPacket();
	}

	/**
	 * 22: has denied the invitation<br>
	 * 
	 * @param message
	 * @param charname
	 * @return
	 */
	public static MaplePacket partyStatusMessage(int message, String charname) {
		// 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.write(message);
		mplew.writeMapleAsciiString(charname);

		return mplew.getPacket();
	}

	private static void addPartyStatus(int forchannel, MapleParty party, LittleEndianWriter lew, boolean leaving) {
		List<MaplePartyCharacter> partymembers = new ArrayList<MaplePartyCharacter>(party.getMembers());
		while (partymembers.size() < 6) {
			partymembers.add(new MaplePartyCharacter());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getId());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeAsciiString(StringUtil.getRightPaddedStr(partychar.getName(), '\0', 13));
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getJobId());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			lew.writeInt(partychar.getLevel());
		}
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.isOnline()) {
				lew.writeInt(partychar.getChannel() - 1);
			} else {
				lew.writeInt(-2);
			}
		}
		lew.writeInt(party.getLeader().getId());
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.getChannel() == forchannel) {
				lew.writeInt(partychar.getMapid());
			} else {
				lew.writeInt(-2);
			}
		}
		for (MaplePartyCharacter partychar : partymembers) {
			if (partychar.getChannel() == forchannel && !leaving) {
				lew.writeInt(partychar.getDoorTown());
				lew.writeInt(partychar.getDoorTarget());
				lew.writeInt(partychar.getDoorPosition().x);
				lew.writeInt(partychar.getDoorPosition().y);
			} else {
				lew.writeInt(999999999);
				lew.writeInt(999999999);
				lew.writeInt(-1);
				lew.writeInt(-1);
			}
		}
	}

	public static MaplePacket updateParty(int forChannel, MapleParty party, PartyOperation op,
											MaplePartyCharacter target) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		switch (op) {
			case DISBAND:
			case EXPEL:
			case LEAVE:
				mplew.write(0xC);
				mplew.writeShort(0x8b);
				mplew.writeShort(2);
				mplew.writeInt(target.getId());

				if (op == PartyOperation.DISBAND) {
					mplew.write(0);
					mplew.writeInt(party.getId());
				} else {
					mplew.write(1);
					if (op == PartyOperation.EXPEL) {
						mplew.write(1);
					} else {
						mplew.write(0);
					}
					mplew.writeMapleAsciiString(target.getName());
					addPartyStatus(forChannel, party, mplew, false);
					// addLeavePartyTail(mplew);
				}

				break;
			case JOIN:
				mplew.write(0xF);
				mplew.writeShort(0x8b);
				mplew.writeShort(2);
				mplew.writeMapleAsciiString(target.getName());
				addPartyStatus(forChannel, party, mplew, false);
				// addJoinPartyTail(mplew);
				break;
			case SILENT_UPDATE:
			case LOG_ONOFF:
				if (op == PartyOperation.LOG_ONOFF) {
					mplew.write(0x1F); // actually this is silent too

				} else {
					mplew.write(0x7);
				}
				mplew.write(0xdd);
				mplew.write(0x14);
				mplew.writeShort(0);
				addPartyStatus(forChannel, party, mplew, false);
				// addJoinPartyTail(mplew);
				// addDoorPartyTail(mplew);
				break;

		}
		// System.out.println("partyupdate: " +
		// HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	public static MaplePacket partyPortal(int townId, int targetId, Point position) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
		mplew.writeShort(0x22);
		mplew.writeInt(townId);
		mplew.writeInt(targetId);
		mplew.writeShort(position.x);
		mplew.writeShort(position.y);
		// System.out.println("partyportal: " +
		// HexTool.toString(mplew.getPacket().getBytes()));
		return mplew.getPacket();
	}

	// 87 00 30 75 00 00# 00 02 00 00 00 03 00 00
	public static MaplePacket updatePartyMemberHP(int cid, int curhp, int maxhp) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.UPDATE_PARTYMEMBER_HP.getValue());
		mplew.writeInt(cid);
		mplew.writeInt(curhp);
		mplew.writeInt(maxhp);
		return mplew.getPacket();
	}

	/**
	 * mode: 0 buddychat; 1 partychat; 2 guildchat
	 * 
	 * @param name
	 * @param chattext
	 * @param mode
	 * @return
	 */
	public static MaplePacket multiChat(String name, String chattext, int mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MULTICHAT.getValue());
		mplew.write(mode);
		mplew.writeMapleAsciiString(name);
		mplew.writeMapleAsciiString(chattext);
		return mplew.getPacket();
	}

	public static MaplePacket applyMonsterStatus(int oid, Map<MonsterStatus, Integer> stats, int skill,
													boolean monsterSkill, int delay) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		// 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01
		// 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 01 00 79 00 01 00 B4 78 00 00 00 00 84 03
		mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
		mplew.writeInt(oid);

		int mask = 0;
		for (MonsterStatus stat : stats.keySet()) {
			mask |= stat.getValue();
		}

		mplew.writeInt(mask);

		for (Integer val : stats.values()) {
			mplew.writeShort(val);
			if (monsterSkill) {
				mplew.writeShort(skill);
				mplew.writeShort(1);
			} else {
				mplew.writeInt(skill);
			}
			mplew.writeShort(0); // as this looks similar to giveBuff this
			// might actually be the buffTime but it's
			// not displayed anywhere

		}

		mplew.writeShort(delay); // delay in ms

		mplew.write(1); // ?

		return mplew.getPacket();
	}

	public static MaplePacket cancelMonsterStatus(int oid, Map<MonsterStatus, Integer> stats) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_MONSTER_STATUS.getValue());

		mplew.writeInt(oid);
		int mask = 0;
		for (MonsterStatus stat : stats.keySet()) {
			mask |= stat.getValue();
		}

		mplew.writeInt(mask);
		mplew.write(1);

		return mplew.getPacket();
	}

	public static MaplePacket getClock(int time) { // time in seconds

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
		mplew.write(2); // clock type. if you send 3 here you have to send
		// another byte (which does not matter at all) before
		// the timestamp

		mplew.writeInt(time);
		return mplew.getPacket();
	}

	public static MaplePacket getClockTime(int hour, int min, int sec) { // Current Time

		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
		mplew.write(1); //Clock-Type
		mplew.write(hour);
		mplew.write(min);
		mplew.write(sec);
		return mplew.getPacket();
	}

	public static MaplePacket spawnMist(int oid, int ownerCid, int skillId, Rectangle mistPosition) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SPAWN_MIST.getValue());
		mplew.writeInt(oid); // maybe this should actually be the "mistid" -
		// seems to always be 1 with only one mist in
		// the map...

		mplew.write(0);
		mplew.writeInt(ownerCid); // probably only intresting for smokescreen

		mplew.writeInt(skillId);
		mplew.write(1); // who knows

		mplew.writeShort(7); // ???

		mplew.writeInt(mistPosition.x); // left position

		mplew.writeInt(mistPosition.y); // bottom position

		mplew.writeInt(mistPosition.x + mistPosition.width); // left position

		mplew.writeInt(mistPosition.y + mistPosition.height); // upper
																// position

		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket removeMist(int oid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.REMOVE_MIST.getValue());
		mplew.writeInt(oid);

		return mplew.getPacket();
	}

	public static MaplePacket damageSummon(int cid, int summonSkillId, int damage, int unkByte, int monsterIdFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.DAMAGE_SUMMON.getValue());
		// 77 00 29 1D 02 00 FA FE 30 00 00 10 00 00 00 BF 70 8F 00 00
		mplew.writeInt(cid);
		mplew.writeInt(summonSkillId);
		mplew.write(unkByte);
		mplew.writeInt(damage);
		mplew.writeInt(monsterIdFrom);
		mplew.write(0);

		return mplew.getPacket();
	}

	public static MaplePacket damageMonster(int oid, int damage) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
		mplew.writeInt(oid);
		mplew.write(0);
		mplew.writeInt(damage);

		return mplew.getPacket();
	}

	public static MaplePacket updateBuddylist(Collection<BuddylistEntry> buddylist) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(7);
		mplew.write(buddylist.size());
		for (BuddylistEntry buddy : buddylist) {
			if (buddy.isVisible()) {
				mplew.writeInt(buddy.getCharacterId()); // cid

				mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getName(), '\0', 13));
				mplew.write(0);
				mplew.writeInt(buddy.getChannel() - 1);
			}
		}
		for (int x = 0; x < buddylist.size(); x++) {
			mplew.writeInt(0);
		}
		return mplew.getPacket();
	}

	public static MaplePacket requestBuddylistAdd(int cidFrom, String nameFrom) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(9);
		mplew.writeInt(cidFrom);
		mplew.writeMapleAsciiString(nameFrom);
		mplew.writeInt(cidFrom);
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(nameFrom, '\0', 13));
		mplew.write(1);
		mplew.write(31);
		mplew.writeInt(0);

		return mplew.getPacket();
	}

	public static MaplePacket updateBuddyChannel(int characterid, int channel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		// 2B 00 14 30 C0 23 00 00 11 00 00 00
		mplew.write(0x14);
		mplew.writeInt(characterid);
		mplew.write(0);
		mplew.writeInt(channel);

		// 2B 00 14 30 C0 23 00 00 0D 00 00 00
		// 2B 00 14 30 75 00 00 00 11 00 00 00
		return mplew.getPacket();
	}

	public static MaplePacket itemEffect(int characterid, int itemid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_ITEM_EFFECT.getValue());

		mplew.writeInt(characterid);
		mplew.writeInt(itemid);

		return mplew.getPacket();
	}

	public static MaplePacket updateBuddyCapacity(int capacity) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
		mplew.write(0x15);
		mplew.write(capacity);

		return mplew.getPacket();
	}

	public static MaplePacket showChair(int characterid, int itemid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.SHOW_CHAIR.getValue());

		mplew.writeInt(characterid);
		mplew.writeInt(itemid);

		return mplew.getPacket();
	}

	public static MaplePacket cancelChair() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CANCEL_CHAIR.getValue());

		mplew.write(0);

		return mplew.getPacket();
	}
	
	// is there a way to spawn reactors non-animated?
	public static MaplePacket spawnReactor(MapleReactor reactor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		Point pos = reactor.getPosition();

		mplew.writeShort(SendPacketOpcode.REACTOR_SPAWN.getValue());
		mplew.writeInt(reactor.getObjectId());
		mplew.writeInt(reactor.getId());
		mplew.write(reactor.getState());
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		mplew.write(0);

		return mplew.getPacket();
	}
	
	public static MaplePacket triggerReactor(MapleReactor reactor, int stance) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		Point pos = reactor.getPosition();

		mplew.writeShort(SendPacketOpcode.REACTOR_HIT.getValue());
		mplew.writeInt(reactor.getObjectId());
		mplew.write(reactor.getState());
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);
		mplew.writeShort(stance);
		mplew.write(0);

		//frame delay, set to 5 since there doesn't appear to be a fixed formula for it
		mplew.write(5);

		return mplew.getPacket();
	}
	
	public static MaplePacket destroyReactor(MapleReactor reactor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		Point pos = reactor.getPosition();

		mplew.writeShort(SendPacketOpcode.REACTOR_DESTROY.getValue());
		mplew.writeInt(reactor.getObjectId());
		mplew.write(reactor.getState());
		mplew.writeShort(pos.x);
		mplew.writeShort(pos.y);

		return mplew.getPacket();
	}

	public static MaplePacket musicChange(String song) {
		return environmentChange(song, 6);
	}

	public static MaplePacket showEffect(String effect) {
		return environmentChange(effect, 3);
	}

	public static MaplePacket playSound(String sound) {
		return environmentChange(sound, 4);
	}

	public static MaplePacket environmentChange(String env, int mode) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
		mplew.write(mode);
		mplew.writeMapleAsciiString(env);

		return mplew.getPacket();
	}

	public static MaplePacket startMapEffect(String msg, int itemid, boolean active) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
		mplew.write(active ? 0 : 1);

		mplew.writeInt(itemid);
		if (active)
			mplew.writeMapleAsciiString(msg);
		return mplew.getPacket();
	}

	public static MaplePacket removeMapEffect() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
		mplew.write(0);
		mplew.writeInt(0);

		return mplew.getPacket();
	}
	
	public static MaplePacket showGuildInfo(MapleCharacter c) {
		//whatever functions calling this better make sure
		//that the character actually HAS a guild
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x1A); //signature for showing guild info

		if (c == null) //show empty guild (used for leaving, expelled)
		{
			mplew.write(0);
			return mplew.getPacket();
		}

		MapleGuildCharacter initiator = c.getMGC();

		MapleGuild g = c.getClient().getChannelServer().getGuild(initiator);

		if (g == null) //failed to read from DB - don't show a guild
		{
			mplew.write(0);
			log.warn(MapleClient.getLogMessage(c, "Couldn't load a guild"));
			return mplew.getPacket();
		} else {
			//MapleGuild holds the absolute correct value of guild rank
			//after it is initiated
			MapleGuildCharacter mgc = g.getMGC(c.getId());
			c.setGuildRank(mgc.getGuildRank());
		}

		mplew.write(1); //bInGuild
		mplew.writeInt(c.getGuildId()); //not entirely sure about this one

		mplew.writeMapleAsciiString(g.getName());

		for (int i = 1; i <= 5; i++)
			mplew.writeMapleAsciiString(g.getRankTitle(i));

		Collection<MapleGuildCharacter> members = g.getMembers();

		mplew.write(members.size());
		//then it is the size of all the members

		for (MapleGuildCharacter mgc : members)
			//and each of their character ids o_O
			mplew.writeInt(mgc.getId());

		for (MapleGuildCharacter mgc : members) {
			mplew.writeAsciiString(StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13));
			mplew.writeInt(mgc.getJobId());
			mplew.writeInt(mgc.getLevel());
			mplew.writeInt(mgc.getGuildRank());
			mplew.writeInt(mgc.isOnline() ? 1 : 0);
			mplew.writeInt(g.getSignature());
		}

		mplew.writeInt(g.getCapacity());
		mplew.writeShort(g.getLogoBG());
		mplew.write(g.getLogoBGColor());
		mplew.writeShort(g.getLogo());
		mplew.write(g.getLogoColor());
		mplew.writeMapleAsciiString(g.getNotice());
		mplew.writeInt(g.getGP());

		//System.out.println("DEBUG: showGuildInfo packet:\n" + mplew.toString());

		return mplew.getPacket();
	}

	public static MaplePacket guildMemberOnline(int gid, int cid, boolean bOnline) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3d);
		mplew.writeInt(gid);
		mplew.writeInt(cid);
		mplew.write(bOnline ? 1 : 0);

		return mplew.getPacket();
	}

	public static MaplePacket guildInvite(int gid, String charName) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x05);
		mplew.writeInt(gid);
		mplew.writeMapleAsciiString(charName);

		return mplew.getPacket();
	}

	public static MaplePacket genericGuildMessage(byte code) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(code);

		return mplew.getPacket();
	}

	public static MaplePacket newGuildMember(MapleGuildCharacter mgc) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x27);

		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.writeAsciiString(StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13));
		mplew.writeInt(mgc.getJobId());
		mplew.writeInt(mgc.getLevel());
		mplew.writeInt(mgc.getGuildRank()); //should be always 5 but whatevs
		mplew.writeInt(mgc.isOnline() ? 1 : 0); //should always be 1 too
		mplew.writeInt(1); //? could be guild signature, but doesn't seem to matter

		return mplew.getPacket();
	}

	//someone leaving, mode == 0x2c for leaving, 0x2f for expelled
	public static MaplePacket memberLeft(MapleGuildCharacter mgc, boolean bExpelled) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(bExpelled ? 0x2f : 0x2c);

		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.writeMapleAsciiString(mgc.getName());

		return mplew.getPacket();
	}

	//rank change
	public static MaplePacket changeRank(MapleGuildCharacter mgc) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x40);
		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.write(mgc.getGuildRank());

		return mplew.getPacket();
	}

	public static MaplePacket guildNotice(int gid, String notice) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x44);

		mplew.writeInt(gid);
		mplew.writeMapleAsciiString(notice);

		return mplew.getPacket();
	}

	public static MaplePacket guildMemberLevelJobUpdate(MapleGuildCharacter mgc) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3C);

		mplew.writeInt(mgc.getGuildId());
		mplew.writeInt(mgc.getId());
		mplew.writeInt(mgc.getLevel());
		mplew.writeInt(mgc.getJobId());

		return mplew.getPacket();
	}

	public static MaplePacket rankTitleChange(int gid, String[] ranks) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3e);
		mplew.writeInt(gid);

		for (int i = 0; i < 5; i++)
			mplew.writeMapleAsciiString(ranks[i]);

		return mplew.getPacket();
	}

	public static MaplePacket guildDisband(int gid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x32);
		mplew.writeInt(gid);
		mplew.write(1);

		return mplew.getPacket();
	}

	public static MaplePacket guildEmblemChange(int gid, short bg, byte bgcolor, short logo, byte logocolor) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x42);
		mplew.writeInt(gid);
		mplew.writeShort(bg);
		mplew.write(bgcolor);
		mplew.writeShort(logo);
		mplew.write(logocolor);

		return mplew.getPacket();
	}

	public static MaplePacket guildCapacityChange(int gid, int capacity) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x3a);
		mplew.writeInt(gid);
		mplew.write(capacity);

		return mplew.getPacket();
	}

	public static void addThread(MaplePacketLittleEndianWriter mplew, ResultSet rs) throws SQLException {
		mplew.writeInt(rs.getInt("localthreadid"));
		mplew.writeInt(rs.getInt("postercid"));
		mplew.writeMapleAsciiString(rs.getString("name"));
		mplew.writeLong(MaplePacketCreator.getKoreanTimestamp(rs.getLong("timestamp")));
		mplew.writeInt(rs.getInt("icon"));
		mplew.writeInt(rs.getInt("replycount"));
	}

	public static MaplePacket BBSThreadList(ResultSet rs, int start) throws SQLException {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
		mplew.write(0x06);

		if (!rs.last())
		//no result at all
		{
			mplew.write(0);
			mplew.writeInt(0);
			mplew.writeInt(0);
			return mplew.getPacket();
		}

		int threadCount = rs.getRow();
		if (rs.getInt("localthreadid") == 0) //has a notice
		{
			mplew.write(1);
			addThread(mplew, rs);
			threadCount--; //one thread didn't count (because it's a notice)
		} else
			mplew.write(0);

		if (!rs.absolute(start + 1)) //seek to the thread before where we start
		{
			rs.first(); //uh, we're trying to start at a place past possible
			start = 0;
			// System.out.println("Attempting to start past threadCount");
		}

		mplew.writeInt(threadCount);
		mplew.writeInt(Math.min(10, threadCount - start));

		for (int i = 0; i < Math.min(10, threadCount - start); i++) {
			addThread(mplew, rs);
			rs.next();
		}

		return mplew.getPacket();
	}

	public static MaplePacket showThread(int localthreadid, ResultSet threadRS, ResultSet repliesRS)
																									throws SQLException,
																									RuntimeException {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
		mplew.write(0x07);

		mplew.writeInt(localthreadid);
		mplew.writeInt(threadRS.getInt("postercid"));
		mplew.writeLong(getKoreanTimestamp(threadRS.getLong("timestamp")));
		mplew.writeMapleAsciiString(threadRS.getString("name"));
		mplew.writeMapleAsciiString(threadRS.getString("startpost"));
		mplew.writeInt(threadRS.getInt("icon"));

		if (repliesRS != null) {
			int replyCount = threadRS.getInt("replycount");
			mplew.writeInt(replyCount);

			int i;
			for (i = 0; i < replyCount && repliesRS.next(); i++) {
				mplew.writeInt(repliesRS.getInt("replyid"));
				mplew.writeInt(repliesRS.getInt("postercid"));
				mplew.writeLong(getKoreanTimestamp(repliesRS.getLong("timestamp")));
				mplew.writeMapleAsciiString(repliesRS.getString("content"));
			}

			if (i != replyCount || repliesRS.next()) {
				//in the unlikely event that we lost count of replyid
				throw new RuntimeException(String.valueOf(threadRS.getInt("threadid")));
				//we need to fix the database and stop the packet sending
				//or else it'll probably error 38 whoever tries to read it

				//there is ONE case not checked, and that's when the thread 
				//has a replycount of 0 and there is one or more replies to the
				//thread in bbs_replies 
			}
		} else
			mplew.writeInt(0); //0 replies

		return mplew.getPacket();
	}
	
	public static MaplePacket showGuildRanks(int npcid, ResultSet rs) throws SQLException
	{
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x49);
		mplew.writeInt(npcid);
		if (!rs.last())		//no guilds o.o
		{
			mplew.writeInt(0);
			return mplew.getPacket();
		}
		
		mplew.writeInt(rs.getRow());		//number of entries
		
		rs.beforeFirst();
		while (rs.next())
		{
			mplew.writeMapleAsciiString(rs.getString("name"));
			mplew.writeInt(rs.getInt("GP"));
			mplew.writeInt(rs.getInt("logo"));
			mplew.writeInt(rs.getInt("logoColor"));
			mplew.writeInt(rs.getInt("logoBG"));
			mplew.writeInt(rs.getInt("logoBGColor"));
		}
		
		return mplew.getPacket();
	}
	
	public static MaplePacket updateGP(int gid, int GP)
	{
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
		mplew.write(0x48);
		mplew.writeInt(gid);
		mplew.writeInt(GP);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket skillEffect(MapleCharacter from, int skillId, byte flags) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.SKILL_EFFECT.getValue());
		mplew.writeInt(from.getId());
		mplew.writeInt(skillId);
		mplew.write(0x01); // unknown at this point
		mplew.write(flags);
		mplew.write(0x04); // unknown at this point
		return mplew.getPacket();
	}
	
	public static MaplePacket skillCancel(MapleCharacter from, int skillId) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.CANCEL_SKILL_EFFECT.getValue());
		mplew.writeInt(from.getId());
		mplew.writeInt(skillId);
		return mplew.getPacket();
	}

        public static MaplePacket showMagnet(int mobid, byte success) {  // Monster Magnet
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
            mplew.writeShort(SendPacketOpcode.SHOW_MAGNET.getValue());
            mplew.writeInt(mobid);
            mplew.write(success);
            return mplew.getPacket();
        }
        
	public static MaplePacket messengerInvite(String from, int messengerid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(0x03);
		mplew.writeMapleAsciiString(from);
		mplew.write(0x00);
		mplew.writeInt(messengerid);
		mplew.write(0x00);
		return mplew.getPacket();
	}
        
        public static MaplePacket addMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
                mplew.write(0x00);
                mplew.write(position);
		addCharLook(mplew, chr, true);
		mplew.writeMapleAsciiString(from);
		mplew.write(channel);
                mplew.write(0x00);
		return mplew.getPacket();
	}
        
        public static MaplePacket removeMessengerPlayer(int position) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
		mplew.write(0x02);
                mplew.write(position);
		return mplew.getPacket();
	}
        
        public static MaplePacket updateMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
                mplew.write(0x07);
                mplew.write(position);
		addCharLook(mplew, chr, true);
		mplew.writeMapleAsciiString(from);
		mplew.write(channel);
                mplew.write(0x00);
		return mplew.getPacket();
	}
                
        public static MaplePacket joinMessenger(int position) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
                mplew.write(0x01);
		mplew.write(position);
		return mplew.getPacket();
	}
        
        public static MaplePacket messengerChat(String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
                mplew.write(0x06);
		mplew.writeMapleAsciiString(text);
		return mplew.getPacket();
	}
        
        public static MaplePacket messengerNote(String text, int mode, int mode2) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
                mplew.write(mode);
		mplew.writeMapleAsciiString(text);
                mplew.write(mode2);
		return mplew.getPacket();
	}        
	
	public static MaplePacket warpCS(MapleClient c) {
		
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		MapleCharacter chr = c.getPlayer();
		
		mplew.write(HexTool.getByteArrayFromHexString("50 00 FF FF"));
		
		addCharStats(mplew, chr);
		
		mplew.write(0x14); // ???
		mplew.writeInt(chr.getMeso()); // mesos
		mplew.write(100); // equip slots
		mplew.write(100); // use slots
		mplew.write(100); // set-up slots
		mplew.write(100); // etc slots
		mplew.write(100); // cash slots
		mplew.write(0); // storage slots

		if (chr.getGender() == 0) {
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00 00 00 01 00 CD 1F 05 00 65 6E 74 65 72 03 00 CE 1F 00 E9 5D 46 4C D0 C7 01 EB 03 00 04 FA 8F 4B D0 C7 01 EC 03 00 12 21 97 4B D0 C7 01 00 00 00 00 00 00 00 00 FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B 01"));
			mplew.writeMapleAsciiString(chr.getClient().getAccountName());
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 45 00 E1 9C 98 00 00 06 00 00 00 FF E2 9C 98 00 00 06 00 00 00 FF E3 9C 98 00 00 06 00 00 00 FF E6 9C 98 00 00 06 00 00 00 FF EC 9C 98 00 00 02 00 00 01 ED 9C 98 00 00 02 00 00 01 EE 9C 98 00 00 02 00 00 01 EF 9C 98 00 00 02 00 00 01 F0 9C 98 00 00 02 00 00 01 F1 9C 98 00 00 02 00 00 01 02 9D 98 00 00 06 00 00 00 FF 06 9D 98 00 00 06 00 00 00 FF 07 9D 98 00 00 06 00 00 00 FF C2 2E 31 01 08 04 00 00 07 FF C3 2E 31 01 08 04 00 00 07 FF C4 2E 31 01 08 06 00 00 07 01 FF C5 2E 31 01 08 06 00 00 07 01 FF C6 2E 31 01 08 06 00 00 08 01 02 C7 2E 31 01 00 02 00 00 01 C8 2E 31 01 00 02 00 00 01 A4 B3 32 01 08 00 00 00 08 A8 B3 32 01 08 04 00 00 08 02 B6 B3 32 01 08 00 00 00 08 C3 B3 32 01 08 00 00 00 08 D1 B3 32 01 08 00 00 00 08 DD B3 32 01 08 04 00 00 07 FF E7 B3 32 01 08 06 00 00 07 01 FF E8 B3 32 01 00 02 00 00 01 03 C2 35 01 08 04 00 00 07 FF 0B C2 35 01 08 04 00 00 07 FF 0C C2 35 01 00 02 00 00 01 3B CE 38 01 08 00 00 00 09 CE CE 38 01 08 06 00 00 07 01 FF 6E DB 3B 01 08 00 00 00 09 8E DB 3B 01 08 00 00 00 08 53 62 3D 01 08 00 00 00 09 8A 62 3D 01 08 00 00 00 09 C8 62 3D 01 08 00 00 00 09 D0 62 3D 01 00 06 00 00 00 FF D1 62 3D 01 08 00 00 00 09 B0 E8 3E 01 08 04 00 00 07 FF BC E8 3E 01 08 00 00 00 09 DA E8 3E 01 08 00 00 00 09 DB E8 3E 01 08 00 00 00 06 DC E8 3E 01 08 00 00 00 09 DD E8 3E 01 08 00 00 00 09 DE E8 3E 01 08 04 00 00 07 FF DF E8 3E 01 08 04 00 00 08 02 24 4A CB 01 08 00 00 00 08 36 4A CB 01 08 00 00 00 09 C8 D0 CC 01 08 04 00 00 07 FF C9 D0 CC 01 08 00 00 00 08 CB D0 CC 01 08 00 00 00 08 DE D0 CC 01 08 00 00 00 08 E2 D0 CC 01 08 00 00 00 08 E4 D0 CC 01 00 04 00 00 FF E5 D0 CC 01 00 02 00 00 01 E6 D0 CC 01 00 02 00 00 01 3B 77 FC 02 08 04 00 00 08 02 A0 91 02 03 08 04 00 00 08 02 AC 91 02 03 08 04 00 00 07 FF 1A 87 93 03 08 00 00 00 09 26 87 93 03 08 00 00 00 08 CC 0D 95 03 00 02 00 00 00 CD 0D 95 03 00 02 00 00 00 40 94 96 03 08 00 00 00 08 43 94 96 03 08 04 00 00 08 02 5A 94 96 03 08 00 00 00 08 65 94 96 03 08 04 00 00 08 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 05 00 0F 00 B8 01 08 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 20 00 14 00 A3 00 0A 04 78 02 14 00 B8 14 38 03 37 00 2E 00 37 00 30 00 2E 00 31 00 34 00 34 00 2E 00 30 00 00 00 0C 04 04 00 05 00 AE 01 0C 04 0E 00 00 00 62 00 65 00 01 00 00 00 00 00 00 00 24 4A CB 01 01 00 00 00 00 00 00 00 80 C3 C9 01 01 00 00 00 00 00 00 00 E5 FD FD 02 01 00 00 00 00 00 00 00 A9 F0 FA 02 01 00 00 00 00 00 00 00 A7 F0 FA 02 01 00 00 00 01 00 00 00 24 4A CB 01 01 00 00 00 01 00 00 00 80 C3 C9 01 01 00 00 00 01 00 00 00 E5 FD FD 02 01 00 00 00 01 00 00 00 A9 F0 FA 02 01 00 00 00 01 00 00 00 A7 F0 FA 02 02 00 00 00 00 00 00 00 24 4A CB 01 02 00 00 00 00 00 00 00 80 C3 C9 01 02 00 00 00 00 00 00 00 E5 FD FD 02 02 00 00 00 00 00 00 00 A9 F0 FA 02 02 00 00 00 00 00 00 00 A7 F0 FA 02 02 00 00 00 01 00 00 00 24 4A CB 01 02 00 00 00 01 00 00 00 80 C3 C9 01 02 00 00 00 01 00 00 00 E5 FD FD 02 02 00 00 00 01 00 00 00 A9 F0 FA 02 02 00 00 00 01 00 00 00 A7 F0 FA 02 03 00 00 00 00 00 00 00 24 4A CB 01 03 00 00 00 00 00 00 00 80 C3 C9 01 03 00 00 00 00 00 00 00 E5 FD FD 02 03 00 00 00 00 00 00 00 A9 F0 FA 02 03 00 00 00 00 00 00 00 A7 F0 FA 02 03 00 00 00 01 00 00 00 24 4A CB 01 03 00 00 00 01 00 00 00 80 C3 C9 01 03 00 00 00 01 00 00 00 E5 FD FD 02 03 00 00 00 01 00 00 00 A9 F0 FA 02 03 00 00 00 01 00 00 00 A7 F0 FA 02 04 00 00 00 00 00 00 00 24 4A CB 01 04 00 00 00 00 00 00 00 80 C3 C9 01 04 00 00 00 00 00 00 00 E5 FD FD 02 04 00 00 00 00 00 00 00 A9 F0 FA 02 04 00 00 00 00 00 00 00 A7 F0 FA 02 04 00 00 00 01 00 00 00 24 4A CB 01 04 00 00 00 01 00 00 00 80 C3 C9 01 04 00 00 00 01 00 00 00 E5 FD FD 02 04 00 00 00 01 00 00 00 A9 F0 FA 02 04 00 00 00 01 00 00 00 A7 F0 FA 02 05 00 00 00 00 00 00 00 24 4A CB 01 05 00 00 00 00 00 00 00 80 C3 C9 01 05 00 00 00 00 00 00 00 E5 FD FD 02 05 00 00 00 00 00 00 00 A9 F0 FA 02 05 00 00 00 00 00 00 00 A7 F0 FA 02 05 00 00 00 01 00 00 00 24 4A CB 01 05 00 00 00 01 00 00 00 80 C3 C9 01 05 00 00 00 01 00 00 00 E5 FD FD 02 05 00 00 00 01 00 00 00 A9 F0 FA 02 05 00 00 00 01 00 00 00 A7 F0 FA 02 06 00 00 00 00 00 00 00 24 4A CB 01 06 00 00 00 00 00 00 00 80 C3 C9 01 06 00 00 00 00 00 00 00 E5 FD FD 02 06 00 00 00 00 00 00 00 A9 F0 FA 02 06 00 00 00 00 00 00 00 A7 F0 FA 02 06 00 00 00 01 00 00 00 24 4A CB 01 06 00 00 00 01 00 00 00 80 C3 C9 01 06 00 00 00 01 00 00 00 E5 FD FD 02 06 00 00 00 01 00 00 00 A9 F0 FA 02 06 00 00 00 01 00 00 00 A7 F0 FA 02 07 00 00 00 00 00 00 00 24 4A CB 01 07 00 00 00 00 00 00 00 80 C3 C9 01 07 00 00 00 00 00 00 00 E5 FD FD 02 07 00 00 00 00 00 00 00 A9 F0 FA 02 07 00 00 00 00 00 00 00 A7 F0 FA 02 07 00 00 00 01 00 00 00 24 4A CB 01 07 00 00 00 01 00 00 00 80 C3 C9 01 07 00 00 00 01 00 00 00 E5 FD FD 02 07 00 00 00 01 00 00 00 A9 F0 FA 02 07 00 00 00 01 00 00 00 A7 F0 FA 02 08 00 00 00 00 00 00 00 24 4A CB 01 08 00 00 00 00 00 00 00 80 C3 C9 01 08 00 00 00 00 00 00 00 E5 FD FD 02 08 00 00 00 00 00 00 00 A9 F0 FA 02 08 00 00 00 00 00 00 00 A7 F0 FA 02 08 00 00 00 01 00 00 00 24 4A CB 01 08 00 00 00 01 00 00 00 80 C3 C9 01 08 00 00 00 01 00 00 00 E5 FD FD 02 08 00 00 00 01 00 00 00 A9 F0 FA 02 08 00 00 00 01 00 00 00 A7 F0 FA 02 00 00 21 00 74 C0 4C 00 1F 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 32 43 32 01 32 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 48 BC 4E 00 26 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 33 43 32 01 33 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 19 5C 10 00 2E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 34 43 32 01 34 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 43 4B 4C 00 35 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 1E 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 44 4B 4C 00 36 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 42 4B 4C 00 37 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 45 4B 4C 00 38 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 48 4B 4C 00 3A 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 46 4B 4C 00 3B 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 4B 4B 4C 00 3E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 4C 4B 4C 00 40 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 57 4B 4C 00 41 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 59 4B 4C 00 42 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 51 4B 4C 00 43 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 54 4B 4C 00 44 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 58 4B 4C 00 45 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 5C 4B 4C 00 46 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 0A 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 64 4B 4C 00 47 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 0A 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 A0 17 52 00 48 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 50 E3 4E 00 49 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 90 01 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 41 BC 4E 00 4B 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 36 43 32 01 36 43 32 01 0A 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 82 34 10 00 4E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 36 43 32 01 36 43 32 01 0A 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 6B 5C 10 00 55 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 37 43 32 01 37 43 32 01 0A 00 00 00 0E 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 82 34 10 00 5D 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 40 BC 4E 00 5E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 60 0A 4F 00 62 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 C8 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 0D 00 00 00 0E 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 72 C0 4C 00 6D 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3A 43 32 01 3A 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 A3 F8 10 00 72 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 35 F8 10 00 73 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 76 C0 4C 00 74 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 90 0E 4D 00 7E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 90 01 00 00 00 00 00 00 0F 00 00 00 3C 43 32 01 3C 43 32 01 0D 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 3C 71 0F 00 7F 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3D 43 32 01 3D 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 83 5C 10 00 81 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3D 43 32 01 3D 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00"));
		} else {
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 01 02 E9 7D 3F 00 00 00 80 05 BB 46 E6 17 02 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B FF C9 9A 3B 01"));
			mplew.writeMapleAsciiString(chr.getClient().getAccountName());
			mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 45 00 E1 9C 98 00 00 06 00 00 00 FF E2 9C 98 00 00 06 00 00 00 FF E3 9C 98 00 00 06 00 00 00 FF E6 9C 98 00 00 06 00 00 00 FF EC 9C 98 00 00 02 00 00 01 ED 9C 98 00 00 02 00 00 01 EE 9C 98 00 00 02 00 00 01 EF 9C 98 00 00 02 00 00 01 F0 9C 98 00 00 02 00 00 01 F1 9C 98 00 00 02 00 00 01 02 9D 98 00 00 06 00 00 00 FF 06 9D 98 00 00 06 00 00 00 FF 07 9D 98 00 00 06 00 00 00 FF C2 2E 31 01 08 04 00 00 07 FF C3 2E 31 01 08 04 00 00 07 FF C4 2E 31 01 08 06 00 00 07 01 FF C5 2E 31 01 08 06 00 00 07 01 FF C6 2E 31 01 08 06 00 00 08 01 02 C7 2E 31 01 00 02 00 00 01 C8 2E 31 01 00 02 00 00 01 A4 B3 32 01 08 00 00 00 08 A8 B3 32 01 08 04 00 00 08 02 B6 B3 32 01 08 00 00 00 08 C3 B3 32 01 08 00 00 00 08 D1 B3 32 01 08 00 00 00 08 DD B3 32 01 08 04 00 00 07 FF E7 B3 32 01 08 06 00 00 07 01 FF E8 B3 32 01 00 02 00 00 01 03 C2 35 01 08 04 00 00 07 FF 0B C2 35 01 08 04 00 00 07 FF 0C C2 35 01 00 02 00 00 01 3B CE 38 01 08 00 00 00 09 CE CE 38 01 08 06 00 00 07 01 FF 6E DB 3B 01 08 00 00 00 09 8E DB 3B 01 08 00 00 00 08 53 62 3D 01 08 00 00 00 09 8A 62 3D 01 08 00 00 00 09 C8 62 3D 01 08 00 00 00 09 D0 62 3D 01 00 06 00 00 00 FF D1 62 3D 01 08 00 00 00 09 B0 E8 3E 01 08 04 00 00 07 FF BC E8 3E 01 08 00 00 00 09 DA E8 3E 01 08 00 00 00 09 DB E8 3E 01 08 00 00 00 06 DC E8 3E 01 08 00 00 00 09 DD E8 3E 01 08 00 00 00 09 DE E8 3E 01 08 04 00 00 07 FF DF E8 3E 01 08 04 00 00 08 02 24 4A CB 01 08 00 00 00 08 36 4A CB 01 08 00 00 00 09 C8 D0 CC 01 08 04 00 00 07 FF C9 D0 CC 01 08 00 00 00 08 CB D0 CC 01 08 00 00 00 08 DE D0 CC 01 08 00 00 00 08 E2 D0 CC 01 08 00 00 00 08 E4 D0 CC 01 00 04 00 00 FF E5 D0 CC 01 00 02 00 00 01 E6 D0 CC 01 00 02 00 00 01 3B 77 FC 02 08 04 00 00 08 02 A0 91 02 03 08 04 00 00 08 02 AC 91 02 03 08 04 00 00 07 FF 1A 87 93 03 08 00 00 00 09 26 87 93 03 08 00 00 00 08 CC 0D 95 03 00 02 00 00 00 CD 0D 95 03 00 02 00 00 00 40 94 96 03 08 00 00 00 08 43 94 96 03 08 04 00 00 08 02 5A 94 96 03 08 00 00 00 08 65 94 96 03 08 04 00 00 08 02 00 33 00 33 00 00 00 00 00 03 00 0D 00 78 00 08 05 90 01 14 00 F0 8B 9C 03 64 00 49 00 70 00 00 00 03 00 10 00 7D 01 0A 05 08 00 00 00 31 00 39 00 33 00 33 00 00 00 08 05 04 00 13 00 72 00 0C 05 98 01 14 00 28 25 B9 01 67 00 69 00 6E 00 49 00 70 00 00 00 00 00 00 00 05 00 17 00 76 01 08 05 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 24 4A CB 01 01 00 00 00 00 00 00 00 80 C3 C9 01 01 00 00 00 00 00 00 00 E5 FD FD 02 01 00 00 00 00 00 00 00 A9 F0 FA 02 01 00 00 00 00 00 00 00 A7 F0 FA 02 01 00 00 00 01 00 00 00 24 4A CB 01 01 00 00 00 01 00 00 00 80 C3 C9 01 01 00 00 00 01 00 00 00 E5 FD FD 02 01 00 00 00 01 00 00 00 A9 F0 FA 02 01 00 00 00 01 00 00 00 A7 F0 FA 02 02 00 00 00 00 00 00 00 24 4A CB 01 02 00 00 00 00 00 00 00 80 C3 C9 01 02 00 00 00 00 00 00 00 E5 FD FD 02 02 00 00 00 00 00 00 00 A9 F0 FA 02 02 00 00 00 00 00 00 00 A7 F0 FA 02 02 00 00 00 01 00 00 00 24 4A CB 01 02 00 00 00 01 00 00 00 80 C3 C9 01 02 00 00 00 01 00 00 00 E5 FD FD 02 02 00 00 00 01 00 00 00 A9 F0 FA 02 02 00 00 00 01 00 00 00 A7 F0 FA 02 03 00 00 00 00 00 00 00 24 4A CB 01 03 00 00 00 00 00 00 00 80 C3 C9 01 03 00 00 00 00 00 00 00 E5 FD FD 02 03 00 00 00 00 00 00 00 A9 F0 FA 02 03 00 00 00 00 00 00 00 A7 F0 FA 02 03 00 00 00 01 00 00 00 24 4A CB 01 03 00 00 00 01 00 00 00 80 C3 C9 01 03 00 00 00 01 00 00 00 E5 FD FD 02 03 00 00 00 01 00 00 00 A9 F0 FA 02 03 00 00 00 01 00 00 00 A7 F0 FA 02 04 00 00 00 00 00 00 00 24 4A CB 01 04 00 00 00 00 00 00 00 80 C3 C9 01 04 00 00 00 00 00 00 00 E5 FD FD 02 04 00 00 00 00 00 00 00 A9 F0 FA 02 04 00 00 00 00 00 00 00 A7 F0 FA 02 04 00 00 00 01 00 00 00 24 4A CB 01 04 00 00 00 01 00 00 00 80 C3 C9 01 04 00 00 00 01 00 00 00 E5 FD FD 02 04 00 00 00 01 00 00 00 A9 F0 FA 02 04 00 00 00 01 00 00 00 A7 F0 FA 02 05 00 00 00 00 00 00 00 24 4A CB 01 05 00 00 00 00 00 00 00 80 C3 C9 01 05 00 00 00 00 00 00 00 E5 FD FD 02 05 00 00 00 00 00 00 00 A9 F0 FA 02 05 00 00 00 00 00 00 00 A7 F0 FA 02 05 00 00 00 01 00 00 00 24 4A CB 01 05 00 00 00 01 00 00 00 80 C3 C9 01 05 00 00 00 01 00 00 00 E5 FD FD 02 05 00 00 00 01 00 00 00 A9 F0 FA 02 05 00 00 00 01 00 00 00 A7 F0 FA 02 06 00 00 00 00 00 00 00 24 4A CB 01 06 00 00 00 00 00 00 00 80 C3 C9 01 06 00 00 00 00 00 00 00 E5 FD FD 02 06 00 00 00 00 00 00 00 A9 F0 FA 02 06 00 00 00 00 00 00 00 A7 F0 FA 02 06 00 00 00 01 00 00 00 24 4A CB 01 06 00 00 00 01 00 00 00 80 C3 C9 01 06 00 00 00 01 00 00 00 E5 FD FD 02 06 00 00 00 01 00 00 00 A9 F0 FA 02 06 00 00 00 01 00 00 00 A7 F0 FA 02 07 00 00 00 00 00 00 00 24 4A CB 01 07 00 00 00 00 00 00 00 80 C3 C9 01 07 00 00 00 00 00 00 00 E5 FD FD 02 07 00 00 00 00 00 00 00 A9 F0 FA 02 07 00 00 00 00 00 00 00 A7 F0 FA 02 07 00 00 00 01 00 00 00 24 4A CB 01 07 00 00 00 01 00 00 00 80 C3 C9 01 07 00 00 00 01 00 00 00 E5 FD FD 02 07 00 00 00 01 00 00 00 A9 F0 FA 02 07 00 00 00 01 00 00 00 A7 F0 FA 02 08 00 00 00 00 00 00 00 24 4A CB 01 08 00 00 00 00 00 00 00 80 C3 C9 01 08 00 00 00 00 00 00 00 E5 FD FD 02 08 00 00 00 00 00 00 00 A9 F0 FA 02 08 00 00 00 00 00 00 00 A7 F0 FA 02 08 00 00 00 01 00 00 00 24 4A CB 01 08 00 00 00 01 00 00 00 80 C3 C9 01 08 00 00 00 01 00 00 00 E5 FD FD 02 08 00 00 00 01 00 00 00 A9 F0 FA 02 08 00 00 00 01 00 00 00 A7 F0 FA 02 00 00 25 00 74 C0 4C 00 1F 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 32 43 32 01 32 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 E9 F8 19 00 23 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 50 00 00 00 00 00 00 00 0F 00 00 00 33 43 32 01 33 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 48 BC 4E 00 26 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 33 43 32 01 33 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 59 98 0F 00 2D 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 34 43 32 01 34 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 19 5C 10 00 2E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 34 43 32 01 34 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 41 4B 4C 00 33 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 43 4B 4C 00 35 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 1E 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 44 4B 4C 00 36 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 42 4B 4C 00 37 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 45 4B 4C 00 38 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 47 4B 4C 00 39 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 1E 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 48 4B 4C 00 3A 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 46 4B 4C 00 3B 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 49 4B 4C 00 3D 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 4B 4B 4C 00 3E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 4C 4B 4C 00 40 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 57 4B 4C 00 41 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 59 4B 4C 00 42 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 51 4B 4C 00 43 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 54 4B 4C 00 44 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 58 4B 4C 00 45 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 5C 4B 4C 00 46 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 0A 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 64 4B 4C 00 47 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 0A 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 A0 17 52 00 48 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 50 E3 4E 00 49 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 90 01 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 41 BC 4E 00 4B 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 36 43 32 01 36 43 32 01 0A 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 82 34 10 00 4E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 36 43 32 01 36 43 32 01 0A 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 6B 5C 10 00 55 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 37 43 32 01 37 43 32 01 0A 00 00 00 0E 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 CC A9 10 00 56 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 37 43 32 01 37 43 32 01 0A 00 00 00 0E 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 40 BC 4E 00 5E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 60 0A 4F 00 62 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 C8 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 0D 00 00 00 0E 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 C0 F8 19 00 65 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 39 43 32 01 39 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 72 C0 4C 00 6D 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3A 43 32 01 3A 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 35 F8 10 00 73 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 76 C0 4C 00 74 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 90 0E 4D 00 7E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 90 01 00 00 00 00 00 00 0F 00 00 00 3C 43 32 01 3C 43 32 01 0D 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 83 5C 10 00 81 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3D 43 32 01 3D 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00"));
		}
		/*mplew.writeMapleAsciiString("********");
		mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 45 00 E1 9C 98 00 00 06 00 00 00 FF E2 9C 98 00 00 06 00 00 00 FF E3 9C 98 00 00 06 00 00 00 FF E6 9C 98 00 00 06 00 00 00 FF EC 9C 98 00 00 02 00 00 01 ED 9C 98 00 00 02 00 00 01 EE 9C 98 00 00 02 00 00 01 EF 9C 98 00 00 02 00 00 01 F0 9C 98 00 00 02 00 00 01 F1 9C 98 00 00 02 00 00 01 02 9D 98 00 00 06 00 00 00 FF 06 9D 98 00 00 06 00 00 00 FF 07 9D 98 00 00 06 00 00 00 FF C2 2E 31 01 08 04 00 00 07 FF C3 2E 31 01 08 04 00 00 07 FF C4 2E 31 01 08 06 00 00 07 01 FF C5 2E 31 01 08 06 00 00 07 01 FF C6 2E 31 01 08 06 00 00 08 01 02 C7 2E 31 01 00 02 00 00 01 C8 2E 31 01 00 02 00 00 01 A4 B3 32 01 08 00 00 00 08 A8 B3 32 01 08 04 00 00 08 02 B6 B3 32 01 08 00 00 00 08 C3 B3 32 01 08 00 00 00 08 D1 B3 32 01 08 00 00 00 08 DD B3 32 01 08 04 00 00 07 FF E7 B3 32 01 08 06 00 00 07 01 FF E8 B3 32 01 00 02 00 00 01 03 C2 35 01 08 04 00 00 07 FF 0B C2 35 01 08 04 00 00 07 FF 0C C2 35 01 00 02 00 00 01 3B CE 38 01 08 00 00 00 09 CE CE 38 01 08 06 00 00 07 01 FF 6E DB 3B 01 08 00 00 00 09 8E DB 3B 01 08 00 00 00 08 53 62 3D 01 08 00 00 00 09 8A 62 3D 01 08 00 00 00 09 C8 62 3D 01 08 00 00 00 09 D0 62 3D 01 00 06 00 00 00 FF D1 62 3D 01 08 00 00 00 09 B0 E8 3E 01 08 04 00 00 07 FF BC E8 3E 01 08 00 00 00 09 DA E8 3E 01 08 00 00 00 09 DB E8 3E 01 08 00 00 00 06 DC E8 3E 01 08 00 00 00 09 DD E8 3E 01 08 00 00 00 09 DE E8 3E 01 08 04 00 00 07 FF DF E8 3E 01 08 04 00 00 08 02 24 4A CB 01 08 00 00 00 08 36 4A CB 01 08 00 00 00 09 C8 D0 CC 01 08 04 00 00 07 FF C9 D0 CC 01 08 00 00 00 08 CB D0 CC 01 08 00 00 00 08 DE D0 CC 01 08 00 00 00 08 E2 D0 CC 01 08 00 00 00 08 E4 D0 CC 01 00 04 00 00 FF E5 D0 CC 01 00 02 00 00 01 E6 D0 CC 01 00 02 00 00 01 3B 77 FC 02 08 04 00 00 08 02 A0 91 02 03 08 04 00 00 08 02 AC 91 02 03 08 04 00 00 07 FF 1A 87 93 03 08 00 00 00 09 26 87 93 03 08 00 00 00 08 CC 0D 95 03 00 02 00 00 00 CD 0D 95 03 00 02 00 00 00 40 94 96 03 08 00 00 00 08 43 94 96 03 08 04 00 00 08 02 5A 94 96 03 08 00 00 00 08 65 94 96 03 08 04 00 00 08 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 05 00 0F 00 B8 01 08 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 20 00 14 00 A3 00 0A 04 78 02 14 00 B8 14 38 03 37 00 2E 00 37 00 30 00 2E 00 31 00 34 00 34 00 2E 00 30 00 00 00 0C 04 04 00 05 00 AE 01 0C 04 0E 00 00 00 62 00 65 00 01 00 00 00 00 00 00 00 24 4A CB 01 01 00 00 00 00 00 00 00 80 C3 C9 01 01 00 00 00 00 00 00 00 E5 FD FD 02 01 00 00 00 00 00 00 00 A9 F0 FA 02 01 00 00 00 00 00 00 00 A7 F0 FA 02 01 00 00 00 01 00 00 00 24 4A CB 01 01 00 00 00 01 00 00 00 80 C3 C9 01 01 00 00 00 01 00 00 00 E5 FD FD 02 01 00 00 00 01 00 00 00 A9 F0 FA 02 01 00 00 00 01 00 00 00 A7 F0 FA 02 02 00 00 00 00 00 00 00 24 4A CB 01 02 00 00 00 00 00 00 00 80 C3 C9 01 02 00 00 00 00 00 00 00 E5 FD FD 02 02 00 00 00 00 00 00 00 A9 F0 FA 02 02 00 00 00 00 00 00 00 A7 F0 FA 02 02 00 00 00 01 00 00 00 24 4A CB 01 02 00 00 00 01 00 00 00 80 C3 C9 01 02 00 00 00 01 00 00 00 E5 FD FD 02 02 00 00 00 01 00 00 00 A9 F0 FA 02 02 00 00 00 01 00 00 00 A7 F0 FA 02 03 00 00 00 00 00 00 00 24 4A CB 01 03 00 00 00 00 00 00 00 80 C3 C9 01 03 00 00 00 00 00 00 00 E5 FD FD 02 03 00 00 00 00 00 00 00 A9 F0 FA 02 03 00 00 00 00 00 00 00 A7 F0 FA 02 03 00 00 00 01 00 00 00 24 4A CB 01 03 00 00 00 01 00 00 00 80 C3 C9 01 03 00 00 00 01 00 00 00 E5 FD FD 02 03 00 00 00 01 00 00 00 A9 F0 FA 02 03 00 00 00 01 00 00 00 A7 F0 FA 02 04 00 00 00 00 00 00 00 24 4A CB 01 04 00 00 00 00 00 00 00 80 C3 C9 01 04 00 00 00 00 00 00 00 E5 FD FD 02 04 00 00 00 00 00 00 00 A9 F0 FA 02 04 00 00 00 00 00 00 00 A7 F0 FA 02 04 00 00 00 01 00 00 00 24 4A CB 01 04 00 00 00 01 00 00 00 80 C3 C9 01 04 00 00 00 01 00 00 00 E5 FD FD 02 04 00 00 00 01 00 00 00 A9 F0 FA 02 04 00 00 00 01 00 00 00 A7 F0 FA 02 05 00 00 00 00 00 00 00 24 4A CB 01 05 00 00 00 00 00 00 00 80 C3 C9 01 05 00 00 00 00 00 00 00 E5 FD FD 02 05 00 00 00 00 00 00 00 A9 F0 FA 02 05 00 00 00 00 00 00 00 A7 F0 FA 02 05 00 00 00 01 00 00 00 24 4A CB 01 05 00 00 00 01 00 00 00 80 C3 C9 01 05 00 00 00 01 00 00 00 E5 FD FD 02 05 00 00 00 01 00 00 00 A9 F0 FA 02 05 00 00 00 01 00 00 00 A7 F0 FA 02 06 00 00 00 00 00 00 00 24 4A CB 01 06 00 00 00 00 00 00 00 80 C3 C9 01 06 00 00 00 00 00 00 00 E5 FD FD 02 06 00 00 00 00 00 00 00 A9 F0 FA 02 06 00 00 00 00 00 00 00 A7 F0 FA 02 06 00 00 00 01 00 00 00 24 4A CB 01 06 00 00 00 01 00 00 00 80 C3 C9 01 06 00 00 00 01 00 00 00 E5 FD FD 02 06 00 00 00 01 00 00 00 A9 F0 FA 02 06 00 00 00 01 00 00 00 A7 F0 FA 02 07 00 00 00 00 00 00 00 24 4A CB 01 07 00 00 00 00 00 00 00 80 C3 C9 01 07 00 00 00 00 00 00 00 E5 FD FD 02 07 00 00 00 00 00 00 00 A9 F0 FA 02 07 00 00 00 00 00 00 00 A7 F0 FA 02 07 00 00 00 01 00 00 00 24 4A CB 01 07 00 00 00 01 00 00 00 80 C3 C9 01 07 00 00 00 01 00 00 00 E5 FD FD 02 07 00 00 00 01 00 00 00 A9 F0 FA 02 07 00 00 00 01 00 00 00 A7 F0 FA 02 08 00 00 00 00 00 00 00 24 4A CB 01 08 00 00 00 00 00 00 00 80 C3 C9 01 08 00 00 00 00 00 00 00 E5 FD FD 02 08 00 00 00 00 00 00 00 A9 F0 FA 02 08 00 00 00 00 00 00 00 A7 F0 FA 02 08 00 00 00 01 00 00 00 24 4A CB 01 08 00 00 00 01 00 00 00 80 C3 C9 01 08 00 00 00 01 00 00 00 E5 FD FD 02 08 00 00 00 01 00 00 00 A9 F0 FA 02 08 00 00 00 01 00 00 00 A7 F0 FA 02 00 00 21 00 74 C0 4C 00 1F 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 32 43 32 01 32 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 48 BC 4E 00 26 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 33 43 32 01 33 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 19 5C 10 00 2E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 34 43 32 01 34 43 32 01 0E 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 43 4B 4C 00 35 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 1E 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 44 4B 4C 00 36 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 42 4B 4C 00 37 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 45 4B 4C 00 38 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 28 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 48 4B 4C 00 3A 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 46 4B 4C 00 3B 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 4B 4B 4C 00 3E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 4C 4B 4C 00 40 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 57 4B 4C 00 41 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 59 4B 4C 00 42 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 51 4B 4C 00 43 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 54 4B 4C 00 44 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 58 4B 4C 00 45 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 5C 4B 4C 00 46 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 0A 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 64 4B 4C 00 47 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 0A 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 A0 17 52 00 48 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 14 00 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 50 E3 4E 00 49 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 90 01 00 00 00 00 00 00 0F 00 00 00 35 43 32 01 35 43 32 01 08 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 41 BC 4E 00 4B 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 36 43 32 01 36 43 32 01 0A 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 82 34 10 00 4E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 36 43 32 01 36 43 32 01 0A 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 6B 5C 10 00 55 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 37 43 32 01 37 43 32 01 0A 00 00 00 0E 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 82 34 10 00 5D 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 40 BC 4E 00 5E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 60 0A 4F 00 62 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 C8 00 00 00 00 00 00 00 0F 00 00 00 38 43 32 01 38 43 32 01 0D 00 00 00 0E 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 72 C0 4C 00 6D 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3A 43 32 01 3A 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 A3 F8 10 00 72 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 35 F8 10 00 73 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 76 C0 4C 00 74 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3B 43 32 01 3B 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 90 0E 4D 00 7E 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 90 01 00 00 00 00 00 00 0F 00 00 00 3C 43 32 01 3C 43 32 01 0D 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 3C 71 0F 00 7F 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3D 43 32 01 3D 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 83 5C 10 00 81 20 9A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 3C 00 00 00 00 00 00 00 0F 00 00 00 3D 43 32 01 3D 43 32 01 09 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00"));*/
		
		return mplew.getPacket();
	}
	
	public static MaplePacket showNXMapleTokens(MapleCharacter chr) {
		
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(0xEE);
		mplew.writeInt(chr.returnCSPoints(0)); // NX
		mplew.writeInt(chr.returnCSPoints(1)); // Maple Points
		mplew.writeInt(chr.returnCSPoints(2)); // Gift Tokens
		
		return mplew.getPacket();
	}
	
	public static MaplePacket showBoughtCSItem(int itemid) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.write(HexTool.getByteArrayFromHexString("EF 00 68 01 00 00 00 01 00 18 00"));
		mplew.writeInt(itemid);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket enableCSUse0() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.write(HexTool.getByteArrayFromHexString("0A 00 00 00 00 00 00"));
		
		return mplew.getPacket();
	}
	
	public static MaplePacket enableCSUse1() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.write(HexTool.getByteArrayFromHexString("EF 00 2C 00 00 04 00"));
		
		return mplew.getPacket();
	}
	
	public static MaplePacket enableCSUse2() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.write(HexTool.getByteArrayFromHexString("EF 00 2E 00 00"));
		
		return mplew.getPacket();
	}
	
	public static MaplePacket enableCSUse3() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.write(HexTool.getByteArrayFromHexString("EF 00 30 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"));
		
		return mplew.getPacket();
	}
	
	public static MaplePacket wrongCouponCode() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.write(HexTool.getByteArrayFromHexString("EF 00 3D 82"));
		
		return mplew.getPacket();
	}
	
	public static MaplePacket getFindReplyWithCS(String target) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
		mplew.write(9);
		mplew.writeMapleAsciiString(target);
		mplew.write(2);
		mplew.writeInt(-1);
		return mplew.getPacket();
	}
	
        public static MaplePacket updatePet(MaplePet pet, boolean alive) {
                MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
                mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
                mplew.write(0);
                mplew.write(2);
		mplew.write(3);
                mplew.write(5);
                mplew.write(pet.getPosition());
                mplew.writeShort(0);
                mplew.write(5);
                mplew.write(pet.getPosition());
		mplew.write(0);
		mplew.write(3);
                mplew.writeInt(pet.getItemId());
                mplew.write(1);
                mplew.writeInt(pet.getUniqueId());
                mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 40 6f e5 0f e7 17 02"));
		String petname = pet.getName();
		if (petname.length() > 13) {
			petname = petname.substring(0, 13);
		}
                mplew.writeAsciiString(petname);
                for (int i = petname.length(); i < 13; i++) {
			mplew.write(0);
                }
                mplew.write(pet.getLevel());
                mplew.writeShort(pet.getCloseness());
                mplew.write(pet.getFullness());
                if(alive) {
                        mplew.writeLong(getKoreanTimestamp((long) (System.currentTimeMillis() * 1.5)));
			mplew.writeInt(0);
                } else {
                        mplew.write(HexTool.getByteArrayFromHexString("00 80 05 bb 46 e6 17 02 00 00 00 00"));
                }
                return mplew.getPacket();
        }
	
	public static MaplePacket showPet(MapleCharacter chr, MaplePet pet, boolean remove) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.SPAWN_PET.getValue());

		mplew.writeInt(chr.getId());
		mplew.write(chr.getPetIndex(pet));
		if (remove) {
			mplew.writeShort(0);
		} else {
			mplew.write(1);
			mplew.write(1);

			mplew.writeInt(pet.getItemId());
			mplew.writeMapleAsciiString(pet.getName());
			mplew.writeInt(pet.getUniqueId());

			mplew.writeInt(0);

			mplew.writeShort(chr.getPosition().x);
			mplew.writeShort(chr.getPosition().y - 12);
		    
			mplew.write(0);
			mplew.writeInt(0x81);
		}
		return mplew.getPacket();
	}
	
	public static MaplePacket movePet(int cid, int pid, List<LifeMovementFragment> moves) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.MOVE_PET.getValue());
		mplew.writeInt(cid);
		// mplew.write(HexTool.getByteArrayFromHexString("24 00 3F FD")); //?
		mplew.write(0);
		mplew.writeInt(pid);

		serializeMovementList(mplew, moves);

		return mplew.getPacket();
	}
	
	public static MaplePacket petChat(int cid, int un, String text) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.PET_CHAT.getValue());
		mplew.writeInt(cid);
		mplew.write(0);
		mplew.writeShort(un);
		mplew.writeMapleAsciiString(text);
		mplew.write(0);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket commandResponse(int cid, byte command, boolean success, boolean food) {
		// 84 00 09 03 2C 00 00 00 19 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// 84 00 E6 DC 17 00 00 01 00 00
		mplew.writeShort(SendPacketOpcode.PET_COMMAND.getValue());
		mplew.writeInt(cid);
		if (!food) {
			mplew.write(0);
		}
		mplew.write(0);
		
		mplew.write(command);
		if (success) {
			mplew.write(1);
		} else {
			mplew.write(0);
		}
		mplew.write(0);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket showPetLevelUp() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
		mplew.write(4);
		mplew.writeShort(0);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket changePetName(MapleClient c, String newname) {
		// 82 00 E6 DC 17 00 00 04 00 4A 65 66 66 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.PET_NAMECHANGE.getValue());
		mplew.writeInt(c.getPlayer().getId());
		mplew.write(0);
		mplew.writeMapleAsciiString(newname);
		mplew.write(0);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket weirdStatUpdate() {
		/*	23 00
			00 00
			08
			00 
			18 
			00 00 00 00 00 00 00 00
			00 00 00 00 00 00 00 00
			00 00 00 00 00 00 00 00
			00
			01 */
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
		mplew.writeShort(0);
		mplew.write(8);
		mplew.write(0);
		mplew.write(0x18);
		mplew.writeLong(0);
		mplew.writeLong(0);
		mplew.writeLong(0);
		mplew.write(0);
		mplew.write(1);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket showApple() {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(0x5C);
		return mplew.getPacket();
	}
	
	public static MaplePacket skillCooldown(int sid, int time) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		
		mplew.writeShort(SendPacketOpcode.COOLDOWN.getValue());
		
		mplew.writeInt(sid);
		mplew.writeShort(time);
		
		return mplew.getPacket();
	}
	
	public static MaplePacket spawnTestZak(MapleMonster life, int tehbyte) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		// 95 00 DA 33 37 00 01 58 CC 6C 00 00 00 00 00 B7 FF F3 FB 02 1A 00 1A
		// 00 02 0E 06 00 00 FF
		// OP OBJID MOBID NULL PX PY ST 00 00 FH
		// 95 00 7A 00 00 00 01 58 CC 6C 00 00 00 00 00 56 FF 3D FA 05 00 00 00
		// 00 FE FF

		mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
			// mplew.writeShort(0xA0); // 47 9e
		mplew.write(tehbyte);
		
		mplew.writeInt(life.getObjectId());
		mplew.write(5); // ????!? either 5 or 1?

		mplew.writeInt(life.getId());
		mplew.writeInt(0); // if nonnull client crashes (?)

		mplew.writeShort(life.getPosition().x);
		mplew.writeShort(life.getPosition().y);
		// System.out.println(life.getPosition().x);
		// System.out.println(life.getPosition().y);
		// mplew.writeShort(life.getCy());
		mplew.write(life.getStance()); // or 5? o.O"

		mplew.writeShort(0); // ??

		mplew.writeShort(life.getFh()); // seems to be left and right
										// restriction...

		mplew.writeShort(-1);

		// System.out.println(mplew.toString());
		return mplew.getPacket();
	}
}

