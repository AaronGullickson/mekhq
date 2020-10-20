package mekhq.campaign.stratcon;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import mekhq.adapter.DateAdapter;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.AtBDynamicScenario;
import mekhq.campaign.mission.AtBScenario;
import mekhq.campaign.mission.ScenarioForceTemplate;
import mekhq.campaign.mission.ScenarioTemplate;

/**
 * Class that handles scenario metadata and interaction at the StratCon level
 * @author NickAragua
 *
 */
public class StratconScenario implements IStratconDisplayable {
    public enum ScenarioState {
        NONEXISTENT,
        UNRESOLVED,
        PRIMARY_FORCES_COMMITTED,
        REINFORCEMENTS_COMMITTED,
        COMPLETED,
        IGNORED,
        DEFEATED;
    }
    
    private final static Map<ScenarioState, String> scenarioStateNames;
    
    static {
        scenarioStateNames = new HashMap<>();
        scenarioStateNames.put(ScenarioState.NONEXISTENT, "Shouldn't be seen");
        scenarioStateNames.put(ScenarioState.UNRESOLVED, "Unresolved");
        scenarioStateNames.put(ScenarioState.PRIMARY_FORCES_COMMITTED, "Primary forces committed");
        scenarioStateNames.put(ScenarioState.COMPLETED, "Victory");
        scenarioStateNames.put(ScenarioState.IGNORED, "Ignored");
        scenarioStateNames.put(ScenarioState.DEFEATED, "Defeat");
    }
    
    private AtBDynamicScenario backingScenario;
    
    private int backingScenarioID; 
    private ScenarioState currentState = ScenarioState.UNRESOLVED;
    private int requiredPlayerLances;
    private boolean requiredScenario;
    private boolean isStrategicObjective;
    private LocalDate deploymentDate;
    private LocalDate actionDate;
    private LocalDate returnDate;
    private StratconCoords coords;
    private int numDefensivePoints;

    /**
     * Add a force to the backing scenario. Do our best to add the force as a "primary" force, as defined in the scenario template.
     * @param forceID ID of the force to add.
     */
    public void addPrimaryForce(int forceID) {
        backingScenario.addForce(forceID, ScenarioForceTemplate.PRIMARY_FORCE_TEMPLATE_ID);
    }
    
    /**
     * Add a force to the backing scenario, trying to associate it with the given template.
     * @param forceID
     * @param templateID
     */
    public void addForce(int forceID, String templateID) {
        backingScenario.addForce(forceID, templateID);
    }
    
    /**
     * Add an individual unit to the backing scenario, trying to associate it with the given template.
     * @param unitID
     * @param templateID
     */
    public void addUnit(UUID unitID, String templateID) {
        backingScenario.addUnit(unitID, templateID);
    }
    
    public void clearReinforcements() {
        for(int forceID : backingScenario.getForceIDs()) {
            if(!backingScenario.getPlayerForceTemplates().containsKey(forceID)) {
                backingScenario.removeForce(forceID);
            }
        }
    }

    public List<Integer> getAssignedForces() {
        return backingScenario.getForceIDs();
    }
    
    public List<Integer> getPrimaryPlayerForceIDs() {
        return backingScenario.getPrimaryPlayerForceIDs();
    }
    
    /**
     * This method triggers the performance of all operations specific to this scenario 
     * that should occur after the player has committed the primary forces to this scenario.
     */
    public void commitPrimaryForces(Campaign campaign, AtBContract contract) {
        currentState = ScenarioState.PRIMARY_FORCES_COMMITTED;
    }

    public ScenarioState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(ScenarioState state) {
        currentState = state;
    }

    public String getInfo() {
        return getInfo(true, true);
    }
    
    public String getInfo(boolean includeForces, boolean html) {
        StringBuilder stateBuilder = new StringBuilder();

        if(this.isStrategicObjective) {
            stateBuilder.append("<span color='red'>Contract objective located</span>").append(html ? "<br/>" : "");
        }
        
        stateBuilder.append(backingScenario.getName());
        stateBuilder.append(html ? "<br/>" : "");
        stateBuilder.append(backingScenario.getTemplate().shortBriefing);
        stateBuilder.append(html ? "<br/>" : "");
        
        if(this.isRequiredScenario()) {
            stateBuilder.append("<span color='red'>Deployment required by contract</span>").append(html ? "<br/>" : "");
        }
        
        stateBuilder.append("Status: ");
        stateBuilder.append(scenarioStateNames.get(currentState));
        stateBuilder.append("<br/>");
        
        stateBuilder.append("Terrain: ");
        if((backingScenario.getTerrainType() >= 0) && (backingScenario.getTerrainType() < AtBScenario.terrainTypes.length)) {
            stateBuilder.append(AtBScenario.terrainTypes[backingScenario.getTerrainType()]);
            stateBuilder.append(" : ");
            stateBuilder.append(backingScenario.getMap());
        }
        stateBuilder.append("<br/>");

        if (deploymentDate != null) {
            stateBuilder.append("Deployment Date: ");
            stateBuilder.append(deploymentDate.toString());
            stateBuilder.append("<br/>");
        }
        
        if (actionDate != null) {
            stateBuilder.append("Battle Date: ");
            stateBuilder.append(actionDate.toString());
            stateBuilder.append("<br/>");
        }
        
        if (returnDate != null) {
            stateBuilder.append("Return Date: ");
            stateBuilder.append(returnDate.toString());
            stateBuilder.append("<br/>");
        }
        
        /*if(includeForces) {
            List<UUID> unitIDs = backingScenario.getForces(currentCampaign).getAllUnits();
            
            if(!unitIDs.isEmpty()) {
                stateBuilder.append("<br/><br/>Assigned Units:<br/>");
                
                for(UUID unitID : unitIDs) {
                    stateBuilder.append("&nbsp;&nbsp;");
                    stateBuilder.append(currentCampaign.getUnit(unitID).getName());
                    stateBuilder.append("<br/>");
                }
            }
        }*/        

        stateBuilder.append("</html>");
        return stateBuilder.toString();
    }
    
    public void updateMinefieldCount(int minefieldType, int number) {
        backingScenario.setNumPlayerMinefields(minefieldType, number);
    }

    public String getName() {
        return backingScenario.getName();
    }
    
    public int getRequiredPlayerLances() {
        return requiredPlayerLances;
    }
    

    public void setRequiredPlayerLances(int requiredPlayerLances) {
        this.requiredPlayerLances = requiredPlayerLances;
    }
    
    public void incrementRequiredPlayerLances() {
        requiredPlayerLances++;
    }

    public boolean isRequiredScenario() {
        return requiredScenario;
    }

    public void setRequiredScenario(boolean requiredScenario) {
        this.requiredScenario = requiredScenario;
    }
    
    @XmlTransient
    public AtBDynamicScenario getBackingScenario() {
        return backingScenario;
    }

    @XmlJavaTypeAdapter(DateAdapter.class)
    public LocalDate getDeploymentDate() {
        return deploymentDate;
    }

    public void setDeploymentDate(LocalDate deploymentDate) {
        this.deploymentDate = deploymentDate;
    }

    @XmlJavaTypeAdapter(DateAdapter.class)
    public LocalDate getActionDate() {
        return actionDate;
    }

    public void setActionDate(LocalDate actionDate) {
        this.actionDate = actionDate;
        backingScenario.setDate(actionDate);
    }

    @XmlJavaTypeAdapter(DateAdapter.class)
    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }
    
    public StratconCoords getCoords() {
        return coords;
    }

    public void setCoords(StratconCoords coords) {
        this.coords = coords;
    }
    
    public boolean isStrategicObjective() {
        return isStrategicObjective;
    }
    
    public void setStrategicObjective(boolean value) {
        isStrategicObjective = value;
    }
    
    public ScenarioTemplate getScenarioTemplate() {
        return backingScenario.getTemplate();
    }

    public int getBackingScenarioID() {
        return backingScenarioID;
    }
    
    public void setBackingScenario(AtBDynamicScenario backingScenario) {
        this.backingScenario = backingScenario;
    }

    public void setBackingScenarioID(int backingScenarioID) {
        this.backingScenarioID = backingScenarioID;
    }

    public int getNumDefensivePoints() {
        return numDefensivePoints;
    }

    public void setNumDefensivePoints(int numDefensivePoints) {
        this.numDefensivePoints = numDefensivePoints;
    }
    
    public void useDefensivePoint() {
        numDefensivePoints--;
    }
}