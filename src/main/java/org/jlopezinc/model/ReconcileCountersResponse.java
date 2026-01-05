package org.jlopezinc.model;

import lombok.Data;

@Data
public class ReconcileCountersResponse {
    private String eventId;
    private String status;
    private CountersModel before;
    private CountersModel after;
    private String message;
}
