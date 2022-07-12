package org.synyx.urlaubsverwaltung.sicknote.sicknote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.Errors;
import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeDto;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeService;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeViewModelService;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.Role;
import org.synyx.urlaubsverwaltung.settings.Settings;
import org.synyx.urlaubsverwaltung.settings.SettingsService;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentForm;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentFormValidator;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentService;
import org.synyx.urlaubsverwaltung.sicknote.sicknotetype.SickNoteType;
import org.synyx.urlaubsverwaltung.sicknote.sicknotetype.SickNoteTypeService;
import org.synyx.urlaubsverwaltung.workingtime.WorkDaysCountService;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.HOLIDAY;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.OVERTIME;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeColor.ORANGE;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeColor.YELLOW;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;
import static org.synyx.urlaubsverwaltung.person.Role.USER;
import static org.synyx.urlaubsverwaltung.sicknote.sicknote.SickNoteStatus.ACTIVE;

@ExtendWith(MockitoExtension.class)
class SickNoteViewControllerTest {

    private SickNoteViewController sut;

    private final int UNKNOWN_SICK_NOTE_ID = 0;
    private final int SOME_SICK_NOTE_ID = 15;

    @Mock
    private SickNoteService sickNoteService;
    @Mock
    private SickNoteInteractionService sickNoteInteractionService;
    @Mock
    private SickNoteCommentService sickNoteCommentService;
    @Mock
    private SickNoteTypeService sickNoteTypeService;
    @Mock
    private VacationTypeService vacationTypeService;
    @Mock
    private VacationTypeViewModelService vacationTypeViewModelService;
    @Mock
    private PersonService personService;
    @Mock
    private DepartmentService departmentService;
    @Mock
    private WorkDaysCountService workDaysCountService;
    @Mock
    private SickNoteValidator sickNoteValidator;
    @Mock
    private SickNoteCommentFormValidator sickNoteCommentFormValidator;
    @Mock
    private SickNoteConvertFormValidator sickNoteConvertFormValidator;
    @Mock
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        sut = new SickNoteViewController(sickNoteService,
            sickNoteInteractionService, sickNoteCommentService, sickNoteTypeService,
            vacationTypeService, vacationTypeViewModelService, personService, departmentService, workDaysCountService, sickNoteValidator,
            sickNoteCommentFormValidator, sickNoteConvertFormValidator, settingsService, Clock.systemUTC());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OFFICE", "BOSS"})
    void ensureGetNewSickNoteProvidesCorrectModelAttributesAndViewForRole(Role role) throws Exception {

        final Person personWithRole = personWithRole(role);
        when(personService.getSignedInUser()).thenReturn(personWithRole);

        final List<Person> activePersons = of(somePerson());
        when(personService.getActivePersons()).thenReturn(activePersons);
        final List<SickNoteType> sickNoteTypes = of(someSickNoteType());
        when(sickNoteTypeService.getSickNoteTypes()).thenReturn(sickNoteTypes);

        perform(get("/web/sicknote/new"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sickNote", instanceOf(SickNoteForm.class)))
            .andExpect(model().attribute("persons", activePersons))
            .andExpect(model().attribute("sickNoteTypes", sickNoteTypes))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensureGetNewSickNoteProvidesCorrectModelAttributesAndViewForDepartmentHead() throws Exception {

        final Person departmentHead = personWithRole(DEPARTMENT_HEAD);
        when(personService.getSignedInUser()).thenReturn(departmentHead);

        final List<Person> departmentPersons = of(somePerson());
        when(departmentService.getMembersForDepartmentHead(departmentHead)).thenReturn(departmentPersons);
        final List<SickNoteType> sickNoteTypes = of(someSickNoteType());
        when(sickNoteTypeService.getSickNoteTypes()).thenReturn(sickNoteTypes);

        perform(get("/web/sicknote/new"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sickNote", instanceOf(SickNoteForm.class)))
            .andExpect(model().attribute("persons", departmentPersons))
            .andExpect(model().attribute("sickNoteTypes", sickNoteTypes))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensureGetNewSickNoteProvidesCorrectModelAttributesAndViewForSecondStageAuthority() throws Exception {

        final Person secondStageAuthority = personWithRole(SECOND_STAGE_AUTHORITY);
        when(personService.getSignedInUser()).thenReturn(secondStageAuthority);

        final List<Person> departmentPersons = of(somePerson());
        when(departmentService.getMembersForSecondStageAuthority(secondStageAuthority)).thenReturn(departmentPersons);
        final List<SickNoteType> sickNoteTypes = of(someSickNoteType());
        when(sickNoteTypeService.getSickNoteTypes()).thenReturn(sickNoteTypes);

        perform(get("/web/sicknote/new"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sickNote", instanceOf(SickNoteForm.class)))
            .andExpect(model().attribute("persons", departmentPersons))
            .andExpect(model().attribute("sickNoteTypes", sickNoteTypes))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensureGetNewSickNoteProvidesCorrectModelAttributesAndViewForDepartmentHeadAndSecondStageAuthority() throws Exception {

        final Person departmentHeadAndSsa = new Person();
        departmentHeadAndSsa.setId(1);
        departmentHeadAndSsa.setPermissions(List.of(USER, DEPARTMENT_HEAD, SECOND_STAGE_AUTHORITY));
        when(personService.getSignedInUser()).thenReturn(departmentHeadAndSsa);

        final Person person = somePerson();
        person.setId(2);
        person.setFirstName("firstname");
        person.setLastName("lastname");
        when(departmentService.getMembersForDepartmentHead(departmentHeadAndSsa)).thenReturn(of(person));

        final Person person2 = somePerson();
        person2.setId(3);
        person2.setFirstName("firstname two");
        person2.setLastName("lastname two");
        when(departmentService.getMembersForSecondStageAuthority(departmentHeadAndSsa)).thenReturn(of(person2));

        final List<SickNoteType> sickNoteTypes = of(someSickNoteType());
        when(sickNoteTypeService.getSickNoteTypes()).thenReturn(sickNoteTypes);

        perform(get("/web/sicknote/new"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sickNote", instanceOf(SickNoteForm.class)))
            .andExpect(model().attribute("persons", of(person, person2)))
            .andExpect(model().attribute("sickNoteTypes", sickNoteTypes))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensureGetNewSickNoteManagedMembersDistinct() throws Exception {

        final Person departmentHeadAndSsa = new Person();
        departmentHeadAndSsa.setId(1);
        departmentHeadAndSsa.setPermissions(List.of(USER, DEPARTMENT_HEAD, SECOND_STAGE_AUTHORITY));
        when(personService.getSignedInUser()).thenReturn(departmentHeadAndSsa);

        final Person person = somePerson();
        person.setId(2);
        person.setFirstName("firstname");
        person.setLastName("lastname");
        when(departmentService.getMembersForDepartmentHead(departmentHeadAndSsa)).thenReturn(of(person));
        when(departmentService.getMembersForSecondStageAuthority(departmentHeadAndSsa)).thenReturn(of(person));

        final List<SickNoteType> sickNoteTypes = of(someSickNoteType());
        when(sickNoteTypeService.getSickNoteTypes()).thenReturn(sickNoteTypes);

        perform(get("/web/sicknote/new"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sickNote", instanceOf(SickNoteForm.class)))
            .andExpect(model().attribute("persons", of(person)))
            .andExpect(model().attribute("sickNoteTypes", sickNoteTypes))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensureGetNewSickNoteManagedMembersIsOrdered() throws Exception {

        final Person departmentHeadAndSsa = new Person();
        departmentHeadAndSsa.setId(1);
        departmentHeadAndSsa.setPermissions(List.of(USER, DEPARTMENT_HEAD, SECOND_STAGE_AUTHORITY));
        when(personService.getSignedInUser()).thenReturn(departmentHeadAndSsa);

        final Person person = somePerson();
        person.setId(2);
        person.setFirstName("B");
        person.setLastName("B");
        when(departmentService.getMembersForDepartmentHead(departmentHeadAndSsa)).thenReturn(of(person));

        final Person person2 = somePerson();
        person2.setId(3);
        person2.setFirstName("A");
        person2.setLastName("A");
        when(departmentService.getMembersForSecondStageAuthority(departmentHeadAndSsa)).thenReturn(of(person2));

        final List<SickNoteType> sickNoteTypes = of(someSickNoteType());
        when(sickNoteTypeService.getSickNoteTypes()).thenReturn(sickNoteTypes);

        perform(get("/web/sicknote/new"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sickNote", instanceOf(SickNoteForm.class)))
            .andExpect(model().attribute("persons", of(person2, person)))
            .andExpect(model().attribute("sickNoteTypes", sickNoteTypes))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensureGetEditHasCorrectModelAttributes() throws Exception {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));
        when(vacationTypeViewModelService.getVacationTypeColors()).thenReturn(List.of(new VacationTypeDto(1, ORANGE)));

        final List<SickNoteType> sickNoteTypes = of(someSickNoteType());
        when(sickNoteTypeService.getSickNoteTypes()).thenReturn(sickNoteTypes);

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID + "/edit"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sickNote", instanceOf(SickNoteForm.class)))
            .andExpect(model().attribute("sickNoteTypes", sickNoteTypes))
            .andExpect(model().attribute("vacationTypeColors", equalTo(List.of(new VacationTypeDto(1, ORANGE)))))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensureGetEditSickNoteForUnknownSickNoteIdThrowsUnknownSickNoteException() {

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + UNKNOWN_SICK_NOTE_ID + "/edit"))
        ).hasCauseInstanceOf(UnknownSickNoteException.class);
    }

    @Test
    void ensureGetEditSickNoteForInactiveThrowsSickNoteAlreadyInactiveException() {

        when(sickNoteService.getById(UNKNOWN_SICK_NOTE_ID)).thenReturn(Optional.of(someInactiveSickNote()));

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + UNKNOWN_SICK_NOTE_ID + "/edit"))
        ).hasCauseInstanceOf(SickNoteAlreadyInactiveException.class);
    }

    @Test
    void ensureGetSickNoteDetailsForUnknownSickNoteIdThrowsUnknownSickNoteException() {

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + UNKNOWN_SICK_NOTE_ID))
        ).hasCauseInstanceOf(UnknownSickNoteException.class);
    }

    @Test
    void ensureGetSickNoteDetailsAccessibleForSickNoteOwner() throws Exception {

        final Person somePerson = somePerson();
        when(personService.getSignedInUser()).thenReturn(somePerson);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(somePerson)));
        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OFFICE", "BOSS"})
    void ensureGetSickNoteDetailsAccessibleForPersonWithRole(Role role) throws Exception {

        final Person officePerson = personWithRole(role);
        when(personService.getSignedInUser()).thenReturn(officePerson);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(officePerson)));

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID)).andExpect(status().isOk());
    }

    @Test
    void ensureGetSickNoteDetailsAccessibleForPersonWithRoleDepartmentHead() throws Exception {

        final Person departmentHeadPerson = personWithRole(DEPARTMENT_HEAD);
        when(personService.getSignedInUser()).thenReturn(departmentHeadPerson);
        final Person person = personWithRole(USER);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(departmentService.isDepartmentHeadAllowedToManagePerson(departmentHeadPerson, person)).thenReturn(true);

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID)).andExpect(status().isOk());
    }

    @Test
    void ensureGetSickNoteDetailsIsNotAccessibleForPersonWithDepartmentHeadForWrongUser() {

        final Person departmentHeadPerson = personWithRole(DEPARTMENT_HEAD);
        departmentHeadPerson.setId(1);
        when(personService.getSignedInUser()).thenReturn(departmentHeadPerson);
        final Person person = personWithRole(USER);
        person.setId(2);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(departmentService.isDepartmentHeadAllowedToManagePerson(departmentHeadPerson, person)).thenReturn(false);

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
        ).hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ensureGetSickNoteDetailsAccessibleForPersonWithRoleSecondStageAuthority() throws Exception {

        final Person secondStageAuthority = personWithRole(SECOND_STAGE_AUTHORITY);
        when(personService.getSignedInUser()).thenReturn(secondStageAuthority);
        final Person person = personWithRole(USER);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(departmentService.isSecondStageAuthorityAllowedToManagePerson(secondStageAuthority, person)).thenReturn(true);

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID)).andExpect(status().isOk());
    }

    @Test
    void ensureGetSickNoteDetailsIsNotAccessibleForPersonWithSecondStageAuthorityForWrongUser() {

        final Person secondStageAuthority = personWithRole(SECOND_STAGE_AUTHORITY);
        secondStageAuthority.setId(1);
        when(personService.getSignedInUser()).thenReturn(secondStageAuthority);
        final Person person = personWithRole(USER);
        person.setId(2);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(departmentService.isSecondStageAuthorityAllowedToManagePerson(secondStageAuthority, person)).thenReturn(false);

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
        ).hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ensureGetSickNoteDetailsNotAccessibleForOtherPersonIfNotRoleOffice() {

        final int somePersonId = 1;
        when(personService.getSignedInUser()).thenReturn(personWithId(somePersonId));

        final int anotherPersonId = 2;
        when(sickNoteService.getById(SOME_SICK_NOTE_ID))
            .thenReturn(Optional.of(sickNoteOfPerson(personWithId(anotherPersonId))));

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
        ).hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ensureGetSickNoteDetailsProvidesCorrectModelAttributesAndView() throws Exception {

        when(personService.getSignedInUser()).thenReturn(personWithRole(OFFICE));

        final Person person = new Person();
        final SickNote sickNote = someActiveSickNote();
        sickNote.setPerson(person);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNote));

        when(sickNoteCommentService.getCommentsBySickNote(any(SickNote.class))).thenReturn(List.of());

        final Department department = new Department();
        when(departmentService.getAssignedDepartmentsOfMember(person)).thenReturn(List.of(department));

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
            .andExpect(model().attribute("sickNote", instanceOf(ExtendedSickNote.class)))
            .andExpect(model().attribute("comment", instanceOf(SickNoteCommentForm.class)))
            .andExpect(model().attribute("comments", instanceOf(List.class)))
            .andExpect(model().attribute("departmentsOfPerson", List.of(department)))
            .andExpect(view().name("sicknote/sick_note"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OFFICE", "BOSS"})
    void ensureGetSickNoteDetailsCanEditSickNotesWithRole(Role role) throws Exception {

        when(personService.getSignedInUser()).thenReturn(personWithRole(role));
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));
        when(sickNoteCommentService.getCommentsBySickNote(any(SickNote.class))).thenReturn(List.of());

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
            .andExpect(view().name("sicknote/sick_note"))
            .andExpect(model().attribute("canEditSickNote", true));
    }

    @Test
    void ensureGetSickNoteDetailsCanEditSickNotesDepartmentHead() throws Exception {

        final Person departmentHead = personWithRole(DEPARTMENT_HEAD);
        when(personService.getSignedInUser()).thenReturn(departmentHead);

        final Person person = new Person();
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(sickNoteCommentService.getCommentsBySickNote(any(SickNote.class))).thenReturn(List.of());
        when(departmentService.isDepartmentHeadAllowedToManagePerson(departmentHead, person)).thenReturn(true);

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
            .andExpect(view().name("sicknote/sick_note"))
            .andExpect(model().attribute("canEditSickNote", true));
    }

    @Test
    void ensureGetSickNoteDetailsCanNotEditSickNotesDepartmentHeadOfDifferentDepartment() {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final Person departmentHead = personWithRole(DEPARTMENT_HEAD);
        when(personService.getSignedInUser()).thenReturn(departmentHead);

        final Person person = new Person();
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(departmentService.isDepartmentHeadAllowedToManagePerson(departmentHead, person)).thenReturn(false);

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
        ).hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ensureGetSickNoteDetailsCanEditSickNotesSecondStageAuthority() throws Exception {

        final Person ssa = personWithRole(SECOND_STAGE_AUTHORITY);
        when(personService.getSignedInUser()).thenReturn(ssa);

        final Person person = new Person();
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(sickNoteCommentService.getCommentsBySickNote(any(SickNote.class))).thenReturn(List.of());
        when(departmentService.isSecondStageAuthorityAllowedToManagePerson(ssa, person)).thenReturn(true);

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
            .andExpect(view().name("sicknote/sick_note"))
            .andExpect(model().attribute("canEditSickNote", true));
    }

    @Test
    void ensureGetSickNoteDetailsCanNotEditSickNotesSecondStageAuthorityOfDifferentDepartment() {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final Person ssa = personWithRole(SECOND_STAGE_AUTHORITY);
        when(personService.getSignedInUser()).thenReturn(ssa);

        final Person person = new Person();
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNoteOfPerson(person)));
        when(departmentService.isSecondStageAuthorityAllowedToManagePerson(ssa, person)).thenReturn(false);

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
        ).hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ensureGetSickNoteDetailsCanConvertSickNotes() throws Exception {

        when(personService.getSignedInUser()).thenReturn(personWithRole(OFFICE));
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));
        when(sickNoteCommentService.getCommentsBySickNote(any(SickNote.class))).thenReturn(List.of());

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
            .andExpect(view().name("sicknote/sick_note"))
            .andExpect(model().attribute("canConvertSickNote", true));
    }

    @Test
    void ensureGetSickNoteDetailsCanDeleteSickNotes() throws Exception {

        when(personService.getSignedInUser()).thenReturn(personWithRole(OFFICE));
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));
        when(sickNoteCommentService.getCommentsBySickNote(any(SickNote.class))).thenReturn(List.of());

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
            .andExpect(view().name("sicknote/sick_note"))
            .andExpect(model().attribute("canDeleteSickNote", true));
    }

    @Test
    void ensureGetSickNoteDetailsCanCommentSickNotes() throws Exception {

        when(personService.getSignedInUser()).thenReturn(personWithRole(OFFICE));
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));
        when(sickNoteCommentService.getCommentsBySickNote(any(SickNote.class))).thenReturn(List.of());

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID))
            .andExpect(view().name("sicknote/sick_note"))
            .andExpect(model().attribute("canCommentSickNote", true));
    }

    @Test
    void ensurePostNewSickNoteShowsFormIfValidationFails() throws Exception {

        when(vacationTypeViewModelService.getVacationTypeColors()).thenReturn(List.of(new VacationTypeDto(1, ORANGE)));

        doAnswer(invocation -> {
            Errors errors = invocation.getArgument(1);
            errors.rejectValue("person", "error");
            return null;
        }).when(sickNoteValidator).validate(any(), any());

        perform(post("/web/sicknote"))
            .andExpect(model().attribute("vacationTypeColors", equalTo(List.of(new VacationTypeDto(1, ORANGE)))))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void ensurePostNewSickNoteCreatesSickNoteIfValidationSuccessful() throws Exception {

        final Person signedInPerson = somePerson();
        when(personService.getSignedInUser()).thenReturn(signedInPerson);

        perform(post("/web/sicknote/"));

        verify(sickNoteInteractionService).create(any(SickNote.class), eq(signedInPerson), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"25.03.2022", "25.03.22", "25.3.2022", "25.3.22", "1.4.22"})
    void ensureCreateSickNoteSucceedsWithDate(String givenDate) throws Exception {

        final Person signedInPerson = somePerson();
        when(personService.getSignedInUser()).thenReturn(signedInPerson);

        perform(
            post("/web/sicknote/")
                .param("startDate", givenDate)
                .param("endDate", givenDate)
                .param("aubStartDate", givenDate)
                .param("aubEndDate", givenDate)
        );

        verify(sickNoteInteractionService).create(any(SickNote.class), eq(signedInPerson), any());
    }

    @Test
    void ensurePostNewSickNoteRedirectsToCreatedSickNote() throws Exception {

        when(personService.getSignedInUser()).thenReturn(somePerson());

        doAnswer(invocation -> {
            SickNote sickNote = invocation.getArgument(0);
            sickNote.setId(SOME_SICK_NOTE_ID);
            return sickNote;
        }).when(sickNoteInteractionService).create(any(SickNote.class), any(Person.class), any());

        perform(post("/web/sicknote/"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/web/sicknote/" + SOME_SICK_NOTE_ID));
    }

    @Test
    void editPostSickNoteShowsFormIfValidationFails() throws Exception {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));
        when(vacationTypeViewModelService.getVacationTypeColors()).thenReturn(List.of(new VacationTypeDto(1, ORANGE)));

        doAnswer(invocation -> {
            Errors errors = invocation.getArgument(1);
            errors.rejectValue("person", "error");
            return null;
        }).when(sickNoteValidator).validate(any(), any());

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/edit"))
            .andExpect(model().attribute("vacationTypeColors", equalTo(List.of(new VacationTypeDto(1, ORANGE)))))
            .andExpect(view().name("sicknote/sick_note_form"));
    }

    @Test
    void editPostSickNoteUpdatesSickNoteIfValidationSuccessful() throws Exception {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final Person signedInPerson = somePerson();
        when(personService.getSignedInUser()).thenReturn(signedInPerson);

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/edit"));

        verify(sickNoteInteractionService).update(any(SickNote.class), eq(signedInPerson), any());
    }

    @Test
    void editPostSickNoteRedirectsToCreatedSickNote() throws Exception {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/edit"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/web/sicknote/" + SOME_SICK_NOTE_ID));
    }

    @Test
    void editPostSickNoteThrowsUnknownSickNoteException() {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID + "/edit"))
        ).hasCauseInstanceOf(UnknownSickNoteException.class);
    }

    @Test
    void ensurePostAddCommentThrowsUnknownSickNoteException() {
        assertThatThrownBy(() ->
            perform(post("/web/sicknote/" + UNKNOWN_SICK_NOTE_ID + "/comment"))
        ).hasCauseInstanceOf(UnknownSickNoteException.class);
    }

    @Test
    void ensurePostAddCommentAddsFlashAttributeAndRedirectsToSickNoteIfValidationFails() throws Exception {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        doAnswer(invocation -> {
            final Errors errors = invocation.getArgument(1);
            errors.rejectValue("text", "errors");
            return null;
        }).when(sickNoteCommentFormValidator).validate(any(SickNoteCommentForm.class), any(Errors.class));

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/comment"))
            .andExpect(flash().attribute("errors", instanceOf(Errors.class)))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/web/sicknote/" + SOME_SICK_NOTE_ID));
    }

    @Test
    void ensurePostAddCommentCreatesSickNoteCommentIfValidationSuccessful() throws Exception {

        final Person signedInPerson = somePerson();
        when(personService.getSignedInUser()).thenReturn(signedInPerson);

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/comment"));

        verify(sickNoteCommentService).create(any(SickNote.class), any(), eq(signedInPerson), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"comment", "convert", "cancel"})
    void ensureRedirectToSickNote(String path) throws Exception {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/" + path))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/web/sicknote/" + SOME_SICK_NOTE_ID));
    }

    @Test
    void ensureGetConvertSickNoteToVacationForUnknownSickNoteIdThrowsUnknownSickNoteException() {

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + UNKNOWN_SICK_NOTE_ID + "/convert"))
        ).hasCauseInstanceOf(UnknownSickNoteException.class);
    }

    @Test
    void ensureGetConvertSickNoteToVacationThrowsSickNoteAlreadyInactiveException() {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someInactiveSickNote()));

        assertThatThrownBy(() ->
            perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID + "/convert"))
        ).hasCauseInstanceOf(SickNoteAlreadyInactiveException.class);
    }

    @Test
    void ensureGetConvertSickNoteToVacationAddModel() throws Exception {

        overtimeActive(false);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final List<VacationType> vacationTypes = List.of(new VacationType(1, true, HOLIDAY, "message_key", true, YELLOW, false));
        when(vacationTypeService.getActiveVacationTypesWithoutCategory(OVERTIME)).thenReturn(vacationTypes);

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID + "/convert"))
            .andExpect(model().attribute("sickNote", instanceOf(ExtendedSickNote.class)))
            .andExpect(model().attribute("sickNoteConvertForm", instanceOf(SickNoteConvertForm.class)))
            .andExpect(model().attribute("vacationTypes", vacationTypes));
    }

    @Test
    void ensureGetConvertSickNoteToVacationAddModelOvertimeTrue() throws Exception {

        overtimeActive(true);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final List<VacationType> vacationTypes = List.of(new VacationType(1, true, HOLIDAY, "message_key", true, YELLOW, false));
        when(vacationTypeService.getActiveVacationTypes()).thenReturn(vacationTypes);

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID + "/convert"))
            .andExpect(model().attribute("sickNote", instanceOf(ExtendedSickNote.class)))
            .andExpect(model().attribute("sickNoteConvertForm", instanceOf(SickNoteConvertForm.class)))
            .andExpect(model().attribute("vacationTypes", vacationTypes));
    }

    @Test
    void ensureGetConvertSickNoteToVacationUsesCorrectView() throws Exception {

        overtimeActive(false);

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        perform(get("/web/sicknote/" + SOME_SICK_NOTE_ID + "/convert"))
            .andExpect(view().name("sicknote/sick_note_convert"));
    }

    @Test
    void ensurePostConvertSickNoteToVacationForUnknownSickNoteIdThrowsUnknownSickNoteException() {

        assertThatThrownBy(() ->
            perform(post("/web/sicknote/" + UNKNOWN_SICK_NOTE_ID + "/convert"))
        ).hasCauseInstanceOf(UnknownSickNoteException.class);
    }

    @Test
    void ensurePostConvertSickNoteToVacationFilledModelCorrectlyAndViewIfValidationFails() throws Exception {

        overtimeActive(false);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final List<VacationType> vacationTypes = List.of(new VacationType(1, true, HOLIDAY, "message_key", true, YELLOW, false));
        when(vacationTypeService.getActiveVacationTypesWithoutCategory(OVERTIME)).thenReturn(vacationTypes);

        doAnswer(invocation -> {
            final Errors errors = invocation.getArgument(1);
            errors.rejectValue("person", "error");
            return null;
        }).when(sickNoteConvertFormValidator).validate(any(), any());

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/convert"))
            .andExpect(view().name("sicknote/sick_note_convert"))
            .andExpect(model().attribute("errors", instanceOf(Errors.class)))
            .andExpect(model().attribute("sickNote", instanceOf(ExtendedSickNote.class)))
            .andExpect(model().attribute("sickNoteConvertForm", instanceOf(SickNoteConvertForm.class)))
            .andExpect(model().attribute("vacationTypes", vacationTypes));
    }

    @Test
    void ensurePostConvertSickNoteToVacationFilledModelCorrectlyAndViewIfValidationFailsWithOvertimeActive() throws Exception {

        overtimeActive(true);
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final List<VacationType> vacationTypes = List.of(new VacationType(1, true, HOLIDAY, "message_key", true, YELLOW, false));
        when(vacationTypeService.getActiveVacationTypes()).thenReturn(vacationTypes);

        doAnswer(invocation -> {
            final Errors errors = invocation.getArgument(1);
            errors.rejectValue("person", "error");
            return null;
        }).when(sickNoteConvertFormValidator).validate(any(), any());

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/convert"))
            .andExpect(view().name("sicknote/sick_note_convert"))
            .andExpect(model().attribute("errors", instanceOf(Errors.class)))
            .andExpect(model().attribute("sickNote", instanceOf(ExtendedSickNote.class)))
            .andExpect(model().attribute("sickNoteConvertForm", instanceOf(SickNoteConvertForm.class)))
            .andExpect(model().attribute("vacationTypes", vacationTypes));
    }

    @Test
    void ensurePostConvertSickNoteToVacationConvertsSickNoteIfValidationSuccessful() throws Exception {

        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(someActiveSickNote()));

        final Person signedInPerson = somePerson();
        when(personService.getSignedInUser()).thenReturn(signedInPerson);

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/convert"));

        verify(sickNoteInteractionService).convert(any(SickNote.class), any(Application.class), eq(signedInPerson));
    }

    @Test
    void ensureCancelSickNoteThrowsUnknownSickNoteException() {

        assertThatThrownBy(() ->
            perform(post("/web/sicknote/" + UNKNOWN_SICK_NOTE_ID + "/cancel"))
        ).hasCauseInstanceOf(UnknownSickNoteException.class);
    }

    @Test
    void ensureCancelSickNoteCancelsSickNoteCorrectly() throws Exception {

        final SickNote sickNote = someActiveSickNote();
        when(sickNoteService.getById(SOME_SICK_NOTE_ID)).thenReturn(Optional.of(sickNote));

        final Person signedInPerson = somePerson();
        when(personService.getSignedInUser()).thenReturn(signedInPerson);

        perform(post("/web/sicknote/" + SOME_SICK_NOTE_ID + "/cancel"));

        verify(sickNoteInteractionService).cancel(sickNote, signedInPerson);
    }

    private void overtimeActive(boolean active) {
        final Settings settings = new Settings();
        settings.getOvertimeSettings().setOvertimeActive(active);
        when(settingsService.getSettings()).thenReturn(settings);
    }

    private SickNote sickNoteOfPerson(Person somePerson) {
        final SickNote sickNote = new SickNote();
        sickNote.setPerson(somePerson);
        return sickNote;
    }

    private Person personWithRole(Role role) {
        final Person person = new Person();
        person.setId(1);
        person.setPermissions(List.of(role));
        return person;
    }

    private SickNote someInactiveSickNote() {
        final SickNote sickNote = new SickNote();
        sickNote.setPerson(new Person());
        sickNote.setStatus(SickNoteStatus.CANCELLED);
        return sickNote;
    }

    private SickNote someActiveSickNote() {
        final SickNote sickNote = new SickNote();
        sickNote.setPerson(new Person());
        sickNote.setStatus(ACTIVE);
        return sickNote;
    }

    private Person somePerson() {
        return new Person();
    }

    private Person personWithId(int personId) {
        Person person = new Person();
        person.setId(personId);

        return person;
    }

    private SickNoteType someSickNoteType() {
        return new SickNoteType();
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return standaloneSetup(sut).build().perform(builder);
    }
}
