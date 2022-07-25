package org.synyx.urlaubsverwaltung.sicknote.sicknote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.Role;
import org.synyx.urlaubsverwaltung.settings.Settings;
import org.synyx.urlaubsverwaltung.settings.SettingsService;
import org.synyx.urlaubsverwaltung.sicknote.settings.SickNoteSettings;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.person.Role.USER;
import static org.synyx.urlaubsverwaltung.sicknote.sicknote.SickNoteStatus.ACTIVE;

@ExtendWith(MockitoExtension.class)
class SickNoteServiceImplTest {

    private SickNoteServiceImpl sut;

    @Mock
    private SickNoteRepository sickNoteRepository;
    @Mock
    private SettingsService settingsService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2021-06-28T00:00:00.00Z"), UTC);

    @BeforeEach
    void setUp() {
        sut = new SickNoteServiceImpl(sickNoteRepository, settingsService, fixedClock);
    }

    @Test
    void save() {
        final SickNote sickNote = new SickNote();
        sut.save(sickNote);
        verify(sickNoteRepository).save(sickNote);
    }

    @Test
    void getById() {
        final Optional<SickNote> sickNote = Optional.of(new SickNote());
        when(sickNoteRepository.findById(1)).thenReturn(sickNote);

        final Optional<SickNote> actualSickNote = sut.getById(1);
        assertThat(actualSickNote).isEqualTo(sickNote);
    }

    @Test
    void findByPeriod() {
        final LocalDate from = LocalDate.of(2015, 1, 1);
        final LocalDate to = LocalDate.of(2016, 1, 1);
        final SickNote sickNote = new SickNote();
        final List<Role> roles = List.of(USER);
        when(sickNoteRepository.findByPersonPermissionsIsInAndStatusInAndEndDateIsGreaterThanEqualAndStartDateIsLessThanEqual(roles, List.of(ACTIVE), from, to)).thenReturn(List.of(sickNote));

        final List<SickNote> sickNotes = sut.getActiveByPeriodAndPersonHasRole(from, to, roles);
        assertThat(sickNotes).contains(sickNote);
    }

    @Test
    void getAllActiveByYear() {
        final SickNote sickNote = new SickNote();
        when(sickNoteRepository.findByPersonPermissionsIsInAndStatusInAndEndDateIsGreaterThanEqualAndStartDateIsLessThanEqual(List.of(USER), List.of(ACTIVE), LocalDate.of(2017, 1, 1), LocalDate.of(2017, 12, 31))).thenReturn(List.of(sickNote));

        final List<SickNote> sickNotes = sut.getAllActiveByYear(2017);
        assertThat(sickNotes).contains(sickNote);
    }

    @Test
    void getNumberOfPersonsWithMinimumOneSickNote() {
        when(sickNoteRepository.findNumberOfPersonsWithMinimumOneSickNote(2017)).thenReturn(5L);

        final Long numberOfPersonsWithMinimumOneSickNote = sut.getNumberOfPersonsWithMinimumOneSickNote(2017);
        assertThat(numberOfPersonsWithMinimumOneSickNote).isSameAs(5L);
    }

    @Test
    void getSickNotesReachingEndOfSickPay() {

        final SickNoteSettings sickNoteSettings = new SickNoteSettings();
        sickNoteSettings.setMaximumSickPayDays(5);
        sickNoteSettings.setDaysBeforeEndOfSickPayNotification(2);

        final Settings settings = new Settings();
        settings.setSickNoteSettings(sickNoteSettings);
        when(settingsService.getSettings()).thenReturn(settings);

        final SickNote sickNote = new SickNote();
        when(sickNoteRepository.findSickNotesToNotifyForSickPayEnd(5, 2, LocalDate.of(2021, 6, 28))).thenReturn(List.of(sickNote));

        final List<SickNote> sickNotesReachingEndOfSickPay = sut.getSickNotesReachingEndOfSickPay();
        assertThat(sickNotesReachingEndOfSickPay).contains(sickNote);
    }

    @Test
    void getForStatesSince() {
        final List<SickNoteStatus> openSickNoteStatuses = List.of(ACTIVE);

        final SickNote sickNote = new SickNote();
        when(sickNoteRepository.findByStatusInAndEndDateGreaterThanEqual(openSickNoteStatuses, LocalDate.of(2020, 10, 3))).thenReturn(List.of(sickNote));

        final List<SickNote> sickNotes = sut.getForStatesSince(openSickNoteStatuses, LocalDate.of(2020, 10, 3));
        assertThat(sickNotes)
            .hasSize(1)
            .contains(sickNote);
    }


    @Test
    void getForStatesAndPerson() {
        final Person person = new Person();
        final List<Person> persons = List.of(person);
        final List<SickNoteStatus> openSickNoteStatuses = List.of(ACTIVE);

        final LocalDate since = LocalDate.of(2020, 10, 3);

        final SickNote sickNote = new SickNote();
        when(sickNoteRepository.findByStatusInAndPersonInAndEndDateIsGreaterThanEqual(openSickNoteStatuses, persons, since)).thenReturn(List.of(sickNote));

        final List<SickNote> sickNotes = sut.getForStatesAndPersonSince(openSickNoteStatuses, persons, since);
        assertThat(sickNotes)
            .hasSize(1)
            .contains(sickNote);
    }

    @Test
    void setEndOfSickPayNotificationSend() {
        final SickNote sickNote = new SickNote();

        final ArgumentCaptor<SickNote> argument = ArgumentCaptor.forClass(SickNote.class);
        sut.setEndOfSickPayNotificationSend(sickNote);

        verify(sickNoteRepository).save(argument.capture());
        assertThat(argument.getValue().getEndOfSickPayNotificationSend()).isEqualTo(LocalDate.now(fixedClock));
    }
}
