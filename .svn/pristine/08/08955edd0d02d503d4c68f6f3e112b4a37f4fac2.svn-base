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
	Amon
*/
var status = 0;
var map = 0;

function start() {
	status = -1;
	action(1, 0, 0);
}

function action(mode, type, selection) {
	if (mode == -1) {
		cm.dispose();
	} else {
		if (mode == 0) {
			cm.sendOk("Talk to me when you're ready.");
			cm.dispose();
			return;
		}
		if (mode == 1)
			status++;
		else
			status--;
		if (status == 0) {
			cm.sendSimple("Hello, how may I help you?\r\n#L1##bEye Of Fire#k please! (10,000,000 mesos)#l\r\n\#L2#Take me to #bEl Nath#k!#l\r\n\#L3#Take me to #bThe Door To Zakum#k!#l\r\n#L4#Close the #bZakum Altar#k!#l");
		} else if (status == 1) {
			if (selection == 1) {
				if(cm.getMeso() >= 10000000) {
					cm.gainMeso(-10000000);
					cm.gainItem(4001017, 1);
					cm.sendOk("Enjoy!");
				} else {
					cm.sendOk("Sorry, you do not have enough mesos.");
				}
				cm.dispose();
			} else if (selection == 2) {
				if (cm.checkZakLeader() == 1) {
					map = 1;
					cm.sendYesNo("Please make sure you are the last person to leave the Altar.\r\nAre you sure you're the last?");
				} else {
					cm.warp(211000000, 0);
					cm.dispose();
				}
			} else if (selection == 3) {
				if (cm.checkZakLeader() == 1) {
					map = 2;
					cm.sendYesNo("Please make sure you are the last person to leave the Altar.\r\nAre you sure you're the last?");
				} else {
					cm.warp(211042300, 0);
					cm.dispose();
				}
			} else if (selection == 4) {
				if (cm.checkZakLeader() == 1) {
					if (cm.setZakFighting() == 0) {
						cm.sendOk("Sorry, there was an error.");
						cm.dispose();
					} else {
						cm.sendOk("The Altar is closed. You may begin the fight!");
						cm.dispose();
					}
				} else {
					cm.sendOk("Only the #bZakum Squad#k leader may close the altar.");
				}
			}
		} else if (status == 2) {
			if (cm.removeZakSquad() == 0) {
				cm.sendOk("Sorry, there was an error.");
				cm.dispose();
			} else {
				if (map == 1) {
					cm.warp(211000000, 0);
					cm.dispose();
				} else {
					cm.warp(211042300, 0);
					cm.dispose();
				}
			}
		}
	}
}
