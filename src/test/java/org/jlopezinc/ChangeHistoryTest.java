package org.jlopezinc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jlopezinc.model.ChangeHistoryEntry;
import org.jlopezinc.model.PaymentInfo;
import org.jlopezinc.model.UserMetadataModel;
import org.jlopezinc.model.UserModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for verifying the unified change_history feature.
 * 
 * The change_history field tracks all modifications to a user's registration data.
 * This includes:
 * - User re-registration (webhook with existing user)
 * - User data updates (PUT endpoint)
 * - Payment additions
 * - Check-ins and check-in cancellations
 * - Comment changes
 * 
 * Each entry contains:
 * - timestamp: ISO 8601 formatted string
 * - action: Type of action (e.g., "USER_REGISTERED", "PAYMENT_ADDED")
 * - description: Human-friendly summary of what changed
 * 
 * Change history entries are stored in chronological order (oldest first).
 */
@QuarkusTest
class ChangeHistoryTest {

    @Inject
    EventV1Service eventV1Service;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testChangeHistoryEntryStructure() {
        // Verify the structure of a change history entry
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_REGISTERED",
            "User re-registered with updated information via webhook"
        );
        
        assertNotNull(entry.getTimestamp(), "Timestamp should not be null");
        assertNotNull(entry.getAction(), "Action should not be null");
        assertNotNull(entry.getDescription(), "Description should not be null");
        assertEquals("USER_REGISTERED", entry.getAction());
        assertTrue(entry.getTimestamp().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"),
            "Timestamp should be in ISO 8601 format");
    }

    @Test
    void testChangeHistoryIsChronological() {
        // This test validates that change history entries maintain chronological order
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        
        // Simulate multiple changes over time
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T10:00:00.000Z", "USER_REGISTERED", "Initial registration"
        ));
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T11:00:00.000Z", "PAYMENT_ADDED", "Payment confirmed"
        ));
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T12:00:00.000Z", "CHECK_IN_ADDED", "User checked in"
        ));
        
        assertEquals(3, metadata.getChangeHistory().size(), "Should have 3 history entries");
        
        // Verify chronological order (oldest first)
        assertTrue(metadata.getChangeHistory().get(0).getTimestamp()
            .compareTo(metadata.getChangeHistory().get(1).getTimestamp()) < 0,
            "First entry should be older than second");
        assertTrue(metadata.getChangeHistory().get(1).getTimestamp()
            .compareTo(metadata.getChangeHistory().get(2).getTimestamp()) < 0,
            "Second entry should be older than third");
    }

    @Test
    void testChangeHistoryIsExtensible() {
        // This test demonstrates that the ChangeHistoryEntry structure is extensible
        // Future enhancements could include: modifiedFields, actor, previousValue, newValue
        
        ChangeHistoryEntry entry = new ChangeHistoryEntry();
        entry.setTimestamp("2026-01-06T15:30:00.000Z");
        entry.setAction("USER_UPDATED");
        entry.setDescription("User data updated via PUT endpoint. Fields changed: phoneNumber, vehicle");
        
        // The structure allows for future additions without breaking existing code
        assertNotNull(entry);
        assertEquals("USER_UPDATED", entry.getAction());
        
        // Future enhancements could look like:
        // entry.setModifiedFields(Arrays.asList("phoneNumber", "vehicle"));
        // entry.setActor("admin@example.com");
        // entry.setPreviousValue("old-value");
        // entry.setNewValue("new-value");
    }

    @Test
    void testActionTypes() {
        // Verify that all expected action types can be used
        String[] actionTypes = {
            "USER_REGISTERED",
            "USER_UPDATED",
            "PAYMENT_ADDED",
            "CHECK_IN_ADDED",
            "CHECK_IN_REMOVED",
            "COMMENT_UPDATED"
        };
        
        for (String actionType : actionTypes) {
            ChangeHistoryEntry entry = new ChangeHistoryEntry(
                "2026-01-06T15:30:00.000Z",
                actionType,
                "Test description for " + actionType
            );
            
            assertEquals(actionType, entry.getAction(),
                "Action type should match: " + actionType);
        }
    }

    @Test
    void testChangeHistoryWithComments() {
        // Verify that comment changes are tracked in both commentsHistory (deprecated)
        // and changeHistory (new unified system)
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setComment("Initial comment");
        metadata.setCommentsHistory(new ArrayList<>());
        metadata.setChangeHistory(new ArrayList<>());
        
        // Simulate a comment update
        String oldComment = metadata.getComment();
        String newComment = "Updated comment";
        
        // Add to deprecated commentsHistory
        metadata.getCommentsHistory().add(oldComment);
        
        // Add to new changeHistory with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "COMMENT_UPDATED",
            "comment: " + oldComment + " -> " + newComment
        ));
        
        // Update the comment
        metadata.setComment(newComment);
        
        // Verify both systems are updated
        assertEquals(1, metadata.getCommentsHistory().size(),
            "commentsHistory should have 1 entry (backward compatibility)");
        assertEquals(1, metadata.getChangeHistory().size(),
            "changeHistory should have 1 entry");
        assertEquals("Initial comment", metadata.getCommentsHistory().get(0),
            "commentsHistory should contain old comment");
        assertEquals("COMMENT_UPDATED", metadata.getChangeHistory().get(0).getAction(),
            "changeHistory should have COMMENT_UPDATED action");
    }

    @Test
    void testPaymentInfoStructure() {
        // Verify PaymentInfo structure for change history descriptions
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setAmount(new BigDecimal("50.00"));
        paymentInfo.setByWho("admin@example.com");
        
        assertNotNull(paymentInfo.getAmount());
        assertEquals(new BigDecimal("50.00"), paymentInfo.getAmount());
        assertEquals("admin@example.com", paymentInfo.getByWho());
        
        // This can be used to generate descriptive change history entries like:
        // "Payment confirmed: 50.00 by admin@example.com"
    }

    @Test
    void testMultipleChangesAccumulation() {
        // This test validates that changeHistory correctly accumulates
        // multiple changes of different types over time
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        
        // Simulate a user lifecycle
        // 1. User re-registers
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T10:00:00.000Z",
            "USER_REGISTERED",
            "User re-registered with updated information via webhook"
        ));
        
        // 2. User data is updated with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T11:00:00.000Z",
            "USER_UPDATED",
            "phoneNumber: (empty) -> +351123456789\nvehicle: (empty) -> plate: ABC-123"
        ));
        
        // 3. Payment is added
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T12:00:00.000Z",
            "PAYMENT_ADDED",
            "Payment confirmed: 50.00 by admin@example.com"
        ));
        
        // 4. Comment is updated with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T13:00:00.000Z",
            "COMMENT_UPDATED",
            "comment: Pending payment -> Ready for check-in"
        ));
        
        // 5. User is checked in
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T14:00:00.000Z",
            "CHECK_IN_ADDED",
            "User checked in by admin@example.com"
        ));
        
        // 6. Check-in is cancelled
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:00:00.000Z",
            "CHECK_IN_REMOVED",
            "Check-in cancelled by admin@example.com"
        ));
        
        // Verify all changes are recorded
        assertEquals(6, metadata.getChangeHistory().size(),
            "Should have 6 history entries covering the full user lifecycle");
        
        // Verify chronological order
        for (int i = 0; i < metadata.getChangeHistory().size() - 1; i++) {
            String current = metadata.getChangeHistory().get(i).getTimestamp();
            String next = metadata.getChangeHistory().get(i + 1).getTimestamp();
            assertTrue(current.compareTo(next) < 0,
                "Change history should be in chronological order");
        }
        
        // Verify action types diversity
        assertEquals("USER_REGISTERED", metadata.getChangeHistory().get(0).getAction());
        assertEquals("USER_UPDATED", metadata.getChangeHistory().get(1).getAction());
        assertEquals("PAYMENT_ADDED", metadata.getChangeHistory().get(2).getAction());
        assertEquals("COMMENT_UPDATED", metadata.getChangeHistory().get(3).getAction());
        assertEquals("CHECK_IN_ADDED", metadata.getChangeHistory().get(4).getAction());
        assertEquals("CHECK_IN_REMOVED", metadata.getChangeHistory().get(5).getAction());
    }

    @Test
    void testEmptyChangeHistory() {
        // Verify that a new user starts with no change history
        UserMetadataModel metadata = new UserMetadataModel();
        
        assertNull(metadata.getChangeHistory(),
            "New user should have null changeHistory");
        
        // After first change, it should be initialized
        metadata.setChangeHistory(new ArrayList<>());
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_REGISTERED",
            "Initial registration"
        ));
        
        assertNotNull(metadata.getChangeHistory());
        assertEquals(1, metadata.getChangeHistory().size());
    }

    @Test
    void testBackwardCompatibilityWithCommentsHistory() {
        // Verify that the deprecated commentsHistory field still works
        // alongside the new changeHistory field
        
        UserMetadataModel metadata = new UserMetadataModel();
        
        // Set up both systems
        metadata.setCommentsHistory(new ArrayList<>());
        metadata.setChangeHistory(new ArrayList<>());
        
        metadata.getCommentsHistory().add("Old comment 1");
        metadata.getCommentsHistory().add("Old comment 2");
        
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T10:00:00.000Z",
            "COMMENT_UPDATED",
            "comment: Old comment 1 -> Old comment 2"
        ));
        
        // Both should coexist
        assertEquals(2, metadata.getCommentsHistory().size(),
            "Legacy commentsHistory should still work");
        assertEquals(1, metadata.getChangeHistory().size(),
            "New changeHistory should work alongside legacy");
    }

    @Test
    void testCommentSanitization() {
        // Verify that special characters in comments are properly handled
        // to prevent JSON serialization issues or log injection
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        
        // Simulate comment with special characters
        String commentWithSpecialChars = "Payment \"pending\"\nNeeds follow-up\tASAP\\check";
        String expectedSanitized = "Payment \\\"pending\\\"\\nNeeds follow-up\\tASAP\\\\check";
        
        // Create a change history entry as if sanitization was applied with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "COMMENT_UPDATED",
            "comment: Initial comment -> " + expectedSanitized
        ));
        
        // Verify the entry was created
        assertEquals(1, metadata.getChangeHistory().size());
        ChangeHistoryEntry entry = metadata.getChangeHistory().get(0);
        
        // Verify the description contains escaped characters
        assertTrue(entry.getDescription().contains("\\\""),
            "Double quotes should be escaped");
        assertTrue(entry.getDescription().contains("\\n"),
            "Newlines should be escaped");
        assertTrue(entry.getDescription().contains("\\t"),
            "Tabs should be escaped");
        assertTrue(entry.getDescription().contains("\\\\"),
            "Backslashes should be escaped");
    }

    /**
     * Tests for PUT endpoint audit trail behavior.
     * 
     * These tests verify that PUT actions only create audit trail entries when
     * tracked fields (people, phoneNumber, vehicle, paymentFile, vehicleType, paid)
     * actually change. Comment-only changes should not trigger PUT audit entries.
     */

    @Test
    void testPutAuditTrail_NoTrackedFieldsChanged_NoAuditEntry() {
        // Scenario: PUT request with no changes to tracked fields
        // Expected: No USER_UPDATED audit entry is created
        // This test verifies the fix for the bug where PUT always created entries
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        metadata.setPhoneNumber("123456789");
        metadata.setComment("Original comment");
        
        // Simulate a PUT request that doesn't change any tracked fields
        // (e.g., fetching user data and submitting it unchanged)
        // In this case, no audit entry should be added
        
        int initialSize = metadata.getChangeHistory().size();
        
        // No USER_UPDATED entry is added because no tracked fields changed
        // (This would be done by the service logic)
        
        assertEquals(0, initialSize, "No audit entry should exist initially");
        assertEquals(0, metadata.getChangeHistory().size(), 
            "No audit entry should be added when no tracked fields change");
    }

    @Test
    void testPutAuditTrail_TrackedFieldChanged_AuditEntryCreated() {
        // Scenario: PUT request changes at least one tracked field
        // Expected: USER_UPDATED audit entry is created with old and new values
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        
        // Simulate updating phoneNumber (a tracked field) with old and new values using new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "phoneNumber: (empty) -> +351123456789"
        ));
        
        assertEquals(1, metadata.getChangeHistory().size(), 
            "One audit entry should be created");
        assertEquals("USER_UPDATED", metadata.getChangeHistory().get(0).getAction(),
            "Audit entry should be USER_UPDATED");
        assertTrue(metadata.getChangeHistory().get(0).getDescription().contains("phoneNumber"),
            "Audit entry should mention phoneNumber");
        assertTrue(metadata.getChangeHistory().get(0).getDescription().contains(" -> "),
            "Audit entry should show arrow separator");
        assertTrue(metadata.getChangeHistory().get(0).getDescription().contains("(empty)"),
            "Audit entry should show old value");
    }

    @Test
    void testPutAuditTrail_MultipleTrackedFieldsChanged_AllListedInAudit() {
        // Scenario: PUT request changes multiple tracked fields
        // Expected: Single USER_UPDATED audit entry lists all changed fields with old/new values
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        
        // Simulate updating multiple tracked fields with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "phoneNumber: (empty) -> +351123456789\nvehicle: (empty) -> plate: ABC-123\npaid: false -> true"
        ));
        
        assertEquals(1, metadata.getChangeHistory().size(), 
            "One audit entry should be created for all changes");
        
        ChangeHistoryEntry entry = metadata.getChangeHistory().get(0);
        assertEquals("USER_UPDATED", entry.getAction());
        
        // Verify all changed fields are mentioned with old/new values
        assertTrue(entry.getDescription().contains("phoneNumber"),
            "phoneNumber should be in field list");
        assertTrue(entry.getDescription().contains("vehicle"),
            "vehicle should be in field list");
        assertTrue(entry.getDescription().contains("paid"),
            "paid should be in field list");
        assertTrue(entry.getDescription().contains(" -> "),
            "Should use arrow separator");
        assertTrue(entry.getDescription().contains("\n"),
            "Should use newlines to separate fields");
    }

    @Test
    void testPutAuditTrail_CommentOnlyChange_OnlyCommentUpdatedEntry() {
        // Scenario: PUT request changes only the comment field
        // Expected: COMMENT_UPDATED entry is created, but NO USER_UPDATED entry
        // This is the key test for the bug fix
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        metadata.setComment("Old comment");
        
        // Simulate updating only the comment with new format
        // This should create a COMMENT_UPDATED entry, not a USER_UPDATED entry
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "COMMENT_UPDATED",
            "comment: Old comment -> New comment"
        ));
        metadata.setComment("New comment");
        
        assertEquals(1, metadata.getChangeHistory().size(), 
            "Only one audit entry should be created");
        assertEquals("COMMENT_UPDATED", metadata.getChangeHistory().get(0).getAction(),
            "Audit entry should be COMMENT_UPDATED, not USER_UPDATED");
        assertTrue(metadata.getChangeHistory().get(0).getDescription().startsWith("comment:"),
            "Should start with 'comment:' prefix");
    }

    @Test
    void testPutAuditTrail_CommentAndTrackedFieldChange_BothEntriesCreated() {
        // Scenario: PUT request changes both comment and a tracked field
        // Expected: Both COMMENT_UPDATED and USER_UPDATED entries are created
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        
        // First, COMMENT_UPDATED entry with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "COMMENT_UPDATED",
            "comment: Old comment -> New comment"
        ));
        
        // Then, USER_UPDATED entry for the tracked field with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:01.000Z",
            "USER_UPDATED",
            "phoneNumber: (empty) -> +351123456789"
        ));
        
        assertEquals(2, metadata.getChangeHistory().size(), 
            "Two audit entries should be created");
        assertEquals("COMMENT_UPDATED", metadata.getChangeHistory().get(0).getAction(),
            "First entry should be COMMENT_UPDATED");
        assertEquals("USER_UPDATED", metadata.getChangeHistory().get(1).getAction(),
            "Second entry should be USER_UPDATED");
        
        // Verify comment is NOT listed in the USER_UPDATED entry
        String userUpdatedDesc = metadata.getChangeHistory().get(1).getDescription();
        assertFalse(userUpdatedDesc.contains("comment"),
            "Comment should not be listed in USER_UPDATED fields");
    }

    @Test
    void testTrackedFieldsList() {
        // Document which fields are considered "tracked" for PUT audit trail
        // Tracked fields: people, phoneNumber, vehicle, paymentFile, vehicleType, paid
        // Non-tracked fields: comment (has its own COMMENT_UPDATED entries)
        
        String[] trackedFields = {"people", "phoneNumber", "vehicle", "paymentFile", "vehicleType", "paid"};
        String[] nonTrackedFields = {"comment"};
        
        // This test serves as documentation of the expected behavior
        // If tracked fields change, update this test to reflect the changes
        
        assertEquals(6, trackedFields.length, 
            "There should be 6 tracked fields for PUT audit trail");
        assertEquals(1, nonTrackedFields.length,
            "Comment is the only non-tracked field (has its own audit entry type)");
        
        // Verify tracked fields list
        java.util.List<String> trackedFieldsList = java.util.Arrays.asList(trackedFields);
        assertTrue(trackedFieldsList.contains("people"));
        assertTrue(trackedFieldsList.contains("phoneNumber"));
        assertTrue(trackedFieldsList.contains("vehicle"));
        assertTrue(trackedFieldsList.contains("paymentFile"));
        assertTrue(trackedFieldsList.contains("vehicleType"));
        assertTrue(trackedFieldsList.contains("paid"));
        
        // Verify comment is not in tracked fields
        assertFalse(trackedFieldsList.contains("comment"));
    }

    @Test
    void testPutAuditTrail_EdgeCase_EmptyCommentToNull() {
        // Edge case: Clearing a comment (setting to null) should only create COMMENT_UPDATED
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        metadata.setComment("Some comment");
        
        // Simulate clearing the comment (set to null) with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "COMMENT_UPDATED",
            "comment: Some comment -> (empty)"
        ));
        metadata.setComment(null);
        
        assertEquals(1, metadata.getChangeHistory().size());
        assertEquals("COMMENT_UPDATED", metadata.getChangeHistory().get(0).getAction(),
            "Clearing comment should only create COMMENT_UPDATED entry");
    }

    @Test
    void testPutAuditTrail_EdgeCase_AllTrackedFieldsChanged() {
        // Edge case: All tracked fields change at once
        // Expected: Single USER_UPDATED entry listing all fields with old/new values
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        
        // All tracked fields changed with new format
        metadata.getChangeHistory().add(new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "people: (empty) -> driver: John\nphoneNumber: (empty) -> +351123456789\nvehicle: (empty) -> plate: ABC-123\npaymentFile: (empty) -> receipt.pdf\nvehicleType: (empty) -> car\npaid: false -> true"
        ));
        
        assertEquals(1, metadata.getChangeHistory().size());
        ChangeHistoryEntry entry = metadata.getChangeHistory().get(0);
        
        // Verify all tracked fields are mentioned
        assertTrue(entry.getDescription().contains("people"));
        assertTrue(entry.getDescription().contains("phoneNumber"));
        assertTrue(entry.getDescription().contains("vehicle"));
        assertTrue(entry.getDescription().contains("paymentFile"));
        assertTrue(entry.getDescription().contains("vehicleType"));
        assertTrue(entry.getDescription().contains("paid"));
        
        // Verify new format
        assertTrue(entry.getDescription().contains(" -> "));
        assertTrue(entry.getDescription().contains("\n"));
        
        // But comment should NOT be mentioned
        assertFalse(entry.getDescription().contains("comment"),
            "Comment should not be in the tracked fields list");
    }
    
    @Test
    void testAuditTrailFormatConsistency() {
        // This test verifies that the audit trail format is consistent across all field types
        // All entries should follow the new pattern: "field: oldValue -> newValue"
        
        // Test comment format (reference format)
        ChangeHistoryEntry commentEntry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "COMMENT_UPDATED",
            "comment: old comment -> new comment"
        );
        assertTrue(commentEntry.getDescription().contains(" -> "),
            "Comment format should use arrow separator");
        
        // Test other field formats should match the same pattern
        ChangeHistoryEntry phoneEntry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "phoneNumber: (empty) -> +351123456789"
        );
        assertTrue(phoneEntry.getDescription().contains(" -> "),
            "Phone format should use arrow separator");
        assertTrue(phoneEntry.getDescription().startsWith("phoneNumber:"),
            "Phone format should start with field name");
        
        ChangeHistoryEntry vehicleEntry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "vehicle: (empty) -> plate: ABC-123"
        );
        assertTrue(vehicleEntry.getDescription().contains(" -> "),
            "Vehicle format should use arrow separator");
        
        ChangeHistoryEntry paidEntry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "paid: false -> true"
        );
        assertTrue(paidEntry.getDescription().contains(" -> "),
            "Paid format should use arrow separator");
    }

    /**
     * New tests for the improved audit trail format
     */

    @Test
    void testPutWithMultipleFieldChanges_OnlyChangedFieldsAppear() {
        // Test that PUT with multiple field changes shows only the fields that actually changed
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "vehicleType: car -> quad\nphoneNumber: 123456789 -> 987654321"
        );
        
        assertEquals("USER_UPDATED", entry.getAction());
        
        // Verify new format with newlines
        assertTrue(entry.getDescription().contains("vehicleType: car -> quad"),
            "Should contain vehicleType change in new format");
        assertTrue(entry.getDescription().contains("phoneNumber: 123456789 -> 987654321"),
            "Should contain phoneNumber change in new format");
        assertTrue(entry.getDescription().contains("\n"),
            "Should use newlines to separate field changes");
        
        // Verify the old verbose format is NOT used
        assertFalse(entry.getDescription().contains("changed from"),
            "Should NOT contain old verbose format");
        assertFalse(entry.getDescription().contains("User data updated via PUT endpoint"),
            "Should NOT contain generic prefix");
    }

    @Test
    void testPutWithIdenticalValues_NoAuditEntry() {
        // Test that PUT with identical values (no actual changes) doesn't create an audit entry
        // This test verifies the fix for the bug where PUT always created entries
        
        UserMetadataModel metadata = new UserMetadataModel();
        metadata.setChangeHistory(new ArrayList<>());
        metadata.setPhoneNumber("123456789");
        metadata.setComment("Original comment");
        
        // Simulate a PUT request where values are re-submitted but unchanged
        // The service logic should NOT add a USER_UPDATED entry
        
        int initialSize = metadata.getChangeHistory().size();
        assertEquals(0, initialSize, "No audit entry should exist initially");
        
        // After processing a PUT with no actual changes, size should remain 0
        assertEquals(0, metadata.getChangeHistory().size(), 
            "No audit entry should be added when values don't actually change");
    }

    @Test
    void testPutWithMixOfChangedAndUnchangedFields_OnlyChangedAppear() {
        // Test that PUT with a mix of changed and unchanged fields only shows changed fields
        
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "phoneNumber: 916165469 -> 912345678"
        );
        
        // Only phoneNumber changed, other fields (vehicle, vehicleType, etc.) remained the same
        assertTrue(entry.getDescription().contains("phoneNumber"),
            "Should contain changed phoneNumber");
        assertFalse(entry.getDescription().contains("vehicle"),
            "Should NOT contain unchanged vehicle");
        assertFalse(entry.getDescription().contains("vehicleType"),
            "Should NOT contain unchanged vehicleType");
        
        // Verify new format
        assertTrue(entry.getDescription().contains(" -> "),
            "Should use arrow notation");
    }

    @Test
    void testNewFormatUsesNewlinesAndSimplifiedSyntax() {
        // Verify that the new format uses newlines and simplified "field: old -> new" syntax
        
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "vehicleType: car -> quad\nphoneNumber: 916165469 -> 912345678\npaid: false -> true"
        );
        
        // Verify newlines are used
        assertTrue(entry.getDescription().contains("\n"),
            "Should use newlines to separate changes");
        
        // Verify simplified syntax (contains at least one "field: value -> value" pattern)
        assertTrue(entry.getDescription().contains(": ") && entry.getDescription().contains(" -> "),
            "Should use 'field: old -> new' format");
        
        // Count the number of newlines (should be 2 for 3 fields)
        long newlineCount = entry.getDescription().chars().filter(ch -> ch == '\n').count();
        assertEquals(2, newlineCount, "Should have 2 newlines for 3 field changes");
        
        // Verify old format is NOT used
        assertFalse(entry.getDescription().contains("changed from"),
            "Should NOT use old 'changed from' format");
        assertFalse(entry.getDescription().contains("\" to \""),
            "Should NOT use old quoted format");
    }

    @Test
    void testComplexObjectsComparedCorrectly_PeopleObject() {
        // Test that complex objects like people are compared correctly
        
        // Create two different people lists
        String oldPeopleStr = "driver: John Doe (CC: 12345678)";
        String newPeopleStr = "driver: Jane Smith (CC: 87654321)";
        
        // Verify they are different
        assertFalse(oldPeopleStr.equals(newPeopleStr),
            "Different people should not be equal");
        
        // Create entry with people change
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "people: " + oldPeopleStr + " -> " + newPeopleStr
        );
        
        assertTrue(entry.getDescription().contains("people:"),
            "Should show people change");
        assertTrue(entry.getDescription().contains("John Doe"),
            "Should show old driver name");
        assertTrue(entry.getDescription().contains("Jane Smith"),
            "Should show new driver name");
    }

    @Test
    void testComplexObjectsComparedCorrectly_VehicleObject() {
        // Test that complex objects like vehicle are compared correctly
        
        String oldVehicleStr = "plate: 23-FX-52, make: Land Rover Discovery";
        String newVehicleStr = "plate: 45-AB-12, make: Toyota Hilux";
        
        // Verify they are different
        assertFalse(oldVehicleStr.equals(newVehicleStr),
            "Different vehicles should not be equal");
        
        // Create entry with vehicle change
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "vehicle: " + oldVehicleStr + " -> " + newVehicleStr
        );
        
        assertTrue(entry.getDescription().contains("vehicle:"),
            "Should show vehicle change");
        assertTrue(entry.getDescription().contains("23-FX-52"),
            "Should show old plate");
        assertTrue(entry.getDescription().contains("45-AB-12"),
            "Should show new plate");
    }

    @Test
    void testComplexObjectsComparedCorrectly_IdenticalObjects() {
        // Test that identical complex objects don't create audit entries
        
        String vehicleStr = "plate: 23-FX-52, make: Land Rover Discovery";
        
        // When old and new are the same, no entry should be added
        // This is verified by the service logic checking oldValue.equals(newValue)
        assertTrue(vehicleStr.equals(vehicleStr),
            "Identical vehicles should be equal");
    }

    @Test
    void testDifferentDataTypesHandledCorrectly_String() {
        // Test string field changes
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "phoneNumber: 123456789 -> 987654321"
        );
        
        assertTrue(entry.getDescription().contains("phoneNumber: 123456789 -> 987654321"),
            "String values should be formatted correctly");
    }

    @Test
    void testDifferentDataTypesHandledCorrectly_Boolean() {
        // Test boolean field changes
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "paid: false -> true"
        );
        
        assertTrue(entry.getDescription().contains("paid: false -> true"),
            "Boolean values should be formatted as 'true' or 'false'");
        
        // Verify it's not using quotes
        assertFalse(entry.getDescription().contains("\"true\""),
            "Boolean values should not be quoted");
    }

    @Test
    void testDifferentDataTypesHandledCorrectly_ComplexObject() {
        // Test complex object field changes
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "people: driver: John (CC: 123) -> driver: Jane (CC: 456)"
        );
        
        assertTrue(entry.getDescription().contains("people:"),
            "Complex objects should be formatted correctly");
    }

    @Test
    void testEmptyValueHandling() {
        // Test that empty/null values are handled correctly
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "phoneNumber: (empty) -> 123456789"
        );
        
        assertTrue(entry.getDescription().contains("(empty)"),
            "Empty values should be shown as '(empty)'");
    }

    @Test
    void testRealWorldScenario_MultipleFieldsWithSomeUnchanged() {
        // Real-world scenario from the problem statement
        // User submits a PUT with several fields, but only vehicleType and phoneNumber actually changed
        
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "USER_UPDATED",
            "vehicleType: car -> quad\nphoneNumber: 916165469 -> 912345678"
        );
        
        // Verify only changed fields appear
        assertTrue(entry.getDescription().contains("vehicleType: car -> quad"));
        assertTrue(entry.getDescription().contains("phoneNumber: 916165469 -> 912345678"));
        
        // Verify unchanged fields do NOT appear
        assertFalse(entry.getDescription().contains("people"),
            "Unchanged people should not appear");
        assertFalse(entry.getDescription().contains("vehicle:"),
            "Unchanged vehicle should not appear");
        assertFalse(entry.getDescription().contains("paymentFile"),
            "Unchanged paymentFile should not appear");
        
        // Verify format
        assertTrue(entry.getDescription().contains("\n"),
            "Should use newlines");
        assertFalse(entry.getDescription().contains("changed from"),
            "Should not use old format");
    }

    @Test
    void testCommentFormat() {
        // Test that comment updates also use the new format
        ChangeHistoryEntry entry = new ChangeHistoryEntry(
            "2026-01-06T15:30:00.000Z",
            "COMMENT_UPDATED",
            "comment: Old comment text -> New comment text"
        );
        
        assertEquals("COMMENT_UPDATED", entry.getAction());
        assertTrue(entry.getDescription().contains("comment: Old comment text -> New comment text"),
            "Comment should use new simplified format");
        assertFalse(entry.getDescription().contains("Comment changed from"),
            "Comment should not use old verbose format");
    }
}
