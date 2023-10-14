package org.jlopezinc.dynamodb;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbAtomicCounter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class CounterDB extends EventsRegisterDb {
    private long count;

    @DynamoDbAtomicCounter
    @DynamoDbAttribute("count")
    public long getCount() {
        return count;
    }

}
