package org.synyx.urlaubsverwaltung.application.statistics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.synyx.urlaubsverwaltung.account.AccountService;
import org.synyx.urlaubsverwaltung.account.VacationDaysLeft;
import org.synyx.urlaubsverwaltung.account.VacationDaysService;
import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.application.ApplicationService;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.overtime.OvertimeService;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.basedata.PersonBasedata;
import org.synyx.urlaubsverwaltung.workingtime.WorkDaysCountService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static java.math.BigDecimal.ZERO;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static java.util.Optional.empty;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.ALLOWED_CANCELLATION_REQUESTED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.TEMPORARY_ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.WAITING;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeServiceImpl.convert;

/**
 * Builds a {@link ApplicationForLeaveStatistics} for the given
 * {@link org.synyx.urlaubsverwaltung.person.Person} and period.
 */
@Component
class ApplicationForLeaveStatisticsBuilder {

    private final AccountService accountService;
    private final ApplicationService applicationService;
    private final WorkDaysCountService workDaysCountService;
    private final VacationDaysService vacationDaysService;
    private final OvertimeService overtimeService;
    private final Clock clock;

    @Autowired
    ApplicationForLeaveStatisticsBuilder(AccountService accountService, ApplicationService applicationService,
                                         WorkDaysCountService workDaysCountService, VacationDaysService vacationDaysService,
                                         OvertimeService overtimeService, Clock clock) {
        this.accountService = accountService;
        this.applicationService = applicationService;
        this.workDaysCountService = workDaysCountService;
        this.vacationDaysService = vacationDaysService;
        this.overtimeService = overtimeService;
        this.clock = clock;
    }

    public ApplicationForLeaveStatistics build(Person person, PersonBasedata personBasedata, LocalDate from, LocalDate to, List<VacationType> vacationTypes) {
        Assert.isTrue(from.getYear() == to.getYear(), "From and to must be in the same year");

        final ApplicationForLeaveStatistics statistics = build(person, from, to, vacationTypes);
        statistics.setPersonBasedata(personBasedata);

        return statistics;
    }

    public ApplicationForLeaveStatistics build(Person person, LocalDate from, LocalDate to, List<VacationType> vacationTypes) {

        final LocalDate today = LocalDate.now(clock);
        final ApplicationForLeaveStatistics statistics = new ApplicationForLeaveStatistics(person);

        accountService.getHolidaysAccount(from.getYear(), person)
            .ifPresent(account -> {
                final LocalDate firstDayOfYear = from.with(firstDayOfYear());
                final LocalDate lastDayOfYear = to.with(lastDayOfYear());
                final VacationDaysLeft vacationDaysLeftYear = vacationDaysService.getVacationDaysLeft(firstDayOfYear, lastDayOfYear, account, empty());
                statistics.setLeftVacationDaysForYear(vacationDaysLeftYear.getLeftVacationDays(today, account.getExpiryDate()));
                statistics.setLeftRemainingVacationDaysForYear(vacationDaysLeftYear.getRemainingVacationDaysLeft(today, account.getExpiryDate()));

                final VacationDaysLeft vacationDaysLeftPeriod = vacationDaysService.getVacationDaysLeft(from, to, account, empty());
                statistics.setLeftVacationDaysForPeriod(vacationDaysLeftPeriod.getLeftVacationDays(to, account.getExpiryDate()));
                statistics.setLeftRemainingVacationDaysForPeriod(vacationDaysLeftPeriod.getRemainingVacationDaysLeft(to, account.getExpiryDate()));
            });

        statistics.setLeftOvertimeForYear(overtimeService.getLeftOvertimeForPerson(person));
        statistics.setLeftOvertimeForPeriod(overtimeService.getLeftOvertimeForPerson(person, from, to));

        for (VacationType type : vacationTypes) {
            statistics.addWaitingVacationDays(type, ZERO);
            statistics.addAllowedVacationDays(type, ZERO);
        }

        final List<Application> applications = applicationService.getApplicationsForACertainPeriodAndPerson(from, to, person);
        for (Application application : applications) {
            if (application.hasStatus(WAITING) || application.hasStatus(TEMPORARY_ALLOWED)) {
                statistics.addWaitingVacationDays(convert(application.getVacationType()), getVacationDaysFor(application, from, to));
            } else if (application.hasStatus(ALLOWED) || application.hasStatus(ALLOWED_CANCELLATION_REQUESTED)) {
                statistics.addAllowedVacationDays(convert(application.getVacationType()), getVacationDaysFor(application, from, to));
            }
        }
        return statistics;
    }

    private BigDecimal getVacationDaysFor(Application application, LocalDate from, LocalDate to) {

        final DayLength dayLength = application.getDayLength();
        final Person person = application.getPerson();

        final LocalDate startDate = application.getStartDate().isBefore(from) ? from : application.getStartDate();
        final LocalDate endDate = application.getEndDate().isAfter(to) ? to : application.getEndDate();

        return workDaysCountService.getWorkDaysCount(dayLength, startDate, endDate, person);
    }
}
