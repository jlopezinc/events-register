package org.jlopezinc;

import lombok.Data;

@Data
public class UserModel {
    private String eventName;
    private String userEmail;

    private String qrToken;
    private boolean paid;
    private String vehicleType;
    private UserMetadataModel metadata;
}
