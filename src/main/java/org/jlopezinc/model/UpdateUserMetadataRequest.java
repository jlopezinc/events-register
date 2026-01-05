package org.jlopezinc.model;

import lombok.Data;

import java.util.Date;

/**
 * Request model for updating user data via PUT endpoint.
 * All fields except email and event name can be updated.
 */
@Data
public class UpdateUserMetadataRequest {
    // User details
    private String driverName;
    private String driverCc;
    private String address;
    private String phoneNumber;
    
    // Vehicle details
    private String vehicleType;
    private String vehiclePlate;
    private String vehicleBrand;
    
    // Guest details
    private Integer guestsNumber;
    private String guestsNames;
    private String guestsCc;
    
    // Payment
    private String payment;
    
    // Comment
    private String comment;
    
    // Payment status (if updating via FE)
    private Boolean paid;
}
