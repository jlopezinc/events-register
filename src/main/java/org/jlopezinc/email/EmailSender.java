package org.jlopezinc.email;

import io.smallrye.mutiny.Uni;

public interface EmailSender {
    Uni<Void> send(String to, String subject, String htmlBody);
}
