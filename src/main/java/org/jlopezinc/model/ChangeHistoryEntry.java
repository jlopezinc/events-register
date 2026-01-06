package org.jlopezinc.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single entry in the change history for an event user.
 * 
 * This class is part of the unified audit trail system that tracks all modifications
 * to a user's registration data. Each entry includes:
 * - timestamp: ISO 8601 formatted timestamp of when the change occurred
 * - action: The type of action performed (e.g., "USER_REGISTERED", "PAYMENT_ADDED")
 * - description: A human-friendly summary of what changed
 * 
 * The schema is designed to be extensible for future metadata additions such as:
 * - modifiedFields: List of specific fields that were changed
 * - actor: Who performed the action (user ID, admin name, etc.)
 * - previousValue/newValue: For detailed field-level tracking
 * 
 * Change history entries are stored in ascending chronological order (oldest first).
 * This allows consumers to easily understand the sequence of events by reading
 * from the beginning of the array.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class ChangeHistoryEntry {
    /**
     * ISO 8601 formatted timestamp indicating when this change occurred.
     * Example: "2026-01-06T15:30:00.000Z"
     */
    private String timestamp;
    
    /**
     * The type of action that was performed. Common values include:
     * - USER_REGISTERED: User was re-registered (webhook with existing user)
     * - USER_UPDATED: User data was updated via PUT endpoint
     * - PAYMENT_ADDED: Payment information was added or updated
     * - CHECK_IN_ADDED: User was checked in
     * - CHECK_IN_REMOVED: User's check-in was cancelled
     * - COMMENT_UPDATED: User comment was changed (backward compatibility)
     */
    private String action;
    
    /**
     * A human-friendly description of what changed.
     * Examples:
     * - "User re-registered with updated information"
     * - "Payment confirmed: €50.00 by Admin"
     * - "Checked in by admin@example.com"
     * - "Check-in cancelled by admin@example.com"
     */
    private String description;
}
