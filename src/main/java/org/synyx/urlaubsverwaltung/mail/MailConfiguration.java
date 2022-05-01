package org.synyx.urlaubsverwaltung.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;

import static org.thymeleaf.templatemode.TemplateMode.TEXT;

@Configuration
public class MailConfiguration {

    private static final String EMAIL_TEMPLATE_ENCODING = "UTF-8";

    private final ApplicationContext applicationContext;

    @Autowired
    public MailConfiguration(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean(name = "emailTemplateEngine")
    public TemplateEngine emailTemplateEngine() {
        final SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(textTemplateResolver());
        templateEngine.addDialect(new Java8TimeDialect());
        return templateEngine;
    }

    private SpringResourceTemplateResolver textTemplateResolver() {
        final SpringResourceTemplateResolver textEmailTemplateResolver = new SpringResourceTemplateResolver();
        textEmailTemplateResolver.setApplicationContext(applicationContext);
        textEmailTemplateResolver.setOrder(1);
        textEmailTemplateResolver.setPrefix("classpath:/mail/");
        textEmailTemplateResolver.setSuffix(".txt");
        textEmailTemplateResolver.setTemplateMode(TEXT);
        textEmailTemplateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
        textEmailTemplateResolver.setCacheable(false);
        return textEmailTemplateResolver;
    }
}
