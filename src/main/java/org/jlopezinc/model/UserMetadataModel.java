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
    private CheckIn checkIn;
    private String rawWebhook;
    private PaymentInfo paymentInfo;
    private String comment;
    /**
     * @deprecated Use changeHistory instead. This field is maintained for backward compatibility.
     */
    @Deprecated
    private List<String> commentsHistory;
    
    /**
     * Unified change history tracking all modifications to this user's registration.
     * Each entry contains a timestamp (ISO 8601), action type, and human-friendly description.
     * Entries are stored in chronological order (oldest first).
     * 
     * This replaces the legacy commentsHistory field and provides a comprehensive audit trail
     * for all user-level mutations including registrations, updates, payments, check-ins, etc.
     */
    private List<ChangeHistoryEntry> changeHistory;

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
