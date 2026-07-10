package org.jlopezinc.email;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@LookupIfProperty(name = "app.mailer.provider", stringValue = "smtp")
@ApplicationScoped
public class SmtpEmailSender implements EmailSender {

    @Inject
    ReactiveMailer mailer;

    @Override
    public Uni<Void> send(String to, String subject, String htmlBody) {
        return mailer.send(Mail.withHtml(to, subject, htmlBody));
    }
}
