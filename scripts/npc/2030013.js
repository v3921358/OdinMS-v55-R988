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
	Adobis
*/
var status = 0;

function start() {
	status = -1;
	action(1, 0, 0);
}

function action(mode, type, selection) {
	if (mode == -1) {
		cm.dispose();
	} else {
		if (mode == 0 && status == 0) {
			cm.dispose();
			return;
		}
		if (mode == 1)
			status++;
		else
			status--;
		if (status == 0) {
			if (cm.checkZakSquad() == 0) {
				cm.sendSimple("Would you like to setup a Zakum Squad?\r\n#b#L1#Lets get this going!#l\r\n\#L2#Nah, I think I'll wait a bit...#l");
			} else if (cm.checkZakSquad() == 2) {
				cm.sendOk("Sorry but the Zakum Squad has already started.");
				cm.dispose();
			} else {
				cm.sendSimple("Would you like to join the Zakum squad?\r\n#b#L1#Yeah, lemme fight!#l\r\n\#L2#Nah, I think I'll wait a bit...#l");
				status = 9;
			}
		} else if (status == 1) {
			if (selection == 1) {
				if (cm.createZakSquad() == 0) {
					cm.sendOk("Sorry, there was an error.");
					cm.dispose();
				} else {
					cm.sendOk("The Zakum Squad has been created, tell your members to signup now.\r\n\r\nI will now take you to #rZakum's Altar#k. Your members will follow shortly.");
				}
			} else if (selection == 2) {
				cm.sendOk("Alright, just tell me when you're ready.");
				cm.dispose();
			}
		} else if (status == 10) {
			if (selection == 1) {
				if (cm.numMembers > 29) {
					cm.sendOk("Sorry, the Zakum Squad is full.");
					cm.dispose();
				} else {
					if (cm.addZakMember() == 0) {
						cm.sendOk("Sorry, there was an error.");
						cm.dispose();
					} else {
						cm.sendOk("You have signed up, I will now take you to #rZakum's Altar#k.");
					}
				}
			} else if (selection == 2) {
				cm.sendOk("Alright, just tell me when you're ready.");
				cm.dispose();
			}
		} else if (status == 11 || status == 2) {
			cm.warp(280030000);
			cm.dispose();
		}
	}
}
