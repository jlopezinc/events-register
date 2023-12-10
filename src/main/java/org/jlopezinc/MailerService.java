package org.jlopezinc;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.mailer.MailTemplate;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jlopezinc.model.UserModel;

@ApplicationScoped
public class MailerService {

    @Inject
    ReactiveMailer mailer;

    @CheckedTemplate
    static class Templates {
        public static native MailTemplate.MailTemplateInstance userRegistration (UserModel userModel);
        public static native MailTemplate.MailTemplateInstance almostThere (UserModel userModel);
    }

    public Uni<Void> sendRegistrationEmail(UserModel userModel){
        return Templates.userRegistration(userModel)
                .to(userModel.getUserEmail())
                .subject("Inscrição confirmada - " + userModel.getMetadata().getPeople().get(0).getName())
                .send();
    }

    public Uni<Void> sendAlmostThere(UserModel userModel){
        return Templates.almostThere(userModel)
                .to(userModel.getUserEmail())
                .subject("Está quase!")
                .send();
    }

}
