package org.jlopezinc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NoContentException;
import org.jlopezinc.dynamodb.CounterDB;
import org.jlopezinc.dynamodb.UserModelDB;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.utils.StringUtils;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@ApplicationScoped
public class EventV1Service {

    private static final String EVENTS_TABLE = "eventsRegister";
    static final String CHECK_IN_COUNTER = "checkInCounter";

    private DynamoDbAsyncTable<UserModelDB> userModelTable;
    private DynamoDbAsyncTable<CounterDB> counterModelTable;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EventV1Service (DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient){
        userModelTable = dynamoDbEnhancedAsyncClient.table(EVENTS_TABLE, TableSchema.fromClass(UserModelDB.class));
        counterModelTable = dynamoDbEnhancedAsyncClient.table(EVENTS_TABLE, TableSchema.fromClass(CounterDB.class));
    }

    public Uni<UserModel> getByEventAndEmail (String event, String email){
        Key partitioKey = Key.builder().partitionValue(event).sortValue(email).build();
        return Uni.createFrom().completionStage(() -> userModelTable.getItem(partitioKey)).onItem().transform(
                userModelDbTransform
        );
    }

    public Uni<CountersModel> getCountersByEvent(String event) {
        CompletableFuture<CounterDB> totalKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue("total").build());
        CompletableFuture<CounterDB> checkInCarKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(CHECK_IN_COUNTER + "car").build());
        CompletableFuture<CounterDB> checkInMotorcycleKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(CHECK_IN_COUNTER + "motorcycle").build());

        return Uni.combine()
                .all().unis(
                        Uni.createFrom().completionStage(totalKey),
                        Uni.createFrom().completionStage(checkInCarKey),
                        Uni.createFrom().completionStage(checkInMotorcycleKey)).combinedWith(responses -> {
                            CountersModel countersModel = new CountersModel();
                            CounterDB totalUser = (CounterDB) responses.get(0);
                            CounterDB checkedInCarUser = (CounterDB) responses.get(1);
                            CounterDB checkedInMotorbikeUser = (CounterDB) responses.get(2);

                            countersModel.setTotal(totalUser != null ? totalUser.getCount() : 0);
                            countersModel.setCheckedInCar(checkedInCarUser != null ? checkedInCarUser.getCount() : 0);
                            countersModel.setCheckedInMotorcycle(checkedInMotorbikeUser != null ? checkedInMotorbikeUser.getCount() : 0);
                            return countersModel;
                        }
                );
    }

    /** Sample with a GSI
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
     }*/

    public Uni<UserModel> checkInByEventAndEmail(String event, String email, String who){
        return getByEventAndEmail(event, email)
                .onItem().call(userModel -> {
                    if (userModel == null){
                        return Uni.createFrom().failure(new NoContentException("Not Found"));
                    }
                    if (userModel.getMetadata().getCheckIn() != null && userModel.getMetadata().getCheckIn().getCheckInAt() != null){
                        Log.info("user " + email + ", (" + event + ") already checked in at " + userModel.getMetadata().getCheckIn().getCheckInAt() + " by " + who);
                        return Uni.createFrom().failure(new NoContentException("Already checked in"));
                    }

                    userModel.getMetadata().setCheckIn(new UserMetadataModel.CheckIn(){{
                        setCheckInAt(new Date());
                        setByWho(who);
                    }});
                    UserModelDB userModelDB = userModelTransform(userModel);

                    incrementCheckInCounter(userModelDB);

                    return Uni.createFrom().completionStage(() -> userModelTable.updateItem(userModelDB));
                });
    }

    private void incrementCheckInCounter(UserModelDB userModelDB) {
        String sortKey = CHECK_IN_COUNTER + userModelDB.getVehicleType();
        UserModelDB counter = new UserModelDB();
        counter.setEventName(userModelDB.getEventName());
        counter.setUserEmail(sortKey);
        userModelTable.updateItem(counter);
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
            setVehicleType(userModel.getVehicleType());
            try {
                setMetadata(objectMapper.writeValueAsString(userModel.getMetadata()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }};

    }


}
