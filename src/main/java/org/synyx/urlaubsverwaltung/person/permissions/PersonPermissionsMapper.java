package org.synyx.urlaubsverwaltung.person.permissions;

import org.synyx.urlaubsverwaltung.person.Person;

import java.util.List;

final class PersonPermissionsMapper {

    private PersonPermissionsMapper() {
    }

    static PersonPermissionsDto mapToPersonPermissionsDto(Person person) {
        final PersonPermissionsDto personPermissionsDto = new PersonPermissionsDto();
        personPermissionsDto.setId(person.getId());
        personPermissionsDto.setNiceName(person.getNiceName());
        personPermissionsDto.setGravatarURL(person.getGravatarURL());
        personPermissionsDto.setEmail(person.getEmail());
        personPermissionsDto.setPermissions(List.copyOf(person.getPermissions()));
        personPermissionsDto.setNotifications(List.copyOf(person.getNotifications()));
        return personPermissionsDto;
    }

    static Person merge(Person person, PersonPermissionsDto personPermissionsDto) {
        person.setPermissions(personPermissionsDto.getPermissions());
        person.setNotifications(personPermissionsDto.getNotifications());
        return person;
    }
}
