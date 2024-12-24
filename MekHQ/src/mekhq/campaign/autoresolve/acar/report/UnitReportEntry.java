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

package mekhq.campaign.autoresolve.acar.report;

import megamek.client.ui.swing.util.UIUtil;
import mekhq.campaign.autoresolve.component.Formation;

import java.awt.*;

public class UnitReportEntry extends PublicReportEntry {

    private final String formationName;
    private final String unitName;
    private final String playerColorHex;

    public UnitReportEntry(String formationName, String unitName, String playerColorHex) {
        super(0);
        this.formationName = formationName;
        this.unitName = unitName;
        this.playerColorHex = playerColorHex;
        noNL();
    }

    public UnitReportEntry(Formation formation, int unitIndex, Color color) {
        this(formation.generalName(), unitName(formation, unitIndex), UIUtil.hexColor(color));
    }

    @Override
    protected String reportText() {
        return "<span style='color:" + playerColorHex + "; font-weight: bold;'>" + formationName + "</span>" + ", "
            + "<span style='color:" + playerColorHex + "; font-weight: bold;'>" + unitName + "</span>";
    }

    private static String unitName(Formation formation, int unitIndex) {
        if ((unitIndex < 0) || (unitIndex > formation.getUnits().size())) {
            throw new IllegalArgumentException("Invalid unit index");
        } else {
            return formation.getUnits().get(unitIndex).getName();
        }
    }
}
