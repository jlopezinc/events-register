package org.jlopezinc;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Data
public class UserModelDB {

    private static final String EVENT_PK = "event_name";

    public UserModelDB(){

    }
    private String eventName;
    private String userEmail;

    private boolean paid;
    private String vehicleType;
    private String metadata;

    @DynamoDbPartitionKey
    @DynamoDbAttribute(EVENT_PK)
    public String getEventName() {
        return eventName;
    }

    @DynamoDbAttribute("email")
    @DynamoDbSortKey
    public String getUserEmail() {
        return userEmail;
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
}
