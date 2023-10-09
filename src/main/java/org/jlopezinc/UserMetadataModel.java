package org.jlopezinc;

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

    @Data
    static class Vehicle{
        private String plate;
        private String make;
        private String model;
    }

    @Data
    static class People {
        private String type;
        private String name;
        private String driversLicence;
        private String phoneNumber;
    }

    @Data
    static class CheckIn {
        private Date checkInAt;
        private String byWho;
    }
}
