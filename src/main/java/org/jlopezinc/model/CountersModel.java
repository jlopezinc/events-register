package org.jlopezinc.model;

import lombok.Data;

@Data
public class CountersModel {
    private long total;
    private long checkedInCar;
    private long checkedInMotorcycle;
    private long paid;
}
