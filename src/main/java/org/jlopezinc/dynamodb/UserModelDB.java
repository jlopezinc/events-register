package org.jlopezinc.dynamodb;

import lombok.Data;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
@SuperBuilder
public class UserModelDB extends EventsRegisterDb {

    private boolean paid;
    private String vehicleType;
    private String metadata;
    private boolean checkedIn;

    public UserModelDB() {
    }

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

    @DynamoDbAttribute("checkedIn")
    public boolean isCheckedIn() {
        return checkedIn;
    }
}
