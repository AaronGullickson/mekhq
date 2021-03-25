/*
* MegaMek - Copyright (C) 2020 - The MegaMek Team
*
* This program is free software; you can redistribute it and/or modify it under
* the terms of the GNU General Public License as published by the Free Software
* Foundation; either version 2 of the License, or (at your option) any later
* version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
* FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
* details.
*/

package mekhq.campaign.stratcon;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import megamek.common.Compute;
import megamek.common.Minefield;
import megamek.common.UnitType;
import megamek.common.annotations.Nullable;
import megamek.common.event.Subscribe;
import mekhq.MekHQ;
import mekhq.Utilities;
import mekhq.campaign.Campaign;
import mekhq.campaign.ResolveScenarioTracker;
import mekhq.campaign.againstTheBot.enums.AtBLanceRole;
import mekhq.campaign.event.NewDayEvent;
import mekhq.campaign.force.Force;
import mekhq.campaign.force.Lance;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.AtBDynamicScenario;
import mekhq.campaign.mission.AtBDynamicScenarioFactory;
import mekhq.campaign.mission.AtBScenario;
import mekhq.campaign.mission.Contract;
import mekhq.campaign.mission.Scenario;
import mekhq.campaign.mission.ScenarioForceTemplate;
import mekhq.campaign.mission.ScenarioMapParameters.MapLocation;
import mekhq.campaign.mission.ScenarioTemplate;
import mekhq.campaign.mission.ScenarioForceTemplate.ForceAlignment;
import mekhq.campaign.mission.atb.AtBScenarioModifier;
import mekhq.campaign.mission.atb.AtBScenarioModifier.EventTiming;
import mekhq.campaign.personnel.SkillType;
import mekhq.campaign.stratcon.StratconScenario.ScenarioState;
import mekhq.campaign.unit.Unit;

/**
 * This class contains "rules" logic for the AtB-Stratcon state
 * @author NickAragua
 */
public class StratconRulesManager {
    /**
     * What makes a particular lance eligible to be reinforcements for a scenario
     * @author NickAragua
     */
    public enum ReinforcementEligibilityType {
        /**
         * Nothing
         */
        None,

        /**
         * Lance is already deployed to the track
         */
        ChainedScenario,

        /**
         * We pay a support point or convert a victory point to a support point
         */
        SupportPoint,

        /**
         * The lance's deployment orders are "Fight"
         */
        FightLance
    }

    /**
     * This function potentially generates non-player-initiated scenarios for the given track.
     */
    public static void generateScenariosForTrack(Campaign campaign, AtBContract contract, StratconTrackState track) {
        // maps scenarios to force IDs
        List<StratconScenario> generatedScenarios = new ArrayList<>();
        boolean autoAssignLances = contract.getCommandRights() == AtBContract.COM_INTEGRATED;

        // get this list just so we have it available
        List<Integer> availableForceIDs = getAvailableForceIDs(campaign);
        Map<MapLocation, List<Integer>> sortedAvailableForceIDs = sortForcesByMapType(availableForceIDs, campaign);

        // make X rolls, where X is the number of required lances for the track
        // that's the chance to spawn a scenario.
        // if a scenario occurs, then we pick a random non-deployed lance and use it to drive the opfor generation later
        // once we've determined that scenarios occur, we loop through the ones that we generated
        // and use the random force to drive opfor generation (#required lances multiplies the BV budget of all
        for (int scenarioIndex = 0; scenarioIndex < track.getRequiredLanceCount(); scenarioIndex++) {
            // if we haven't already used all the player forces and are required to randomly generate a scenario
            if ((availableForceIDs.size() > 0) &&
                    (Compute.randomInt(100) <= track.getScenarioOdds())) {
                // pick random coordinates and force to drive the scenario
                int x = Compute.randomInt(track.getWidth());
                int y = Compute.randomInt(track.getHeight());

                StratconCoords scenarioCoords = new StratconCoords(x, y);

                int randomForceIndex = Compute.randomInt(availableForceIDs.size());
                int randomForceID = availableForceIDs.get(randomForceIndex);

                // remove the force from the available lists so we don't designate it as primary twice
                availableForceIDs.remove(randomForceIndex);

                // we want to remove the actual int with the value, not the value at the index
                sortedAvailableForceIDs.get(MapLocation.AllGroundTerrain).remove((Integer) randomForceID);
                sortedAvailableForceIDs.get(MapLocation.LowAtmosphere).remove((Integer) randomForceID);
                sortedAvailableForceIDs.get(MapLocation.Space).remove((Integer) randomForceID);

                // two scenarios on the same coordinates wind up increasing in size
                if (track.getScenarios().containsKey(scenarioCoords)) {
                    track.getScenarios().get(scenarioCoords).incrementRequiredPlayerLances();
                    assignAppropriateExtraForceToScenario(track.getScenarios().get(scenarioCoords), sortedAvailableForceIDs);
                    continue;
                }

                StratconScenario scenario = setupScenario(scenarioCoords, randomForceID, campaign, contract, track);
                generatedScenarios.add(scenario);

                // if we're auto-assigning lances, deploy the force to the track as well
                if (autoAssignLances) {
                    processForceDeployment(scenarioCoords, randomForceID, campaign, track, false);
                }
            }
        }

        // if under liaison command, pick a random scenario from the ones generated
        // to set as required and attach liaison
        if (contract.getCommandRights() == AtBContract.COM_LIAISON) {
        	StratconScenario randomScenario = Utilities.getRandomItem(generatedScenarios);
        	randomScenario.setRequiredScenario(true);
            setAttachedUnitsModifier(randomScenario, contract);
        }

        // now, we loop through all the scenarios we set up
        // and generate the opfors / events / etc
        // if not auto-assigning lances, we then back out the lance assignments.
        for (StratconScenario scenario : generatedScenarios) {
            AtBDynamicScenarioFactory.finalizeScenario(scenario.getBackingScenario(), contract, campaign);

            if(!autoAssignLances) {
                for(int forceID : scenario.getPrimaryPlayerForceIDs()) {
                    scenario.getBackingScenario().removeForce(forceID);
                }

                scenario.setCurrentState(ScenarioState.UNRESOLVED);
                track.addScenario(scenario);
            } else {
                commitPrimaryForces(campaign, contract, scenario, track);
            }
        }
    }

    /**
     * Deploys a force to the given coordinates on the given track as a result of explicit player action.
     */
    public static void deployForceToCoords(StratconCoords coords, int forceID, 
            Campaign campaign, AtBContract contract, StratconTrackState track, boolean sticky) {
        // the following things should happen:
        // 1. call to "process force deployment", which reveals fog of war in or around the coords, depending on force role
        // 2. if coords are a hostile facility, we get a facility mission
        // 3. if coords are empty, we *may* get a mission

        processForceDeployment(coords, forceID, campaign, track, sticky);

        // don't create a scenario on top of allied facilities
        StratconFacility facility = track.getFacility(coords);
        boolean isNonAlliedFacility = (facility != null) && (facility.getOwner() != ForceAlignment.Allied);
        boolean spawnScenario = (facility == null) && (Compute.randomInt(100) <= track.getScenarioOdds());

        if (isNonAlliedFacility || spawnScenario) {
            StratconScenario scenario = setupScenario(coords, forceID, campaign, contract, track);
            AtBDynamicScenarioFactory.finalizeScenario(scenario.getBackingScenario(), contract, campaign);
            commitPrimaryForces(campaign, contract, scenario, track);
        }
    }

    /**
     * Logic to set up a scenario
     */
    private static StratconScenario setupScenario(StratconCoords coords, int forceID, Campaign campaign, AtBContract contract, StratconTrackState track) {
        StratconScenario scenario = null;

        if (track.getFacilities().containsKey(coords)) {
            StratconFacility facility = track.getFacility(coords);
            boolean alliedFacility = facility.getOwner() == ForceAlignment.Allied;
            ScenarioTemplate template = StratconScenarioFactory.getFacilityScenario(alliedFacility);
            scenario = generateScenario(campaign, contract, track, forceID, coords, template);
            setupFacilityScenario(scenario, facility);
        } else {
            scenario = generateScenario(campaign, contract, track, forceID, coords);

            // we may generate a facility scenario randomly - if so, do the facility-related stuff
            // and add a new facility to the track
            if (scenario.getBackingScenario().getTemplate().isFacilityScenario()) {
                StratconFacility facility = scenario.getBackingScenario().getTemplate().isHostileFacility() ?
                        StratconFacilityFactory.getRandomHostileFacility() : StratconFacilityFactory.getRandomAlliedFacility();
                facility.setVisible(true);
                track.addFacility(coords, facility);
                setupFacilityScenario(scenario, facility);
            }
        }

        return scenario;
    }

    /**
     * carries out tasks relevant to facility scenarios
     */
    private static void setupFacilityScenario(StratconScenario scenario, StratconFacility facility) {
        // this includes:
        // for hostile facilities
        // - add a destroy objective (always the option to level the facility)
        // - add a capture objective (always the option to capture the facility)
        // - if so indicated by parameter, roll a random hostile facility objective and add it if not capture/destroy
        // for allied facilities
        // - add a defend objective (always the option to defend the facility)
        // - if so indicated by parameter, roll a random allied facility objective and add it if not defend
        AtBScenarioModifier objectiveModifier = null;
        boolean alliedFacility = facility.getOwner() == ForceAlignment.Allied;

        if (alliedFacility) {
            objectiveModifier = AtBScenarioModifier.getRandomAlliedFacilityModifier();
        } else {
            objectiveModifier = AtBScenarioModifier.getRandomHostileFacilityModifier();
        }

        if (objectiveModifier != null) {
            scenario.getBackingScenario().addScenarioModifier(objectiveModifier);
            scenario.getBackingScenario().setName(String.format("%s - %s - %s",
                    facility.getFacilityType(), alliedFacility ? "Allied" : "Hostile", objectiveModifier.getModifierName()));
        }

        // add the "fixed" hostile facility modifiers after the primary ones
        if (!alliedFacility) {
            for(AtBScenarioModifier modifier : AtBScenarioModifier.getRequiredHostileFacilityModifiers()) {
                if(!scenario.getBackingScenario().alreadyHasModifier(modifier)) {
                    scenario.getBackingScenario().addScenarioModifier(modifier);
                }
            }
        }
    }

    private static void processFacilityEffects(StratconTrackState track) {
        // TODO: these are "weekly"? effects that a stratcon facility has on the campaign/track state
        // currrently, that's
        // supply depot - allied - +1 sp
        // supply depot - hostile - +5% BV budget to all scenarios on track (shared modifier, so should be implemented there)
        // data center - allied - +1% star league cache contract (don't implement yet, but...)
        // data center - hostile - +5% scenario odds in track
        // industrial center - allied - all scenarios here have THIS IS COMING OUT OF YOUR PAYCHECK modifier
        // orbital defense - allied - sets 'no hostile aircraft' flag for track
        // orbital defense - hostile - sets 'no allied aircraft' flag for track
        // early warning system - allied - sets 'intercept allied base attacks' flag for track
        // early warning system - hostile - sets 'intercept hostile base attacks' flag for track
    }

    /**
     * Process the deployment of a force to the given coordinates on the given track.
     */
    public static void processForceDeployment(StratconCoords coords, int forceID, 
            Campaign campaign, StratconTrackState track, boolean sticky) {
        track.getRevealedCoords().add(coords);
        StratconFacility facility = track.getFacility(coords);
        if (facility != null) {
            facility.setVisible(true);
        }

        if (campaign.getLances().get(forceID).getRole() == AtBLanceRole.SCOUTING) {
            for(int direction = 0; direction < 6; direction++) {
                StratconCoords checkCoords = coords.translate(direction);

                facility = track.getFacility(checkCoords);
                if(facility != null) {
                    facility.setVisible(true);
                }

                track.getRevealedCoords().add(coords.translate(direction));
            }
        }

        // the force may be located in other places on the track - clear it out
        track.unassignForce(forceID);
        track.assignForce(forceID, coords, campaign.getLocalDate(), sticky);
    }

    /**
     * Worker function that processes the effects of deploying a reinforcement force to a scenario
     */
    public static boolean processReinforcementDeployment(ReinforcementEligibilityType reinforcementType,
            StratconCampaignState campaignState, StratconScenario scenario, Campaign campaign) {
        // if the force is already deployed to the track, we're done
        // if the force is a fight lance or we're using a support point
        // if there is an SP to burn, burn it and we're done
        // if there is a VP to burn, burn it and we're done
        // now, roll 2d6 + lance commander tactics
        // 9+ = deploy
        // 6+ = deploy, apply negative modifier to scenario
        // 2+ = fail to deploy, apply negative modifier to scenario; if fight lance, treat as 6+

        if ((reinforcementType == ReinforcementEligibilityType.FightLance) ||
                (reinforcementType == ReinforcementEligibilityType.SupportPoint)) {

            if (campaignState.getSupportPoints() > 0) {
                campaignState.useSupportPoint();
                return true;
            } else if (campaignState.getVictoryPoints() > 0) {
                campaignState.updateVictoryPoints(-1);
                return true;
            }

            int tactics = scenario.getBackingScenario().getLanceCommanderSkill(SkillType.S_TACTICS, campaign);
            int roll = Compute.d6(2);
            int result = roll + tactics;

            StringBuilder reportStatus = new StringBuilder();
            reportStatus.append(String.format("Attempting to reinforce scenario %s without SP/VP, roll 2d6 + %d: %d",
                    scenario.getName(), tactics, result));

            // fail to reinforce
            if ((result < 6) && (reinforcementType != ReinforcementEligibilityType.FightLance)) {
                reportStatus.append(" - reinforcement attempt failed.");
                campaign.addReport(reportStatus.toString());
                return false;
            // succeed but get an extra negative event added to the scenario
            } else if (result < 9) {
                MapLocation mapLocation = scenario.getScenarioTemplate().mapParameters.getMapLocation();
                AtBScenarioModifier scenarioModifier = AtBScenarioModifier.getRandomBattleModifier(mapLocation, false);

                // keep rolling until we get an applicable one
                // TODO: have the AtBScenarioModifier sort these out instead for performance?
                while (scenarioModifier.getEventTiming() != EventTiming.PostForceGeneration) {
                    scenarioModifier = AtBScenarioModifier.getRandomBattleModifier(mapLocation, false);
                }

                scenarioModifier.processModifier(scenario.getBackingScenario(), campaign, EventTiming.PostForceGeneration);

                reportStatus.append(String.format(" - reinforcement attempt succeeded; extra negative modifier (%s) applied to scenario.",
                        scenarioModifier.getModifierName()));
                campaign.addReport(reportStatus.toString());
                return true;
            // succeed without reservation
            } else {
                reportStatus.append(" - reinforcement attempt succeeded;");
                campaign.addReport(reportStatus.toString());
                return true;
            }
        } else if (reinforcementType == ReinforcementEligibilityType.None) {
            return false;
        }

        return true;
    }

    /**
     * Assigns a force to the scenario such that the majority of the force can be deployed
     */
    private static void assignAppropriateExtraForceToScenario(StratconScenario scenario,
            Map<MapLocation, List<Integer>> sortedAvailableForceIDs) {
        // the goal of this function is to avoid assigning ground units to air battles
        // and ground units/conventional fighters to space battle

        List<MapLocation> mapLocations = new ArrayList<>();
        mapLocations.add(MapLocation.Space); // can always add ASFs

        MapLocation scenarioMapLocation = scenario.getScenarioTemplate().mapParameters.getMapLocation();

        if (scenarioMapLocation == MapLocation.LowAtmosphere) {
            mapLocations.add(MapLocation.LowAtmosphere); // can add conventional fighters to ground or low atmo battles
        }

        if ((scenarioMapLocation == MapLocation.AllGroundTerrain) ||
                (scenarioMapLocation == MapLocation.SpecificGroundTerrain)) {
            mapLocations.add(MapLocation.AllGroundTerrain); // can only add ground units to ground battles
        }

        MapLocation selectedLocation = mapLocations.get(Compute.randomInt(mapLocations.size()));
        List<Integer> forceIDs = sortedAvailableForceIDs.get(selectedLocation);
        int forceIndex = Compute.randomInt(forceIDs.size());
        int forceID = forceIDs.get(forceIndex);
        forceIDs.remove(forceIndex);

        scenario.addPrimaryForce(forceID);
    }

    /**
     * Worker function that "locks in" a scenario -
     * Adds it to the campaign so it's visible in the briefing room,
     * adds it to the track
     *
     */
    public static void commitPrimaryForces(Campaign campaign, AtBContract contract, StratconScenario scenario, StratconTrackState trackState) {
        // order of operations is important here, we need a valid scenario ID prior to adding the scenario to the track.
        campaign.addScenario(scenario.getBackingScenario(), contract);
        scenario.setBackingScenarioID(scenario.getBackingScenario().getId());
        trackState.addScenario(scenario);

        // set up dates for the scenario if doesn't have them already
        if (scenario.getDeploymentDate() == null) {
            scenario.setDeploymentDate(campaign.getLocalDate());
        }

        if (scenario.getActionDate() == null) {
            scenario.setActionDate(campaign.getLocalDate());
        }

        if(scenario.getReturnDate() == null) {
            scenario.setReturnDate(campaign.getLocalDate().plusDays(trackState.getDeploymentTime()));
        }

        // set the # of rerolls based on the actual lance assigned.
        int tactics = scenario.getBackingScenario().getLanceCommanderSkill(SkillType.S_TACTICS, campaign);
        scenario.getBackingScenario().setRerolls(tactics);
        // The number of defensive points available to a force entering a scenario is
        // 2 x tactics. By default, those points are spent on conventional minefields.
        if (commanderLanceHasDefensiveAssignment(scenario.getBackingScenario(), campaign)) {
            scenario.setNumDefensivePoints(tactics * 2);
            scenario.updateMinefieldCount(Minefield.TYPE_CONVENTIONAL, tactics * 2);
        }

        for (int forceID : scenario.getPrimaryPlayerForceIDs()) {
            Force force = campaign.getForce(forceID);
            force.clearScenarioIds(campaign, true);
            force.setScenarioId(scenario.getBackingScenarioID());
        }

        scenario.commitPrimaryForces();
    }

    /**
     * Utility method to determine if the current scenario's force commander
     * is on defence
     */
    private static boolean commanderLanceHasDefensiveAssignment(AtBDynamicScenario scenario, Campaign campaign) {
        Unit commanderUnit = scenario.getLanceCommander(campaign).getUnit();
        Lance lance = campaign.getLances().get(commanderUnit.getForceId());

        if ((lance != null) && (lance.getRole() == AtBLanceRole.DEFENCE)) {
            return true;
        }

        return false;
    }

    /**
     * A hackish worker function that takes the given list of force IDs and
     * separates it into three sets;
     * one of forces that can be "primary" on a ground map
     * one of forces that can be "primary" on an atmospheric map
     * one of forces that can be "primary" in a space map
     * @param forceIDs List of force IDs to check
     * @return Sorted hash map
     */
    private static Map<MapLocation, List<Integer>> sortForcesByMapType(List<Integer> forceIDs, Campaign campaign) {
        Map<MapLocation, List<Integer>> retVal = new HashMap<>();

        retVal.put(MapLocation.AllGroundTerrain, new ArrayList<>());
        retVal.put(MapLocation.LowAtmosphere, new ArrayList<>());
        retVal.put(MapLocation.Space, new ArrayList<>());

        for (int forceID : forceIDs) {
            switch(campaign.getForce(forceID).getPrimaryUnitType(campaign)) {
            case UnitType.BATTLE_ARMOR:
            case UnitType.INFANTRY:
            case UnitType.MEK:
            case UnitType.TANK:
            case UnitType.PROTOMEK:
            case UnitType.VTOL:
                retVal.get(MapLocation.AllGroundTerrain).add(forceID);
                break;
            case UnitType.AERO:
                retVal.get(MapLocation.Space).add(forceID);
                // intentional fallthrough here, ASFs can go to atmospheric maps too
            case UnitType.CONV_FIGHTER:
                retVal.get(MapLocation.LowAtmosphere).add(forceID);
                break;
            }
        }


        return retVal;
    }

    /**
     * Determine whether the user should be nagged about unresolved scenarios on AtB Stratcon tracks.
     * @param campaign Campaign to check.
     * @return An informative string containing the reasons the user was nagged.
     */
    public static String nagUnresolvedContacts(Campaign campaign) {
        StringBuilder sb = new StringBuilder();

        // check every track attached to an active contract for unresolved scenarios
        // to which the player must deploy forces today
        for (Contract contract : campaign.getActiveContracts()) {
            if(contract instanceof AtBContract) {
                for(StratconTrackState track : ((AtBContract) contract).getStratconCampaignState().getTracks()) {
                    for(StratconScenario scenario : track.getScenarios().values()) {
                        if(scenario.getCurrentState() == ScenarioState.UNRESOLVED &&
                                campaign.getLocalDate().equals(scenario.getDeploymentDate())) {
                            // "scenario name, track name"
                            sb.append(String.format("%s, %s\n", scenario.getName(), track.getDisplayableName()));
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * Worker function that generates stratcon scenario at the given coords, for the given force, on the given track.
     * Also registers it with the track and campaign.
     */
    private static StratconScenario generateScenario(Campaign campaign, AtBContract contract, StratconTrackState track,
            int forceID, StratconCoords coords) {
        int unitType = campaign.getForce(forceID).getPrimaryUnitType(campaign);
        ScenarioTemplate template = StratconScenarioFactory.getRandomScenario(unitType);
        // useful for debugging specific scenario types
        //ScenarioTemplate template = StratconScenarioFactory.getSpecificScenario("Allied Facility.xml");

        return generateScenario(campaign, contract, track, forceID, coords, template);
    }

    /**
     * Worker function that generates stratcon scenario at the given coords, for the given force, on the given track,
     * using the given template. Also registers it with the track and campaign.
     */
    static StratconScenario generateScenario(Campaign campaign, AtBContract contract, StratconTrackState track,
            int forceID, StratconCoords coords, ScenarioTemplate template) {
        StratconScenario scenario = new StratconScenario();
        scenario.setBackingScenario(AtBDynamicScenarioFactory.initializeScenarioFromTemplate(template, contract, campaign));
        scenario.setCoords(coords);

        // do an appropriate allied force if the contract calls for it
        // do any attached or integrated units
        setAlliedForceModifier(scenario, contract);
        setAttachedUnitsModifier(scenario, contract);
        applyFacilityModifiers(scenario, track, coords);
        applyGlobalModifiers(scenario, contract.getStratconCampaignState());

        if ((contract.getCommandRights() == AtBContract.COM_HOUSE) ||
                (contract.getCommandRights() == AtBContract.COM_INTEGRATED)) {
            scenario.setRequiredScenario(true);
        }

        AtBDynamicScenarioFactory.setScenarioModifiers(scenario.getBackingScenario());
        scenario.setCurrentState(ScenarioState.UNRESOLVED);
        setScenarioDates(track, campaign, scenario);

        // register the scenario with the campaign and the track it's generated on
        if (forceID > Force.FORCE_NONE) {
            scenario.addPrimaryForce(forceID);
        }

        return scenario;
    }

    /**
     * Apply global scenario modifiers from campaign state to given scenario.
     */
    private static void applyGlobalModifiers(StratconScenario scenario, StratconCampaignState campaignState) {
        for (String modifierName : campaignState.getGlobalScenarioModifiers()) {
            AtBScenarioModifier modifier = AtBScenarioModifier.getScenarioModifier(modifierName);

            if (modifier == null) {
                MekHQ.getLogger().error(String.format("Modifier %s not found; ignoring", modifierName));
                continue;
            }

            scenario.getBackingScenario().addScenarioModifier(modifier);
        }
    }

    /**
     * Applies scenario modifiers from the current track to the given scenario.
     * @param scenario
     * @param track
     */
    private static void applyFacilityModifiers(StratconScenario scenario, StratconTrackState track, StratconCoords coords) {
        // loop through all the facilities on the track
        // if a facility has been revealed, then it has a 100% chance to apply its effect
        // if a facility has not been revealed, then it has a x% chance to apply its effect
        // if a facility is on the the scenario coordinates the it applies the local effects
        for (StratconCoords facilityCoords : track.getFacilities().keySet()) {
            boolean scenarioAtFacility = facilityCoords.equals(coords);
            StratconFacility facility = track.getFacilities().get(facilityCoords);
            List<String> modifierIDs = new ArrayList<>();

            if (scenarioAtFacility) {
                modifierIDs = facility.getLocalModifiers();
            } else if (facility.isVisible() || (Compute.randomInt(100) <= facility.getAggroRating())) {
                modifierIDs = facility.getSharedModifiers();
            }

            for (String modifierID : modifierIDs) {
                AtBScenarioModifier modifier = AtBScenarioModifier.getScenarioModifier(modifierID);
                if(modifier == null) {
                    MekHQ.getLogger().error(String.format("Modifier %s not found for facility %s", modifierID, facility.getFormattedDisplayableName()));
                    continue;
                }

                scenario.getBackingScenario().addScenarioModifier(modifier);
            }
        }
    }

    /**
     * Set up the appropriate primary allied force modifier, if any
     * @param contract The scenario's contract.
     */
    private static void setAlliedForceModifier(StratconScenario scenario, AtBContract contract) {
        int alliedUnitOdds = 0;

        // first, we determine the odds of having an allied unit present
        // TODO: move this override out to the contract definition
        if (contract.getMissionType() == AtBContract.MT_RELIEFDUTY) {
            alliedUnitOdds = 50;
        } else {
            switch(contract.getCommandRights()) {
            case AtBContract.COM_INTEGRATED:
                alliedUnitOdds = 50;
                break;
            case AtBContract.COM_HOUSE:
                alliedUnitOdds = 30;
                break;
            case AtBContract.COM_LIAISON:
                alliedUnitOdds = 10;
                break;
            }
        }

        AtBDynamicScenario backingScenario = scenario.getBackingScenario();

        // if an allied unit is present, then we want to make sure that it's ground units
        // for ground battles
        if (Compute.randomInt(100) <= alliedUnitOdds) {
            if((backingScenario.getTemplate().mapParameters.getMapLocation() == MapLocation.LowAtmosphere) ||
               (backingScenario.getTemplate().mapParameters.getMapLocation() == MapLocation.Space)) {
                backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_ALLIED_AIR_UNITS));
            } else {
                backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_ALLIED_GROUND_UNITS));
            }
        }
    }

    /**
     * Set the 'attached' units modifier for the current scenario (integrated, house, liaison),
     * and make sure we're not deploying ground units to an air scenario
     * @param contract The scenario's contract
     */
    public static void setAttachedUnitsModifier(StratconScenario scenario, AtBContract contract) {
        AtBDynamicScenario backingScenario = scenario.getBackingScenario();
        boolean airBattle = (backingScenario.getTemplate().mapParameters.getMapLocation() == MapLocation.LowAtmosphere) ||
                (backingScenario.getTemplate().mapParameters.getMapLocation() == MapLocation.Space);

        // if we're on cadre duty, we're getting three trainees, period
        if (contract.getMissionType() == AtBContract.MT_CADREDUTY) {
            if (airBattle) {
                backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_TRAINEES_AIR));
            } else {
                backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_TRAINEES_GROUND));
            }
            return;
        }

        // if we're under non-independent command rights, a supervisor may come along
        switch (contract.getCommandRights()) {
            case AtBContract.COM_INTEGRATED:
                if (airBattle) {
                    backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_INTEGRATED_UNITS_AIR));
                } else {
                    backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_INTEGRATED_UNITS_GROUND));
                }
                break;
            case AtBContract.COM_HOUSE:
                if (airBattle) {
                    backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_HOUSE_CO_AIR));
                } else {
                    backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_HOUSE_CO_GROUND));
                }
                break;
            case AtBContract.COM_LIAISON:
                if (scenario.isRequiredScenario()) {
                    if(airBattle) {
                        backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_LIAISON_AIR));
                    } else {
                        backingScenario.addScenarioModifier(AtBScenarioModifier.getScenarioModifier(AtBScenarioModifier.SCENARIO_MODIFIER_LIAISON_GROUND));
                    }
                }
                break;
        }
    }

    /**
     * Worker function that sets scenario deploy/battle/return dates based on the track's properties and current campaign date
     */
    private static void setScenarioDates(StratconTrackState track, Campaign campaign, StratconScenario scenario) {
     // set up deployment day, battle day, return day here
        // safety code to prevent attempts to generate random int with upper bound of 0 which is apparently illegal
        int deploymentDay = track.getDeploymentTime() < 7 ? Compute.randomInt(7 - track.getDeploymentTime()) : 0;
        int battleDay = deploymentDay + (track.getDeploymentTime() > 0 ? Compute.randomInt(track.getDeploymentTime()) : 0);
        int returnDay = deploymentDay + track.getDeploymentTime();

        LocalDate deploymentDate = campaign.getLocalDate();
        deploymentDate.plusDays(deploymentDay);
        LocalDate battleDate = campaign.getLocalDate();
        deploymentDate.plusDays(battleDay);
        LocalDate returnDate = campaign.getLocalDate();
        returnDate.plusDays(returnDay);

        scenario.setDeploymentDate(deploymentDate);
        scenario.setActionDate(battleDate);
        scenario.setReturnDate(returnDate);
    }

    /**
     * Helper function that determines if the unit type specified in the given scenario force template
     * would start out airborne on a ground map (hot dropped units aside)
     */
    private static boolean unitTypeIsAirborne(ScenarioForceTemplate template) {
        int unitType = template.getAllowedUnitType();

        return (unitType == UnitType.AERO ||
                unitType == UnitType.CONV_FIGHTER ||
                unitType == UnitType.DROPSHIP ||
                unitType == ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_MIX) &&
                template.getStartingAltitude() > 0;
    }

    /**
     * Determines whether the force in question has the same primary unit type as the force template.
     * @return Whether or not the unit types match.
     */
    public static boolean forceCompositionMatchesDeclaredUnitType(int primaryUnitType, int unitType, boolean reinforcements) {
        // special cases are "ATB_MIX" and "ATB_AERO_MIX", which encompass multiple unit types
        if (unitType == ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_MIX) {
            // "AtB mix" is usually ground units, but air units can sub in
            return primaryUnitType == UnitType.MEK ||
                    primaryUnitType == UnitType.TANK ||
                    primaryUnitType == UnitType.INFANTRY ||
                    primaryUnitType == UnitType.BATTLE_ARMOR ||
                    primaryUnitType == UnitType.PROTOMEK ||
                    primaryUnitType == UnitType.VTOL ||
                    (primaryUnitType == UnitType.AERO) && reinforcements ||
                    (primaryUnitType == UnitType.CONV_FIGHTER) && reinforcements;
        } else if (unitType == ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_AERO_MIX) {
            return primaryUnitType == UnitType.AERO ||
                    primaryUnitType == UnitType.CONV_FIGHTER;
        } else {
            return primaryUnitType == unitType;
        }
    }

    /**
     * This is a set of all force IDs for forces that can be deployed to a scenario.
     * @param campaign Current campaign
     * @return Set of available force IDs.
     */
    public static List<Integer> getAvailableForceIDs(Campaign campaign) {
        List<Integer> retVal = new ArrayList<>();

        // first, we gather a set of all forces that are already deployed to a track so we eliminate those later
        Set<Integer> forcesInTracks = new HashSet<>();
        for (Contract contract : campaign.getActiveContracts()) {
            if(contract instanceof AtBContract) {
                for(StratconTrackState track : ((AtBContract) contract).getStratconCampaignState().getTracks()) {
                    forcesInTracks.addAll(track.getAssignedForceCoords().keySet());
                }
            }
        }

        // now, we get all the forces that qualify as "lances", and filter out those that are
        // deployed to a scenario and not in a track already
        for (int key : campaign.getLances().keySet()) {
            Force force = campaign.getForce(key);
            if (force != null &&
                    !force.isDeployed() &&
                    !forcesInTracks.contains(force.getId())) {
                retVal.add(force.getId());
            }
        }

        return retVal;
    }

    /**
     * This is a list of all force IDs for forces that can be deployed to a scenario in the given force template
     * a) have not been assigned to a track
     * b) are combat-capable
     * c) are not deployed to a scenario
     * d) if attempting to deploy as reinforcements, haven't already failed to deploy
     */
    public static List<Integer> getAvailableForceIDs(int unitType, Campaign campaign, StratconTrackState currentTrack,
            boolean reinforcements, @Nullable StratconScenario currentScenario) {
        List<Integer> retVal = new ArrayList<>();

        Set<Integer> forcesInTracks = new HashSet<>();
        // assemble a set of all force IDs that are currently assigned to tracks that are not this one
        for (Contract contract : campaign.getActiveContracts()) {
            if (contract instanceof AtBContract) {
                for (StratconTrackState track : ((AtBContract) contract).getStratconCampaignState().getTracks()) {
                    if ((track != currentTrack) || !reinforcements) {
                        forcesInTracks.addAll(track.getAssignedForceCoords().keySet());
                    }
                }
            }
        }
        
        // if there's an existing scenario and we're doing reinforcements,
        // prevent forces that failed to deploy from trying to deploy again
        if (reinforcements && (currentScenario != null)) {
            forcesInTracks.addAll(currentScenario.getFailedReinforcements());
        }

        for (int key : campaign.getLances().keySet()) {
            Force force = campaign.getForce(key);

            if (force == null) {
                continue;
            }

            int primaryUnitType = force.getPrimaryUnitType(campaign);
            if (!force.isDeployed() &&
                    (force.getScenarioId() <= 0) &&
                    !force.getUnits().isEmpty() &&
                    !forcesInTracks.contains(force.getId()) &&
                    forceCompositionMatchesDeclaredUnitType(primaryUnitType, unitType, reinforcements)) {
                retVal.add(force.getId());
            }
        }

        return retVal;
    }

    /**
     * Returns a list of individual units eligible for deployment in scenarios run by "Defend" lances
     * @param campaign
     * @return List of unit IDs.
     */
    public static List<Unit> getEligibleDefensiveUnits(Campaign campaign) {
        List<Unit> retVal = new ArrayList<>();

        for (Unit u : campaign.getUnits()) {
            // "defensive" units are infantry, battle armor and (Weisman help you) gun emplacements
        	// and also said unit should be intact/alive/etc
        	boolean isEligibleInfantry = 
        			((u.getEntity().getUnitType() == UnitType.INFANTRY) ||
                    (u.getEntity().getUnitType() == UnitType.BATTLE_ARMOR)) &&
                    !u.isUnmanned();
        	
        	boolean isEligibleGunEmplacement = u.getEntity().getUnitType() == UnitType.GUN_EMPLACEMENT;
        	
        	if ((isEligibleInfantry || isEligibleGunEmplacement) && 
            		!u.isDeployed() &&
                    !u.isMothballed() &&
                    u.isFunctional()) {

                // this is a little inefficient, but probably there aren't too many active AtB contracts at a time
                for (Contract contract : campaign.getActiveContracts()) {
                    if((contract instanceof AtBContract) &&
                            ((AtBContract) contract).getStratconCampaignState().isForceDeployedHere(u.getForceId())) {
                        continue;
                    }
                }

                retVal.add(u);
            }
        }

        return retVal;
    }

    /**
     * Determines what rules to use when deploying a force for reinforcements to the given track.
     */
    public static ReinforcementEligibilityType getReinforcementType(int forceID,
            StratconTrackState trackState, Campaign campaign) {
        // if the force is currently deployed to the track, it'll be able to deploy "for free"
        if (trackState.isForceDeployed(forceID)) {
            return ReinforcementEligibilityType.ChainedScenario;
        }

        // if the force is in 'fight' stance, it'll be able to deploy using 'fight lance' rules
        if (campaign.getLances().containsKey(forceID) &&
                campaign.getLances().get(forceID).getRole() == AtBLanceRole.FIGHTING) {
            return ReinforcementEligibilityType.FightLance;
        }

        // if the force is deployed elsewhere, it cannot be deployed as reinforcements
        for (Contract contract : campaign.getActiveContracts()) {
            if(contract instanceof AtBContract) {
                for(StratconTrackState track : ((AtBContract) contract).getStratconCampaignState().getTracks()) {
                    if(track != trackState && track.getAssignedForceCoords().containsKey(forceID)) {
                        return ReinforcementEligibilityType.None;
                    }
                }
            }
        }

        // otherwise, the force requires support points / vps to deploy
        return ReinforcementEligibilityType.SupportPoint;
    }

    /**
     * Removes the facility associated with the given scenario from the relevant track/
     */
    public static void updateFacilityForScenario(AtBScenario scenario, AtBContract contract, boolean destroy, boolean capture) {
        if (contract.getStratconCampaignState() == null) {
            return;
        }

        // this is kind of kludgy, but there's currently no way to link a scenario back to its backing scenario
        // TODO: introduce mapping in contract or at least trackstate
        // basically, we're looping through all scenarios on all the contract's tracks
        // if we find one with the same ID as the one being resolved, that's our facility: get rid of it.
        for (StratconTrackState trackState : contract.getStratconCampaignState().getTracks()) {
            for (StratconCoords coords : trackState.getScenarios().keySet()) {
                StratconScenario potentialScenario = trackState.getScenario(coords);
                if (potentialScenario.getBackingScenarioID() == scenario.getId()) {

                    if (destroy) {
                        trackState.removeFacility(coords);
                    } else {
                        StratconFacility facility = trackState.getFacility(coords);

                        if (facility == null) {
                            continue;
                        }

                        if (capture) {
                            facility.incrementOwnershipChangeScore();
                        } else {
                            facility.decrementOwnershipChangeScore();
                        }
                    }

                    break;
                }
            }
        }
    }

    /**
     * Processes completion of a Stratcon scenario, if the given tracker is associated
     * with a stratcon-enabled mission. Intended to be called after ResolveScenarioTracker.finish() has been invoked.
     */
    public static void processScenarioCompletion(ResolveScenarioTracker rst) {
        if (rst.getMission() instanceof AtBContract) {
            StratconCampaignState campaignState = ((AtBContract) rst.getMission()).getStratconCampaignState();
            if (campaignState == null) {
                return;
            }

            for (StratconTrackState track : campaignState.getTracks()) {
                if (track.getBackingScenariosMap().containsKey(rst.getScenario().getId())) {
                    // things that may potentially happen:
                    // scenario is removed from track - implemented
                    // track gets remaining forces added to reinforcement pool
                    // facility gets remaining forces stored in reinforcement pool
                    // process VP and SO

                    StratconScenario scenario = track.getBackingScenariosMap().get(rst.getScenario().getId());


                    StratconFacility facility = track.getFacility(scenario.getCoords());

                    if ((facility != null) && (facility.getOwnershipChangeScore() > 0)) {
                        if (facility.getOwner() == ForceAlignment.Allied) {
                            facility.setOwner(ForceAlignment.Opposing);
                        } else {
                            facility.setOwner(ForceAlignment.Allied);
                        }
                    }

                    boolean victory = rst.getScenario().getStatus() == Scenario.S_VICTORY ||
                            rst.getScenario().getStatus() == Scenario.S_MVICTORY;

                    if (scenario.isRequiredScenario()) {
                        campaignState.updateVictoryPoints(victory ? 1 : -1);
                    }

                    if (scenario.isStrategicObjective()) {
                        if (campaignState.strategicObjectivesBehaveAsVPs()) {
                            campaignState.updateVictoryPoints(victory ? 1 : -1);
                        } else {
                            campaignState.incrementStrategicObjectiveCompletedCount();
                        }
                    }

                    processTrackForceReturnDates(track, rst.getCampaign().getLocalDate());

                    track.removeScenario(scenario);
                    break;
                }
            }
        }
    }

    /**
     * Worker function that goes through a track and undeploys any forces where the
     * return date is on or before the given date.
     */
    public static void processTrackForceReturnDates(StratconTrackState track, LocalDate date) {
        List<Integer> forcesToUndeploy = new ArrayList<>();

        // for each force on the track, if the return date is today or in the past,
        // "return to base", unless it's been told to stay in the field
        for (int forceID : track.getAssignedForceReturnDates().keySet()) {
            if ((track.getAssignedForceReturnDates().get(forceID).equals(date) ||
                    track.getAssignedForceReturnDates().get(forceID).isBefore(date)) &&
                    !track.getStickyForces().contains(forceID)) {
                forcesToUndeploy.add(forceID);
            }
        }

        for (int forceID : forcesToUndeploy) {
            track.unassignForce(forceID);
        }
    }

    /**
     * Processes an ignored Stratcon scenario
     */
    public static void processIgnoredScenario(StratconScenario scenario, StratconCampaignState campaignState) {
        for (StratconTrackState track : campaignState.getTracks()) {
            if (track.getScenarios().containsKey(scenario.getCoords())) {
                // subtract VP if scenario is 'required'
                if (scenario.isRequiredScenario()) {
                    campaignState.updateVictoryPoints(-1);
                }

                StratconFacility localFacility = track.getFacility(scenario.getCoords());
                if (localFacility != null) {
                    // if the ignored scenario was on top of an allied facility
                    // then it'll get captured, and the player will possibly lose a SO
                    if (localFacility.getOwner() == ForceAlignment.Allied) {
                        localFacility.setOwner(ForceAlignment.Opposing);

                        if (localFacility.isStrategicObjective()) {
                            campaignState.decrementStrategicObjectiveCompletedCount();
                        }
                    }
                } else {
                    // if it's an open-field
                    // move scenario towards nearest allied facility
                    StratconCoords closestAlliedFacilityCoords = track.findClosestAlliedFacilityCoords(scenario.getCoords());

                    if (closestAlliedFacilityCoords != null) {
                        StratconCoords newCoords = scenario.getCoords().translate(scenario.getCoords().direction(closestAlliedFacilityCoords));
                        scenario.setCoords(newCoords);
                        //scenario.setDeploymentDate(scenario.getDeploymentDate().plusDays(daysToAdd));

                        // TODO: if the allied facility is in the new coords, replace this scenario
                        // with a facility defense, with the opfor coming directly from all hostiles assigned to this scenario

                        scenario.setCurrentState(ScenarioState.UNRESOLVED);
                    } else {
                        // TODO: if there's no allied facilities here, add its forces to track reinforcement pool

                    }
                }

                // either way, it's gone
                track.removeScenario(scenario);
            }
        }
    }

    public void startup() {
        MekHQ.registerHandler(this);
    }

    /**
     * Event handler for the new day event.
     */
    @Subscribe
    public void handleNewDay(NewDayEvent ev) {
    	// don't do any of this if StratCon isn't turned on
    	if (!ev.getCampaign().getCampaignOptions().getUseStratCon()) {
    		return;
    	}
    	
        boolean isMonday = ev.getCampaign().getLocalDate().getDayOfWeek() == DayOfWeek.MONDAY;


        // run scenario generation routine for every track attached to an active contract
        for (Contract contract : ev.getCampaign().getActiveContracts()) {
            StratconCampaignState campaignState = ((AtBContract) contract).getStratconCampaignState();

            if ((contract instanceof AtBContract) &&
            		contract.isActiveOn(ev.getCampaign().getLocalDate()) &&
            		(campaignState != null)) {
                for (StratconTrackState track : ((AtBContract) contract).getStratconCampaignState().getTracks()) {
                    // loop through scenarios - if we haven't deployed in time, fail it and apply consequences
                    for (StratconScenario scenario : track.getScenarios().values()) {
                        if (scenario.getDeploymentDate().isBefore(ev.getCampaign().getLocalDate())) {
                            processIgnoredScenario(scenario, campaignState);
                        }
                    }

                    // on monday, generate new scenarios and decay modifiers
                    if (isMonday) {
                        generateScenariosForTrack(ev.getCampaign(), (AtBContract) contract, track);

                        //
                    }

                    // check if some of the forces have finished deployment
                    processTrackForceReturnDates(track, ev.getCampaign().getLocalDate());
                }
            }
        }
    }

    public void shutdown() {
        MekHQ.unregisterHandler(this);
    }
}
