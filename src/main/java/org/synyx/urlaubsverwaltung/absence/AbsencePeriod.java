package org.synyx.urlaubsverwaltung.absence;

import org.synyx.urlaubsverwaltung.person.Person;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Defines absence periods of a {@link Person}.
 *
 * e.g. Bruce Wayne is absent on:
 * <ul>
 *     <li>24.March 2021 to 28. March 2021 (vacation full day)</li>
 *     <li>31.March 2021 (vacation morning)</li>
 *     <li>9.June 2021 (vacation noon)</li>
 *     <li>26.August 2021 to 27.August 2021 (sick full day)</li>
 * </ul>
 */
public class AbsencePeriod {

    private final List<AbsencePeriod.Record> absenceRecords;

    public AbsencePeriod(List<Record> absenceRecords) {
        this.absenceRecords = absenceRecords;
    }

    public DateRange getDateRange() {
        if (absenceRecords.isEmpty()) {
            // TODO
            return null;
        }
        final Record firstRecord = absenceRecords.get(0);
        final Record lastRecord = absenceRecords.get(absenceRecords.size() - 1);
        return new DateRange(firstRecord.getDate(), lastRecord.getDate());
    }

    public Optional<AbsencePeriod.Record> recordByDate(LocalDate localDate) {
        for (AbsencePeriod.Record record : absenceRecords) {
            if (record.date.isEqual(localDate)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public Map<LocalDate, Optional<Record>> recordsByDate() {
        final HashMap<LocalDate, Optional<Record>> recordsByDate = new HashMap<>();
        int recordCursor = 0;

        for (LocalDate date : getDateRange()) {
            if (absenceRecords.isEmpty()) {
                recordsByDate.put(date, Optional.empty());
            } else {
                final Record record = absenceRecords.get(recordCursor);
                if (record.date.isEqual(date)) {
                    recordsByDate.put(date, Optional.of(record));
                    recordCursor++;
                } else {
                    recordsByDate.put(date, Optional.empty());
                }
            }
        }

        return recordsByDate;
    }

    public List<AbsencePeriod.Record> getAbsenceRecords() {
        return Collections.unmodifiableList(absenceRecords);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbsencePeriod that = (AbsencePeriod) o;
        return Objects.equals(absenceRecords, that.absenceRecords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absenceRecords);
    }

    @Override
    public String toString() {
        return "AbsencePeriod{" +
            "absenceRecords=" + absenceRecords +
            '}';
    }

    /**
     * Specifies an absence for one date. The absence consists of `morning` and `evening`.
     * You may have to handle information yourself for "full absence vacation". In This case morning and evening are
     * defined.
     */
    public static class Record {

        public enum AbsenceType {
            VACATION,
            SICK_NOTE,
            NO_WORKDAY,
        }

        public enum AbsenceStatus {
            // vacation
            WAITING,
            TEMPORARY_ALLOWED,
            ALLOWED,
            ALLOWED_CANCELLATION_REQUESTED,
            // sick note
            ACTIVE,
        }

        private final LocalDate date;
        private final Person person;
        private final RecordMorning morning;
        private final RecordNoon noon;

        public Record(LocalDate date, Person person, RecordMorning morning) {
            this(date, person, morning, null);
        }

        public Record(LocalDate date, Person person, RecordNoon noon) {
            this(date, person, null, noon);
        }

        public Record(LocalDate date, Person person, RecordMorning morning, RecordNoon noon) {
            this.date = date;
            this.person = person;
            this.morning = morning;
            this.noon = noon;
        }

        public LocalDate getDate() {
            return date;
        }

        public Person getPerson() {
            return person;
        }

        public boolean isHalfDayAbsence() {
            return (this.morning == null && this.noon != null) || (this.morning != null && this.noon == null);
        }



        /**
         * Morning RecordInfo is empty when this Record specifies a noon absence only.
         *
         * @return the morning RecordInfo if it exists, empty Optional otherwise.
         */
        public Optional<RecordInfo> getMorning() {
            return Optional.ofNullable(morning);
        }

        /**
         * Noon RecordInfo is empty when this Record specifies a morning absence only.
         *
         * @return the noon RecordInfo if it exists, empty Optional otherwise.
         */
        public Optional<RecordInfo> getNoon() {
            return Optional.ofNullable(noon);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Record absenceRecord = (Record) o;
            return Objects.equals(date, absenceRecord.date) && Objects.equals(person, absenceRecord.person);
        }

        @Override
        public int hashCode() {
            return Objects.hash(date, person);
        }

        @Override
        public String toString() {
            return "Record{" +
                "date=" + date +
                ", person=" + person +
                ", morning=" + morning +
                ", noon=" + noon +
                '}';
        }
    }

    /**
     * Describes an absence record. (e.g. {@link RecordMorning} absence or {@link RecordNoon} absence)
     */
    public interface RecordInfo {
        Record.AbsenceType getType();
        Record.AbsenceStatus getStatus();
        Integer getId();
        boolean hasStatusWaiting();
        boolean hasStatusAllowed();
    }

    /**
     * Describes an absence this morning.
     */
    public interface RecordMorning extends RecordInfo {}

    /**
     * Describes an absence this noon.
     */
    public interface RecordNoon extends RecordInfo {}

    /**
     * Describes an absence record. (e.g. morning absence or noon absence)
     */
    public abstract static class AbstractRecordInfo implements RecordInfo {

        private final Record.AbsenceType type;
        private final Integer id;
        private final Record.AbsenceStatus status;

        private AbstractRecordInfo(Record.AbsenceType type, Integer id, Record.AbsenceStatus status) {
            this.type = type;
            this.id = id;
            this.status = status;
        }

        @Override
        public Record.AbsenceType getType() {
            return type;
        }

        @Override
        public Integer getId() {
            return id;
        }

        @Override
        public Record.AbsenceStatus getStatus() {
            return status;
        }

        public boolean hasStatusOneOf(Record.AbsenceStatus... status) {
            return List.of(status).contains(this.status);
        }

        public boolean hasStatusWaiting() {
            return hasStatusOneOf(Record.AbsenceStatus.WAITING, Record.AbsenceStatus.TEMPORARY_ALLOWED);
        }

        public boolean hasStatusAllowed() {
            return !hasStatusWaiting();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AbstractRecordInfo that = (AbstractRecordInfo) o;
            return type == that.type && status == that.status;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, status);
        }

        @Override
        public String toString() {
            return "AbstractRecordInfo{" +
                "id=" + id +
                '}';
        }
    }

    public static class RecordMorningVacation extends AbstractRecordInfo implements RecordMorning {
        public RecordMorningVacation(Integer applicationId, Record.AbsenceStatus status) {
            super(Record.AbsenceType.VACATION, applicationId, status);
        }
    }

    public static class RecordMorningSick extends AbstractRecordInfo implements RecordMorning {
        public RecordMorningSick(Integer sickNoteId) {
            super(Record.AbsenceType.SICK_NOTE, sickNoteId, Record.AbsenceStatus.ACTIVE);
        }
    }

    public static class RecordMorningNoWorkday extends AbstractRecordInfo implements RecordMorning {
        public RecordMorningNoWorkday() {
            super(Record.AbsenceType.NO_WORKDAY, 0, Record.AbsenceStatus.ACTIVE);
        }
    }

    public static class RecordNoonVacation extends AbstractRecordInfo implements RecordNoon {
        public RecordNoonVacation(Integer applicationId, Record.AbsenceStatus status) {
            super(Record.AbsenceType.VACATION, applicationId, status);
        }
    }

    public static class RecordNoonSick extends AbstractRecordInfo implements RecordNoon {
        public RecordNoonSick(Integer sickNoteId) {
            super(Record.AbsenceType.SICK_NOTE, sickNoteId, Record.AbsenceStatus.ACTIVE);
        }
    }

    public static class RecordNoonNoWorkday extends AbstractRecordInfo implements RecordNoon {
        public RecordNoonNoWorkday() {
            super(Record.AbsenceType.NO_WORKDAY, 0, Record.AbsenceStatus.ACTIVE);
        }
    }
}
