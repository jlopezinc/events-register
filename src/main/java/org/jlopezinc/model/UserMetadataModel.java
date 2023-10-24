package org.jlopezinc.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class UserMetadataModel {

    private Vehicle vehicle;
    private List<People> people;
    private String phoneNumber;
    private Date registeredAt;
    private Date paidAt;
    private CheckIn checkIn;
    private String paymentFile;
    private String rawWebhook;

    @Data
    public static class Vehicle{
        private String plate;
        private String make;
        private String model;
    }

    @Data
    public static class People {
        private String type;
        private String name;
        private String driversLicense;
        private String phoneNumber;
        private String cc;
    }

    @Data
    public static class CheckIn {
        private Date checkInAt;
        private String byWho;
    }
}
