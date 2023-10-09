package org.jlopezinc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@ApplicationScoped
public class EventV1Service {

    private static final String EVENTS_TABLE = "eventsRegister";

    private DynamoDbAsyncTable<UserModelDB> userModelTable;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EventV1Service (DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient){
        userModelTable = dynamoDbEnhancedAsyncClient.table(EVENTS_TABLE, TableSchema.fromClass(UserModelDB.class));
    }

    public Uni<UserModel> getByEventAndQrToken (String event, String token){
        Key partitioKey = Key.builder().partitionValue(event).sortValue(token).build();
        return Uni.createFrom().completionStage(() -> userModelTable.getItem(partitioKey)).onItem().transform(
            userModelDbTransform
        );
    }

    public Uni<UserModel> getByEventAndEmail(String event, String email) {
        DynamoDbAsyncIndex<UserModelDB> byEmail = userModelTable.index("byEmail");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(email).sortValue(event).build());

        return Uni.createFrom().completionStage(() -> {
            CompletableFuture<UserModelDB> userModelDBCompletableFuture = new CompletableFuture<>();

            byEmail.query(queryConditional).subscribe(rp -> {
                List<UserModelDB> userModelDBList = new ArrayList<>(rp.items());
                if (userModelDBList.isEmpty()){
                    userModelDBCompletableFuture.complete(null);
                } else {
                    userModelDBCompletableFuture.complete(userModelDBList.get(0));
                }

            });
            return userModelDBCompletableFuture;
        }).map(userModelDbTransform);
    }

    public Uni<UserModel> checkInByEventAndQrToken (String event, String token, String who){
        return getByEventAndQrToken(event, token)
                .onItem().call(userModel -> {
                    if (userModel == null){
                        return Uni.createFrom().nullItem();
                    }
                    userModel.getMetadata().setCheckIn(new UserMetadataModel.CheckIn(){{
                        setCheckInAt(new Date());
                        setByWho(who);
                    }});
                    UserModelDB userModelDB = userModelTransform(userModel);
                    return Uni.createFrom().completionStage(() -> userModelTable.updateItem(userModelDB));
                });
    }

    final Function<UserModelDB, UserModel> userModelDbTransform = new Function<>() {
        @Override
        public UserModel apply(UserModelDB userModelDB) {
            if (userModelDB == null){
                return null;
            }
            return new UserModel() {{
                setEventName(userModelDB.getEventName());
                setPaid(userModelDB.isPaid());
                setUserEmail(userModelDB.getUserEmail());
                setQrToken(userModelDB.getQrToken());
                setVehicleType(userModelDB.getVehicleType());
                try {
                    setMetadata(objectMapper.readValue(userModelDB.getMetadata(), UserMetadataModel.class));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }};
        }
    };

    final UserModelDB userModelTransform (UserModel userModel){
        return new UserModelDB() {{
            setEventName(userModel.getEventName());
            setPaid(userModel.isPaid());
            setUserEmail(userModel.getUserEmail());
            setQrToken(userModel.getQrToken());
            setVehicleType(userModel.getVehicleType());
            try {
                setMetadata(objectMapper.writeValueAsString(userModel.getMetadata()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }};

    }


}
