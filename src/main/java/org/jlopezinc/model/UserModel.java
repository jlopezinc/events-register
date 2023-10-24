package org.jlopezinc.model;

import lombok.Data;

@Data
public class UserModel {
    private String eventName;
    private String userEmail;
    private boolean paid;
    private String vehicleType;
    private boolean checkedIn;
    private UserMetadataModel metadata;
}
