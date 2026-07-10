package org.jlopezinc;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jlopezinc.email.EmailSender;
import org.jlopezinc.model.UserModel;

@ApplicationScoped
public class MailerService {

    private static final String TEMPLATE_ROOT = "MailerService";
    private static final String EVENT_TEMPLATE_ROOT = TEMPLATE_ROOT + "/events";

    private static final String TEMPLATE_USER_REGISTRATION = "userRegistration";
    private static final String TEMPLATE_ALMOST_THERE = "almostThere";

    @Inject
    @Any
    Instance<EmailSender> emailSenderInstance;

    @Inject
    Engine quteEngine;

    public Uni<Void> sendRegistrationEmail(UserModel userModel){
        return sendTemplate(userModel.getEventName(), TEMPLATE_USER_REGISTRATION, userModel);
    }

    public Uni<Void> sendAlmostThere(UserModel userModel){
        return sendTemplate(userModel.getEventName(), TEMPLATE_ALMOST_THERE, userModel);
    }

    public Uni<Void> sendTemplate(String eventName, String templateName, UserModel userModel) {
        Template template = resolveTemplate(eventName, templateName);
        String htmlBody = template
                .instance()
                .data("userModel", userModel)
                .render();

        return emailSenderInstance.get().send(
                userModel.getUserEmail(),
                resolveSubject(templateName, userModel),
                htmlBody
        );
    }

    private Template resolveTemplate(String eventName, String templateName) {
        Template eventTemplate = null;
        if (eventName != null && !eventName.isBlank()) {
            eventTemplate = quteEngine.getTemplate(EVENT_TEMPLATE_ROOT + "/" + eventName + "/" + templateName);
        }

        if (eventTemplate != null) {
            return eventTemplate;
        }

        Template defaultTemplate = quteEngine.getTemplate(TEMPLATE_ROOT + "/" + templateName);
        if (defaultTemplate != null) {
            return defaultTemplate;
        }

        throw new NotFoundException("Template not found: " + templateName);
    }

    private String resolveSubject(String templateName, UserModel userModel) {
        return switch (templateName) {
            case TEMPLATE_USER_REGISTRATION ->
                    "Inscrição confirmada - " + userModel.getMetadata().getPeople().get(0).getName();
            case TEMPLATE_ALMOST_THERE -> "Está quase!";
            default -> throw new NotFoundException("Template not found: " + templateName);
        };
    }
}
