/*
 * CustomizePlanetarySytemDialog.java
 *
 * Copyright (c) 2020 - The MegaMek Team. All rights reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ.  If not, see <http://www.gnu.org/licenses/>.
 */

package mekhq.gui.dialog;

import megamek.common.util.EncodeControl;
import mekhq.MekHQ;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.gui.preferences.JWindowPreference;
import mekhq.preferences.PreferencesNode;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

public class CustomizePlanetarySystemDialog extends javax.swing.JDialog {

    private PlanetarySystem system;

    public CustomizePlanetarySystemDialog(java.awt.Frame parent, boolean modal, PlanetarySystem s) {
        super(parent, modal);
        this.system = s;
        initComponents();
        setLocationRelativeTo(parent);
        setUserPreferences();
        pack();
    }

    private void setUserPreferences() {
        PreferencesNode preferences = MekHQ.getPreferences().forClass(CustomizePlanetarySystemDialog.class);

        this.setName("dialog");
        preferences.manage(new JWindowPreference(this));
    }

    private void initComponents() {

        ResourceBundle resourceMap = ResourceBundle.getBundle("mekhq.resources.CustomizePlanetarySystemDialog", new EncodeControl()); //$NON-NLS-1$
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(resourceMap.getString("Form.title"));

        getContentPane().setLayout(new BorderLayout());

        getContentPane().add(new JLabel("Test"));

        /** buttons **/
        JPanel panBtns = new JPanel();
        JButton btnDone = new JButton(resourceMap.getString("btnDone.text"));
        btnDone.addActionListener(evt -> btnDoneActionPerformed(evt));
        panBtns.add(btnDone, new GridBagConstraints());

        JButton btnCancel = new JButton(resourceMap.getString("btnCancel.text"));
        btnCancel.addActionListener(evt -> btnCancelActionPerformed(evt));
        panBtns.add(btnCancel, new GridBagConstraints());

        getContentPane().add(panBtns, BorderLayout.PAGE_END);
    }

    private void btnDoneActionPerformed(java.awt.event.ActionEvent evt) {
        setVisible(false);
    }
    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {
        setVisible(false);
    }
}
