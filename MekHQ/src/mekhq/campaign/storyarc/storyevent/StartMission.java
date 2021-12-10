/*
 * StoryArc.java
 *
 * Copyright (c) 2020 - The MegaMek Team. All Rights Reserved
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
package mekhq.campaign.storyarc.storyevent;

import mekhq.MekHQ;
import mekhq.MekHqXmlSerializable;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.Mission;
import mekhq.campaign.storyarc.StoryEvent;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.text.ParseException;
import java.util.UUID;

/**
 * A StoryEvent class to start a new mission. This will pull from hash of possible missions in StoryArc. Because
 * Story missions need to be related to an actual integer id in the campaign, all story missions should be unique,
 * i.e. non-repeatable. Scenarios however can be repeatable.
 */
public class StartMission extends StoryEvent implements Serializable, MekHqXmlSerializable {

    UUID missionId;

    /**
     * The StartMission event has no outcome variability so should choose a single next event,
     * typically an ScenarioStoryEvent
     **/
    UUID nextEventId;

    public StartMission() {
        super();
    }

    @Override
    public void startEvent() {
        super.startEvent();
        Mission m = getStoryArc().getStoryMission(missionId);
        if(null != m) {
            getStoryArc().setCurrentMissionId(getStoryArc().getCampaign().addMission(m));
            //TODO: a pop-up dialog of the mission
        }
        //no need for this event to stick around
        completeEvent();
    }

    @Override
    protected UUID getNextStoryEvent() {
        return nextEventId;
    }

    @Override
    public void writeToXml(PrintWriter pw1, int indent) {

    }

    @Override
    public void loadFieldsFromXmlNode(Node wn, Campaign c) throws ParseException {
        // Okay, now load mission-specific fields!
        NodeList nl = wn.getChildNodes();

        for (int x = 0; x < nl.getLength(); x++) {
            Node wn2 = nl.item(x);

            try {
                if (wn2.getNodeName().equalsIgnoreCase("missionId")) {
                    missionId = UUID.fromString(wn2.getTextContent().trim());
                } else if (wn2.getNodeName().equalsIgnoreCase("nextEventId")) {
                    nextEventId = UUID.fromString(wn2.getTextContent().trim());
                }
            } catch (Exception e) {
                MekHQ.getLogger().error(e);
            }
        }
    }
}
