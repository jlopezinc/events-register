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
import org.jlopezinc.model.CountersModel;
import org.jlopezinc.model.PaymentInfo;
import org.jlopezinc.model.UserMetadataModel;
import org.jlopezinc.model.UserModel;
import org.jlopezinc.model.WebhookModel;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@ApplicationScoped
public class EventV1Service {

    private static final String EVENTS_TABLE = "eventsRegister";
    static final String CHECK_IN_COUNTER = "checkInCounter";
    static final String PAID_COUNTER = "paidCounter";

    private DynamoDbAsyncTable<UserModelDB> userModelTable;
    private DynamoDbAsyncTable<CounterDB> counterModelTable;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MailerService mailerService;

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
        CompletableFuture<CounterDB> paidCounterKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(PAID_COUNTER).build());

        return Uni.combine()
                .all().unis(
                        Uni.createFrom().completionStage(totalKey),
                        Uni.createFrom().completionStage(checkInCarKey),
                        Uni.createFrom().completionStage(checkInMotorcycleKey),
                        Uni.createFrom().completionStage(paidCounterKey))
                .combinedWith(responses -> {
                            CountersModel countersModel = new CountersModel();
                            CounterDB totalUser = (CounterDB) responses.get(0);
                            CounterDB checkedInCarUser = (CounterDB) responses.get(1);
                            CounterDB checkedInMotorbikeUser = (CounterDB) responses.get(2);
                            CounterDB paidUser = (CounterDB) responses.get(3);

                            countersModel.setTotal(totalUser != null ? totalUser.getCount() : 0);
                            countersModel.setCheckedInCar(checkedInCarUser != null ? checkedInCarUser.getCount() : 0);
                            countersModel.setCheckedInMotorcycle(checkedInMotorbikeUser != null ? checkedInMotorbikeUser.getCount() : 0);
                            countersModel.setPaid(paidUser != null ? paidUser.getCount() : 0);
                            return countersModel;
                        }
                );
    }

    /** Sample with a GSI
    public Uni<UserModel> getByEventAndEmail2(String event, String email) {
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

                    if (userModel.isCheckedIn()){
                        Log.info("user " + email + ", (" + event + ") already checked in at " + userModel.getMetadata().getCheckIn().getCheckInAt() + " by " + who);
                        return Uni.createFrom().failure(new NoContentException("Already checked in"));
                    }

                    userModel.getMetadata().setCheckIn(new UserMetadataModel.CheckIn(){{
                        setCheckInAt(new Date());
                        setByWho(who);
                    }});
                    userModel.setCheckedIn(true);
                    UserModelDB userModelDB = userModelTransform(userModel);

                    return Uni.createFrom().completionStage(() -> userModelTable.updateItem(userModelDB))
                            .call(() -> incrementOrDecrementCheckInCounter(userModelDB, true));
                });
    }

    public Uni<UserModel> cancelCheckInByEventAndEmail(String event, String email, String who){
        return getByEventAndEmail(event, email)
                .onItem().call(userModel -> {
                    if (userModel == null){
                        return Uni.createFrom().failure(new NoContentException("Not Found"));
                    }

                    if (!userModel.isCheckedIn()){
                        Log.info("Cancelling user " + email + ", (" + event + ") failed because it's not checked (by "+ who + ")");
                        return Uni.createFrom().failure(new NoContentException("Already checked in"));
                    }

                    userModel.getMetadata().setCheckIn(new UserMetadataModel.CheckIn(){{
                        setCheckInAt(null);
                        setByWho(null);
                    }});
                    userModel.setCheckedIn(false);
                    UserModelDB userModelDB = userModelTransform(userModel);

                    return Uni.createFrom().completionStage(() -> userModelTable.updateItem(userModelDB))
                            .call(() -> incrementOrDecrementCheckInCounter(userModelDB, false));
                });
    }
    public Uni<Void> register(String event, String body) {
        UserModelDB userModelDB;
        try {
            userModelDB = transformWebHook(event, body, this.objectMapper);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return Uni.createFrom().voidItem().call(() -> getByEventAndEmail(event, userModelDB.getUserEmail())
                .onItem().call((userModel) -> {
                            // and send an email
                            return Uni.createFrom().completionStage(() -> userModelTable.putItem(userModelDB)).onItem()
                                    .call(() -> incrementOrDecrementTotalCounter(event, true))
                                    .call(() -> mailerService.sendRegistrationEmail(userModelDbTransform.apply(userModelDB)));
                        }
                ));
    }
    public Uni<Void> updatePaymentInfo(String event, String email, PaymentInfo paymentInfo) {
        return  Uni.createFrom().voidItem().call(() -> getByEventAndEmail(event, email)
                .call(userModel -> {
                    if (userModel == null) {
                        return Uni.createFrom().failure(new NoContentException("Not Found"));
                    }
                    final boolean alreadyPaid = userModel.isPaid();

                    userModel.setPaid(true);
                    PaymentInfo storedPaymentInfo = userModel.getMetadata().getPaymentInfo();
                    if (storedPaymentInfo == null){
                        storedPaymentInfo = new PaymentInfo();
                    }
                    storedPaymentInfo.setConfirmedAt(new Date());
                    storedPaymentInfo.setByWho(paymentInfo.getByWho());
                    storedPaymentInfo.setAmount(paymentInfo.getAmount());
                    if (StringUtils.isNotBlank(paymentInfo.getPaymentFile())) {
                        storedPaymentInfo.setPaymentFile(paymentInfo.getPaymentFile());
                    }
                    userModel.getMetadata().setPaymentInfo(storedPaymentInfo);

                    return Uni.createFrom().completionStage(() -> userModelTable.putItem(userModelTransform(userModel)))
                            .call(() -> {
                                if (!alreadyPaid){
                                    return incrementOrDecrementPaidCounter(event, true);
                                }
                                return Uni.createFrom().voidItem();
                            });
                }));
    }

    private Uni<Void> incrementOrDecrementCounter(String event, String sortKey, boolean increment){
        Key key = Key.builder().partitionValue(event).sortValue(sortKey).build();

        return Uni.createFrom().voidItem().call(() -> Uni.createFrom().completionStage(() -> counterModelTable.getItem(key)).onItem().call(
                counterDB -> {
                    if (counterDB == null){
                        final CounterDB newCounter = new CounterDB();
                        newCounter.setCount(0);
                        newCounter.setEventName(event);
                        newCounter.setUserEmail(sortKey);
                        if (increment) {
                            newCounter.setCount(newCounter.getCount() + 1);
                        }
                        return Uni.createFrom().completionStage(() -> counterModelTable.putItem(newCounter));
                    }
                    if (increment) {
                        counterDB.setCount(counterDB.getCount() + 1);
                    } else {
                        counterDB.setCount(Math.max(counterDB.getCount() - 1, 0));
                    }
                    return Uni.createFrom().completionStage(() -> counterModelTable.updateItem(counterDB));
                }
        ));
    }

    private Uni<Void> incrementOrDecrementCheckInCounter(UserModelDB userModelDB, boolean increment) {
        String sortKey = CHECK_IN_COUNTER + userModelDB.getVehicleType();
        return incrementOrDecrementCounter(userModelDB.getEventName(), sortKey, increment);
    }

    private Uni<Void> incrementOrDecrementTotalCounter(String event, boolean increment) {
        String sortKey = "total";
        return incrementOrDecrementCounter(event, sortKey, increment);
    }
    private Uni<Void> incrementOrDecrementPaidCounter(String event, boolean increment) {
        return incrementOrDecrementCounter(event, PAID_COUNTER, increment);
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
                setCheckedIn(userModelDB.isCheckedIn());
                try {
                    setMetadata(objectMapper.readValue(userModelDB.getMetadata(), UserMetadataModel.class));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }};
        }
    };

    UserModelDB transformWebHook(String event, String rawWebhook, ObjectMapper objectMapper) throws JsonProcessingException {
        WebhookModel webhookModel = objectMapper.readValue(rawWebhook, WebhookModel.class);
        String vehicleType;
        switch (webhookModel.getVehicleType()){
            case "Mota":
                vehicleType = "motorcycle";
                break;
            case "Quad":
                vehicleType = "quad";
                break;
            case "Jipe":
            default:
                vehicleType = "car";
        }

        UserMetadataModel userMetadataModel = new UserMetadataModel();
        userMetadataModel.setPhoneNumber(webhookModel.getPhoneNumber());
        userMetadataModel.setRegisteredAt(webhookModel.getSubmittedAt());
        userMetadataModel.setPeople(transformPeople(webhookModel));
        userMetadataModel.setVehicle(transformVehicle(webhookModel));
        userMetadataModel.setPaymentInfo(new PaymentInfo(){{
            setPaymentFile(webhookModel.getPayment());
        }});
        userMetadataModel.setCheckIn(new UserMetadataModel.CheckIn());
        userMetadataModel.setRawWebhook(rawWebhook);

        return UserModelDB.builder()
                .eventName(event)
                .userEmail(webhookModel.getEmail())
                .paid(false)
                .checkedIn(false)
                .metadata(objectMapper.writeValueAsString(userMetadataModel))
                .vehicleType(vehicleType)
                .build();
    }

    private UserMetadataModel.Vehicle transformVehicle(WebhookModel webhookModel) {
        return new UserMetadataModel.Vehicle(){{
            setPlate(webhookModel.getVehiclePlate());
            setMake(webhookModel.getVehicleBrand());
        }};
    }

    private List<UserMetadataModel.People> transformPeople(WebhookModel webhookModel) {
        String splitBy =  "<BR/>|\n|,";
        List<UserMetadataModel.People> peopleList = new ArrayList<>(webhookModel.getGuestsNumber() + 1);
        peopleList.add(new UserMetadataModel.People(){{
            setName(webhookModel.getDriverName());
            setType("driver");
            setPhoneNumber(webhookModel.getPhoneNumber());
            setCc(webhookModel.getDriverCc());
        }});

        if (StringUtils.isNotBlank(webhookModel.getGuestsNames())){
            List<String> names = Arrays.asList(webhookModel.getGuestsNames().trim().split(splitBy));
            List<String> ccs = null;
            if (webhookModel.getGuestsCc() != null){
                ccs = Arrays.asList(webhookModel.getGuestsCc().trim().split(splitBy));
            }

            for (int i = 0; i < names.size(); i++){
                final String cc;
                if (ccs != null && ccs.size() > i){
                    cc = ccs.get(i);
                } else {
                    cc = null;
                }
                String name = names.get(i);
                peopleList.add(new UserMetadataModel.People(){{
                    setName(name);
                    setCc(cc);
                    setType("guest");
                }});
            }
        }
        return peopleList;
    }

    final UserModelDB userModelTransform (UserModel userModel){
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(userModel.getMetadata());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return UserModelDB.builder()
                .userEmail(userModel.getUserEmail())
                .eventName(userModel.getEventName())
                .paid(userModel.isPaid())
                .checkedIn(userModel.isCheckedIn())
                .vehicleType(userModel.getVehicleType())
                .metadata(metadata).build();
    }

}
