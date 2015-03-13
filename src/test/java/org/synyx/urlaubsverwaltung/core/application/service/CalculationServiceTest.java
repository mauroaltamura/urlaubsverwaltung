package org.synyx.urlaubsverwaltung.core.application.service;

import com.google.common.base.Optional;

import junit.framework.Assert;

import org.joda.time.DateMidnight;
import org.joda.time.DateTimeConstants;

import org.junit.Before;
import org.junit.Test;

import org.mockito.Mockito;

import org.synyx.urlaubsverwaltung.core.account.Account;
import org.synyx.urlaubsverwaltung.core.account.AccountInteractionService;
import org.synyx.urlaubsverwaltung.core.account.AccountService;
import org.synyx.urlaubsverwaltung.core.application.domain.Application;
import org.synyx.urlaubsverwaltung.core.application.domain.ApplicationStatus;
import org.synyx.urlaubsverwaltung.core.application.domain.DayLength;
import org.synyx.urlaubsverwaltung.core.application.domain.VacationDaysLeft;
import org.synyx.urlaubsverwaltung.core.application.domain.VacationType;
import org.synyx.urlaubsverwaltung.core.calendar.JollydayCalendar;
import org.synyx.urlaubsverwaltung.core.calendar.OwnCalendarService;
import org.synyx.urlaubsverwaltung.core.calendar.workingtime.WorkingTime;
import org.synyx.urlaubsverwaltung.core.calendar.workingtime.WorkingTimeService;
import org.synyx.urlaubsverwaltung.core.person.Person;

import java.io.IOException;

import java.math.BigDecimal;

import java.util.Arrays;
import java.util.List;


/**
 * Unit test for {@link CalculationService}.
 *
 * @author  Aljona Murygina - murygina@synyx.de
 */
public class CalculationServiceTest {

    private CalculationService service;
    private ApplicationService applicationService;
    private AccountInteractionService accountInteractionService;
    private AccountService accountService;
    private OwnCalendarService calendarService;

    @Before
    public void setUp() throws IOException {

        applicationService = Mockito.mock(ApplicationService.class);
        accountService = Mockito.mock(AccountService.class);
        accountInteractionService = Mockito.mock(AccountInteractionService.class);

        WorkingTimeService workingTimeService = Mockito.mock(WorkingTimeService.class);
        calendarService = new OwnCalendarService(new JollydayCalendar(), workingTimeService);

        // create working time object (MON-FRI)
        WorkingTime workingTime = new WorkingTime();
        List<Integer> workingDays = Arrays.asList(DateTimeConstants.MONDAY, DateTimeConstants.TUESDAY,
                DateTimeConstants.WEDNESDAY, DateTimeConstants.THURSDAY, DateTimeConstants.FRIDAY);
        workingTime.setWorkingDays(workingDays, DayLength.FULL);

        Mockito.when(workingTimeService.getByPersonAndValidityDateEqualsOrMinorDate(Mockito.any(Person.class),
                Mockito.any(DateMidnight.class))).thenReturn(workingTime);

        service = new CalculationService(applicationService, accountService, accountInteractionService,
                calendarService);
    }


    @Test
    public void testCheckApplication() {

        Person person = new Person();
        person.setLoginName("horscht");

        initCustomService("5", "15");

        Application applicationForLeaveToCheck = new Application();
        applicationForLeaveToCheck.setStartDate(new DateMidnight(2012, DateTimeConstants.AUGUST, 20));
        applicationForLeaveToCheck.setEndDate(new DateMidnight(2012, DateTimeConstants.AUGUST, 21));
        applicationForLeaveToCheck.setPerson(person);
        applicationForLeaveToCheck.setHowLong(DayLength.FULL);

        Account account = new Account(person, new DateMidnight(2012, DateTimeConstants.JANUARY, 1).toDate(),
                new DateMidnight(2012, DateTimeConstants.DECEMBER, 31).toDate(), BigDecimal.valueOf(28),
                BigDecimal.valueOf(5), BigDecimal.ZERO);
        Mockito.when(accountService.getHolidaysAccount(2012, person)).thenReturn(Optional.of(account));

        // vacation days would be left after this application for leave
        account.setVacationDays(BigDecimal.valueOf(28));

        Assert.assertTrue("Should be enough vacation days to apply for leave",
            service.checkApplication(applicationForLeaveToCheck));

        // not enough vacation days for this application for leave
        account.setVacationDays(BigDecimal.valueOf(10));

        Assert.assertFalse("Should NOT be enough vacation days to apply for leave",
            service.checkApplication(applicationForLeaveToCheck));

        // enough vacation days for this application for leave, but none would be left
        account.setVacationDays(BigDecimal.valueOf(20));

        Assert.assertTrue("Should be enough vacation days to apply for leave",
            service.checkApplication(applicationForLeaveToCheck));
    }


    @Test
    public void testGetDaysBeforeApril() {

        Person person = new Person();
        person.setLoginName("horscht");

        DateMidnight firstMilestone = new DateMidnight(2012, DateTimeConstants.JANUARY, 1);
        DateMidnight lastMilestone = new DateMidnight(2012, DateTimeConstants.MARCH, 31);

        // 4 days at all: 2 before January + 2 after January
        Application a1 = new Application();
        a1.setStartDate(new DateMidnight(2011, DateTimeConstants.DECEMBER, 29));
        a1.setEndDate(new DateMidnight(2012, DateTimeConstants.JANUARY, 3));
        a1.setHowLong(DayLength.FULL);
        a1.setStatus(ApplicationStatus.ALLOWED);
        a1.setVacationType(VacationType.HOLIDAY);
        a1.setPerson(person);

        // 5 days
        Application a2 = new Application();
        a2.setStartDate(new DateMidnight(2012, DateTimeConstants.MARCH, 12));
        a2.setEndDate(new DateMidnight(2012, DateTimeConstants.MARCH, 16));
        a2.setHowLong(DayLength.FULL);
        a2.setStatus(ApplicationStatus.ALLOWED);
        a2.setVacationType(VacationType.HOLIDAY);
        a2.setPerson(person);

        // 4 days
        Application a3 = new Application();
        a3.setStartDate(new DateMidnight(2012, DateTimeConstants.FEBRUARY, 6));
        a3.setEndDate(new DateMidnight(2012, DateTimeConstants.FEBRUARY, 9));
        a3.setHowLong(DayLength.FULL);
        a3.setStatus(ApplicationStatus.WAITING);
        a3.setVacationType(VacationType.HOLIDAY);
        a3.setPerson(person);

        // 6 days at all: 2 before April + 4 after April
        Application a4 = new Application();
        a4.setStartDate(new DateMidnight(2012, DateTimeConstants.MARCH, 29));
        a4.setEndDate(new DateMidnight(2012, DateTimeConstants.APRIL, 5));
        a4.setHowLong(DayLength.FULL);
        a4.setStatus(ApplicationStatus.WAITING);
        a4.setVacationType(VacationType.HOLIDAY);
        a4.setPerson(person);

        Mockito.when(applicationService.getApplicationsForACertainPeriodAndPerson(Mockito.any(DateMidnight.class),
                Mockito.any(DateMidnight.class), Mockito.any(Person.class))).thenReturn(Arrays.asList(a1, a2, a3, a4));

        BigDecimal days = service.getUsedDaysBetweenTwoMilestones(person, firstMilestone, lastMilestone);
        // must be: 2 + 5 + 4 + 2 = 13

        Assert.assertNotNull(days);
        Assert.assertEquals(new BigDecimal("13.0"), days);
    }


    @Test
    public void testGetDaysAfterApril() {

        Person person = new Person();
        person.setLoginName("horscht");

        DateMidnight firstMilestone = new DateMidnight(2012, DateTimeConstants.APRIL, 1);
        DateMidnight lastMilestone = new DateMidnight(2012, DateTimeConstants.DECEMBER, 31);

        // 4 days at all: 2.5 before January + 2 after January
        Application a1 = new Application();
        a1.setStartDate(new DateMidnight(2012, DateTimeConstants.DECEMBER, 27));
        a1.setEndDate(new DateMidnight(2013, DateTimeConstants.JANUARY, 3));
        a1.setHowLong(DayLength.FULL);
        a1.setPerson(person);
        a1.setStatus(ApplicationStatus.ALLOWED);
        a1.setVacationType(VacationType.HOLIDAY);

        // 5 days
        Application a2 = new Application();
        a2.setStartDate(new DateMidnight(2012, DateTimeConstants.SEPTEMBER, 3));
        a2.setEndDate(new DateMidnight(2012, DateTimeConstants.SEPTEMBER, 7));
        a2.setHowLong(DayLength.FULL);
        a2.setPerson(person);
        a2.setStatus(ApplicationStatus.ALLOWED);
        a2.setVacationType(VacationType.HOLIDAY);

        // 6 days at all: 2 before April + 4 after April
        Application a4 = new Application();
        a4.setStartDate(new DateMidnight(2012, DateTimeConstants.MARCH, 29));
        a4.setEndDate(new DateMidnight(2012, DateTimeConstants.APRIL, 5));
        a4.setHowLong(DayLength.FULL);
        a4.setPerson(person);
        a4.setStatus(ApplicationStatus.WAITING);
        a4.setVacationType(VacationType.HOLIDAY);

        Mockito.when(applicationService.getApplicationsForACertainPeriodAndPerson(Mockito.any(DateMidnight.class),
                Mockito.any(DateMidnight.class), Mockito.any(Person.class))).thenReturn(Arrays.asList(a1, a2, a4));

        BigDecimal days = service.getUsedDaysBetweenTwoMilestones(person, firstMilestone, lastMilestone);
        // must be: 2.5 + 5 + 4 = 11.5

        Assert.assertNotNull(days);
        Assert.assertEquals(new BigDecimal("11.5"), days);
    }


    @Test
    public void testGetDaysBetweenMilestonesWithInactiveApplicationsForLeaveAndOfOtherVacationTypeThanHoliday() {

        Person person = new Person();
        person.setLoginName("horscht");

        DateMidnight firstMilestone = new DateMidnight(2012, DateTimeConstants.APRIL, 1);
        DateMidnight lastMilestone = new DateMidnight(2012, DateTimeConstants.DECEMBER, 31);

        Application cancelledHoliday = new Application();
        cancelledHoliday.setVacationType(VacationType.HOLIDAY);
        cancelledHoliday.setStatus(ApplicationStatus.CANCELLED);

        Application rejectedHoliday = new Application();
        rejectedHoliday.setVacationType(VacationType.HOLIDAY);
        rejectedHoliday.setStatus(ApplicationStatus.REJECTED);

        Application waitingSpecialLeave = new Application();
        waitingSpecialLeave.setVacationType(VacationType.SPECIALLEAVE);
        waitingSpecialLeave.setStatus(ApplicationStatus.WAITING);

        Application allowedSpecialLeave = new Application();
        allowedSpecialLeave.setVacationType(VacationType.SPECIALLEAVE);
        allowedSpecialLeave.setStatus(ApplicationStatus.ALLOWED);

        Application waitingUnpaidLeave = new Application();
        waitingUnpaidLeave.setVacationType(VacationType.UNPAIDLEAVE);
        waitingUnpaidLeave.setStatus(ApplicationStatus.WAITING);

        Application allowedUnpaidLeave = new Application();
        allowedUnpaidLeave.setVacationType(VacationType.UNPAIDLEAVE);
        allowedUnpaidLeave.setStatus(ApplicationStatus.ALLOWED);

        Application waitingOvertime = new Application();
        waitingOvertime.setVacationType(VacationType.OVERTIME);
        waitingOvertime.setStatus(ApplicationStatus.WAITING);

        Application allowedOvertime = new Application();
        allowedOvertime.setVacationType(VacationType.OVERTIME);
        allowedOvertime.setStatus(ApplicationStatus.ALLOWED);

        Mockito.when(applicationService.getApplicationsForACertainPeriodAndPerson(Mockito.any(DateMidnight.class),
                Mockito.any(DateMidnight.class), Mockito.any(Person.class))).thenReturn(Arrays.asList(cancelledHoliday,
                rejectedHoliday, waitingSpecialLeave, allowedSpecialLeave, waitingUnpaidLeave, allowedUnpaidLeave,
                waitingOvertime, allowedOvertime));

        BigDecimal days = service.getUsedDaysBetweenTwoMilestones(person, firstMilestone, lastMilestone);

        Assert.assertNotNull(days);
        Assert.assertEquals(BigDecimal.ZERO, days);
    }


    @Test
    public void testGetVacationDaysLeft() {

        initCustomService("4", "20");

        Account account = new Account();
        account.setAnnualVacationDays(new BigDecimal("30"));
        account.setVacationDays(new BigDecimal("30"));
        account.setRemainingVacationDays(new BigDecimal("6"));
        account.setRemainingVacationDaysNotExpiring(new BigDecimal("2"));

        VacationDaysLeft vacationDaysLeft = service.getVacationDaysLeft(account);

        Assert.assertNotNull("Should not be null", vacationDaysLeft);

        Assert.assertNotNull("Should not be null", vacationDaysLeft.getVacationDays());
        Assert.assertNotNull("Should not be null", vacationDaysLeft.getRemainingVacationDays());
        Assert.assertNotNull("Should not be null", vacationDaysLeft.getRemainingVacationDaysNotExpiring());

        Assert.assertEquals("Wrong number of vacation days", new BigDecimal("12"), vacationDaysLeft.getVacationDays());
        Assert.assertEquals("Wrong number of remaining vacation days", BigDecimal.ZERO,
            vacationDaysLeft.getRemainingVacationDays());
        Assert.assertEquals("Wrong number of remaining vacation days that do not expire", BigDecimal.ZERO,
            vacationDaysLeft.getRemainingVacationDaysNotExpiring());
    }


    private void initCustomService(final String daysBeforeApril, final String daysAfterApril) {

        service = new CalculationService(applicationService, accountService, accountInteractionService,
                calendarService) {

            @Override
            protected BigDecimal getUsedDaysBeforeApril(Account account) {

                return new BigDecimal(daysBeforeApril);
            }


            @Override
            protected BigDecimal getUsedDaysAfterApril(Account account) {

                return new BigDecimal(daysAfterApril);
            }
        };
    }
}
