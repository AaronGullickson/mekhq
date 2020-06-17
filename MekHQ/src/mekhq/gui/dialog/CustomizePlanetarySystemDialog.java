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

import megamek.common.PlanetaryConditions;
import megamek.common.util.EncodeControl;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Planet;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.gui.model.SortedComboBoxModel;
import mekhq.gui.preferences.JWindowPreference;
import mekhq.preferences.PreferencesNode;

import javax.swing.*;
import javax.xml.bind.annotation.XmlElement;
import java.awt.*;
import java.util.Calendar;
import java.util.List;
import java.util.ResourceBundle;

public class CustomizePlanetarySystemDialog extends javax.swing.JDialog {

    private PlanetarySystem system;
    private Campaign campaign;

    private JTabbedPane tabPlanets;

    public CustomizePlanetarySystemDialog(java.awt.Frame parent, boolean modal, PlanetarySystem s, Campaign c) {
        super(parent, modal);
        this.system = s;
        this.campaign = c;
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

        tabPlanets = new JTabbedPane();
        int idx = 0;
        for(Planet p : system.getPlanets()) {
            tabPlanets.add(p.getPrintableName(campaign.getDateTime()), new PlanetEditorPanel(p));
        }
        getContentPane().add(tabPlanets);

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

    private class PlanetEditorPanel extends JPanel {

        private Planet planet;


        //orbital radius

        //sattelite information

        //planetary type
        private JLabel lblType = new JLabel("Type: ");
        private JComboBox<String> choiceType;
        //diameter
        //gravity
        //dayLength
        //yearLength

        //water
        //landmasses

        //pressure
        private JLabel lblPressure;
        private DefaultComboBoxModel modelPressure;
        private JComboBox<String> choicePressure;
        //composition
        //temperature

        //lifeform

        //desc

        private PlanetEditorPanel(Planet p) {
            this.planet = p;
            setLayout(new GridBagLayout());
            initializeComponents();
        }

        private void initializeComponents() {

            lblType = new JLabel("Planet Type: ");
            lblPressure = new JLabel("Atmospheric Pressure: ");

            modelPressure = new DefaultComboBoxModel();
            for (int i = 0; i < PlanetaryConditions.ATMO_SIZE; i++) {
                modelPressure.addElement(PlanetaryConditions.getAtmosphereDisplayableName(i));
            }
            modelPressure.setSelectedItem(planet.getPressureName(campaign.getDateTime()));
            choicePressure = new JComboBox<String>(modelPressure);

            GridBagConstraints gbc = new GridBagConstraints();

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.fill = GridBagConstraints.NONE;
            add(lblType, gbc);
            gbc.gridx++;
            //add(type, gbc);
            gbc.gridx--;
            gbc.gridy++;
            add(lblPressure, gbc);
            gbc.gridx++;
            add(choicePressure, gbc);
        }

    }
}
