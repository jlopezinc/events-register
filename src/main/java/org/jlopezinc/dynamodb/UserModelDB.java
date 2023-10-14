package org.jlopezinc.dynamodb;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class UserModelDB extends EventsRegisterDb {

    private boolean paid;
    private String vehicleType;
    private String metadata;

    @DynamoDbAttribute("paid")
    public Boolean isPaid() {
        return paid;
    }

    @DynamoDbAttribute("vehicle_type")
    public String getVehicleType() {
        return vehicleType;
    }

    @DynamoDbAttribute("metadata")
    public String getMetadata() {
        return metadata;
    }
}
