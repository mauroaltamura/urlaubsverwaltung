package org.synyx.urlaubsverwaltung.absence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.synyx.urlaubsverwaltung.api.RestApiDateFormat;
import org.synyx.urlaubsverwaltung.api.RestControllerAdviceMarker;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHoliday;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHolidaysService;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTime;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.synyx.urlaubsverwaltung.absence.DayAbsenceDto.Type.SICK_NOTE;
import static org.synyx.urlaubsverwaltung.absence.DayAbsenceDto.Type.VACATION;
import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.period.DayLength.MORNING;
import static org.synyx.urlaubsverwaltung.period.DayLength.NOON;
import static org.synyx.urlaubsverwaltung.security.SecurityRules.IS_BOSS_OR_OFFICE;

@RestControllerAdviceMarker
@Tag(name = "absences", description = "Absences: Get all absences for a certain period")
@RestController
@RequestMapping("/api/persons/{personId}")
public class AbsenceApiController {

    public static final String ABSENCES = "absences";

    private final PersonService personService;
    private final AbsenceService absenceService;
    private final PublicHolidaysService publicHolidaysService;
    private final WorkingTimeService workingTimeService;

    @Autowired
    public AbsenceApiController(PersonService personService, AbsenceService absenceService,
                                PublicHolidaysService publicHolidaysService, WorkingTimeService workingTimeService) {
        this.personService = personService;
        this.absenceService = absenceService;
        this.publicHolidaysService = publicHolidaysService;
        this.workingTimeService = workingTimeService;
    }

    @Operation(
        summary = "Get all absences for a certain period and person",
        description = "Get all absences for a certain period and person"
    )
    @GetMapping(ABSENCES)
    @PreAuthorize(IS_BOSS_OR_OFFICE +
        " or @userApiMethodSecurity.isSamePersonId(authentication, #personId)" +
        " or @userApiMethodSecurity.isInDepartmentOfDepartmentHead(authentication, #personId)" +
        " or @userApiMethodSecurity.isInDepartmentOfSecondStageAuthority(authentication, #personId)")
    public DayAbsencesDto personsAbsences(
        @Parameter(description = "ID of the person")
        @PathVariable("personId")
            Integer personId,
        @Parameter(description = "start of interval to get absences from (inclusive)")
        @RequestParam("from")
        @DateTimeFormat(iso = ISO.DATE)
            LocalDate startDate,
        @Parameter(description = "end of interval to get absences from (inclusive)")
        @RequestParam("to")
        @DateTimeFormat(iso = ISO.DATE)
            LocalDate endDate,
        @Parameter(description = "Type of absences, vacation or sick notes")
        @RequestParam(value = "type", required = false)
            String type,
        @Parameter(description = "Whether to include no-workdays or not. Default is 'false' which will ignore no-workdays.")
        @RequestParam(value = "noWorkdaysInclusive", required = false, defaultValue = "false")
            boolean noWorkdaysInclusive) {

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(BAD_REQUEST, "Start date " + startDate + " must not be after end date " + endDate);
        }

        final Optional<Person> optionalPerson = personService.getPersonByID(personId);
        if (optionalPerson.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No person found for ID=" + personId);
        }

        final Person person = optionalPerson.get();
        final List<DayAbsenceDto> absences = getAbsences(startDate, endDate, person, toType(type), noWorkdaysInclusive);
        return new DayAbsencesDto(absences);
    }

    private List<DayAbsenceDto> getAbsences(LocalDate start, LocalDate end, Person person, DayAbsenceDto.Type type, boolean includeNonWorkingDays) {
        final Predicate<DayAbsenceDto> vacationAsked = dto -> type == null || type.equals(VACATION);
        final Predicate<DayAbsenceDto> sickAsked = dto -> type == null || type.equals(SICK_NOTE);
        final Predicate<DayAbsenceDto> isVacation = dto -> dto.getType().equals(VACATION.name());
        final Predicate<DayAbsenceDto> isSick = dto -> dto.getType().equals(SICK_NOTE.name());

        final Map<LocalDate, PublicHoliday> publicHolidaysByDate = publicHolidaysByDate(person, start, end);

        final List<DayAbsenceDto> absences = absenceService.getOpenAbsences(person, start, end)
            .stream()
            .flatMap(absencePeriod -> this.toDayAbsenceDto(absencePeriod, publicHolidaysByDate))
            .filter(vacationAsked.and(isVacation).or(sickAsked.and(isSick)))
            .collect(toList());

        if (!includeNonWorkingDays) {
            return absences;
        }

        final List<DayAbsenceDto> absencesWithNoWorkdays = new ArrayList<>();

        final List<WorkingTime> workingTimeList = workingTimeService.getByPerson(person);

        for (LocalDate date : new DateRange(start, end)) {
            final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(RestApiDateFormat.DATE_PATTERN);
            final String formattedDate = dateTimeFormatter.format(date);
            final Optional<DayAbsenceDto> maybeAbsenceDto = dayAbsenceDtoForDate(formattedDate, absences);
            if (maybeAbsenceDto.isPresent()) {
                absencesWithNoWorkdays.add(maybeAbsenceDto.get());
            } else if (!isWorkday(date, workingTimeList)) {
                absencesWithNoWorkdays.add(new DayAbsenceDto(date, FULL.getDuration(), FULL.name(), DayAbsenceDto.Type.NO_WORKDAY.name(), "", null));
            }
        }

        return absencesWithNoWorkdays;
    }

    private boolean isWorkday(LocalDate date, List<WorkingTime> workingTimeList) {
        return workingTimeList
            .stream()
            .filter(w -> w.getValidFrom().isBefore(date) || w.getValidFrom().isEqual(date))
            .findFirst()
            .map(w -> w.isWorkingDay(date.getDayOfWeek()))
            .orElse(false);
    }
    private Optional<DayAbsenceDto> dayAbsenceDtoForDate(String formattedDate, List<DayAbsenceDto> absences) {
        return absences.stream().filter(dayAbsenceDto -> dayAbsenceDto.getDate().equals(formattedDate)).findFirst();
    }

    private Map<LocalDate, PublicHoliday> publicHolidaysByDate(Person person, LocalDate start, LocalDate end) {
        return workingTimeService.getFederalStatesByPersonAndDateRange(person, new DateRange(start, end)).entrySet().stream()
            .map(entry -> publicHolidaysService.getPublicHolidays(entry.getKey().getStartDate(), entry.getKey().getEndDate(), entry.getValue()))
            .flatMap(List::stream)
            .collect(toMap(PublicHoliday::getDate, Function.identity()));
    }

    private Stream<DayAbsenceDto> toDayAbsenceDto(AbsencePeriod absence, Map<LocalDate, PublicHoliday> holidaysByDate) {
        return absence.getAbsenceRecords()
            .stream()
            .map(day -> this.toDayAbsenceDto(day, holidaysByDate.get(day.getDate())))
            .flatMap(List::stream);
    }

    private List<DayAbsenceDto> toDayAbsenceDto(AbsencePeriod.Record absenceRecord, PublicHoliday publicHoliday) {
        final List<DayAbsenceDto> result = new ArrayList<>(2);

        sickToDayAbsenceDto(absenceRecord, publicHoliday)
            .ifPresent(result::add);

        vacationToDayAbsenceDto(absenceRecord, publicHoliday)
            .ifPresent(result::add);

        return result;
    }

    private Optional<DayAbsenceDto> sickToDayAbsenceDto(AbsencePeriod.Record absenceRecord, PublicHoliday publicHoliday) {
        final LocalDate date = absenceRecord.getDate();

        final Optional<AbsencePeriod.RecordInfo> morning = absenceRecord.getMorning();
        final Optional<AbsencePeriod.RecordInfo> noon = absenceRecord.getNoon();
        final Optional<AbsencePeriod.Record.AbsenceType> morningType = morning.map(AbsencePeriod.RecordInfo::getType);
        final Optional<AbsencePeriod.Record.AbsenceType> noonType = noon.map(AbsencePeriod.RecordInfo::getType);

        final boolean publicHolidayMorning = publicHoliday != null && publicHoliday.isMorning();
        final boolean publicHolidayNoon = publicHoliday != null && publicHoliday.isNoon();
        final boolean sickMorning = morningType.map(AbsencePeriod.Record.AbsenceType.SICK_NOTE::equals).orElse(false);
        final boolean sickNoon = noonType.map(AbsencePeriod.Record.AbsenceType.SICK_NOTE::equals).orElse(false);
        final boolean sickFull = sickMorning && sickNoon;

        if (sickFull || (sickMorning && publicHolidayNoon) || (sickNoon && publicHolidayMorning)) {
            return morning.or(absenceRecord::getNoon).map(morningOrNoon -> toDayAbsenceDto(date, FULL, morningOrNoon));
        }
        if (sickMorning) {
            return morning.map(morningRecord -> toDayAbsenceDto(date, MORNING, morningRecord));
        }
        if (sickNoon) {
            return noon.map(noonRecord -> toDayAbsenceDto(date, NOON, noonRecord));
        }

        return Optional.empty();
    }

    private Optional<DayAbsenceDto> vacationToDayAbsenceDto(AbsencePeriod.Record absenceRecord, PublicHoliday publicHoliday) {
        final LocalDate date = absenceRecord.getDate();

        final Optional<AbsencePeriod.RecordInfo> morning = absenceRecord.getMorning();
        final Optional<AbsencePeriod.RecordInfo> noon = absenceRecord.getNoon();
        final Optional<AbsencePeriod.Record.AbsenceType> morningType = morning.map(AbsencePeriod.RecordInfo::getType);
        final Optional<AbsencePeriod.Record.AbsenceType> noonType = noon.map(AbsencePeriod.RecordInfo::getType);

        final boolean publicHolidayMorning = publicHoliday != null && publicHoliday.isMorning();
        final boolean publicHolidayNoon = publicHoliday != null && publicHoliday.isNoon();
        final boolean vacationMorning = morningType.map(AbsencePeriod.Record.AbsenceType.VACATION::equals).orElse(false);
        final boolean vacationNoon = noonType.map(AbsencePeriod.Record.AbsenceType.VACATION::equals).orElse(false);
        final boolean vacationFull = vacationMorning && vacationNoon;

        if (vacationFull || (vacationMorning && publicHolidayNoon) || (vacationNoon && publicHolidayMorning)) {
            return morning.or(absenceRecord::getNoon).map(morningOrNoon -> toDayAbsenceDto(date, FULL, morningOrNoon));
        }
        if (vacationMorning) {
            return morning.map(morningRecord -> toDayAbsenceDto(date, MORNING, morningRecord));
        }
        if (vacationNoon) {
            return noon.map(noonRecord -> toDayAbsenceDto(date, NOON, noonRecord));
        }

        return Optional.empty();
    }

    private DayAbsenceDto toDayAbsenceDto(LocalDate date, DayLength dayLength, AbsencePeriod.RecordInfo recordInfo) {
        final String type = toType(recordInfo.getType()).map(DayAbsenceDto.Type::name).orElse("");
        final String status = recordInfo.getStatus().name();
        return new DayAbsenceDto(date, dayLength.getDuration(), dayLength.name(), type, status, recordInfo.getId());
    }

    private DayAbsenceDto.Type toType(String dayAbsenceType) {
        if (dayAbsenceType == null) {
            return null;
        }
        try {
            return DayAbsenceDto.Type.valueOf(dayAbsenceType);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        }
    }

    private Optional<DayAbsenceDto.Type> toType(AbsencePeriod.Record.AbsenceType absenceType) {
        switch (absenceType) {
            case VACATION:
                return Optional.of(VACATION);
            case SICK_NOTE:
                return Optional.of(SICK_NOTE);
            default:
                return Optional.empty();
        }
    }
}
