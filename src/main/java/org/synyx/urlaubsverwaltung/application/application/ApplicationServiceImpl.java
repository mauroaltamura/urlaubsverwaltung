package org.synyx.urlaubsverwaltung.application.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory;
import org.synyx.urlaubsverwaltung.person.Person;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.ALLOWED_CANCELLATION_REQUESTED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.TEMPORARY_ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.WAITING;

/**
 * Implementation of interface {@link ApplicationService}.
 */
@Service
@Transactional
class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;

    @Autowired
    ApplicationServiceImpl(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Application> getApplicationById(Integer id) {
        return applicationRepository.findById(id);
    }

    @Override
    public Application save(Application application) {
        return applicationRepository.save(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getApplicationsForACertainPeriodAndPerson(LocalDate startDate, LocalDate endDate, Person person) {
        return applicationRepository.getApplicationsForACertainTimeAndPerson(startDate, endDate, person);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getApplicationsForACertainPeriodAndPersonAndVacationCategory(LocalDate startDate, LocalDate endDate, Person person, List<ApplicationStatus> statuses, VacationCategory vacationCategory) {
        return applicationRepository.findByStatusInAndPersonAndStartDateBetweenAndVacationTypeCategory(statuses, person, startDate, endDate, vacationCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getApplicationsForACertainPeriodAndState(LocalDate startDate, LocalDate endDate, ApplicationStatus status) {
        return applicationRepository.getApplicationsForACertainTimeAndState(startDate, endDate, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getApplicationsWhereApplicantShouldBeNotifiedAboutUpcomingApplication(LocalDate from, LocalDate to, List<ApplicationStatus> statuses) {
        return applicationRepository.findByStatusInAndStartDateBetweenAndUpcomingApplicationsReminderSendIsNull(statuses, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getApplicationsWhereHolidayReplacementShouldBeNotified(LocalDate from, LocalDate to, List<ApplicationStatus> statuses) {
        return applicationRepository.findByStatusInAndStartDateBetweenAndHolidayReplacementsIsNotEmptyAndUpcomingHolidayReplacementNotificationSendIsNull(statuses, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getApplicationsForACertainPeriodAndPersonAndState(LocalDate startDate, LocalDate endDate, Person person, ApplicationStatus status) {
        return applicationRepository.getApplicationsForACertainTimeAndPersonAndState(startDate, endDate, person, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getForStates(List<ApplicationStatus> statuses) {
        return applicationRepository.findByStatusIn(statuses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getForStatesSince(List<ApplicationStatus> statuses, LocalDate since) {
        return applicationRepository.findByStatusInAndEndDateGreaterThanEqual(statuses, since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getForStatesAndPerson(List<ApplicationStatus> statuses, List<Person> persons) {
        return applicationRepository.findByStatusInAndPersonIn(statuses, persons);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getForStatesAndPersonSince(List<ApplicationStatus> statuses, List<Person> persons, LocalDate since) {
        return applicationRepository.findByStatusInAndPersonInAndEndDateIsGreaterThanEqual(statuses, persons, since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getForStatesAndPerson(List<ApplicationStatus> statuses, List<Person> persons, LocalDate start, LocalDate end) {
        return applicationRepository.findByStatusInAndPersonInAndEndDateIsGreaterThanEqualAndStartDateIsLessThanEqual(statuses, persons, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public Duration getTotalOvertimeReductionOfPerson(Person person) {
        final BigDecimal overtimeReduction = Optional.ofNullable(applicationRepository.calculateTotalOvertimeReductionOfPerson(person)).orElse(BigDecimal.ZERO);
        return Duration.ofMinutes(overtimeReduction.multiply(BigDecimal.valueOf(60)).longValue());
    }

    @Override
    @Transactional(readOnly = true)
    public Duration getTotalOvertimeReductionOfPersonBefore(Person person, LocalDate date) {
        final BigDecimal overtimeReduction = Optional.ofNullable(applicationRepository.calculateTotalOvertimeReductionOfPersonBefore(person, date)).orElse(BigDecimal.ZERO);
        return Duration.ofMinutes(overtimeReduction.multiply(BigDecimal.valueOf(60)).longValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getForHolidayReplacement(Person holidayReplacement, LocalDate date) {
        final List<ApplicationStatus> status = List.of(WAITING, TEMPORARY_ALLOWED, ALLOWED, ALLOWED_CANCELLATION_REQUESTED);
        return applicationRepository.findByHolidayReplacements_PersonAndEndDateIsGreaterThanEqualAndStatusIn(holidayReplacement, date, status);
    }
}
