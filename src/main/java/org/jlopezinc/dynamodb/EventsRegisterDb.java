package org.jlopezinc.dynamodb;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
abstract class EventsRegisterDb {
    private static final String EVENT_PK = "event_name";

    String eventName;
    String userEmail;

    @DynamoDbPartitionKey
    @DynamoDbAttribute(EVENT_PK)
    public String getEventName() {
        return eventName;
    }

    /**
     * This is also used to store counters
     */
    @DynamoDbAttribute("email")
    @DynamoDbSortKey
    public String getUserEmail() {
        return userEmail;
    }

}
