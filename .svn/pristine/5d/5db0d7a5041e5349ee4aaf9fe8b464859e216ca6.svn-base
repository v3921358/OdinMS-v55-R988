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
package net.sf.odinms.scripting.npc;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.odinms.client.IItem;
import net.sf.odinms.client.Item;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventory;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.scripting.AbstractPlayerInteraction;
import net.sf.odinms.scripting.event.EventManager;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MapleShopFactory;
import net.sf.odinms.server.quest.MapleQuest;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.client.MapleStat;

/**
 *
 * @author Matze
 */
public class NPCConversationManager extends AbstractPlayerInteraction {
        private MapleClient c;
	private int npc;
	private String getText;

	public NPCConversationManager(MapleClient c, int npc) {
		super(c);
                this.c = c;
		this.npc = npc;
	}

	public void dispose() {
		NPCScriptManager.getInstance().dispose(this);
	}

	public void sendNext(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "00 01"));
	}

	public void sendPrev(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "01 00"));
	}

	public void sendNextPrev(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "01 01"));
	}

	public void sendOk(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "00 00"));
	}

	public void sendYesNo(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 1, text, ""));
	}

	public void sendAcceptDecline(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 2, text, ""));
	}

	public void sendSimple(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 5, text, ""));
	}

	public void sendStyle(String text, int styles[]) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkStyle(npc, text, styles));
	}

	public void sendGetNumber(String text, int def, int min, int max) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkNum(npc, text, def, min, max));
	}

	public void sendGetText(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkText(npc, text));
	}

	public void setGetText(String text) {
		this.getText = text;
	}

	public String getText() {
		return this.getText;
	}

	public void openShop(int id) {
		MapleShopFactory.getInstance().getShop(id).sendShop(getClient());
	}

	public void changeJob(MapleJob job) {
		getPlayer().changeJob(job);
	}

	public MapleJob getJob() {
		return getPlayer().getJob();
	}

	public void startQuest(int id) {
		MapleQuest.getInstance(id).start(getPlayer(), npc);
	}

	public void completeQuest(int id) {
		MapleQuest.getInstance(id).complete(getPlayer(), npc);
	}

	public void forfeitQuest(int id) {
		MapleQuest.getInstance(id).forfeit(getPlayer());
	}

	/**
	 * use getPlayer().getMeso() instead
	 * @return
	 */
	@Deprecated
	public int getMeso() {
		return getPlayer().getMeso();
	}

	
	public void gainMeso(int gain) {
		getPlayer().gainMeso(gain, true, false, true);
	}

	public void gainExp(int gain) {
		getPlayer().gainExp(gain, true, true);
	}

	public int getNpc() {
		return npc;
	}

	/**
	 * use getPlayer().getLevel() instead
	 * @return
	 */
	@Deprecated
	public int getLevel() {
		return getPlayer().getLevel();
	}

	public void unequipEverything() {
		MapleInventory equipped = getPlayer().getInventory(MapleInventoryType.EQUIPPED);
		MapleInventory equip = getPlayer().getInventory(MapleInventoryType.EQUIP);
		List<Byte> ids = new LinkedList<Byte>();
		for (IItem item : equipped.list()) {
			ids.add(item.getPosition());
		}
		for (byte id : ids) {
			MapleInventoryManipulator.unequip(getC(), id, equip.getNextFreeSlot());
		}
	}

	public void teachSkill(int id, int level, int masterlevel) {
		getPlayer().changeSkillLevel(SkillFactory.getSkill(id), level, masterlevel);
	}

	/**
	 * Use getPlayer() instead (for consistency with MapleClient)
	 * @return
	 */
	@Deprecated
	public MapleCharacter getChar() {
		return getPlayer();
	}

	public MapleClient getC() {
		return getClient();
	}

	public void rechargeStars() {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		IItem stars = getChar().getInventory(MapleInventoryType.USE).getItem((byte) 1);
		if (ii.isThrowingStar(stars.getItemId())) {
			stars.setQuantity(ii.getSlotMax(stars.getItemId()));
			getC().getSession().write(MaplePacketCreator.updateInventorySlot(MapleInventoryType.USE, (Item) stars));
		}
	}

	public EventManager getEventManager(String event) {
		return getClient().getChannelServer().getEventSM().getEventManager(event);
	}
	
	public void showEffect(String effect) {
		getPlayer().getMap().broadcastMessage(MaplePacketCreator.showEffect(effect));
	}
	
	public void playSound(String sound) {
		getClient().getPlayer().getMap().broadcastMessage(MaplePacketCreator.playSound(sound));
	}
	
	@Override
	public String toString() {
		return "Conversation with NPC: " + npc;
	}
        
        public void updateBuddyCapacity(int capacity) {
            getPlayer().setBuddyCapacity(capacity);
        }
        
        public int getBuddyCapacity() {
            return getPlayer().getBuddyCapacity();
        }
        
        public void setHair(int hair) {
	        c.getPlayer().setHair(hair);
	        c.getPlayer().updateSingleStat(MapleStat.HAIR, hair);
	        c.getPlayer().equipChanged();
	}
	
	public void setFace(int face) {
	        c.getPlayer().setFace(face);
	        c.getPlayer().updateSingleStat(MapleStat.FACE, face);
	        c.getPlayer().equipChanged();
	}
	
	public void setSkin(int color) {            
	        c.getPlayer().setSkinColor(c.getPlayer().getSkinColor().getById(color));
	        c.getPlayer().updateSingleStat(MapleStat.SKIN, color);
	        c.getPlayer().equipChanged();
	}
        
        public int createZakSquad() {
            try {
                return ZakSquad.createSquad(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int checkZakSquad() {
            try {
                return ZakSquad.checkSquad(c.getChannel());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int setZakFighting() {
            try {
                return ZakSquad.setFighting(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int checkZakLeader() {
            try {
                return ZakSquad.checkLeader(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }

        public int removeZakSquad() {
            try {
                return ZakSquad.removeSquad(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int numZakMembers() {
            try {
                return ZakSquad.numMembers(c.getChannel());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int addZakMember() {
            try {
                return ZakSquad.addMember(c.getChannel());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int createHTSquad() {
            try {
                return HTSquad.createSquad(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int checkHTSquad() {
            try {
                return HTSquad.checkSquad(c.getChannel());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int setHTFighting() {
            try {
                return HTSquad.setFighting(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int checkHTLeader() {
            try {
                return HTSquad.checkLeader(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }

        public int removeHTSquad() {
            try {
                return HTSquad.removeSquad(c.getChannel(), c.getPlayer().getId());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int numHTMembers() {
            try {
                return HTSquad.numMembers(c.getChannel());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public int addHTMember() {
            try {
                return HTSquad.addMember(c.getChannel());
            } catch (SQLException ex) {
                Logger.getLogger(NPCConversationManager.class.getName()).log(Level.SEVERE, null, ex);
                return 0;
            }
        }
        
        public void resetReactors() {
		c.getPlayer().getMap().resetReactors();
	}
}
