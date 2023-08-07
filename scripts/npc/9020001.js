/* Cloto
Kerning City - Advancement NPC
*/

var status = 0;
var combination = 0;
var checkcombo = 0;

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
			cm.sendOk("Okay.  Maybe next time.");
			cm.dispose();
		}
		if (mode == 1)
			status++;
		else
			status--;
		if (status == 0) {
			var party = 0;
			var iter = cm.getChar().getMap().getCharacters().iterator();
			while (iter.hasNext()) {
				var curChar = iter.next();
				if (curChar.getParty() == cm.getParty()) {
					party += 1;
				}
				if (curChar.getParty() == null) {
					curChar.warpMapTo(103000000);
				}
			}
			if (cm.getChar().getMapId() == 103000800) {
				if (cm.isPartyLeader()) {
					cm.sendNext("Hello. Welcome to the first stage. Look around and you'll see Ligators wandering around. When you defeat it, it'll cough up a piece of #bcoupon#k. Every member of the party other than the leader should talk to me, get a question, and gather up the same number of #bcoupons#k as the answer to the question I'll give to them.\r\nIf you gather up the right amount of coupons, I'll give the #bpass#k to that player. Once all the party members other than the leader gather up the #bpasses#k and give them to the leader, the leader will hand over the #bpasses#k to me, clearing the stage in the process. The faster ou take care of the stages, the more stages you'll be able to challenge, so I suggest you take care of things quickly and swiftly.\r\nWell then, best of luck to you.");
				} else {
					cm.sendNext("Here, you need to collect #bcoupons#k by defeating the same number of ligators as the answer to the question asked individually.");
				}
			} else if (cm.getChar().getMapId() == 103000801) {
				if (cm.isPartyLeader()) {
					var string2;
					var iter2 = cm.getChar().getMap().getCharacters().iterator();
					while (iter2.hasNext()) {
						var curChar2 = iter2.next();
						if (curChar2.getPlayerx() == -753) {
							combination = combination + 1000;
						} else if (curChar2.getPlayerx() == -719) {
							combination = combination + 100;
						} else if (curChar2.getPlayerx() == -584) {
							combination = combination + 10;
						} else if (curChar2.getPlayerx() == -481) {
							combination = combination + 1;
						}
						if (curChar2.getParty() == null) {
							curChar2.warpMapTo(103000000);
						}
					}
					if (party == 1) {
						checkcombo = 9999; // Impossible
					} else if (party == 2) {
						checkcombo = 9999; // Imspobbile
					} else if (party == 3) {
						checkcombo = 111; // Possible
					} else if (party == 4) {
						checkcombo = 1101;
					} else if (party == 5) {
						checkcombo = 1110;
					} else if (party == 6) {
						checkcombo = 111;
					}
					if (combination == checkcombo) {
						cm.environmentChange("gate", 2);
						cm.setPortal(103000801, 2, true);
						cm.showEffect("quest/party/clear");
						cm.playSound("Party1/Clear");
						cm.dispose();
					}else{
						if (cm.isPartyAllHere() && party > 2) {
						cm.showEffect("quest/party/wrong_kor");
						cm.playSound("Party1/Failed");
						cm.dispose();
						} else {
						cm.showNext("Your party is not all here or you do not have enough members to successfully complete this stage.");
						}
					}
					cm.dispose();
				} else {
					cm.sendNext("Let me describe the 2nd stage. You'll see a number of ropes next to me. Of these ropes, #b3 are connected to the portal that heads to the next stage.#k All you need to do is have #b3 party members to find the answer ropes and hang onto them.#k\r\nBUT, it doesn't count as an answer if you hang on to the rope too low; please bring yourself up enough to be counted as a correct answer. Also, only 3 members of you rparty are allowed on the ropes. Once they are hanging on, the leader of the party must #bdouble-click me to check and see if the answer's correct or not.#k Well then, best of luck to you!");
					cm.dispose();
				}
			} else if (cm.getChar().getMapId() == 103000802) {
				if (cm.isPartyLeader()) {
					var string2;
					var iter2 = cm.getChar().getMap().getCharacters().iterator();
					while (iter2.hasNext()) {
						var curChar2 = iter2.next();
						if (curChar2.getPlayery() >= -135 && curChar2.getPlayerx() >= 610 && curChar2.getPlayerx() <= 739) {
							combination = combination + 10000;
						} else if (curChar2.getPlayery() >= -75 && curChar2.getPlayerx() >= 789 && curChar2.getPlayerx() <= 914) {
							combination = combination + 1000;
						} else if (curChar2.getPlayery() >= -135 && curChar2.getPlayerx() >= 964 && curChar2.getPlayerx() <= 1096) {
							combination = combination + 100;
						} else if (curChar2.getPlayery() >= -195 && curChar2.getPlayerx() >= 878 && curChar2.getPlayerx() <= 1007) {
							combination = combination + 10;
						} else if (curChar2.getPlayery() >= -195 && curChar2.getPlayerx() >= 691 && curChar2.getPlayerx() <= 836) {
							combination = combination + 1;
						}
						if (curChar2.getParty() == null) {
							curChar2.warpMapTo(103000000);
						}
					}
					if (party == 1) {
						checkcombo = 99999; // Impossible
					} else if (party == 2) {
						checkcombo = 99999; // Imspobbile
					} else if (party == 3) {
						checkcombo = 11100; // Possible
					} else if (party == 4) {
						checkcombo = 1110;
					} else if (party == 5) {
						checkcombo = 111;
					} else if (party == 6) {
						checkcombo = 10011;
					}
					if (combination == checkcombo) {
						cm.environmentChange("gate", 2);
						cm.setPortal(103000802, 2, true);
						cm.showEffect("quest/party/clear");
						cm.playSound("Party1/Clear");
					}else{
						if (cm.isPartyAllHere() && party > 2) {
						cm.showEffect("quest/party/wrong_kor");
						cm.playSound("Party1/Failed");
						} else {
						cm.showNext("Your party is not all here or you do not have enough members to successfully complete this stage.");
						}
					}
					cm.dispose();
				} else {
					cm.sendNext("Let me describe the 3rd stage. You'll see a bunch of barrels with kittens inside on the top of the platforms. #b3 of these platforms are connected to the portal that sends you to the next stage. 3 of the party members need to fin dth ecorrect platform to step on and clear the stage.\r\nBUT, you need to stand firm right at the center of it, not standing on the edge, in order to be counted as a correct answer, so make sure to remember that. Also, only 3 members of your party are allowed on the platforms. Once the members are on them, the leader of the party must double-click me to check and see if the answer's right or not.#k\r\nWell, then, best of luck to you~!");

					cm.dispose();
				}
			} else if (cm.getChar().getMapId() == 103000803) {
				if (cm.isPartyLeader()) {
					var string2;
					var iter2 = cm.getChar().getMap().getCharacters().iterator();
					while (iter2.hasNext()) {
						var curChar2 = iter2.next();
						if (curChar2.getPlayery() == -234 && curChar2.getPlayerx() >= 912 && curChar2.getPlayerx() <= 947) {
							combination = combination + 100000;
						} else if (curChar2.getPlayery() == -182 && curChar2.getPlayerx() >= 878 && curChar2.getPlayerx() <= 912) {
							combination = combination + 10000;
						} else if (curChar2.getPlayery() == -182 && curChar2.getPlayerx() >= 948 && curChar2.getPlayerx() <= 981) {
							combination = combination + 1000;
						} else if (curChar2.getPlayery() == -130 && curChar2.getPlayerx() >= 844 && curChar2.getPlayerx() <= 875) {
							combination = combination + 100;
						} else if (curChar2.getPlayery() == -130 && curChar2.getPlayerx() >= 911 && curChar2.getPlayerx() <= 945) {
							combination = combination + 10;
						} else if (curChar2.getPlayery() == -130 && curChar2.getPlayerx() >= 979 && curChar2.getPlayerx() <= 1014) {
							combination = combination + 1;
						}
						if (curChar2.getParty() == null) {
							curChar2.warpMapTo(103000000);
						}
					}
					if (party == 1) {
						checkcombo = 999999; // Impossible
					} else if (party == 2) {
						checkcombo = 999999; // Imspobbile
					} else if (party == 3) {
						checkcombo = 101010; // Possible
					} else if (party == 4) {
						checkcombo = 1110;
					} else if (party == 5) {
						checkcombo = 1011;
					} else if (party == 6) {
						checkcombo = 111;
					}
					if (combination == checkcombo) {
						cm.environmentChange("gate", 2);
						cm.setPortal(103000803, 2, true);
						cm.showEffect("quest/party/clear");
						cm.playSound("Party1/Clear");
						cm.spawnMob(103000804, 9300002, 328, -2175);
						cm.spawnMob(103000804, 9300002, 179, -2175);
						cm.spawnMob(103000804, 9300002, -107, -2175);
						cm.spawnMob(103000804, 9300000, -192, -1455);
						cm.spawnMob(103000804, 9300000, -90, -1455);
						cm.spawnMob(103000804, 9300000, 45, -1455);
						cm.spawnMob(103000804, 9300000, 132, -1455);
						cm.spawnMob(103000804, 9300000, 215, -1455);
						cm.spawnMob(103000804, 9300000, 312, -1455);
						cm.spawnMob(103000804, 9300003, 110, -435);
					}else{
						if (cm.isPartyAllHere() && party > 2) {
						cm.showEffect("quest/party/wrong_kor");
						cm.playSound("Party1/Failed");
						} else {
						cm.showNext("Your party is not all here or you do not have enough members to successfully complete this stage.");
						}
					}
					cm.dispose();
				} else {
					cm.sendNext("Let me describe the 4th stage. You'll see a bunch of barrels next to you. Out of these, #b3 are connected to the portals that take you to the next stage. 3 members of the party need to find the right ones and stand on top of them#k to clear the stage. BUT please make sure to stand firm at the center of the barrel, not barely hanging on the edge, because that's the only way the answer counts. Also, only 3 members of your party are allowed on top of the barrels. Once the members are on top, the leader of the party must #bdouble-click me to check and see if the answer's right or not.#k Well then, best of luck to you!");

					cm.dispose();
				}
			} else if (cm.getChar().getMapId() == 103000804) {
				if (cm.isPartyLeader()) {
					if (cm.haveItem(4001008, 10)) {
						cm.showEffect("quest/party/clear");
						cm.playSound("Party1/Clear");
						cm.sendNext("Incredible! You cleared all the stages to get to this point. Here's a small prize for your job well done. Before you accept it, however, please make sure your use and etc. inventories have empty slots available.");
					}else{
						cm.dispose();
					}
				}else{
					cm.dispose();
				}
			}
		} else if (status == 1) {
			var party = 0;
			var iter = cm.getChar().getMap().getCharacters().iterator();
			while (iter.hasNext()) {
				var curChar = iter.next();
				if (curChar.getParty() == cm.getParty()) {
					party += 1;
				}
				if (curChar.getParty() == null) {
					curChar.warpMapTo(103000000);
				}
			}
			if (cm.getChar().getMapId() == 103000800) {
				if (cm.isPartyLeader()) {
					if (cm.haveItem(4001008, (party - 1))) {
						cm.environmentChange("gate", 2);
						cm.gainItem(4001008, -(party - 1));
						cm.sendNext("Wow you collected #b"+(party - 1)+"#k passes. You can now advance to the next stage.");
						cm.setPortal(103000800, 2, true);
						cm.showEffect("quest/party/clear");
						cm.playSound("Party1/Clear");
						cm.dispose();
					} else {
						cm.sendNext("Please turn in #b"+(party - 1)+"#k passes.");
						cm.dipose();
					}
				} else {
				if (cm.haveItem(4001008)) {
					cm.sendNext("Wow you answered my question nicely. Here's the pass to the party; please hand it to the leader.");
					cm.dispose();
				} else {
				if (!cm.haveItem(4001007)) {
					cm.sendNext("Here's the question. Collect the same number of coupons as the minimum amount of DEX level needed to make the first job advancement as the bowman.");
					cm.dispose();
				}else {
					if (cm.haveItem(4001007, 25)) {
						cm.sendNext("Wow you answered my question nicely. Here's the pass to the party; please hand it to the leader.");
						cm.gainItem(4001008, 1);
						cm.gainItem(4001007, -25);
						cm.dispose();
					} else {
						cm.sendNext("That's not the right answer. I can only give you the pass if you collect the same number of coupons as the answer to the question suggests. I'll repeat the question.");
					}
				}
				}
				}
			} else if (cm.getChar().getMapId() == 103000804) {
				cm.gainItem(4001008, -10);
				var iter = cm.getChar().getMap().getCharacters().iterator();
				while (iter.hasNext()) {
					var curChar = iter.next();
					if (curChar.getParty() == cm.getParty()) {
						curChar.warpMapTo(103000805);
					}
					if (curChar.getParty() == null) {
						curChar.warpMapTo(103000000);
					}
				}
				cm.dispose();
			}
		} else if (status == 2) {
			cm.dispose();
			var party = 0;
			var iter = cm.getChar().getMap().getCharacters().iterator();
			while (iter.hasNext()) {
				var curChar = iter.next();
				if (curChar.getParty() == cm.getParty()) {
					party += 1;
				}
				if (curChar.getParty() == null) {
						curChar.warpMapTo(103000000);
				}
			}
			if (cm.getChar().getMapId() == 103000800) {
				cm.sendNext("Here's the question. Collect the same number of coupons as the minimum amount of DEX level needed to make the first job advancement as the bowman.");
				cm.dispose();
			}
		}
	}
}