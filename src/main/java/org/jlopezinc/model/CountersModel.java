package org.jlopezinc.model;

import lombok.Data;

@Data
public class CountersModel {
    private long total;
    private long totalCar;
    private long totalMotorcycle;
    private long totalQuad;
    private long totalParticipants;
    private long checkedInCar;
    private long checkedInMotorcycle;
    private long checkedInQuad;
    private long paid;
    private long paidCar;
    private long paidMotorcycle;
    private long paidQuad;
}
