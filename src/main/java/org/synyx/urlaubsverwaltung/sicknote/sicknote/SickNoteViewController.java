package org.synyx.urlaubsverwaltung.sicknote.sicknote;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.DataBinder;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeDto;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypePropertyEditor;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeService;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeViewModelService;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.web.PersonPropertyEditor;
import org.synyx.urlaubsverwaltung.settings.SettingsService;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentAction;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentEntity;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentForm;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentFormValidator;
import org.synyx.urlaubsverwaltung.sicknote.comment.SickNoteCommentService;
import org.synyx.urlaubsverwaltung.sicknote.sicknotetype.SickNoteTypeService;
import org.synyx.urlaubsverwaltung.web.InstantPropertyEditor;
import org.synyx.urlaubsverwaltung.workingtime.WorkDaysCountService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.OVERTIME;
import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.security.SecurityRules.IS_OFFICE;
import static org.synyx.urlaubsverwaltung.sicknote.sicknote.SickNoteMapper.merge;

/**
 * Controller for {@link SickNote} purposes.
 */
@Controller
@RequestMapping("/web")
class SickNoteViewController {

    private static final String PERSONS_ATTRIBUTE = "persons";
    private static final String SICKNOTE_SICK_NOTE_FORM = "sicknote/sick_note_form";
    private static final String SICK_NOTE = "sickNote";
    private static final String SICK_NOTE_TYPES = "sickNoteTypes";
    private static final String REDIRECT_WEB_SICKNOTE = "redirect:/web/sicknote/";
    private static final String ATTRIBUTE_ERRORS = "errors";

    private final SickNoteService sickNoteService;
    private final SickNoteInteractionService sickNoteInteractionService;
    private final SickNoteCommentService sickNoteCommentService;
    private final SickNoteTypeService sickNoteTypeService;
    private final VacationTypeService vacationTypeService;
    private final VacationTypeViewModelService vacationTypeViewModelService;
    private final PersonService personService;
    private final DepartmentService departmentService;
    private final WorkDaysCountService workDaysCountService;
    private final SickNoteValidator sickNoteValidator;
    private final SickNoteCommentFormValidator sickNoteCommentFormValidator;
    private final SickNoteConvertFormValidator sickNoteConvertFormValidator;
    private final SettingsService settingsService;
    private final Clock clock;

    @Autowired
    SickNoteViewController(SickNoteService sickNoteService, SickNoteInteractionService sickNoteInteractionService,
                           SickNoteCommentService sickNoteCommentService, SickNoteTypeService sickNoteTypeService,
                           VacationTypeService vacationTypeService, VacationTypeViewModelService vacationTypeViewModelService, PersonService personService,
                           DepartmentService departmentService, WorkDaysCountService workDaysCountService, SickNoteValidator sickNoteValidator,
                           SickNoteCommentFormValidator sickNoteCommentFormValidator, SickNoteConvertFormValidator sickNoteConvertFormValidator,
                           SettingsService settingsService, Clock clock) {

        this.sickNoteService = sickNoteService;
        this.sickNoteInteractionService = sickNoteInteractionService;
        this.sickNoteCommentService = sickNoteCommentService;
        this.sickNoteTypeService = sickNoteTypeService;
        this.vacationTypeService = vacationTypeService;
        this.vacationTypeViewModelService = vacationTypeViewModelService;
        this.personService = personService;
        this.departmentService = departmentService;
        this.workDaysCountService = workDaysCountService;
        this.sickNoteValidator = sickNoteValidator;
        this.sickNoteCommentFormValidator = sickNoteCommentFormValidator;
        this.sickNoteConvertFormValidator = sickNoteConvertFormValidator;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(Instant.class, new InstantPropertyEditor(clock, settingsService));
        binder.registerCustomEditor(Person.class, new PersonPropertyEditor(personService));
        binder.registerCustomEditor(VacationType.class, new VacationTypePropertyEditor(vacationTypeService));
    }

    @GetMapping("/sicknote/{id}")
    public String sickNoteDetails(@PathVariable("id") Integer id, Model model) throws UnknownSickNoteException {

        final Person signedInUser = personService.getSignedInUser();
        final SickNote sickNote = sickNoteService.getById(id).orElseThrow(() -> new UnknownSickNoteException(id));

        final boolean isDepartmentHeadOfPerson = departmentService.isDepartmentHeadAllowedToManagePerson(signedInUser, sickNote.getPerson());
        final boolean isSamePerson = sickNote.getPerson().equals(signedInUser);
        if (isSamePerson || signedInUser.hasRole(OFFICE) || signedInUser.hasRole(BOSS) || isDepartmentHeadOfPerson) {
            model.addAttribute(SICK_NOTE, new ExtendedSickNote(sickNote, workDaysCountService));
            model.addAttribute("comment", new SickNoteCommentForm());

            final List<SickNoteCommentEntity> comments = sickNoteCommentService.getCommentsBySickNote(sickNote);
            model.addAttribute("comments", comments);

            model.addAttribute("canEditSickNote", signedInUser.hasRole(OFFICE) || isDepartmentHeadOfPerson);
            model.addAttribute("canConvertSickNote", signedInUser.hasRole(OFFICE));
            model.addAttribute("canDeleteSickNote", signedInUser.hasRole(OFFICE));
            model.addAttribute("canCommentSickNote", signedInUser.hasRole(OFFICE));

            model.addAttribute("departmentsOfPerson", departmentService.getAssignedDepartmentsOfMember(sickNote.getPerson()));

            return "sicknote/sick_note";
        }

        throw new AccessDeniedException(String.format(
            "User '%s' has not the correct permissions to see the sick note of user '%s'",
            signedInUser.getId(), sickNote.getPerson().getId()));
    }

    @PreAuthorize("hasAnyAuthority('OFFICE', 'BOSS', 'DEPARTMENT_HEAD')")
    @GetMapping("/sicknote/new")
    public String newSickNote(Model model) {

        final Person signedInUser = personService.getSignedInUser();

        model.addAttribute(SICK_NOTE, new SickNoteForm());

        final List<Person> managedPersons;
        if (signedInUser.hasRole(OFFICE) || signedInUser.hasRole(BOSS)) {
            managedPersons = personService.getActivePersons();
        } else {
            managedPersons = departmentService.getMembersForDepartmentHead(signedInUser);
        }

        model.addAttribute(PERSONS_ATTRIBUTE, managedPersons);
        model.addAttribute(SICK_NOTE_TYPES, sickNoteTypeService.getSickNoteTypes());

        addVacationTypeColorsToModel(model);

        return SICKNOTE_SICK_NOTE_FORM;
    }

    @PreAuthorize("hasAnyAuthority('OFFICE', 'BOSS', 'DEPARTMENT_HEAD')")
    @PostMapping("/sicknote")
    public String newSickNote(@ModelAttribute(SICK_NOTE) SickNoteForm sickNoteForm, Errors errors, Model model) {

        final Person signedInUser = personService.getSignedInUser();

        final SickNote sickNote = sickNoteForm.generateSickNote();
        sickNote.setApplier(signedInUser);

        sickNoteValidator.validate(sickNote, errors);
        if (errors.hasErrors()) {
            model.addAttribute(ATTRIBUTE_ERRORS, errors);
            model.addAttribute(SICK_NOTE, sickNoteForm);
            model.addAttribute(PERSONS_ATTRIBUTE, personService.getActivePersons());
            model.addAttribute(SICK_NOTE_TYPES, sickNoteTypeService.getSickNoteTypes());

            addVacationTypeColorsToModel(model);

            return SICKNOTE_SICK_NOTE_FORM;
        }

        sickNoteInteractionService.create(sickNote, signedInUser, sickNoteForm.getComment());

        return REDIRECT_WEB_SICKNOTE + sickNote.getId();
    }

    @PreAuthorize("hasAnyAuthority('OFFICE', 'DEPARTMENT_HEAD')")
    @GetMapping("/sicknote/{id}/edit")
    public String editSickNote(@PathVariable("id") Integer id, Model model) throws UnknownSickNoteException,
        SickNoteAlreadyInactiveException {

        final SickNote sickNote = sickNoteService.getById(id).orElseThrow(() -> new UnknownSickNoteException(id));
        final SickNoteForm sickNoteForm = new SickNoteForm(sickNote);

        if (!sickNote.isActive()) {
            throw new SickNoteAlreadyInactiveException(id);
        }

        model.addAttribute(SICK_NOTE, sickNoteForm);
        model.addAttribute(SICK_NOTE_TYPES, sickNoteTypeService.getSickNoteTypes());

        addVacationTypeColorsToModel(model);

        return SICKNOTE_SICK_NOTE_FORM;
    }

    @PreAuthorize("hasAnyAuthority('OFFICE', 'DEPARTMENT_HEAD')")
    @PostMapping("/sicknote/{id}/edit")
    public String editSickNote(@PathVariable("id") Integer sickNoteId,
                               @ModelAttribute(SICK_NOTE) SickNoteForm sickNoteForm, Errors errors, Model model) throws UnknownSickNoteException {

        final Optional<SickNote> maybeSickNote = sickNoteService.getById(sickNoteId);
        if (maybeSickNote.isEmpty()) {
            throw new UnknownSickNoteException(sickNoteId);
        }
        final SickNote persistedSickNote = maybeSickNote.get();
        final SickNote editedSickNote = merge(persistedSickNote, sickNoteForm);
        sickNoteValidator.validate(editedSickNote, errors);

        if (errors.hasErrors()) {
            model.addAttribute(ATTRIBUTE_ERRORS, errors);
            model.addAttribute(SICK_NOTE, sickNoteForm);
            model.addAttribute(SICK_NOTE_TYPES, sickNoteTypeService.getSickNoteTypes());

            addVacationTypeColorsToModel(model);

            return SICKNOTE_SICK_NOTE_FORM;
        }

        final Person signedInUser = personService.getSignedInUser();
        sickNoteInteractionService.update(editedSickNote, signedInUser, sickNoteForm.getComment());

        return REDIRECT_WEB_SICKNOTE + sickNoteId;
    }

    @PreAuthorize(IS_OFFICE)
    @PostMapping("/sicknote/{id}/comment")
    public String addComment(@PathVariable("id") Integer id,
                             @ModelAttribute("comment") SickNoteCommentForm comment, Errors errors, RedirectAttributes redirectAttributes)
        throws UnknownSickNoteException {

        final SickNote sickNote = sickNoteService.getById(id).orElseThrow(() -> new UnknownSickNoteException(id));
        sickNoteCommentFormValidator.validate(comment, errors);

        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute(ATTRIBUTE_ERRORS, errors);
        } else {
            sickNoteCommentService.create(sickNote, SickNoteCommentAction.COMMENTED, personService.getSignedInUser(), comment.getText());
        }

        return REDIRECT_WEB_SICKNOTE + id;
    }

    @PreAuthorize(IS_OFFICE)
    @GetMapping("/sicknote/{id}/convert")
    public String convertSickNoteToVacation(@PathVariable("id") Integer id, Model model)
        throws UnknownSickNoteException, SickNoteAlreadyInactiveException {

        final SickNote sickNote = sickNoteService.getById(id).orElseThrow(() -> new UnknownSickNoteException(id));
        if (!sickNote.isActive()) {
            throw new SickNoteAlreadyInactiveException(id);
        }

        model.addAttribute(SICK_NOTE, new ExtendedSickNote(sickNote, workDaysCountService));
        model.addAttribute("sickNoteConvertForm", new SickNoteConvertForm(sickNote));
        model.addAttribute("vacationTypes", getActiveVacationTypes());

        return "sicknote/sick_note_convert";
    }

    @PreAuthorize(IS_OFFICE)
    @PostMapping("/sicknote/{id}/convert")
    public String convertSickNoteToVacation(@PathVariable("id") Integer id,
                                            @ModelAttribute("sickNoteConvertForm") SickNoteConvertForm sickNoteConvertForm, Errors errors, Model model)
        throws UnknownSickNoteException {

        final SickNote sickNote = sickNoteService.getById(id).orElseThrow(() -> new UnknownSickNoteException(id));
        sickNoteConvertFormValidator.validate(sickNoteConvertForm, errors);

        if (errors.hasErrors()) {
            model.addAttribute(ATTRIBUTE_ERRORS, errors);
            model.addAttribute(SICK_NOTE, new ExtendedSickNote(sickNote, workDaysCountService));
            model.addAttribute("sickNoteConvertForm", sickNoteConvertForm);
            model.addAttribute("vacationTypes", getActiveVacationTypes());

            return "sicknote/sick_note_convert";
        }

        sickNoteInteractionService.convert(sickNote, sickNoteConvertForm.generateApplicationForLeave(clock), personService.getSignedInUser());

        return REDIRECT_WEB_SICKNOTE + id;
    }

    @PreAuthorize(IS_OFFICE)
    @PostMapping("/sicknote/{id}/cancel")
    public String cancelSickNote(@PathVariable("id") Integer id) throws UnknownSickNoteException {

        final SickNote sickNote = sickNoteService.getById(id).orElseThrow(() -> new UnknownSickNoteException(id));
        sickNoteInteractionService.cancel(sickNote, personService.getSignedInUser());

        return REDIRECT_WEB_SICKNOTE + id;
    }

    private List<VacationType> getActiveVacationTypes() {
        final List<VacationType> vacationTypes;

        final boolean overtimeActive = settingsService.getSettings().getOvertimeSettings().isOvertimeActive();
        if (overtimeActive) {
            vacationTypes = vacationTypeService.getActiveVacationTypes();
        } else {
            vacationTypes = vacationTypeService.getActiveVacationTypesWithoutCategory(OVERTIME);
        }
        return vacationTypes;
    }

    private void addVacationTypeColorsToModel(Model model) {
        final List<VacationTypeDto> vacationTypeDtos = vacationTypeViewModelService.getVacationTypeColors();
        model.addAttribute("vacationTypeColors", vacationTypeDtos);
    }
}
