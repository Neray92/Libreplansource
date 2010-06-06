/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.business.planner.limiting.entities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.joda.time.LocalDate;
import org.navalplanner.business.calendars.entities.BaseCalendar;
import org.navalplanner.business.calendars.entities.ResourceCalendar;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.CriterionCompounder;
import org.navalplanner.business.resources.entities.ICriterion;
import org.navalplanner.business.resources.entities.LimitingResourceQueue;
import org.navalplanner.business.resources.entities.Resource;

/**
 *
 * @author Diego Pino Garcia <dpino@igalia.com>
 *
 */
public class Gap implements Comparable<Gap> {

    public static class GapOnQueue {

        public static List<GapOnQueue> onQueue(LimitingResourceQueue queue,
                Collection<? extends Gap> gaps) {
            List<GapOnQueue> result = new ArrayList<GapOnQueue>();
            for (Gap each : gaps) {
                result.add(each.onQueue(queue));
            }
            return result;
        }

        private final LimitingResourceQueue originQueue;

        private final Gap gap;

        GapOnQueue(LimitingResourceQueue originQueue, Gap gap) {
            this.originQueue = originQueue;
            this.gap = gap;
        }

        public LimitingResourceQueue getOriginQueue() {
            return originQueue;
        }

        public Gap getGap() {
            return gap;
        }

        public List<GapOnQueue> splitIntoGapsSatisfyingCriteria(
                Set<Criterion> criteria) {
            return GapOnQueue.onQueue(originQueue, gap
                    .splitIntoGapsSatisfyingCriteria(originQueue.getResource(),
                            criteria));
        }

    }

    private DateAndHour startTime;

    private DateAndHour endTime;

    private Integer hoursInGap;

    public Gap(Resource resource, DateAndHour startTime,
            DateAndHour endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        hoursInGap = calculateHoursInGap(resource, startTime, endTime);
    }

    public GapOnQueue onQueue(LimitingResourceQueue queue) {
        return new GapOnQueue(queue, this);
    }

    private Integer calculateHoursInGap(Resource resource, DateAndHour startTime, DateAndHour endTime) {
        return (endTime == null) ? Integer.MAX_VALUE : calculateHoursInGap(
                resource, startTime.getDate(), startTime.getHour(), endTime
                        .getDate(), endTime.getHour());
    }

    public int getHoursInGap() {
        return hoursInGap;
    }

    private Integer calculateHoursInGap(Resource resource, LocalDate startDate,
            int startHour, LocalDate endDate, int endHour) {

        final ResourceCalendar calendar = resource.getCalendar();

        if (startDate.equals(endDate)) {
            return calendar.getCapacityAt(startDate) - Math.max(startHour, endHour);
        } else {
            int hoursAtStart = calendar.getCapacityAt(startDate) - startHour;
            int hoursInBetween = calendar.getWorkableHours(startDate
                    .plusDays(1), endDate.minusDays(1));
            return hoursAtStart + hoursInBetween + endHour;
        }
    }

    public List<Integer> getHoursInGapUntilAllocatingAndGoingToTheEnd(
            BaseCalendar calendar,
            DateAndHour realStart, DateAndHour allocationEnd, int total) {
        DateAndHour gapEnd = getEndTime();
        Validate.isTrue(gapEnd == null || allocationEnd.compareTo(gapEnd) <= 0);
        Validate.isTrue(startTime == null
                || realStart.compareTo(startTime) >= 0);
        List<Integer> result = new ArrayList<Integer>();
        Iterator<LocalDate> daysUntilEnd = realStart.daysUntil(gapEnd)
                .iterator();
        boolean isFirst = true;
        while (daysUntilEnd.hasNext()) {
            LocalDate each = daysUntilEnd.next();
            final boolean isLast = !daysUntilEnd.hasNext();
            int hoursAtDay = getHoursAtDay(each, calendar, realStart, isFirst,
                    isLast);
            final int hours;
            if (total > 0) {
                hours = Math.min(hoursAtDay, total);
                total -= hours;
            } else {
                hours = hoursAtDay;
            }
            if (isFirst) {
                isFirst = false;
            }
            result.add(hours);
            if (total == 0
                    && DateAndHour.from(each).compareTo(allocationEnd) >= 0) {
                break;
            }
        }
        return result;
    }

    private int getHoursAtDay(LocalDate day, BaseCalendar calendar,
            DateAndHour realStart, boolean isFirst, final boolean isLast) {
        final int capacity = calendar.getCapacityAt(day);
        if (isLast && isFirst) {
            return Math.min(endTime.getHour() - realStart.getHour(),
                    capacity);
        } else if (isFirst) {
            return capacity - realStart.getHour();
        } else if (isLast) {
            return Math.min(endTime.getHour(), capacity);
        } else {
            return capacity;
        }
    }

    public static Gap create(Resource resource, DateAndHour startTime,
            DateAndHour endTime) {
        return new Gap(resource, startTime, endTime);
    }

    public DateAndHour getStartTime() {
        return startTime;
    }

    public DateAndHour getEndTime() {
        return endTime;
    }

    /**
     * Returns true if the gap starts after earlierStartDateBecauseOfGantt and
     * if it's big enough for fitting candidate
     *
     * @param hours
     * @return
     */
    public boolean canFit(LimitingResourceQueueElement candidate) {
        LocalDate startAfter = LocalDate.fromDateFields(candidate
                .getEarlierStartDateBecauseOfGantt());
        LocalDate endsAfter = LocalDate.fromDateFields(candidate
                .getEarliestEndDateBecauseOfGantt());

        return canSatisfyStartConstraint(startAfter)
                && canSatisfyEndConstraint(endsAfter)
                && hoursInGap >= candidate.getIntentedTotalHours();
    }

    private boolean canSatisfyStartConstraint(final LocalDate startsAfter) {
        return startsAfter.compareTo(startTime.getDate()) <= 0;
    }

    private boolean canSatisfyEndConstraint(LocalDate endsAfter) {
        return endTime == null || endsAfter.compareTo(endTime.getDate()) <= 0;
    }

    public String toString() {
        String result = startTime.getDate() + " - " + startTime.getHour();
        if (endTime != null) {
            result += "; " + endTime.getDate() + " - " + endTime.getHour();
        }
        return result;
    }

    @Override
    public int compareTo(Gap o) {
        if (o == null) {
            return 1;
        }
        return this.getStartTime().compareTo(o.getStartTime());
    }

    public boolean isBefore(Gap gap) {
        return (compareTo(gap) < 0);
    }

    public List<Gap> splitIntoGapsSatisfyingCriteria(Resource resource,
            Set<Criterion> criteria) {
        return splitIntoGapsSatisfyingCriteria(resource, criteria,
                getStartTime(), getEndTime());
    }

    /**
     * Returns a set of {@link Gap} composed by those gaps which satisfy
     * <em>criteria</em> within the period: <em>gapStartTime</em> till
     * <em>gapEndTime</em>
     * @param resource
     * @param criteria
     *            criteria to be satisfied by resource
     * @param gapStartTime
     *            start time of gap
     * @param gapEndTime
     *            end time of gap
     * @return
     */
    private static List<Gap> splitIntoGapsSatisfyingCriteria(Resource resource,
            Set<Criterion> criteria, DateAndHour gapStartTime,
            DateAndHour gapEndTime) {

        final ICriterion compositedCriterion = CriterionCompounder.buildAnd(
                criteria).getResult();
        final ResourceCalendar calendar = resource.getCalendar();

        // FIXME: If endTime is null (lastGap), set endTime as 100 years ahead
        // startTime
        final LocalDate gapEndDate = gapEndTime != null ? gapEndTime.getDate()
                .plusDays(1) : gapStartTime.getDate().plusYears(10);
        final LocalDate gapStartDate = gapStartTime.getDate();

        List<Gap> result = new ArrayList<Gap>();

        LocalDate date = gapStartDate;
        boolean open = compositedCriterion
                .isSatisfiedBy(resource, toDate(date));
        DateAndHour startTime = gapStartTime, endTime;
        while (date.isBefore(gapEndDate)) {
            if (calendar.getCapacityAt(date) == 0) {
                date = date.plusDays(1);
                continue;
            }

            if (open == false
                    && compositedCriterion
                            .isSatisfiedBy(resource, toDate(date))) {
                startTime = new DateAndHour(date, 0);
                open = true;
            }
            if (open == true
                    && !compositedCriterion.isSatisfiedBy(resource,
                            toDate(date))) {
                endTime = new DateAndHour(date, 0);
                result.add(Gap.create(resource, startTime, endTime));
                open = false;
            }
            date = date.plusDays(1);
        }
        result.add(Gap.create(resource, startTime, gapEndTime));

        return result;
    }

    private static Date toDate(LocalDate date) {
        return date != null ? date.toDateTimeAtStartOfDay().toDate() : null;
    }

}