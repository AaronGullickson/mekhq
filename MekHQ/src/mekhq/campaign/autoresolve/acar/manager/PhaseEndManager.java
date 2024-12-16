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

package mekhq.campaign.autoresolve.acar.manager;

import megamek.common.enums.GamePhase;
import mekhq.campaign.autoresolve.acar.SimulationManager;
import mekhq.campaign.autoresolve.acar.report.PublicReportEntry;

public record PhaseEndManager(SimulationManager simulationManager) implements SimulationManagerHelper {

    public void managePhase() {
        switch (simulationManager.getGame().getPhase()) {
            case INITIATIVE:
                simulationManager.addReport(new PublicReportEntry(999));
                simulationManager.getGame().setupDeployment();
                simulationManager.resetFormations();
                simulationManager.flushPendingReports();
                if (simulationManager.getGame().shouldDeployThisRound()) {
                    simulationManager.changePhase(GamePhase.DEPLOYMENT);
                } else {
                    simulationManager.changePhase(GamePhase.SBF_DETECTION);
                }
                break;
            case DEPLOYMENT:
                simulationManager.addReport(new PublicReportEntry(999));
                simulationManager.getGame().clearDeploymentThisRound();
                phaseCleanup();
                simulationManager.changePhase(GamePhase.SBF_DETECTION);
                break;
            case SBF_DETECTION:
                simulationManager.actionsProcessor.handleActions();
                phaseCleanup();
                simulationManager.changePhase(GamePhase.MOVEMENT);
                break;
            case MOVEMENT:
                simulationManager.addReport(new PublicReportEntry(999));
                simulationManager.addReport(new PublicReportEntry(2201));
                simulationManager.actionsProcessor.handleActions();
                phaseCleanup();
                simulationManager.changePhase(GamePhase.FIRING);
                break;
            case FIRING:
                simulationManager.addReport(new PublicReportEntry(999));
                simulationManager.addReport(new PublicReportEntry(2002));
                simulationManager.actionsProcessor.handleActions();
                phaseCleanup();
                simulationManager.changePhase(GamePhase.END);
                break;
            case END:
                simulationManager.actionsProcessor.handleActions();
                phaseCleanup();
                if (simulationManager.checkForVictory()) {
                    simulationManager.changePhase(GamePhase.VICTORY);
                }
                break;
            case VICTORY:
                phaseCleanup();
            case STARTING_SCENARIO:
            default:
                break;
        }
    }

    private void phaseCleanup() {
        simulationManager.resetPlayersDone();
        simulationManager.resetFormations();
        simulationManager.flushPendingReports();
    }

}
