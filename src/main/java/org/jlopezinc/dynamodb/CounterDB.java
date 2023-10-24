package org.jlopezinc.dynamodb;

import lombok.Data;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
@SuperBuilder
public class CounterDB extends EventsRegisterDb {

    private long count;

    public CounterDB(){}

    @DynamoDbAttribute("count")
    public long getCount() {
        return count;
    }

}
