/*
 * Copyright (c) 2024 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.campaign.personnel.autoAwards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.personnel.Award;

public class TimeAwards {
    private static final MMLogger logger = MMLogger.create(TimeAwards.class);

    /**
     * This function loops through Time Awards, checking whether the person is
     * eligible to receive each type of award
     *
     * @param campaign the campaign to be processed
     * @param person   the person to check award eligibility for
     * @param awards   the awards to be processed (should only include awards where
     *                 item == Time)
     */
    public static Map<Integer, List<Object>> TimeAwardsProcessor(Campaign campaign, UUID person, List<Award> awards) {
        int requiredYearsOfService;
        boolean isCumulative;
        long yearsOfService;

        List<Award> eligibleAwards = new ArrayList<>();
        List<Award> eligibleAwardsBestable = new ArrayList<>();
        Award bestAward = new Award();

        for (Award award : awards) {
            try {
                requiredYearsOfService = award.getQty();
            } catch (Exception e) {
                logger.warn("Award {} from the {} set has an invalid qty value {}",
                        award.getName(), award.getSet(), award.getQty());
                continue;
            }

            try {
                isCumulative = award.isStackable();
            } catch (Exception e) {
                logger.warn("Award {} from the {} set has an invalid stackable value {}",
                        award.getName(), award.getSet(), award.getQty());
                continue;
            }

            if (award.canBeAwarded(campaign.getPerson(person))) {
                try {
                    yearsOfService = campaign.getPerson(person).getYearsInService(campaign);
                } catch (Exception e) {
                    logger.error("Unable to parse yearsOfService for {} while processing Award {} from the [{}] set.",
                            campaign.getPerson(person).getFullName(), award.getName(), award.getSet());
                    continue;
                }

                if (isCumulative) {
                    requiredYearsOfService *= campaign.getPerson(person).getAwardController().getNumberOfAwards(award)
                            + 1;
                }

                if (yearsOfService >= requiredYearsOfService) {
                    eligibleAwardsBestable.add(award);
                }
            }
        }

        if (!eligibleAwardsBestable.isEmpty()) {
            if (campaign.getCampaignOptions().isIssueBestAwardOnly()) {
                int rollingQty = 0;

                for (Award award : eligibleAwardsBestable) {
                    if (award.getQty() > rollingQty) {
                        rollingQty = award.getQty();
                        bestAward = award;
                    }
                }

                eligibleAwards.add(bestAward);
            } else {
                eligibleAwards.addAll(eligibleAwardsBestable);
            }
        }

        return AutoAwardsController.prepareAwardData(person, eligibleAwards);
    }
}
