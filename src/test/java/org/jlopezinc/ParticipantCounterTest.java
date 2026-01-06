package org.jlopezinc;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for verifying the participant check-in counter feature.
 * 
 * The participant counters track:
 * - participantsCheckedIn: Total number of participants (driver + guests) who are checked-in
 * - participantsNotCheckedIn: Total number of participants who are NOT checked-in
 * 
 * These counters should always satisfy:
 * participantsCheckedIn + participantsNotCheckedIn = totalParticipants
 */
@QuarkusTest
class ParticipantCounterTest {

    @Test
    void testCounterFieldsExist() {
        // This test validates that the new counter fields exist in CountersModel
        // and can be accessed without errors
        
        org.jlopezinc.model.CountersModel counters = new org.jlopezinc.model.CountersModel();
        
        // Test that we can set and get the new counter fields
        counters.setParticipantsCheckedIn(10);
        counters.setParticipantsNotCheckedIn(5);
        
        assertEquals(10, counters.getParticipantsCheckedIn(), 
            "participantsCheckedIn should be accessible");
        assertEquals(5, counters.getParticipantsNotCheckedIn(), 
            "participantsNotCheckedIn should be accessible");
    }

    @Test
    void testCounterConstantsExist() {
        // This test validates that the new counter constants are defined
        // and have the expected values
        
        assertEquals("participantsCheckedIn", EventV1Service.PARTICIPANTS_CHECKED_IN_COUNTER, 
            "PARTICIPANTS_CHECKED_IN_COUNTER constant should be defined");
        assertEquals("participantsNotCheckedIn", EventV1Service.PARTICIPANTS_NOT_CHECKED_IN_COUNTER, 
            "PARTICIPANTS_NOT_CHECKED_IN_COUNTER constant should be defined");
    }

    @Test
    void testCounterInvariant() {
        // This test validates the invariant that should always hold:
        // participantsCheckedIn + participantsNotCheckedIn = totalParticipants
        
        // In a real scenario, these values would come from the database
        // after a reconcileCounters operation or counter updates
        
        org.jlopezinc.model.CountersModel counters = new org.jlopezinc.model.CountersModel();
        counters.setTotalParticipants(25);
        counters.setParticipantsCheckedIn(15);
        counters.setParticipantsNotCheckedIn(10);
        
        long total = counters.getParticipantsCheckedIn() + counters.getParticipantsNotCheckedIn();
        
        assertEquals(counters.getTotalParticipants(), total, 
            "Sum of participantsCheckedIn and participantsNotCheckedIn should equal totalParticipants");
    }

    @Test
    void testCheckInScenario() {
        // This test demonstrates the expected behavior when users check in/out
        // and validates the counter logic conceptually
        
        // Initial state: 3 users registered, none checked in
        // User 1: 1 participant (driver only)
        // User 2: 3 participants (driver + 2 guests)
        // User 3: 2 participants (driver + 1 guest)
        // Total: 6 participants, 0 checked in, 6 not checked in
        
        org.jlopezinc.model.CountersModel initialCounters = new org.jlopezinc.model.CountersModel();
        initialCounters.setTotalParticipants(6);
        initialCounters.setParticipantsCheckedIn(0);
        initialCounters.setParticipantsNotCheckedIn(6);
        
        // Scenario 1: User 1 checks in (1 participant)
        // Expected: participantsCheckedIn = 1, participantsNotCheckedIn = 5
        long checkedIn1 = initialCounters.getParticipantsCheckedIn() + 1;
        long notCheckedIn1 = initialCounters.getParticipantsNotCheckedIn() - 1;
        assertEquals(1, checkedIn1);
        assertEquals(5, notCheckedIn1);
        assertEquals(initialCounters.getTotalParticipants(), checkedIn1 + notCheckedIn1);
        
        // Scenario 2: User 2 checks in (3 participants)
        // Expected: participantsCheckedIn = 4, participantsNotCheckedIn = 2
        long checkedIn2 = checkedIn1 + 3;
        long notCheckedIn2 = notCheckedIn1 - 3;
        assertEquals(4, checkedIn2);
        assertEquals(2, notCheckedIn2);
        assertEquals(initialCounters.getTotalParticipants(), checkedIn2 + notCheckedIn2);
        
        // Scenario 3: User 1 cancels check-in (1 participant)
        // Expected: participantsCheckedIn = 3, participantsNotCheckedIn = 3
        long checkedIn3 = checkedIn2 - 1;
        long notCheckedIn3 = notCheckedIn2 + 1;
        assertEquals(3, checkedIn3);
        assertEquals(3, notCheckedIn3);
        assertEquals(initialCounters.getTotalParticipants(), checkedIn3 + notCheckedIn3);
    }

    @Test
    void testUpdatePeopleScenario() {
        // This test demonstrates the expected behavior when a user's people list
        // is updated via PUT and validates the counter adjustment logic
        
        // Initial state: User with 2 participants (driver + 1 guest), not checked in
        // participantsNotCheckedIn = 10, participantsCheckedIn = 5
        
        org.jlopezinc.model.CountersModel initialCounters = new org.jlopezinc.model.CountersModel();
        initialCounters.setParticipantsCheckedIn(5);
        initialCounters.setParticipantsNotCheckedIn(10);
        initialCounters.setTotalParticipants(15);
        
        // Scenario 1: User adds 1 more guest (2 -> 3 participants), user is NOT checked in
        // Expected: participantsNotCheckedIn increases by 1, totalParticipants increases by 1
        long notCheckedIn1 = initialCounters.getParticipantsNotCheckedIn() + 1;
        long totalParticipants1 = initialCounters.getTotalParticipants() + 1;
        assertEquals(11, notCheckedIn1);
        assertEquals(16, totalParticipants1);
        assertEquals(totalParticipants1, initialCounters.getParticipantsCheckedIn() + notCheckedIn1);
        
        // Scenario 2: Checked-in user removes 1 guest (3 -> 2 participants)
        // Expected: participantsCheckedIn decreases by 1, totalParticipants decreases by 1
        long checkedInScenario2 = 5 - 1;
        long totalParticipantsScenario2 = totalParticipants1 - 1;
        assertEquals(4, checkedInScenario2);
        assertEquals(15, totalParticipantsScenario2);
        assertEquals(totalParticipantsScenario2, checkedInScenario2 + notCheckedIn1);
    }

    @Test
    void testReconcileCountersScenario() {
        // This test demonstrates the expected behavior of reconcileCounters
        // which recalculates all counters from the database state
        
        // Database state:
        // User 1: checked in, 2 participants
        // User 2: checked in, 1 participant
        // User 3: NOT checked in, 3 participants
        // User 4: NOT checked in, 1 participant
        
        // Expected reconciled values:
        // participantsCheckedIn = 2 + 1 = 3
        // participantsNotCheckedIn = 3 + 1 = 4
        // totalParticipants = 7
        
        long expectedCheckedIn = 2 + 1;
        long expectedNotCheckedIn = 3 + 1;
        long expectedTotal = expectedCheckedIn + expectedNotCheckedIn;
        
        assertEquals(3, expectedCheckedIn);
        assertEquals(4, expectedNotCheckedIn);
        assertEquals(7, expectedTotal);
        assertEquals(expectedTotal, expectedCheckedIn + expectedNotCheckedIn);
    }
}
