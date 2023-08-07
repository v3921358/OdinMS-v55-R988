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

import static net.sf.odinms.client.messages.CommandProcessor.getNamedDoubleArg;
import static net.sf.odinms.client.messages.CommandProcessor.getNamedIntArg;
import static net.sf.odinms.client.messages.CommandProcessor.getOptionalIntArg;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.Command;
import net.sf.odinms.client.messages.CommandDefinition;
import net.sf.odinms.client.messages.IllegalCommandSyntaxException;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.server.life.MapleLifeFactory;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.life.MapleMonsterStats;
public class SpawnMonsterCommand implements Command {
	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpawnMonsterCommand.class);

	@Override
	public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception,
																					IllegalCommandSyntaxException {
		int mid = Integer.parseInt(splitted[1]);
		int num = Math.min(getOptionalIntArg(splitted, 2, 1), 500);

		if (mid == 9400203) {
			log.info(MapleClient.getLogMessage(c.getPlayer(), "Trying to spawn a silver slime"));
			return;
		}

		Integer hp = getNamedIntArg(splitted, 1, "hp");
		Integer exp = getNamedIntArg(splitted, 1, "exp");
		Double php = getNamedDoubleArg(splitted, 1, "php");
		Double pexp = getNamedDoubleArg(splitted, 1, "pexp");

		MapleMonster onemob = MapleLifeFactory.getMonster(mid);

		int newhp = 0;
		int newexp = 0;

		double oldExpRatio = ((double) onemob.getHp() / onemob.getExp());

		if (hp != null) {
			newhp = hp.intValue();
		} else if (php != null) {
			newhp = (int) (onemob.getMaxHp() * (php.doubleValue() / 100));
		} else {
			newhp = onemob.getMaxHp();
		}
		if (exp != null) {
			newexp = exp.intValue();
		} else if (pexp != null) {
			newexp = (int) (onemob.getExp() * (pexp.doubleValue() / 100));
		} else {
			newexp = onemob.getExp();
		}

		if (newhp < 1) {
			newhp = 1;
		}
		double newExpRatio = ((double) newhp / newexp);
		if (newExpRatio < oldExpRatio && newexp > 0) {
			mc.dropMessage("The new hp/exp ratio is better than the old one. (" + newExpRatio + " < " +
				oldExpRatio + ") Please don't do this");
			return;
		}
		
		MapleMonsterStats overrideStats = new MapleMonsterStats();
		overrideStats.setHp(newhp);
		overrideStats.setExp(newexp);
		overrideStats.setMp(onemob.getMaxMp());
		
		for (int i = 0; i < num; i++) {
			MapleMonster mob = MapleLifeFactory.getMonster(mid);
			mob.setHp(newhp);
			mob.setOverrideStats(overrideStats);
			c.getPlayer().getMap().spawnMonsterOnGroudBelow(mob, c.getPlayer().getPosition());
		}
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("spawn", "[hp newHp] [exp newExp] [php procentual Hp] [pexp procentual Exp] monsterid", "Spawns the monster with the given id", 200),
		};
	}

}
