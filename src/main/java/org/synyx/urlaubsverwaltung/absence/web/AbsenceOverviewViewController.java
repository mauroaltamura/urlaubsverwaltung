package org.synyx.urlaubsverwaltung.absence.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.synyx.urlaubsverwaltung.absence.AbsencePeriod;
import org.synyx.urlaubsverwaltung.absence.AbsenceService;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHoliday;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHolidaysService;
import org.synyx.urlaubsverwaltung.settings.SettingsService;
import org.synyx.urlaubsverwaltung.workingtime.FederalState;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Integer.parseInt;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.springframework.util.StringUtils.hasText;
import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.INACTIVE;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;

@RequestMapping("/web/absences")
@Controller
@Transactional
public class AbsenceOverviewViewController {

    private final PersonService personService;
    private final DepartmentService departmentService;
    private final MessageSource messageSource;
    private final Clock clock;
    private final PublicHolidaysService publicHolidaysService;
    private final SettingsService settingsService;
    private final AbsenceService absenceService;
    private final WorkingTimeService workingTimeService;

    @Autowired
    public AbsenceOverviewViewController(PersonService personService, DepartmentService departmentService,
                                         MessageSource messageSource, Clock clock,
                                         PublicHolidaysService publicHolidaysService, SettingsService settingsService,
                                         AbsenceService absenceService, WorkingTimeService workingTimeService) {
        this.personService = personService;
        this.departmentService = departmentService;
        this.messageSource = messageSource;
        this.clock = clock;
        this.publicHolidaysService = publicHolidaysService;
        this.settingsService = settingsService;
        this.absenceService = absenceService;
        this.workingTimeService = workingTimeService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(List.class, new CustomCollectionEditor(List.class));
    }

    @GetMapping
    public String absenceOverview(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) String month,
        @RequestParam(name = "department", required = false, defaultValue = "") List<String> rawSelectedDepartments, Model model, Locale locale) {

        final Person signedInUser = personService.getSignedInUser();

        final List<Person> overviewPersons;
        if (departmentService.getNumberOfDepartments() > 0) {

            final List<Department> visibleDepartments = departmentService.getDepartmentsPersonHasAccessTo(signedInUser);
            model.addAttribute("visibleDepartments", visibleDepartments);

            if (visibleDepartments.isEmpty()) {
                overviewPersons = List.of(signedInUser);
            } else {
                final List<String> selectedDepartmentNames = getSelectedDepartmentNames(rawSelectedDepartments, visibleDepartments);
                model.addAttribute("selectedDepartments", selectedDepartmentNames);

                overviewPersons = visibleDepartments.stream()
                    .filter(department -> selectedDepartmentNames.contains(department.getName()))
                    .map(Department::getMembers)
                    .flatMap(List::stream)
                    .filter(member -> !member.hasRole(INACTIVE))
                    .distinct()
                    .sorted(comparing(Person::getFirstName))
                    .collect(toList());
            }
        } else {
            overviewPersons = personService.getActivePersons();
        }

        final LocalDate startDate = getStartDate(year, month);
        final LocalDate endDate = getEndDate(year, month);

        model.addAttribute("currentYear", Year.now(clock).getValue());
        model.addAttribute("selectedYear", startDate.getYear());

        final String selectedMonth = getSelectedMonth(month, startDate);
        model.addAttribute("selectedMonth", selectedMonth);

        final List<Person> membersOfSignedInUser = getActiveMembersOfPerson(signedInUser);
        model.addAttribute("showRichLegend", !membersOfSignedInUser.isEmpty());

        final DateRange dateRange = new DateRange(startDate, endDate);
        final List<AbsenceOverviewMonthDto> months = getAbsenceOverViewMonthModels(dateRange, overviewPersons, locale, membersOfSignedInUser);
        final AbsenceOverviewDto absenceOverview = new AbsenceOverviewDto(months);
        model.addAttribute("absenceOverview", absenceOverview);

        return "thymeleaf/absences/absences-overview";
    }

    private List<String> getSelectedDepartmentNames(List<String> rawSelectedDepartments, List<Department> departments) {
        final List<String> preparedSelectedDepartments = rawSelectedDepartments.stream().filter(StringUtils::hasText).collect(toList());
        return preparedSelectedDepartments.isEmpty() ? List.of(departments.get(0).getName()) : preparedSelectedDepartments;
    }

    private List<AbsenceOverviewMonthDto> getAbsenceOverViewMonthModels(DateRange dateRange, List<Person> personList, Locale locale, List<Person> members) {
        final LocalDate today = LocalDate.now(clock);
        final HashMap<Integer, AbsenceOverviewMonthDto> monthsByNr = new HashMap<>();

        for (DateRange dateRangeSplitByMonth : dateRange.splitByMonth()) {
            final Map<Person, LocalDate> personNextDateCursor = personList.stream().collect(toMap(Function.identity(), unused -> dateRangeSplitByMonth.getStartDate()));

            final Map<Person, Map<LocalDate, List<AbsencePeriod>>> openAbsences = absenceService.getOpenAbsencesForPersons(personList, dateRangeSplitByMonth.getStartDate(), dateRangeSplitByMonth.getEndDate());

            final FederalState defaultFederalState = settingsService.getSettings().getWorkingTimeSettings().getFederalState();

            final Map<Person, Map<LocalDate, PublicHoliday>> publicHolidaysOfAllPersons = new HashMap<>();
            for (Person person : personList) {
                final Map<LocalDate, PublicHoliday> publicHolidaysOfPerson = getPublicHolidaysOfPerson(dateRangeSplitByMonth, person);
                publicHolidaysOfAllPersons.put(person, publicHolidaysOfPerson);
            }

            for (LocalDate date : dateRangeSplitByMonth) {

                final AbsenceOverviewMonthDto allPersonsMonthView = monthsByNr.computeIfAbsent(date.getMonthValue(),
                    monthValue -> this.initializeAbsenceOverviewMonthDto(date, personList, locale));

                final AbsenceOverviewMonthDayDto tableHeadDay = tableHeadDay(date, defaultFederalState, today, locale);
                allPersonsMonthView.getDays().add(tableHeadDay);

                final Map<AbsenceOverviewMonthPersonDto, Person> monthPersonDtoPersonMap = personList.stream()
                    .collect(
                        toMap(person -> allPersonsMonthView.getPersons().stream()
                            .filter(view -> view.getEmail().equals(person.getEmail()) &&
                                view.getFirstName().equals(person.getFirstName()) &&
                                view.getLastName().equals(person.getLastName())
                            )
                            .findFirst()
                            .orElse(null), Function.identity()
                        )
                    );

                // create an absence day dto for every person of the department
                for (AbsenceOverviewMonthPersonDto overviewMonthPersonDto : allPersonsMonthView.getPersons()) {
                    final Person person = monthPersonDtoPersonMap.get(overviewMonthPersonDto);

                    final Map<LocalDate, PublicHoliday> publicHolidayByDate = publicHolidaysOfAllPersons.get(person);

                    if (personNextDateCursor.get(person).isEqual(date)) {

                        final List<AbsencePeriod> absencePeriodsStartingAtDate = openAbsences.get(person).get(date);

                        if (absencePeriodsStartingAtDate.isEmpty()) {
                            // normal working day without absence.
                            // add morning and noon cell

                            final AbsenceOverviewPersonRowCellDto morning = new AbsenceOverviewPersonRowCellDto();
                            final AbsenceOverviewPersonRowCellDto noon = new AbsenceOverviewPersonRowCellDto();

                            final PublicHoliday publicHoliday = publicHolidayByDate.get(date);
                            if (publicHoliday != null) {
                                if (publicHoliday.isFull() || publicHoliday.isMorning()) {
                                    morning.setPublicHolidayCols(List.of(1));
                                }
                                if (publicHoliday.isFull() || publicHoliday.isNoon()) {
                                    noon.setPublicHolidayCols(List.of(1));
                                }
                            }

                            overviewMonthPersonDto.getDays().add(morning);
                            overviewMonthPersonDto.getDays().add(noon);

                            personNextDateCursor.put(person, date.plusDays(1));
                        } else if (absencePeriodsStartingAtDate.size() == 1) {
                            // sickNote OR applicationForLeave OR no-workday
                            final AbsencePeriod absencePeriod = absencePeriodsStartingAtDate.get(0);
                            final List<AbsencePeriod.Record> absenceRecords = absencePeriod.getAbsenceRecords();
                            if (absenceRecords.size() == 1) {
                                // morning OR noon OR fullDay
                                final AbsencePeriod.Record record = absenceRecords.get(0);
                                if (record.isHalfDayAbsence()) {
                                    final AbsenceOverviewPersonRowCellDto morning;
                                    final AbsenceOverviewPersonRowCellDto noon;
                                    if (record.getMorning().isPresent()) {
                                        final String type = recordInfoToCss(record.getMorning().orElseThrow());
                                        morning = new AbsenceOverviewPersonRowCellDto(1, type, true, true, true);
                                        noon = new AbsenceOverviewPersonRowCellDto(1, "");
                                    } else {
                                        final String type = recordInfoToCss(record.getNoon().orElseThrow());
                                        morning = new AbsenceOverviewPersonRowCellDto(1, "");
                                        noon = new AbsenceOverviewPersonRowCellDto(1, type, true, true, true);
                                    }
                                    final PublicHoliday publicHoliday = publicHolidayByDate.get(date);
                                    if (publicHoliday != null) {
                                        if (publicHoliday.isFull() || publicHoliday.isMorning()) {
                                            morning.setPublicHolidayCols(List.of(1));
                                        }
                                        if (publicHoliday.isFull() || publicHoliday.isNoon()) {
                                            noon.setPublicHolidayCols(List.of(1));
                                        }
                                    }
                                    overviewMonthPersonDto.getDays().add(morning);
                                    overviewMonthPersonDto.getDays().add(noon);
                                } else {
                                    final String type = recordInfoToCss(record.getMorning().orElseThrow());
                                    final AbsenceOverviewPersonRowCellDto morningAndNoon = new AbsenceOverviewPersonRowCellDto(2, type, absencePeriod.isIncludesBeginning(), absencePeriod.isIncludesEnd(), true);
                                    final PublicHoliday publicHoliday = publicHolidayByDate.get(date);
                                    if (publicHoliday != null) {
                                        if (publicHoliday.isMorning()) {
                                            morningAndNoon.setPublicHolidayCols(List.of(1));
                                        }
                                        else if (publicHoliday.isNoon()) {
                                            morningAndNoon.setPublicHolidayCols(List.of(2));
                                        }
                                        else if (publicHoliday.isFull()) {
                                            morningAndNoon.setPublicHolidayCols(List.of(1, 2));
                                        }
                                    }
                                    overviewMonthPersonDto.getDays().add(morningAndNoon);
                                }
                                personNextDateCursor.put(person, date.plusDays(1));
                            } else {
                                // full day absence stretched over multiple days, maybe interrupted by no-workday or public-holiday
                                final List<AbsenceOverviewPersonRowCellDto> cellDtos = new ArrayList<>();
                                boolean isFirst = true;
                                boolean isLast = true;
                                int colspan = 0;
                                List<Integer> publicHolidayCols = new ArrayList<>();
                                String type = null;

                                final Map<LocalDate, Optional<AbsencePeriod.Record>> absencePeriodRecordsByDate = absencePeriod.recordsByDate();
                                final DateRange absencePeriodDateRange = absencePeriod.getDateRange();

                                LocalDate ddddate = absencePeriodDateRange.getEndDate();
                                // maximum cols could be from 0 to endDate.
                                // multiply with 2 since a day is constructed with morning and noon.
                                // add 1 since x.until(x) is 0, however, 1 is required due to inclusiveness.
                                int publicHolidayCol = 2 * (1 + Math.toIntExact(absencePeriodDateRange.getStartDate().until(ddddate, DAYS)));
                                while (ddddate.isEqual(absencePeriodDateRange.getStartDate()) || ddddate.isAfter(absencePeriodDateRange.getStartDate())) {

                                    final PublicHoliday publicHoliday = publicHolidayByDate.get(ddddate);
                                    if (publicHoliday != null) {
                                        if (publicHoliday.isNoon()) {
                                            publicHolidayCols.add(publicHolidayCol);
                                        } else if (publicHoliday.isMorning()) {
                                            publicHolidayCols.add(publicHolidayCol - 1);
                                        } else if (publicHoliday.isFull()) {
                                            publicHolidayCols.add(publicHolidayCol);
                                            publicHolidayCols.add(publicHolidayCol - 1);
                                        }
                                    }
                                    publicHolidayCol -= 2;

                                    final Optional<AbsencePeriod.Record> maybeRecord = absencePeriodRecordsByDate.get(ddddate);
                                    if (maybeRecord.isPresent()) {
                                        final AbsencePeriod.Record record = maybeRecord.get();

                                        isFirst = record.getDate().isEqual(absencePeriodDateRange.getStartDate());
                                        final boolean isRoundedLeft = isFirst && absencePeriod.isIncludesBeginning();
                                        final boolean isRoundedRight = isLast && absencePeriod.isIncludesEnd();

                                        if (isNoWorkdayRecord(record)) {
                                            if (type != null) {
                                                final AbsenceOverviewPersonRowCellDto cell = new AbsenceOverviewPersonRowCellDto(colspan, type, isRoundedLeft, isRoundedRight, isFirst);
                                                // reverse list since we're building it backwards from end to start.
                                                Collections.reverse(publicHolidayCols);
                                                cell.setPublicHolidayCols(publicHolidayCols);
                                                publicHolidayCols = new ArrayList<>();

                                                cellDtos.add(cell);
                                                type = null;
                                            }

                                            final AbsenceOverviewPersonRowCellDto cell = new AbsenceOverviewPersonRowCellDto(2, "no-workday");
                                            cell.setPublicHolidayCols(publicHolidayCols);
                                            publicHolidayCols = new ArrayList<>();

                                            cellDtos.add(cell);
                                            colspan = 0;
                                            isLast = false;
                                        } else {
                                            // absence day (sickNote or applicationForLeave)
                                            //
                                            type = record.getMorning().map(AbsenceOverviewViewController::recordInfoToCss).orElseThrow();
                                            colspan += 2;
                                            if (isFirst) {
                                                final AbsenceOverviewPersonRowCellDto cell = new AbsenceOverviewPersonRowCellDto(colspan, type, isRoundedLeft, isRoundedRight, true);
                                                cell.setPublicHolidayCols(publicHolidayCols);
                                                publicHolidayCols = new ArrayList<>();

                                                cellDtos.add(cell);
                                            }
                                        }
                                    } else {
                                        // gap in an absencePeriod -> public holiday.
                                        colspan += 2;
                                    }

                                    ddddate = ddddate.minusDays(1);
                                }

                                Collections.reverse(cellDtos);
                                for (AbsenceOverviewPersonRowCellDto cellDto : cellDtos) {
                                    overviewMonthPersonDto.getDays().add(cellDto);
                                }

                                personNextDateCursor.put(person, absenceRecords.get(absenceRecords.size() - 1).getDate().plusDays(1));
                            }

                        } else {
                            // morning: sickNote OR applicationForLeave
                            // noon: applicationForLeave OR sickNote

                            final AbsencePeriod firstAbsencePeriod = absencePeriodsStartingAtDate.get(0);
                            final AbsencePeriod secondAbsencePeriod = absencePeriodsStartingAtDate.get(1);

                            final AbsencePeriod.RecordInfo morning;
                            final AbsencePeriod.RecordInfo noon;

                            if (firstAbsencePeriod.getAbsenceRecords().get(0).getMorning().isPresent()) {
                                morning = firstAbsencePeriod.getAbsenceRecords().get(0).getMorning().orElseThrow();
                                noon = secondAbsencePeriod.getAbsenceRecords().get(0).getNoon().orElseThrow();
                            } else {
                                morning = secondAbsencePeriod.getAbsenceRecords().get(0).getMorning().orElseThrow();
                                noon = firstAbsencePeriod.getAbsenceRecords().get(0).getNoon().orElseThrow();
                            }

                            overviewMonthPersonDto.getDays().add(new AbsenceOverviewPersonRowCellDto(1, recordInfoToCss(morning), true, false, true));
                            overviewMonthPersonDto.getDays().add(new AbsenceOverviewPersonRowCellDto(1, recordInfoToCss(noon), false, true, true));

                            personNextDateCursor.put(person, date.plusDays(1));
                        }
                    }
                }
            }
        }


        return new ArrayList<>(monthsByNr.values());
    }

    private static String recordInfoToCss(AbsencePeriod.RecordInfo recordInfo) {
        final AbsencePeriod.Record.AbsenceType type = recordInfo.getType();
        switch (type) {
            case VACATION:
                final AbsencePeriod.Record.AbsenceStatus status = recordInfo.getStatus();
                return status.equals(AbsencePeriod.Record.AbsenceStatus.ALLOWED) ? "vacation-approved" : "vacation-waiting";
            case SICK_NOTE:
                return "sick-note";
            case NO_WORKDAY:
                return "no-workday";
        }
        return "";
    }


    private Map<LocalDate, PublicHoliday> getPublicHolidaysOfPerson(DateRange dateRange, Person person) {
        return workingTimeService.getFederalStatesByPersonAndDateRange(person, dateRange)
            .entrySet().stream()
            .map(entry -> publicHolidaysService.getPublicHolidays(entry.getKey().getStartDate(), entry.getKey().getEndDate(), entry.getValue()))
            .flatMap(List::stream)
            .collect(toMap(PublicHoliday::getDate, Function.identity()));
    }

    private AbsenceOverviewMonthDto initializeAbsenceOverviewMonthDto(LocalDate date, List<Person> personList, Locale locale) {

        final List<AbsenceOverviewMonthPersonDto> monthViewPersons = personList.stream()
            .map(AbsenceOverviewViewController::initializeAbsenceOverviewMonthPersonDto)
            .collect(toList());

        return new AbsenceOverviewMonthDto(getMonthText(date, locale), new ArrayList<>(), monthViewPersons);
    }

    private static AbsenceOverviewMonthPersonDto initializeAbsenceOverviewMonthPersonDto(Person person) {

        final String firstName = person.getFirstName();
        final String lastName = person.getLastName();
        final String email = person.getEmail();
        final String gravatarUrl = person.getGravatarURL();

        return new AbsenceOverviewMonthPersonDto(firstName, lastName, email, gravatarUrl, new ArrayList<>(), new ArrayList<>());
    }

    private AbsenceOverviewDayType.Builder getAbsenceOverviewDayType(List<AbsencePeriod.Record> absenceRecords, List<Person> members, PublicHoliday publicHoliday) {
        AbsenceOverviewDayType.Builder builder = getAbsenceOverviewDayType(absenceRecords, members);
        if (publicHoliday.getDayLength().equals(DayLength.MORNING)) {
            builder = builder.publicHolidayMorning();
        }
        if (publicHoliday.getDayLength().equals(DayLength.NOON)) {
            builder = builder.publicHolidayNoon();
        }
        if (publicHoliday.getDayLength().equals(DayLength.FULL)) {
            builder = builder.publicHolidayFull();
        }
        return builder;
    }

    private AbsenceOverviewDayType.Builder getAbsenceOverviewDayType(List<AbsencePeriod.Record> absenceRecords, List<Person> members) {
        if (absenceRecords.isEmpty()) {
            return AbsenceOverviewDayType.builder();
        }

        AbsenceOverviewDayType.Builder builder = AbsenceOverviewDayType.builder();
        for (AbsencePeriod.Record absenceRecord : absenceRecords) {
            final boolean showAllInformation = members.contains(absenceRecord.getPerson());
            if (absenceRecord.isHalfDayAbsence()) {
                builder = getAbsenceOverviewDayTypeForHalfDay(builder, absenceRecord, showAllInformation);
            } else {
                builder = getAbsenceOverviewDayTypeForFullDay(builder, absenceRecord, showAllInformation);
            }
            if (isNoWorkdayRecord(absenceRecord)) {
                builder = builder.noWorkday();
            }
        }

        return builder;
    }

    private static boolean isNoWorkdayRecord(AbsencePeriod.Record record) {
        // no-workday is only possible for a full day currently.
        boolean morning = record.getMorning().map(recordInfo -> recordInfo instanceof AbsencePeriod.RecordMorningNoWorkday).orElse(false);
        boolean noon = record.getNoon().map(recordInfo -> recordInfo instanceof AbsencePeriod.RecordNoonNoWorkday).orElse(false);
        return morning && noon;
    }

    private AbsenceOverviewDayType.Builder getAbsenceOverviewDayTypeForHalfDay(AbsenceOverviewDayType.Builder builder, AbsencePeriod.Record absenceRecord, boolean showAllInformation) {
        final AbsencePeriod.Record.AbsenceType morningAbsenceType = absenceRecord.getMorning().map(AbsencePeriod.RecordInfo::getType).orElse(null);
        final AbsencePeriod.Record.AbsenceType noonAbsenceType = absenceRecord.getNoon().map(AbsencePeriod.RecordInfo::getType).orElse(null);

        if (AbsencePeriod.Record.AbsenceType.SICK_NOTE.equals(morningAbsenceType)) {
            return showAllInformation ? builder.sickNoteMorning() : builder.absenceMorning();
        }
        if (AbsencePeriod.Record.AbsenceType.SICK_NOTE.equals(noonAbsenceType)) {
            return showAllInformation ? builder.sickNoteNoon() : builder.absenceNoon();
        }

        final boolean morningWaiting = absenceRecord.getMorning().map(AbsencePeriod.RecordInfo::hasStatusWaiting).orElse(false);
        if (morningWaiting) {
            return showAllInformation ? builder.waitingVacationMorning() : builder.absenceMorning();
        }
        final boolean morningAllowed = absenceRecord.getMorning().map(AbsencePeriod.RecordInfo::hasStatusAllowed).orElse(false);
        if (morningAllowed) {
            return showAllInformation ? builder.allowedVacationMorning() : builder.absenceMorning();
        }

        final boolean noonWaiting = absenceRecord.getNoon().map(AbsencePeriod.RecordInfo::hasStatusWaiting).orElse(false);
        if (noonWaiting) {
            return showAllInformation ? builder.waitingVacationNoon() : builder.absenceNoon();
        }

        return showAllInformation ? builder.allowedVacationNoon() : builder.absenceNoon();
    }

    private AbsenceOverviewDayType.Builder getAbsenceOverviewDayTypeForFullDay(AbsenceOverviewDayType.Builder builder, AbsencePeriod.Record absenceRecord, boolean showAllInformation) {
        final Optional<AbsencePeriod.RecordInfo> morning = absenceRecord.getMorning();
        final Optional<AbsencePeriod.RecordInfo> noon = absenceRecord.getNoon();
        final Optional<AbsencePeriod.Record.AbsenceType> morningType = morning.map(AbsencePeriod.RecordInfo::getType);
        final Optional<AbsencePeriod.Record.AbsenceType> noonType = noon.map(AbsencePeriod.RecordInfo::getType);

        final boolean sickMorning = morningType.map(AbsencePeriod.Record.AbsenceType.SICK_NOTE::equals).orElse(false);
        final boolean sickNoon = noonType.map(AbsencePeriod.Record.AbsenceType.SICK_NOTE::equals).orElse(false);
        final boolean sickFull = sickMorning && sickNoon;

        if (sickFull) {
            return showAllInformation ? builder.sickNoteFull() : builder.absenceFull();
        }

        final boolean morningWaiting = morning.map(AbsencePeriod.RecordInfo::hasStatusWaiting).orElse(false);
        final boolean noonWaiting = noon.map(AbsencePeriod.RecordInfo::hasStatusWaiting).orElse(false);

        if (morningWaiting && noonWaiting) {
            return showAllInformation ? builder.waitingVacationFull() : builder.absenceFull();
        } else if (!morningWaiting && !noonWaiting) {
            return showAllInformation ? builder.allowedVacationFull() : builder.absenceFull();
        }

        return builder;
    }

    private AbsenceOverviewDayType.Builder getPublicHolidayType(DayLength dayLength) {
        final AbsenceOverviewDayType.Builder builder = AbsenceOverviewDayType.builder();
        switch (dayLength) {
            case MORNING:
                return builder.publicHolidayMorning();
            case NOON:
                return builder.publicHolidayNoon();
            case FULL:
            default:
                return builder.publicHolidayFull();
        }
    }

    private String getSelectedMonth(String month, LocalDate startDate) {
        String selectedMonth = "";
        if (month == null) {
            selectedMonth = String.valueOf(startDate.getMonthValue());
        } else if (hasText(month)) {
            selectedMonth = month;
        }
        return selectedMonth;
    }

    private AbsenceOverviewMonthDayDto tableHeadDay(LocalDate date, FederalState defaultFederalState, LocalDate today, Locale locale) {
        final Optional<PublicHoliday> maybePublicHoliday = publicHolidaysService.getPublicHoliday(date, defaultFederalState);
        final DayLength publicHolidayDayLength = maybePublicHoliday.isPresent() ? maybePublicHoliday.get().getDayLength() : DayLength.ZERO;

        AbsenceOverviewDayType publicHolidayType = null;
        if (DayLength.ZERO.compareTo(publicHolidayDayLength) != 0) {
            publicHolidayType = getPublicHolidayType(publicHolidayDayLength).build();
        }

        final String tableHeadDayText = String.format("%02d", date.getDayOfMonth());
        final String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale);
        final boolean isToday = date.isEqual(today);

        return new AbsenceOverviewMonthDayDto(publicHolidayType, tableHeadDayText, dayOfWeek, isWeekend(date), isToday);
    }

    private String getMonthText(LocalDate date, Locale locale) {
        return messageSource.getMessage(getMonthMessageCode(date), new Object[]{}, locale);
    }

    private String getMonthMessageCode(LocalDate localDate) {
        switch (localDate.getMonthValue()) {
            case 1:
                return "month.january";
            case 2:
                return "month.february";
            case 3:
                return "month.march";
            case 4:
                return "month.april";
            case 5:
                return "month.may";
            case 6:
                return "month.june";
            case 7:
                return "month.july";
            case 8:
                return "month.august";
            case 9:
                return "month.september";
            case 10:
                return "month.october";
            case 11:
                return "month.november";
            case 12:
                return "month.december";
            default:
                throw new IllegalStateException("month value not in range of 1 to 12 cannot be mapped to a message key.");
        }
    }

    private LocalDate getStartDate(Integer year, String month) {
        return getStartOrEndDate(year, month, TemporalAdjusters::firstDayOfYear, TemporalAdjusters::firstDayOfMonth);
    }

    private LocalDate getEndDate(Integer year, String month) {
        return getStartOrEndDate(year, month, TemporalAdjusters::lastDayOfYear, TemporalAdjusters::lastDayOfMonth);
    }

    private LocalDate getStartOrEndDate(Integer year, String month, Supplier<TemporalAdjuster> firstOrLastOfYearSupplier,
                                        Supplier<TemporalAdjuster> firstOrLastOfMonthSupplier) {

        final LocalDate now = LocalDate.now(clock);

        if (year != null) {
            if (hasText(month)) {
                return now.withYear(year).withMonth(parseInt(month)).with(firstOrLastOfMonthSupplier.get());
            }
            if ("".equals(month)) {
                return now.withYear(year).with(firstOrLastOfYearSupplier.get());
            }
            return now.withYear(year).with(firstOrLastOfMonthSupplier.get());
        }

        if (hasText(month)) {
            return now.withMonth(parseInt(month)).with(firstOrLastOfMonthSupplier.get());
        }
        return now.with(firstOrLastOfMonthSupplier.get());
    }

    private static boolean isWeekend(LocalDate date) {
        final DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == SATURDAY || dayOfWeek == SUNDAY;
    }

    private List<Person> getActiveMembersOfPerson(final Person person) {

        if (person.hasRole(BOSS) || person.hasRole(OFFICE)) {
            return personService.getActivePersons();
        }

        final List<Person> relevantPersons = new ArrayList<>();
        if (person.hasRole(DEPARTMENT_HEAD)) {
            departmentService.getMembersForDepartmentHead(person).stream()
                .filter(member -> !member.hasRole(INACTIVE))
                .collect(toCollection(() -> relevantPersons));
        }

        if (person.hasRole(SECOND_STAGE_AUTHORITY)) {
            departmentService.getMembersForSecondStageAuthority(person).stream()
                .filter(member -> !member.hasRole(INACTIVE))
                .collect(toCollection(() -> relevantPersons));
        }

        return relevantPersons.stream()
            .distinct()
            .collect(toList());
    }
}
