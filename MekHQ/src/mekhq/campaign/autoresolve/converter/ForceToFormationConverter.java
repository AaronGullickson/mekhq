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

package mekhq.campaign.autoresolve.converter;

import megamek.common.Entity;
import megamek.common.ForceAssignable;
import megamek.common.Game;
import megamek.common.alphaStrike.ASDamage;
import megamek.common.alphaStrike.ASDamageVector;
import megamek.common.alphaStrike.ASRange;
import megamek.common.alphaStrike.AlphaStrikeElement;
import megamek.common.alphaStrike.conversion.ASConverter;
import megamek.common.force.Force;
import megamek.common.force.Forces;
import megamek.common.strategicBattleSystems.BaseFormationConverter;
import megamek.common.strategicBattleSystems.SBFUnit;
import megamek.common.strategicBattleSystems.SBFUnitConverter;
import mekhq.campaign.autoresolve.acar.SimulationContext;
import mekhq.campaign.autoresolve.component.Formation;

import java.util.ArrayList;

public class ForceToFormationConverter extends BaseFormationConverter<Formation> {

    public ForceToFormationConverter(Force force, SimulationContext game) {
        super(force, game, new Formation());
    }

    @Override
    public Formation convert() {
        Forces forces = game.getForces();
        for (Force subforce : forces.getFullSubForces(force)) {
            var thisUnit = new ArrayList<AlphaStrikeElement>();
            for (ForceAssignable entity : forces.getFullEntities(subforce)) {
                if (entity instanceof Entity entityCast) {
                    thisUnit.add(ASConverter.convertAndKeepRefs(entityCast));
                }
            }
            SBFUnit convertedUnit = new SBFUnitConverter(thisUnit, subforce.getName(), report).createSbfUnit();
            formation.addUnit(convertedUnit);
        }
        formation.setName(force.getName());
        formation.setStdDamage(setStdDamageForFormation(formation));
        return formation;
    }

    private ASDamageVector setStdDamageForFormation(Formation formation) {
        // Get the list of damage objects from the units in the formation
        var damages = formation.getUnits().stream().map(SBFUnit::getDamage).toList();
        var size = damages.size();

        // Initialize accumulators for the different damage types
        var l = 0;
        var m = 0;
        var s = 0;

        // Sum up the damage values for each type
        for (var damage : damages) {
            l += damage.getDamage(ASRange.LONG).damage;
            m += damage.getDamage(ASRange.MEDIUM).damage;
            s += damage.getDamage(ASRange.SHORT).damage;
        }
        return new ASDamageVector(
            new ASDamage(Math.ceil((double) s / size)),
            new ASDamage(Math.ceil((double) m / size)),
            new ASDamage(Math.ceil((double) l / size)),
            null,
            size,
            true);
    }

}
