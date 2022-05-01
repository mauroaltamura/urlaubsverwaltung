package org.synyx.urlaubsverwaltung.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of interface {@link MailService}.
 */
@Service("mailService")
@EnableConfigurationProperties(MailProperties.class)
class MailServiceImpl implements MailService {

    private static final Locale LOCALE = Locale.GERMAN;

    private final MessageSource messageSource;
    private final TemplateEngine mailTemplateEngine;
    private final MailSenderService mailSenderService;
    private final MailProperties mailProperties;
    private final PersonService personService;

    @Autowired
    MailServiceImpl(MessageSource messageSource, @Qualifier("emailTemplateEngine") TemplateEngine mailTemplateEngine, MailSenderService mailSenderService,
                    MailProperties mailProperties, PersonService personService) {

        this.messageSource = messageSource;
        this.mailTemplateEngine = mailTemplateEngine;
        this.mailProperties = mailProperties;
        this.mailSenderService = mailSenderService;
        this.personService = personService;
    }

    @Override
    public void send(Mail mail) {

        final Context context = new Context(LOCALE);
        context.setVariables(mail.getTemplateModel());
        context.setVariable("baseLinkURL", getApplicationUrl());

        final String subject = getTranslation(mail.getSubjectMessageKey(), mail.getSubjectMessageArguments());
        final String sender = generateMailAddressAndDisplayName(mailProperties.getSender(), mailProperties.getSenderDisplayName());

        getRecipients(mail).forEach(recipient -> {
            context.setVariable("recipient", recipient);
            final String body = mailTemplateEngine.process(mail.getTemplateName(), context);

            mail.getMailAttachments().ifPresentOrElse(
                mailAttachments -> mailSenderService.sendEmail(sender, List.of(recipient.getEmail()), subject, body, mailAttachments),
                () -> mailSenderService.sendEmail(sender, List.of(recipient.getEmail()), subject, body)
            );
        });
    }

    private List<Person> getRecipients(Mail mail) {

        final List<Person> recipients = new ArrayList<>();
        mail.getMailNotificationRecipients().ifPresent(mailNotification -> recipients.addAll(personService.getPersonsWithNotificationType(mailNotification)));
        mail.getMailAddressRecipients().ifPresent(recipients::addAll);

        if (mail.isSendToTechnicalMail()) {
            recipients.add(new Person(null, null, "Administrator", mailProperties.getAdministrator()));
        }

        return recipients;
    }

    private String getTranslation(String key, Object... args) {
        return messageSource.getMessage(key, args, LOCALE);
    }

    private String getApplicationUrl() {
        final String applicationUrl = mailProperties.getApplicationUrl();
        return applicationUrl.endsWith("/") ? applicationUrl : applicationUrl + "/";
    }

    private String generateMailAddressAndDisplayName(String address, String displayName) {
        return String.format("%s <%s>", displayName, address);
    }
}
