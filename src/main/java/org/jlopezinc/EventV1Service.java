package org.jlopezinc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.NoContentException;
import org.jlopezinc.dynamodb.CounterDB;
import org.jlopezinc.dynamodb.UserModelDB;
import org.jlopezinc.model.ChangeHistoryEntry;
import org.jlopezinc.model.CountersModel;
import org.jlopezinc.model.PaymentInfo;
import org.jlopezinc.model.ReconcileCountersResponse;
import org.jlopezinc.model.UserMetadataModel;
import org.jlopezinc.model.UserModel;
import org.jlopezinc.model.WebhookModel;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.time.Instant;

@ApplicationScoped
public class EventV1Service {

    private static final String EVENTS_TABLE = "eventsRegister";
    static final String CHECK_IN_COUNTER = "checkInCounter";
    static final String PAID_COUNTER = "paidCounter";

    static final String TOTAL_PARTICIPANTS_COUNTER = "totalParticipants";

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

    /**
     * Adds an entry to the change history for a user.
     * 
     * This method is the central point for recording all user-level mutations in the audit trail.
     * It creates a timestamped entry with the action type and a human-friendly description.
     * 
     * The change history is maintained in chronological order (oldest first), allowing
     * administrators to track the complete lifecycle of a user's registration.
     * 
     * @param metadata The user metadata to add the change history entry to (must not be null)
     * @param action The type of action performed (e.g., "USER_REGISTERED", "PAYMENT_ADDED") (must not be null)
     * @param description A human-friendly description of what changed (must not be null)
     * @throws IllegalArgumentException if any parameter is null
     */
    private void addChangeHistoryEntry(UserMetadataModel metadata, String action, String description) {
        // Validate input parameters
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("action cannot be null or empty");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("description cannot be null or empty");
        }
        
        if (metadata.getChangeHistory() == null) {
            metadata.setChangeHistory(new ArrayList<>());
        }
        
        // Create ISO 8601 timestamp using thread-safe java.time API
        String timestamp = Instant.now().toString();
        
        ChangeHistoryEntry entry = new ChangeHistoryEntry(timestamp, action, description);
        metadata.getChangeHistory().add(entry);
    }
    
    /**
     * Sanitizes a string for safe inclusion in change history descriptions.
     * Escapes special characters that could cause issues with JSON serialization or log injection.
     * 
     * @param text The text to sanitize (can be null)
     * @return The sanitized text, or "(empty)" if null or blank
     */
    private String sanitizeForDescription(String text) {
        if (text == null || text.isBlank()) {
            return "(empty)";
        }
        
        // Replace problematic characters
        return text
            .replace("\\", "\\\\")  // Backslash
            .replace("\"", "\\\"")  // Double quote
            .replace("\n", "\\n")   // Newline
            .replace("\r", "\\r")   // Carriage return
            .replace("\t", "\\t");  // Tab
    }

    public Uni<UserModel> getByEventAndEmail (String event, String email){
        Key partitioKey = Key.builder().partitionValue(event).sortValue(email).build();
        return Uni.createFrom().completionStage(() -> userModelTable.getItem(partitioKey)).onItem().transform(
                userModelDbTransform
        );
    }

    public Uni<UserModel> getByEventAndPhoneNumber(String event, String phoneNumber) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(event).build());
        
        return Uni.createFrom().completionStage(() -> {
            CompletableFuture<UserModelDB> userModelDBCompletableFuture = new CompletableFuture<>();

            userModelTable.query(r -> r.queryConditional(queryConditional)
                    .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                            .expression("phoneNumber = :phoneNumber")
                            .putExpressionValue(":phoneNumber", AttributeValue.builder().s(phoneNumber).build())
                            .build()))
                    .subscribe(rp -> {
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

    public Uni<CountersModel> getCountersByEvent(String event) {
        CompletableFuture<CounterDB> totalKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue("total").build());
        CompletableFuture<CounterDB> totalCarKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue("totalcar").build());
        CompletableFuture<CounterDB> totalMotorCycleKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue("totalmotorcycle").build());
        CompletableFuture<CounterDB> totalQuadKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue("totalquad").build());
        CompletableFuture<CounterDB> checkInCarKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(CHECK_IN_COUNTER + "car").build());
        CompletableFuture<CounterDB> checkInMotorcycleKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(CHECK_IN_COUNTER + "motorcycle").build());
        CompletableFuture<CounterDB> checkInQuadKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(CHECK_IN_COUNTER + "quad").build());
        CompletableFuture<CounterDB> paidCounterKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(PAID_COUNTER).build());
        CompletableFuture<CounterDB> paidCarKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(PAID_COUNTER + "car").build());
        CompletableFuture<CounterDB> paidMotorcycleKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(PAID_COUNTER + "motorcycle").build());
        CompletableFuture<CounterDB> paidQuadKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(PAID_COUNTER + "quad").build());
        CompletableFuture<CounterDB> totalParticipantsKey = counterModelTable.getItem(Key.builder().partitionValue(event).sortValue(TOTAL_PARTICIPANTS_COUNTER).build());

        return Uni.combine()
                .all().unis(
                        Uni.createFrom().completionStage(totalKey),
                        Uni.createFrom().completionStage(totalCarKey),
                        Uni.createFrom().completionStage(totalMotorCycleKey),
                        Uni.createFrom().completionStage(totalQuadKey),
                        Uni.createFrom().completionStage(checkInCarKey),
                        Uni.createFrom().completionStage(checkInMotorcycleKey),
                        Uni.createFrom().completionStage(checkInQuadKey),
                        Uni.createFrom().completionStage(paidCounterKey),
                        Uni.createFrom().completionStage(paidCarKey),
                        Uni.createFrom().completionStage(paidMotorcycleKey),
                        Uni.createFrom().completionStage(paidQuadKey),
                        Uni.createFrom().completionStage(totalParticipantsKey))
                .combinedWith(responses -> {
                            CountersModel countersModel = new CountersModel();
                            CounterDB totalUser = (CounterDB) responses.get(0);
                            CounterDB totalCar = (CounterDB) responses.get(1);
                            CounterDB totalMotorCycle = (CounterDB) responses.get(2);
                            CounterDB totalQuad = (CounterDB) responses.get(3);
                            CounterDB checkedInCarUser = (CounterDB) responses.get(4);
                            CounterDB checkedInMotorbikeUser = (CounterDB) responses.get(5);
                            CounterDB checkedInQuadUser = (CounterDB) responses.get(6);
                            CounterDB paidUser = (CounterDB) responses.get(7);
                            CounterDB paidCar = (CounterDB) responses.get(8);
                            CounterDB paidMotorbike = (CounterDB) responses.get(9);
                            CounterDB paidQuad = (CounterDB) responses.get(10);
                            CounterDB totalParticipants = (CounterDB) responses.get(11);

                            countersModel.setTotal(totalUser != null ? totalUser.getCount() : 0);
                            countersModel.setTotalCar(totalCar != null ? totalCar.getCount() : 0);
                            countersModel.setTotalMotorcycle(totalMotorCycle != null ? totalMotorCycle.getCount() : 0);
                            countersModel.setTotalQuad(totalQuad != null ? totalQuad.getCount() : 0);
                            countersModel.setCheckedInCar(checkedInCarUser != null ? checkedInCarUser.getCount() : 0);
                            countersModel.setCheckedInMotorcycle(checkedInMotorbikeUser != null ? checkedInMotorbikeUser.getCount() : 0);
                            countersModel.setCheckedInQuad(checkedInQuadUser != null ? checkedInQuadUser.getCount() : 0);
                            countersModel.setPaid(paidUser != null ? paidUser.getCount() : 0);
                            countersModel.setPaidCar(paidCar != null ? paidCar.getCount() : 0);
                            countersModel.setPaidMotorcycle(paidMotorbike != null ? paidMotorbike.getCount() : 0);
                            countersModel.setPaidQuad(paidQuad != null ? paidQuad.getCount() : 0);
                            countersModel.setTotalParticipants(totalParticipants != null ? totalParticipants.getCount() : 0);
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
                    
                    // Add change history entry
                    addChangeHistoryEntry(userModel.getMetadata(), "CHECK_IN_ADDED", 
                        "User checked in by " + who);
                    
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
                    
                    // Add change history entry
                    addChangeHistoryEntry(userModel.getMetadata(), "CHECK_IN_REMOVED", 
                        "Check-in cancelled by " + who);
                    
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
                .onItem().call((existingUser) -> {
                            // Only increment counter if user doesn't already exist
                            final boolean isNewUser = (existingUser == null);
                            
                            // If user exists, preserve comment history and add change history entry
                            if (!isNewUser) {
                                try {
                                    UserMetadataModel existingMetadata = existingUser.getMetadata();
                                    UserMetadataModel newMetadata = objectMapper.readValue(userModelDB.getMetadata(), UserMetadataModel.class);
                                    
                                    // Preserve existing change history
                                    if (existingMetadata.getChangeHistory() != null) {
                                        newMetadata.setChangeHistory(existingMetadata.getChangeHistory());
                                    }
                                    
                                    // Add change history entry for re-registration
                                    addChangeHistoryEntry(newMetadata, "USER_REGISTERED", "User re-registered with updated information via webhook");
                                    
                                    // Handle comments history (backward compatibility)
                                    String existingComment = existingMetadata.getComment();
                                    String newComment = newMetadata.getComment();
                                    
                                    // Check if comments are different (handling null cases)
                                    boolean commentsAreDifferent = (newComment == null && existingComment != null) ||
                                                                   (newComment != null && !newComment.equals(existingComment));
                                    
                                    if (commentsAreDifferent) {
                                        // Initialize commentsHistory if it doesn't exist
                                        if (newMetadata.getCommentsHistory() == null) {
                                            newMetadata.setCommentsHistory(new ArrayList<>());
                                        }
                                        
                                        // Add the previous comment to history only if it exists and is not blank
                                        if (StringUtils.isNotBlank(existingComment)) {
                                            newMetadata.getCommentsHistory().add(existingComment);
                                            // Also add to change history with sanitized comment text
                                            addChangeHistoryEntry(newMetadata, "COMMENT_UPDATED", 
                                                "Comment changed from \"" + sanitizeForDescription(existingComment) + 
                                                "\" to \"" + sanitizeForDescription(newComment) + "\"");
                                        }
                                    } else {
                                        // Preserve existing comments history
                                        newMetadata.setCommentsHistory(existingMetadata.getCommentsHistory());
                                    }
                                    
                                    // Update the metadata in userModelDB
                                    userModelDB.setMetadata(objectMapper.writeValueAsString(newMetadata));
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            
                            return Uni.createFrom().completionStage(() -> userModelTable.putItem(userModelDB)).onItem()
                                    .call(() -> {
                                        if (isNewUser) {
                                            return incrementOrDecrementTotalCounter(userModelDB, true);
                                        }
                                        return Uni.createFrom().voidItem();
                                    })
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
                    
                    // Add change history entry
                    String amountStr = paymentInfo.getAmount() != null ? paymentInfo.getAmount().toString() : "unknown amount";
                    String byWho = paymentInfo.getByWho() != null ? paymentInfo.getByWho() : "system";
                    addChangeHistoryEntry(userModel.getMetadata(), "PAYMENT_ADDED", 
                        "Payment confirmed: " + amountStr + " by " + byWho);

                    return Uni.createFrom().completionStage(() -> userModelTable.putItem(userModelTransform(userModel)))
                            .call(() -> {
                                if (!alreadyPaid){
                                    return incrementOrDecrementPaidCounter(userModelTransform(userModel), true);
                                }
                                return Uni.createFrom().voidItem();
                            });
                }));
    }

    public Uni<UserModel> updateUserMetadata(String event, String email, UserModel updateRequest) {
        return getByEventAndEmail(event, email)
                .onItem().call(userModel -> {
                    if (userModel == null) {
                        return Uni.createFrom().failure(new NoContentException("Not Found"));
                    }

                    UserMetadataModel metadata = userModel.getMetadata();
                    String existingComment = metadata.getComment();
                    List<String> updatedFields = new ArrayList<>();
                    
                    // Get the incoming metadata from the request
                    UserMetadataModel incomingMetadata = updateRequest.getMetadata();
                    if (incomingMetadata == null) {
                        return Uni.createFrom().item(userModel);
                    }
                    
                    // Update metadata fields if provided
                    if (incomingMetadata.getPeople() != null && !incomingMetadata.getPeople().isEmpty()) {
                        updatePeopleInMetadata(metadata, updateRequest);
                        updatedFields.add("people");
                    }
                    
                    // Update phone number in metadata (not on driver object to avoid duplication)
                    if (incomingMetadata.getPhoneNumber() != null) {
                        metadata.setPhoneNumber(incomingMetadata.getPhoneNumber());
                        // Also update on driver if people exist
                        if (metadata.getPeople() != null && !metadata.getPeople().isEmpty()) {
                            metadata.getPeople().get(0).setPhoneNumber(incomingMetadata.getPhoneNumber());
                        }
                        updatedFields.add("phoneNumber");
                    }
                    
                    if (incomingMetadata.getVehicle() != null) {
                        updateVehicleInMetadata(metadata, updateRequest);
                        updatedFields.add("vehicle");
                    }
                    
                    PaymentInfo incomingPaymentInfo = incomingMetadata.getPaymentInfo();
                    if (incomingPaymentInfo != null && incomingPaymentInfo.getPaymentFile() != null) {
                        PaymentInfo paymentInfo = metadata.getPaymentInfo();
                        if (paymentInfo == null) {
                            paymentInfo = new PaymentInfo();
                            metadata.setPaymentInfo(paymentInfo);
                        }
                        paymentInfo.setPaymentFile(incomingPaymentInfo.getPaymentFile());
                        updatedFields.add("paymentFile");
                    }
                    
                    // Handle comment with history tracking
                    String newComment = incomingMetadata.getComment();
                    // Check if comments are different (handling null cases)
                    boolean commentsAreDifferent = (newComment == null && existingComment != null) ||
                                                   (newComment != null && !newComment.equals(existingComment));
                    
                    if (commentsAreDifferent) {
                        // Add the previous comment to history only if it exists and is not blank
                        if (StringUtils.isNotBlank(existingComment)) {
                            // Initialize commentsHistory if it doesn't exist
                            if (metadata.getCommentsHistory() == null) {
                                metadata.setCommentsHistory(new ArrayList<>());
                            }
                            
                            // Add the previous comment to history
                            metadata.getCommentsHistory().add(existingComment);
                            
                            // Also add to change history with sanitized comment text
                            addChangeHistoryEntry(metadata, "COMMENT_UPDATED", 
                                "Comment changed from \"" + sanitizeForDescription(existingComment) + 
                                "\" to \"" + sanitizeForDescription(newComment) + "\"");
                        }
                        
                        // Update the comment with the new value (including null to clear it)
                        metadata.setComment(newComment);
                        updatedFields.add("comment");
                    }
                    
                    // Update vehicleType if provided
                    if (updateRequest.getVehicleType() != null) {
                        String vehicleType = normalizeVehicleType(updateRequest.getVehicleType());
                        userModel.setVehicleType(vehicleType);
                        updatedFields.add("vehicleType");
                    }
                    
                    // Update paid status if different from current value
                    // Note: Both fields are primitives, so we can compare directly
                    if (updateRequest.isPaid() != userModel.isPaid()) {
                        userModel.setPaid(updateRequest.isPaid());
                        updatedFields.add("paid");
                    }
                    
                    // Add change history entry if any fields were updated
                    if (!updatedFields.isEmpty()) {
                        String fieldsStr = String.join(", ", updatedFields);
                        addChangeHistoryEntry(metadata, "USER_UPDATED", 
                            "User data updated via PUT endpoint. Fields changed: " + fieldsStr);
                    }

                    UserModelDB userModelDB = userModelTransform(userModel);
                    return Uni.createFrom().completionStage(() -> userModelTable.updateItem(userModelDB))
                            .onItem().transform(userModelDbTransform);
                });
    }
    
    private void updatePeopleInMetadata(UserMetadataModel metadata, UserModel updateRequest) {
        UserMetadataModel incomingMetadata = updateRequest.getMetadata();
        // Get incoming people data and ensure at least one person (driver) exists
        if (incomingMetadata == null || incomingMetadata.getPeople() == null || incomingMetadata.getPeople().isEmpty()) {
            return;
        }
        
        List<UserMetadataModel.People> incomingPeople = incomingMetadata.getPeople();
        
        List<UserMetadataModel.People> people = metadata.getPeople();
        if (people == null || people.isEmpty()) {
            people = new ArrayList<>();
            metadata.setPeople(people);
        }
        
        // Update driver (first person in the list)
        UserMetadataModel.People driver;
        if (people.isEmpty()) {
            driver = new UserMetadataModel.People();
            driver.setType("driver");
            people.add(driver);
        } else {
            driver = people.get(0);
        }
        
        // Update driver from incoming data
        UserMetadataModel.People incomingDriver = incomingPeople.get(0);
        if (incomingDriver.getName() != null) {
            driver.setName(incomingDriver.getName());
        }
        if (incomingDriver.getCc() != null) {
            driver.setCc(incomingDriver.getCc());
        }
        // Note: Phone number is set separately in updateUserMetadata to avoid duplication
        
        // Update guests if provided
        if (incomingPeople.size() > 1) {
            // Remove existing guests (keep only driver)
            if (people.size() > 1) {
                people.subList(1, people.size()).clear();
            }
            
            // Add new guests from incoming data
            for (int i = 1; i < incomingPeople.size(); i++) {
                UserMetadataModel.People incomingGuest = incomingPeople.get(i);
                UserMetadataModel.People guest = new UserMetadataModel.People();
                guest.setName(incomingGuest.getName());
                guest.setCc(incomingGuest.getCc());
                guest.setType("guest");
                people.add(guest);
            }
        }
    }
    
    private void updateVehicleInMetadata(UserMetadataModel metadata, UserModel updateRequest) {
        UserMetadataModel incomingMetadata = updateRequest.getMetadata();
        if (incomingMetadata == null || incomingMetadata.getVehicle() == null) {
            return;
        }
        
        UserMetadataModel.Vehicle vehicle = metadata.getVehicle();
        if (vehicle == null) {
            vehicle = new UserMetadataModel.Vehicle();
            metadata.setVehicle(vehicle);
        }
        
        UserMetadataModel.Vehicle incomingVehicle = incomingMetadata.getVehicle();
        if (incomingVehicle.getPlate() != null) {
            vehicle.setPlate(incomingVehicle.getPlate());
        }
        if (incomingVehicle.getMake() != null) {
            vehicle.setMake(incomingVehicle.getMake());
        }
    }
    
    private String normalizeVehicleType(String vehicleType) {
        return switch (vehicleType.toLowerCase()) {
            case "mota", "motorcycle" -> "motorcycle";
            case "quad" -> "quad";
            case "jipe", "car" -> "car";
            default -> "car";
        };
    }

    private Uni<Void> incrementOrDecrementCounter(String event, String sortKey, boolean increment){
        return incrementOrDecrementCounter(event, sortKey, increment, 1);
    }
    private Uni<Void> incrementOrDecrementCounter(String event, String sortKey, boolean increment, int by){
        Key key = Key.builder().partitionValue(event).sortValue(sortKey).build();

        return Uni.createFrom().voidItem().call(() -> Uni.createFrom().completionStage(() -> counterModelTable.getItem(key)).onItem().call(
                counterDB -> {
                    if (counterDB == null){
                        final CounterDB newCounter = new CounterDB();
                        newCounter.setCount(0);
                        newCounter.setEventName(event);
                        newCounter.setUserEmail(sortKey);
                        if (increment) {
                            newCounter.setCount(newCounter.getCount() + by);
                        }
                        return Uni.createFrom().completionStage(() -> counterModelTable.putItem(newCounter));
                    }
                    if (increment) {
                        counterDB.setCount(counterDB.getCount() + by);
                    } else {
                        counterDB.setCount(Math.max(counterDB.getCount() - by, 0));
                    }
                    return Uni.createFrom().completionStage(() -> counterModelTable.updateItem(counterDB));
                }
        ));
    }

    private Uni<Void> incrementOrDecrementCheckInCounter(UserModelDB userModelDB, boolean increment) {
        String sortKey = CHECK_IN_COUNTER + userModelDB.getVehicleType();
        return incrementOrDecrementCounter(userModelDB.getEventName(), sortKey, increment);
    }

    private Uni<Void> incrementOrDecrementTotalCounter(UserModelDB userModelDB, boolean increment) {
        String sortKey = "total";
        String countByType = "total" + userModelDB.getVehicleType();
        UserMetadataModel userMetadataModel;
        try {
            userMetadataModel = objectMapper.readValue(userModelDB.getMetadata(), UserMetadataModel.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return Uni.combine()
                .all().unis(incrementOrDecrementCounter(userModelDB.getEventName(), sortKey, increment),
                        incrementOrDecrementCounter(userModelDB.getEventName(), countByType, increment),
                        incrementOrDecrementCounter(userModelDB.getEventName(), TOTAL_PARTICIPANTS_COUNTER, increment, userMetadataModel.getPeople().size()))
                .combinedWith((unused, unused2, unused3) -> null);
    }
    private Uni<Void> incrementOrDecrementPaidCounter(UserModelDB userModelDB, boolean increment) {
        return Uni.combine()
                .all().unis(incrementOrDecrementCounter(userModelDB.getEventName(), PAID_COUNTER, increment),
                        incrementOrDecrementCounter(userModelDB.getEventName(), PAID_COUNTER + userModelDB.getVehicleType(), increment))
                .combinedWith((unused, unused2) -> null);
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
        userMetadataModel.setComment(webhookModel.getComment());
        userMetadataModel.setRawWebhook(rawWebhook);

        return UserModelDB.builder()
                .eventName(event)
                .userEmail(webhookModel.getEmail())
                .paid(false)
                .checkedIn(false)
                .metadata(objectMapper.writeValueAsString(userMetadataModel))
                .vehicleType(vehicleType)
                .phoneNumber(webhookModel.getPhoneNumber())
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
                    // other possible types like "guestFree" or "guestHalf"
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
                .phoneNumber(userModel.getMetadata() != null ? userModel.getMetadata().getPhoneNumber() : null)
                .metadata(metadata).build();
    }

    public Uni<Void> sendEmailTemplate(String event, String email, String emailTemplate) {
        return switch (emailTemplate) {
            case "userRegistration" -> Uni.createFrom().voidItem().call(() -> getByEventAndEmail(event, email)
                    .onItem().call((userModel) ->
                            mailerService.sendRegistrationEmail(userModel)
                    ));
            case "almostThere" -> Uni.createFrom().voidItem().call(() -> getByEventAndEmail(event, email)
                    .onItem().call((userModel) ->
                            mailerService.sendAlmostThere(userModel)
                    ));
            default -> Uni.createFrom().failure(NotFoundException::new);
        };
    }

    public Uni<ReconcileCountersResponse> reconcileCounters(String event) {
        // Get current counters before reconciliation
        return getCountersByEvent(event)
                .onItem().transformToUni(beforeCounters -> {
                    // Query all users for this event
                    QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(event).build());
                    
                    return Uni.createFrom().completionStage(() -> {
                        CompletableFuture<List<UserModelDB>> usersFuture = new CompletableFuture<>();
                        List<UserModelDB> allUsers = new ArrayList<>();
                        
                        userModelTable.query(r -> r.queryConditional(queryConditional))
                                .subscribe(page -> {
                                    // Filter out counter records (they don't have user emails in the sort key)
                                    page.items().stream()
                                            .filter(user -> user.getUserEmail() != null && !user.getUserEmail().startsWith("total") 
                                                    && !user.getUserEmail().startsWith(CHECK_IN_COUNTER) 
                                                    && !user.getUserEmail().startsWith(PAID_COUNTER))
                                            .forEach(allUsers::add);
                                })
                                .whenComplete((v, error) -> {
                                    if (error != null) {
                                        usersFuture.completeExceptionally(error);
                                    } else {
                                        usersFuture.complete(allUsers);
                                    }
                                });
                        
                        return usersFuture;
                    }).onItem().transformToUni(users -> {
                                // Calculate actual counts
                                long totalCar = 0;
                                long totalMotorcycle = 0;
                                long totalQuad = 0;
                                long checkedInCar = 0;
                                long checkedInMotorcycle = 0;
                                long checkedInQuad = 0;
                                long paidTotal = 0;
                                long paidCar = 0;
                                long paidMotorcycle = 0;
                                long paidQuad = 0;
                                long totalParticipants = 0;
                                
                                for (UserModelDB user : users) {
                                    String vehicleType = user.getVehicleType();
                                    
                                    // Count by vehicle type
                                    switch (vehicleType) {
                                        case "car":
                                            totalCar++;
                                            if (user.isCheckedIn()) checkedInCar++;
                                            if (user.isPaid()) paidCar++;
                                            break;
                                        case "motorcycle":
                                            totalMotorcycle++;
                                            if (user.isCheckedIn()) checkedInMotorcycle++;
                                            if (user.isPaid()) paidMotorcycle++;
                                            break;
                                        case "quad":
                                            totalQuad++;
                                            if (user.isCheckedIn()) checkedInQuad++;
                                            if (user.isPaid()) paidQuad++;
                                            break;
                                    }
                                    
                                    if (user.isPaid()) paidTotal++;
                                    
                                    // Count total participants (driver + guests)
                                    try {
                                        UserMetadataModel metadata = objectMapper.readValue(user.getMetadata(), UserMetadataModel.class);
                                        totalParticipants += metadata.getPeople() != null ? metadata.getPeople().size() : 1;
                                    } catch (JsonProcessingException e) {
                                        Log.error("Error parsing user metadata for participant count", e);
                                        totalParticipants++; // At least count the driver
                                    }
                                }
                                
                                long totalUsers = totalCar + totalMotorcycle + totalQuad;
                                
                                // Update all counters with actual values
                                List<Uni<Void>> updates = new ArrayList<>();
                                
                                updates.add(setCounter(event, "total", totalUsers));
                                updates.add(setCounter(event, "totalcar", totalCar));
                                updates.add(setCounter(event, "totalmotorcycle", totalMotorcycle));
                                updates.add(setCounter(event, "totalquad", totalQuad));
                                updates.add(setCounter(event, CHECK_IN_COUNTER + "car", checkedInCar));
                                updates.add(setCounter(event, CHECK_IN_COUNTER + "motorcycle", checkedInMotorcycle));
                                updates.add(setCounter(event, CHECK_IN_COUNTER + "quad", checkedInQuad));
                                updates.add(setCounter(event, PAID_COUNTER, paidTotal));
                                updates.add(setCounter(event, PAID_COUNTER + "car", paidCar));
                                updates.add(setCounter(event, PAID_COUNTER + "motorcycle", paidMotorcycle));
                                updates.add(setCounter(event, PAID_COUNTER + "quad", paidQuad));
                                updates.add(setCounter(event, TOTAL_PARTICIPANTS_COUNTER, totalParticipants));
                                
                                return Uni.combine().all().unis(updates)
                                        .combinedWith(results -> null)
                                        .onItem().transformToUni(v -> getCountersByEvent(event))
                                        .onItem().transform(afterCounters -> {
                                            ReconcileCountersResponse response = new ReconcileCountersResponse();
                                            response.setEventId(event);
                                            response.setStatus("success");
                                            response.setBefore(beforeCounters);
                                            response.setAfter(afterCounters);
                                            response.setMessage("Counters reconciled successfully. Scanned " + users.size() + " user records.");
                                            return response;
                                        });
                            });
                });
    }
    
    private Uni<Void> setCounter(String event, String sortKey, long value) {
        Key key = Key.builder().partitionValue(event).sortValue(sortKey).build();
        
        return Uni.createFrom().completionStage(() -> counterModelTable.getItem(key))
                .onItem().transformToUni(counterDB -> {
                    if (counterDB == null) {
                        CounterDB newCounter = new CounterDB();
                        newCounter.setCount(value);
                        newCounter.setEventName(event);
                        newCounter.setUserEmail(sortKey);
                        return Uni.createFrom().completionStage(() -> counterModelTable.putItem(newCounter));
                    } else {
                        counterDB.setCount(value);
                        return Uni.createFrom().completionStage(() -> counterModelTable.updateItem(counterDB));
                    }
                })
                .onItem().ignore().andContinueWithNull();
    }
}
