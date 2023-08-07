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

package net.sf.odinms.client;

import net.sf.odinms.net.IntValueHolder;

public enum MapleDisease implements IntValueHolder {
	SEDUCE(0x4000),
	STUN(0x20000),
	POISON(0x40000),
	SEAL(0x80000),
	DARKNESS(0x100000),
	WEAKEN(0x40000000)
	;
	
	private final int i;

	private MapleDisease(int i) {
		this.i = i;
	}

	@Override
	public int getValue() {
		return i;
	}
	
	public MapleDisease getType(int skill) {
		switch (skill) {
			case 120:
				return MapleDisease.SEAL;
			case 121:
				return MapleDisease.DARKNESS;
			case 122:
				return MapleDisease.WEAKEN;
			case 123:
				return MapleDisease.STUN;
			case 125:
				return MapleDisease.POISON;
			case 128:
				return MapleDisease.SEDUCE;
			default:
				return null;
		}
	}
}
