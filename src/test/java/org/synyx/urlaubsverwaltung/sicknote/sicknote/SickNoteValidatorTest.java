package org.synyx.urlaubsverwaltung.sicknote.sicknote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.overlap.OverlapService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static java.time.Month.DECEMBER;
import static java.time.Month.MARCH;
import static java.time.Month.NOVEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.TestDataCreator.createSickNote;
import static org.synyx.urlaubsverwaltung.TestDataCreator.createWorkingTime;
import static org.synyx.urlaubsverwaltung.overlap.OverlapCase.FULLY_OVERLAPPING;
import static org.synyx.urlaubsverwaltung.overlap.OverlapCase.NO_OVERLAPPING;
import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.period.DayLength.MORNING;
import static org.synyx.urlaubsverwaltung.period.DayLength.NOON;
import static org.synyx.urlaubsverwaltung.person.Role.ADMIN;
import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

/**
 * Unit test for {@link SickNoteValidator}.
 */
@ExtendWith(MockitoExtension.class)
class SickNoteValidatorTest {

    private SickNoteValidator sut;
    private final Clock clock = Clock.systemUTC();

    @Mock
    private OverlapService overlapService;
    @Mock
    private WorkingTimeService workingTimeService;
    @Mock
    private DepartmentService departmentService;

    @BeforeEach
    void setUp() {
        sut = new SickNoteValidator(overlapService, workingTimeService, departmentService, Clock.systemUTC());
    }

    @Test
    void ensureNoApplierReturnsNoErrorOnEdit() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isOne();
    }

    @Test
    void ensureApplierWithWrongRoleReturnsError() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("dh", "department", "head", "department@example.org");
        applier.setPermissions(List.of(USER, ADMIN));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isOne();
    }

    @Test
    void ensureDepartmentHeadApplierForWrongDepartmentReturnsError() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("dh", "department", "head", "department@example.org");
        applier.setPermissions(List.of(USER, DEPARTMENT_HEAD));
        sickNote.setApplier(applier);
        when(departmentService.isDepartmentHeadAllowedToManagePerson(applier, person)).thenReturn(false);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isOne();
    }

    @Test
    void ensureSecondStageAuthorityApplierForWrongDepartmentReturnsError() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("ssa", "ssa", "ssa", "ssa@example.org");
        applier.setPermissions(List.of(USER, SECOND_STAGE_AUTHORITY));
        sickNote.setApplier(applier);
        when(departmentService.isSecondStageAuthorityAllowedToManagePerson(applier, person)).thenReturn(false);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isOne();
    }

    @Test
    void ensureValidOfficeApplierHasNoErrors() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureValidBossApplierHasNoErrors() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("boss", "boss", "boss", "boss@example.org");
        applier.setPermissions(List.of(USER, BOSS));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureValidDepartmentHeadApplierHasNoErrors() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("dh", "department", "head", "department@example.org");
        applier.setPermissions(List.of(USER, DEPARTMENT_HEAD));
        sickNote.setApplier(applier);
        when(departmentService.isDepartmentHeadAllowedToManagePerson(applier, person)).thenReturn(true);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureValidSecondStageAuthorityApplierHasNoErrors() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final SickNote sickNote = createSickNote(person,
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("ssa", "second stage authority", "second stage authority", "ssa@example.org");
        applier.setPermissions(List.of(USER, SECOND_STAGE_AUTHORITY));
        sickNote.setApplier(applier);
        when(departmentService.isSecondStageAuthorityAllowedToManagePerson(applier, person)).thenReturn(true);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureValidDatesHaveNoErrors() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureDayLengthMayNotBeNull() {

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 20),
            null);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("dayLength").get(0).getCode()).isEqualTo("error.entry.mandatory");
    }

    @Test
    void ensureStartDateMayNotBeNull() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            null,
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("startDate").get(0).getCode()).isEqualTo("error.entry.mandatory");
    }

    @Test
    void ensureEndDateMayNotBeNull() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 19),
            null,
            FULL);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("endDate").get(0).getCode()).isEqualTo("error.entry.mandatory");
    }

    @Test
    void ensureStartDateMustBeBeforeEndDateToHaveAValidPeriod() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, DECEMBER, 10),
            LocalDate.of(2013, DECEMBER, 1),
            FULL);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("endDate").get(0).getCode()).isEqualTo("error.entry.invalidPeriod");

        verifyNoInteractions(overlapService, workingTimeService);
    }

    @Test
    void ensureStartAndEndDateMustBeEqualsDatesForDayLengthNoon() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 21),
            NOON);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("endDate").get(0).getCode()).isEqualTo("sicknote.error.halfDayPeriod");
    }

    @Test
    void ensureStartAndEndDateMustBeEqualsDatesForDayLengthMorning() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 19),
            LocalDate.of(2013, NOVEMBER, 21),
            MORNING);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("endDate").get(0).getCode()).isEqualTo("sicknote.error.halfDayPeriod");
    }

    @Test
    void ensureStartDateMustBeBeforeEndDateToHaveAValidPeriodForDayLengthMorning() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 21),
            LocalDate.of(2013, NOVEMBER, 19),
            MORNING);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("endDate").get(0).getCode()).isEqualTo("error.entry.invalidPeriod");
    }

    @Test
    void ensureStartDateMustBeBeforeEndDateToHaveAValidPeriodForDayLengthNoon() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 21),
            LocalDate.of(2013, NOVEMBER, 19),
            NOON);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("endDate").get(0).getCode()).isEqualTo("error.entry.invalidPeriod");
    }

    @Test
    void ensureAUStartDateMustBeBeforeAUEndDateToHaveAValidPeriod() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 30),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 20));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 19));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("aubEndDate").get(0).getCode()).isEqualTo("error.entry.invalidPeriod");
    }

    @Test
    void ensureValidAUPeriodHasNoErrors() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 30),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 19));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 20));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodMultipleDays() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 30),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 1));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 30));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodMultipleDaysStart() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 30),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 1));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 10));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodMultipleDaysEnd() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 30),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 20));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 30));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodMultipleDaysStartOverlapping() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 20),
            LocalDate.of(2013, NOVEMBER, 30),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 1));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 20));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("aubStartDate").get(0).getCode()).isEqualTo("sicknote.error.aubInvalidPeriod");
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodMultipleDaysEndOverlapping() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 20));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 30));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("aubEndDate").get(0).getCode()).isEqualTo("sicknote.error.aubInvalidPeriod");
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodMultipleDaysNoneOverlapping() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 10),
            LocalDate.of(2013, NOVEMBER, 20),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 1));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 9));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("aubStartDate").get(0).getCode()).isEqualTo("sicknote.error.aubInvalidPeriod");
        assertThat(errors.getFieldErrors("aubEndDate").get(0).getCode()).isEqualTo("sicknote.error.aubInvalidPeriod");
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodOneDay() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 1),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 1));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 1));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getErrorCount()).isZero();
    }

    @Test
    void ensureAUPeriodMustBeWithinSickNotePeriodButIsNotForOneDay() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 1),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 2));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 2));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("aubStartDate").get(0).getCode()).isEqualTo("sicknote.error.aubInvalidPeriod");
    }

    @Test
    void ensureSickNoteMustNotHaveAnyOverlapping() {

        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, MARCH, 1),
            LocalDate.of(2013, MARCH, 10),
            FULL);
        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(FULLY_OVERLAPPING);

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getGlobalErrors().get(0).getCode()).isEqualTo("application.error.overlap");
    }

    @Test
    void ensureWorkingTimeConfigurationMustExistForPeriodOfSickNote() {
        final LocalDate startDate = LocalDate.of(2015, MARCH, 1);
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            startDate,
            LocalDate.of(2015, MARCH, 10),
            FULL);

        when(workingTimeService.getWorkingTime(any(Person.class), any(LocalDate.class))).thenReturn(Optional.empty());

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getGlobalErrors().get(0).getCode()).isEqualTo("sicknote.error.noValidWorkingTime");
        verify(workingTimeService).getWorkingTime(sickNote.getPerson(), startDate);
    }

    @Test
    void ensureInvalidPeriodWithValidAUBPeriodIsNotValid() {
        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 10),
            LocalDate.of(2013, NOVEMBER, 4),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 1));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 2));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("endDate").get(0).getCode()).isEqualTo("error.entry.invalidPeriod");
    }

    @Test
    void ensureInvalidAUBPeriodWithValidPeriodIsNotValid() {

        when(overlapService.checkOverlap(any(SickNote.class))).thenReturn(NO_OVERLAPPING);
        when(workingTimeService.getWorkingTime(any(Person.class),
            any(LocalDate.class))).thenReturn(Optional.of(createWorkingTime()));

        final SickNote sickNote = createSickNote(new Person("muster", "Muster", "Marlene", "muster@example.org"),
            LocalDate.of(2013, NOVEMBER, 1),
            LocalDate.of(2013, NOVEMBER, 4),
            FULL);
        sickNote.setAubStartDate(LocalDate.of(2013, NOVEMBER, 2));
        sickNote.setAubEndDate(LocalDate.of(2013, NOVEMBER, 1));

        final Person applier = new Person("office", "office", "office", "office@example.org");
        applier.setPermissions(List.of(USER, OFFICE));
        sickNote.setApplier(applier);

        final Errors errors = new BeanPropertyBindingResult(sickNote, "sickNote");
        sut.validate(sickNote, errors);
        assertThat(errors.getFieldErrors("aubEndDate").get(0).getCode()).isEqualTo("error.entry.invalidPeriod");
    }
}
