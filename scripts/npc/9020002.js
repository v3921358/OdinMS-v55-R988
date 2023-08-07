/* Nella
Kerning City - Exit NPC
*/

var status = 0;

function start() {
	status = -1;
	action(1, 0, 0);
}

function action(mode, type, selection) {
	if (mode == -1) {
		cm.dispose();
		return;
	} else {
		if (mode == 0) {
			if (cm.getChar().getMapId() == 103000805)
			cm.sendOk("I see. This map is designated for hunting, so it'll be best for you to hunt as much as possible before time runs out. If you feel like leaving this stage, by all means talk to me.");
			if (cm.getChar().getMapId() != 103000805 && cm.getChar().getMapId() != 103000890)
			cm.sendOk("I see. Teamwork is very important here. Please work harder with your fellow party members.");
			cm.dispose();
		}
		if (mode == 1)
			status++;
		else
			status--;
		if (status == 0) {
			if (cm.getChar().getMapId() == 103000890) {
				cm.gainItem(4001007, -(cm.itemQuantity(4001007)));
				cm.gainItem(4001008, -(cm.itemQuantity(4001008)));
				cm.warp(103000000);
				cm.dispose();
			}
			if (cm.getChar().getMapId() == 103000805) {
				cm.sendYesNo("Did you hunt a lot at the bonus map? Once you leave this place, you won't be able to come back and hunt again. Are you sure you want to leave here?");
			}
			if (cm.getChar().getMapId() != 103000805 && cm.getChar().getMapId() != 103000890) {
				cm.sendYesNo("Once you leave the map, you'll have to restart the whole quest if you want to try it agian. Do you still want to leave this map?");
			}
		}  else if (status == 1) {
			if (cm.getChar().getMapId() == 103000805) {
				var prizeRand = 1 + Math.floor(Math.random() * 150);
				if (prizeRand >= 0 && prizeRand < 10) {
					cm.gainItem(1112223, 1);
				} else if (prizeRand >= 10 && prizeRand < 20) {
					cm.gainItem(1112224, 1);
				} else if (prizeRand >= 20 && prizeRand < 30) {
					cm.gainItem(1112225, 1);
				} else if (prizeRand >= 30 && prizeRand < 40) {		
					cm.gainItem(1112226, 1);
				} else if (prizeRand >= 40 && prizeRand < 50) {
					cm.gainItem(1112227, 1);
				} else if (prizeRand >= 50 && prizeRand < 60) {
					cm.gainItem(1112112, 1);
				} else if (prizeRand >= 60 && prizeRand < 70) {
					cm.gainItem(1112113, 1);
				} else if (prizeRand >= 70 && prizeRand < 80) {
					cm.gainItem(1112114, 1);
				} else if (prizeRand >= 80 && prizeRand < 90) {
					cm.gainItem(1112115, 1);
				} else if (prizeRand >= 90 && prizeRand < 100) {
					cm.gainItem(1112116, 1);
				} else if (prizeRand >= 100 && prizeRand < 125) {
					cm.gainItem(5040001, 5);
				} else if (prizeRand >= 125 && prizeRand <= 150) {
					cm.gainItem(5041000, 5);
				}
				if (cm.isPartyLeader()) {
					var iter = cm.getChar().getMap().getCharacters().iterator();
					var partynum = 0;
					while (iter.hasNext()) {
						var curChar = iter.next();
						curChar.warpMapTo(103000890);
					}
				} else {
					cm.warp(103000890);
				}
				cm.dispose();
			} else {
				if (cm.isPartyLeader()) {
					var iter = cm.getChar().getMap().getCharacters().iterator();
					var partynum = 0;
					while (iter.hasNext()) {
						var curChar = iter.next();
						curChar.warpMapTo(103000890);
					}
				} else {
					cm.warp(103000890);
				}
				cm.dispose();
			}
		}
	}
}