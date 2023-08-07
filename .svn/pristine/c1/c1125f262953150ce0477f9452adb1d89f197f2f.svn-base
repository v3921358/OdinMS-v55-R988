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

/* Mr. Goldstein
	located in Lith Harbour (104000000)
	Buddy List Admin
*/

var status = 0;
var capacity;
var newcapacity;
var price = 5000000;

function start() {
	status = -1;
	action(1, 0, 0);
}

function action(mode, type, selection) {
	if (mode == -1) {
		cm.dispose();
	} else {
		if (status >= 2 && mode == 0) {
			cm.sendOk("If you want to increase your Buddy List Capacity in the future, feel free to come back.");
			cm.dispose();
			return;
		}
		if (mode == 1)
			status++;
		else
			status--;
		if (status == 0) {
			cm.sendNext("Hello, I can update your Buddy List Capacity.");
		} else if (status == 1) {
			capacity = cm.getPlayer().getBuddylist().getCapacity();
			if (capacity == 50) {
				cm.sendOk("You already have the maximum capacity of 50, you cannot upgrade your Buddy List any further");
				cm.dispose();
			}
			else {
				newcapacity = capacity + 5;
				cm.sendYesNo("Would you like to update your Buddy List Capacity to #b" + newcapacity + "#k for #b" + price + " #kmesos?");
			}
		} else if (status == 2) {
			if (cm.getMeso() < price) {
				cm.sendOk("Sorry but it doesn't look like you have enough mesos!")
				cm.dispose();
			}
			else {
				cm.updateBuddyCapacity(newcapacity);
				cm.gainMeso(-price);
				cm.sendOk("Enjoy your new buddy capacity.");
				cm.dispose();
			}
		} 
	}
}