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

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.Command;
import net.sf.odinms.client.messages.CommandDefinition;
import net.sf.odinms.client.messages.CommandProcessor;
import net.sf.odinms.client.messages.IllegalCommandSyntaxException;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.server.ShutdownServer;

public class ShutdownCommands implements Command {
	@Override
	public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception,
																				IllegalCommandSyntaxException {
		if (splitted[0].equals("!shutdown")) {
			int time = 60000;
			if (splitted.length > 1) {
				time = Integer.parseInt(splitted[1]) * 60000;
			}
			CommandProcessor.forcePersisting();
			c.getChannelServer().shutdown(time);
		} else if (splitted[0].equals("!shutdownworld")) {
			int time = 60000;
			if (splitted.length > 1) {
				time = Integer.parseInt(splitted[1]) * 60000;
			}
			CommandProcessor.forcePersisting();
			c.getChannelServer().shutdownWorld(time);
			// shutdown
		} else if (splitted[0].equals("!shutdownnow")) {
			CommandProcessor.forcePersisting();
			new ShutdownServer(c.getChannel()).run();
		}
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("shutdown", "[when in Minutes]", "Shuts down the current channel - don't use atm", 1000),
			new CommandDefinition("shutdownnow", "", "Shuts down the current channel now", 1000),
			new CommandDefinition("shutdownworld", "[when in Minutes]", "Cleanly shuts down all channels and the loginserver of this world", 500),
		};
	}

}
