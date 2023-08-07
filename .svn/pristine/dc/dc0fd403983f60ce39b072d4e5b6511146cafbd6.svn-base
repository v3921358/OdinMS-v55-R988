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

package net.sf.odinms.client.messages.commands;

import java.util.Arrays;
import java.util.List;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.Command;
import net.sf.odinms.client.messages.CommandDefinition;
import net.sf.odinms.client.messages.CommandProcessor;
import net.sf.odinms.client.messages.IllegalCommandSyntaxException;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.tools.MaplePacketCreator;

public class MonsterInfoCommands implements Command {
	@Override
	public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception,
																					IllegalCommandSyntaxException {
		if (splitted[0].equals("!killall") || splitted[0].equals("!monsterdebug")) {
			MapleMap map = c.getPlayer().getMap();
			double range = Double.POSITIVE_INFINITY;
			if (splitted.length > 1) {
				int irange = Integer.parseInt(splitted[1]);
				range = irange * irange;
			}
			List<MapleMapObject> monsters = map.getMapObjectsInRange(c.getPlayer().getPosition(), range, Arrays
				.asList(MapleMapObjectType.MONSTER));
			boolean kill = splitted[0].equals("!killall");
			for (MapleMapObject monstermo : monsters) {
				MapleMonster monster = (MapleMonster) monstermo;
				if (kill) {
					map.killMonster(monster, c.getPlayer(), false);
				} else {
					mc.dropMessage("Monster " + monster.toString());
				}
			}
			if (kill) {
				mc.dropMessage("Killed " + monsters.size() + " monsters <3");
			}
		} else if (splitted[0].equals("!testzak")) {
			if (splitted.length < 2) {
				throw new IllegalCommandSyntaxException(1);
			}
			int oid = c.getPlayer().getMap().spawnTestZak();
			MapleMonster zak = c.getPlayer().getMap().getMonsterByOid(oid);
			c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.spawnTestZak(zak, CommandProcessor.getOptionalIntArg(splitted, 1, 1)), true);
		}
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("killall", "[range]", "", 100),
			new CommandDefinition("monsterdebug", "[range]", "", 100),
		};
	}

}
