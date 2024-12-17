/*
 * Copyright (c) 2024 - The MegaMek Team. All Rights Reserved.
 *
 *  This file is part of MekHQ.
 *
 *  MekHQ is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  MekHQ is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */

package mekhq.campaign.autoresolve.acar.action;

import megamek.common.TargetRoll;
import mekhq.campaign.autoresolve.component.EngagementControl;
import mekhq.campaign.autoresolve.component.Formation;
import mekhq.utilities.I18n;

public class ManeuverToHitData extends TargetRoll {

    public ManeuverToHitData(int value, String desc) {
        super(value, desc);
    }

    public static ManeuverToHitData compileToHit(Formation formation) {
        var toHit = new ManeuverToHitData(formation.getTactics(), I18n.t("acar.formation_tactics"));
        processFormationModifiers(toHit, formation);
        processCombatUnit(toHit, formation);
        return toHit;
    }

    private static void processFormationModifiers(ManeuverToHitData toHit, Formation formation) {
        if (formation.getEngagementControl() == EngagementControl.FORCED_ENGAGEMENT) {
            toHit.addModifier(1, I18n.t("acar.forced_engagement"));
        }
        if (formation.isAerospace()) {
            toHit.addModifier(2, I18n.t("acar.aerospace_formation"));
        }
    }

    private static void processCombatUnit(ManeuverToHitData toHit, Formation formation) {
        switch (formation.getSkill()) {
            case 7 -> toHit.addModifier(+4, I18n.t("acar.skill_7"));
            case 6 -> toHit.addModifier(+3, I18n.t("acar.skill_6"));
            case 5 -> toHit.addModifier(+2, I18n.t("acar.skill_5"));
            case 4 -> toHit.addModifier(+1, I18n.t("acar.skill_4"));
            case 3 -> toHit.addModifier(0, I18n.t("acar.skill_3"));
            case 2 -> toHit.addModifier(-1, I18n.t("acar.skill_2"));
            case 1 -> toHit.addModifier(-2, I18n.t("acar.skill_1"));
            case 0 -> toHit.addModifier(-3, I18n.t("acar.skill_0"));
            default -> toHit.addModifier(TargetRoll.IMPOSSIBLE, I18n.t("acar.invalid_skill"));
        }

        switch (formation.moraleStatus()) {
            case SHAKEN -> toHit.addModifier(+0, I18n.t("acar.shaken_morale"));
            case UNSTEADY -> toHit.addModifier(+1, I18n.t("acar.unsteady_morale"));
            case BROKEN -> toHit.addModifier(+2, I18n.t("acar.broken_morale"));
            case ROUTED -> toHit.addModifier(+2, I18n.t("acar.routed_morale"));
        }
    }
}
