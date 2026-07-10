package org.jlopezinc.email;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@LookupIfProperty(name = "app.mailer.provider", stringValue = "ses", lookupIfMissing = true)
@ApplicationScoped
public class SesEmailSender implements EmailSender {

    @Inject
    SesAsyncClient sesClient;

    @ConfigProperty(name = "quarkus.mailer.from")
    String from;

    @Override
    public Uni<Void> send(String to, String subject, String htmlBody) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(from)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                .build())
                        .build())
                .build();

        return Uni.createFrom().completionStage(() -> sesClient.sendEmail(request))
                .replaceWithVoid();
    }
}
