package org.jlopezinc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jlopezinc.dynamodb.UserModelDB;
import org.jlopezinc.model.UserMetadataModel;
import org.jlopezinc.model.UserModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PhoneNumberEndpointTest {

    @Test
    @Disabled // todo resolve injections
    void testTransformWebHookSetsPhoneNumber() throws JsonProcessingException {
        String raw = "{\n" +
                "    \"created_at\": \"1698171807321\",\n" +
                "    \"formId\": \"176ApWzf50S01l4ROdEfIFfGpJ-EiBmCns5jgz3IqhGY\",\n" +
                "    \"formName\": \"Registo XI TT Amigos do Natal\",\n" +
                "    \"formUrl\": \"https://docs.google.com/forms/d/e/1FAIpQLSeiUW1SBf4afmODFtqMzA0oeQLZVLO7FLC5zf6u6J9ULktTYA/viewform\",\n" +
                "    \"submittedAt\": \"1698171313407\",\n" +
                "    \"driverName\": \"João Lopes\",\n" +
                "    \"driverCc\": \"12808562\",\n" +
                "    \"address\": \"3230-269\",\n" +
                "    \"phoneNumber\": \"916165469\",\n" +
                "    \"vehicleType\": \"Mota\",\n" +
                "    \"vehiclePlate\": \"123-131\",\n" +
                "    \"vehicleBrand\": \"land rover\",\n" +
                "    \"guestsNumber\": \"2\",\n" +
                "    \"guestsNames\": \"Andreia Santos<BR/>Leonor Lopes\",\n" +
                "    \"guestsCc\": \"128085<BR/>656465465\",\n" +
                "    \"accept\": \"Sim\",\n" +
                "    \"payment\": \"<a href='https://drive.google.com/open?id=1dd5R_KcAFIva5Q6Yiah0DuQuU45gFwXj'>File 1<plain>https://drive.google.com/open?id=1dd5R_KcAFIva5Q6Yiah0DuQuU45gFwXj</plain></a>\",\n" +
                "    \"email\": \"jlopez.inc@gmail.com\"\n" +
                "}";

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        UserModelDB userModelDB = new EventV1Service(null).transformWebHook("ttamigosnatal2023", raw, objectMapper);
        
        // Verify phoneNumber is set on UserModelDB
        Assertions.assertEquals("916165469", userModelDB.getPhoneNumber());
        
        // Verify phoneNumber is also in metadata
        UserMetadataModel userMetadataModel = objectMapper.readValue(userModelDB.getMetadata(), UserMetadataModel.class);
        Assertions.assertEquals("916165469", userMetadataModel.getPhoneNumber());
    }

    @Test
    @Disabled // todo resolve injections
    void testUserModelTransformSetsPhoneNumber() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Create a UserModel with metadata containing phoneNumber
        UserModel userModel = new UserModel();
        userModel.setEventName("testEvent");
        userModel.setUserEmail("test@example.com");
        userModel.setPaid(true);
        userModel.setCheckedIn(false);
        userModel.setVehicleType("car");
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setPhoneNumber("123456789");
        userModel.setMetadata(metadata);
        
        EventV1Service service = new EventV1Service(null);
        service.objectMapper = objectMapper;
        
        // Transform to UserModelDB
        UserModelDB userModelDB = service.userModelTransform(userModel);
        
        // Verify phoneNumber is extracted and set
        Assertions.assertEquals("123456789", userModelDB.getPhoneNumber());
    }

    @Test
    @Disabled // todo resolve injections
    void testUserModelTransformHandlesNullMetadata() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Create a UserModel without metadata
        UserModel userModel = new UserModel();
        userModel.setEventName("testEvent");
        userModel.setUserEmail("test@example.com");
        userModel.setPaid(true);
        userModel.setCheckedIn(false);
        userModel.setVehicleType("car");
        userModel.setMetadata(null);
        
        EventV1Service service = new EventV1Service(null);
        service.objectMapper = objectMapper;
        
        // Transform to UserModelDB
        UserModelDB userModelDB = service.userModelTransform(userModel);
        
        // Verify phoneNumber is null when metadata is null
        Assertions.assertNull(userModelDB.getPhoneNumber());
    }
}
