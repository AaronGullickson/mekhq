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

package mekhq.campaign.unit.damage;

import megamek.common.*;

/**
 * @author Luana Coppio
 */
public class DamageApplierChooser {

    public enum EntityFinalState {
        ANY(false, false),
        CREW_MUST_SURVIVE(true, false),
        ENTITY_MUST_SURVIVE(false, true),
        CREW_AND_ENTITY_MUST_SURVIVE(true, true);

        final boolean crewMustSurvive;
        final boolean entityMustSurvive;

        EntityFinalState(boolean crewMustSurvive, boolean entityMustSurvive) {
            this.crewMustSurvive = crewMustSurvive;
            this.entityMustSurvive = entityMustSurvive;
        }
    }

    /**
     * Choose the correct DamageHandler for the given entity.
     * A damage handler is a class that handles applying damage on an entity, be it a Mek, Infantry, etc.
     * It can damage internal, armor, cause criticals, kill crew, set limbs as blown-off, can even destroy the entity,
     * @param entity the entity to choose the handler for
     * @return the correct DamageHandler for the given entity
     */
    public static DamageApplier<?> choose(Entity entity) {
        return choose(entity, EntityFinalState.ANY);
    }

    /**
     * Choose the correct DamageHandler for the given entity.
     * A damage handler is a class that handles applying damage on an entity, be it a Mek, Infantry, etc.
     * It can damage internal, armor, cause criticals, kill crew, set limbs as blown-off, can even destroy the entity,
     * This one also accepts parameters to indicate if the crew must survive and if the entity must survive.
     * @param entity the entity to choose the handler for
     * @param entityFinalState if the crew must survive and/or entity must survive
     * @return the correct DamageHandler for the given entity
     */
    public static DamageApplier<?> choose(
        Entity entity, EntityFinalState entityFinalState) {
        return choose(entity, entityFinalState.crewMustSurvive, entityFinalState.entityMustSurvive);
    }

    /**
     * Choose the correct DamageHandler for the given entity.
     * A damage handler is a class that handles applying damage on an entity, be it a Mek, Infantry, etc.
     * It can damage internal, armor, cause critical, kill crew, set limbs as blown-off, can even destroy the entity,
     * This one also accepts parameters to indicate if the crew must survive and if the entity must survive.
     * @param entity the entity to choose the handler for
     * @param crewMustSurvive if the crew must survive
     * @param entityMustSurvive if the entity must survive
     * @return the correct DamageHandler for the given entity
     */
    public static DamageApplier<?> choose(
        Entity entity, boolean crewMustSurvive, boolean entityMustSurvive) {

        if (entity instanceof Infantry) {
            return new InfantryDamageApplier((Infantry) entity);
        } else if (entity instanceof Mek) {
            return new MekDamageApplier((Mek) entity, crewMustSurvive, entityMustSurvive);
        } else if (entity instanceof GunEmplacement) {
            return new GunEmplacementDamageApplier((GunEmplacement) entity, crewMustSurvive, entityMustSurvive);
        } else if (entity instanceof Aero) {
            return new AeroDamageApplier((Aero) entity, crewMustSurvive, entityMustSurvive);
        }
        return new SimpleDamageApplier(entity, crewMustSurvive, entityMustSurvive);
    }

    /**
     * Automatically applies damage to the entity based on the "removal condition" provided.
     * The damage is calculated as being a percentage of the total armor of the unit, then it is transformed in a roll of dices
     * which the average roll is that amount, then the total damage is calculated and applied in clusters of 5 damage. It rolls a minimum of
     * 1 dice of damage.
     * The removal condition is a code that indicates why the entity is being removed from the game.
     * It will decide if the unit or entity must survive based on the type of removal condition.
     * The removal conditions are:
     *      * RETREAT: crew must survive, entity must survive, 80% of the total armor is applied as damage
     *      * SALVAGEABLE: crew may die, entity must be destroyed, 75% of the total armor is applied as damage
     *      * CAPTURED: crew must survive, entity must be destroyed, 33% of the total armor is applied as damage
     *      * EJECTED: crew must survive, entity must be destroyed, 33% of the total armor is applied as damage
     *      * DEVASTATED: crew may survive, entity must be destroyed, no damage applied
     *      * OTHER: crew may die, entity may be destroyed, 33% of the total armor applied as damage
     * The amount of damage applied present right now was decided arbitrarily and can be changed later, maybe even make it follow
     * a config file, client settings, etc.
     * @param entity the entity to choose the handler for
     * @param removalCondition the reason why the entity is being removed
     * @return the total amount of damage applied to the entity
     */
    public static int damageRemovedEntity(Entity entity, int removalCondition) {
        double targetDamage = switch (removalCondition) {
            case IEntityRemovalConditions.REMOVE_CAPTURED, IEntityRemovalConditions.REMOVE_EJECTED -> (double) (entity.getTotalOArmor() * (1 / (Compute.d6() + 1)));
            case IEntityRemovalConditions.REMOVE_DEVASTATED -> -1; // no damage is actually applied
            case IEntityRemovalConditions.REMOVE_IN_RETREAT -> entity.getTotalOArmor() * 0.8;
            case IEntityRemovalConditions.REMOVE_SALVAGEABLE -> entity.getTotalOArmor() * 0.75;
            default -> entity.getTotalOArmor() * 0.33;
        };
        var numberOfDices = Math.max(1, (int) (targetDamage / 6 / 0.6));
        var damage = Compute.d6(numberOfDices);
        var clusterSize = 5;

        var retreating = removalCondition == IEntityRemovalConditions.REMOVE_IN_RETREAT;
        var captured = removalCondition == IEntityRemovalConditions.REMOVE_CAPTURED;
        var ejected = removalCondition == IEntityRemovalConditions.REMOVE_EJECTED;
        var devastated = removalCondition == IEntityRemovalConditions.REMOVE_DEVASTATED;
        var salvageable = removalCondition == IEntityRemovalConditions.REMOVE_SALVAGEABLE;

        var crewMustSurvive = retreating || captured || ejected;
        var entityMustSurvive = !devastated && !salvageable && !ejected;

        return DamageApplierChooser.choose(entity, crewMustSurvive, entityMustSurvive)
            .applyDamageInClusters(damage, clusterSize);
    }

}
