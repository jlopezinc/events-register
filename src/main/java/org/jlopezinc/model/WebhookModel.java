package org.jlopezinc.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.Date;

@Data
@RegisterForReflection
public class WebhookModel {
    Date submittedAt;
    String email;
    String driverName;
    String driverCc;
    String address;
    String phoneNumber;
    String vehicleType;
    String vehiclePlate;
    String vehicleBrand;
    int guestsNumber;
    String guestsNames;
    String guestsCc;
    String payment;
}
