# Change History Implementation Guide

## Overview

The unified `changeHistory` audit trail tracks all modifications to a user's registration data, providing a comprehensive timeline of user-level mutations. This document describes the implementation details and conventions.

## Architecture

### Data Model

**ChangeHistoryEntry** (`src/main/java/org/jlopezinc/model/ChangeHistoryEntry.java`)
- `timestamp` (String): ISO 8601 formatted UTC timestamp
- `action` (String): Action type identifier (see Action Types below)
- `description` (String): Human-friendly description of the change

**UserMetadataModel** (`src/main/java/org/jlopezinc/model/UserMetadataModel.java`)
- `changeHistory` (List<ChangeHistoryEntry>): Array of change entries in chronological order
- `commentsHistory` (List<String>): Deprecated field maintained for backward compatibility

### Helper Method

The `EventV1Service.addChangeHistoryEntry()` method is the central point for recording all mutations:

```java
private void addChangeHistoryEntry(UserMetadataModel metadata, String action, String description)
```

This method:
1. Initializes the `changeHistory` array if null
2. Creates an ISO 8601 timestamp in UTC
3. Constructs a `ChangeHistoryEntry` object
4. Appends the entry to the array (maintaining chronological order)

## Action Types

### USER_REGISTERED
**When**: An existing user re-registers via webhook (POST /v1/{event}/webhook)  
**Description Format**: "User re-registered with updated information via webhook"  
**Implementation**: `EventV1Service.register()`

### USER_UPDATED
**When**: User data is modified via PUT endpoint (PUT /v1/{event}/{email})  
**Description Format**: "User data updated via PUT endpoint. Fields changed: {fields}"  
**Implementation**: `EventV1Service.updateUserMetadata()`  
**Note**: Tracks which fields were modified (people, phoneNumber, vehicle, paymentFile, comment, vehicleType, paid)

### PAYMENT_ADDED
**When**: Payment information is added or updated (POST /v1/{event}/{email}/payment)  
**Description Format**: "Payment confirmed: {amount} by {byWho}"  
**Implementation**: `EventV1Service.updatePaymentInfo()`

### CHECK_IN_ADDED
**When**: User is checked in (PUT /v1/{event}/{email}/checkin)  
**Description Format**: "User checked in by {who}"  
**Implementation**: `EventV1Service.checkInByEventAndEmail()`

### CHECK_IN_REMOVED
**When**: User's check-in is cancelled (DELETE /v1/{event}/{email})  
**Description Format**: "Check-in cancelled by {who}"  
**Implementation**: `EventV1Service.cancelCheckInByEventAndEmail()`

### COMMENT_UPDATED
**When**: User comment is modified (via webhook or PUT endpoint)  
**Description Format**: "Comment changed from \"{oldComment}\" to \"{newComment}\""  
**Implementation**: `EventV1Service.register()` and `EventV1Service.updateUserMetadata()`  
**Note**: Also updates the deprecated `commentsHistory` field for backward compatibility

## Implementation Guidelines

### When to Add Change History

Add a change history entry for any operation that:
1. Modifies user-level data (not just metadata)
2. Changes state that affects the user's registration status
3. Should be visible to administrators for audit purposes
4. Happens as part of a transactional update

### Transaction Consistency

Change history entries are added within the same code path as the mutation, ensuring they are part of the same logical transaction. The entry is added to the model object before calling `userModelTable.updateItem()` or `userModelTable.putItem()`.

### Description Format

Descriptions should:
- Be written in past tense
- Include relevant details (amounts, actor names, field names)
- Be human-readable without requiring technical knowledge
- Include context when useful (e.g., "via webhook", "via PUT endpoint")

### Timestamp Format

All timestamps use ISO 8601 format in UTC timezone:
```
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
Example: 2026-01-06T15:30:00.000Z
```

## Testing

See `ChangeHistoryTest.java` for comprehensive test coverage including:
- Entry structure validation
- Chronological ordering
- Action type coverage
- Multiple changes accumulation
- Backward compatibility with commentsHistory
- Extensibility patterns

## Future Enhancements

The schema is designed to be extensible. Potential additions:

### Actor Tracking
```java
private String actor; // "admin@example.com", "system", "webhook"
```

### Field-Level Details
```java
private List<String> modifiedFields; // ["phoneNumber", "vehicle"]
private Map<String, String> previousValues;
private Map<String, String> newValues;
```

### Categorization
```java
private String category; // "payment", "checkin", "profile", "admin"
```

### Request Metadata
```java
private String requestId;
private String ipAddress;
private String userAgent;
```

## Migration Notes

### From commentsHistory to changeHistory

The `commentsHistory` field is maintained for backward compatibility but should not be used in new code. When reading user data:

**Old approach** (deprecated):
```java
List<String> oldComments = metadata.getCommentsHistory();
```

**New approach** (recommended):
```java
List<ChangeHistoryEntry> history = metadata.getChangeHistory();
// Filter for comment updates if needed
List<ChangeHistoryEntry> commentChanges = history.stream()
    .filter(e -> "COMMENT_UPDATED".equals(e.getAction()))
    .collect(Collectors.toList());
```

### Database Compatibility

DynamoDB will automatically handle the new `changeHistory` field:
- Existing records without `changeHistory` will return null
- New records will include the field
- No migration is required for existing data
- The deprecated `commentsHistory` field continues to work for all records

## API Response Example

When fetching a user via GET /v1/{event}/{email}, the response includes:

```json
{
  "eventName": "ttamigosnatal2023",
  "userEmail": "user@example.com",
  "paid": true,
  "checkedIn": true,
  "vehicleType": "car",
  "metadata": {
    "phoneNumber": "916165469",
    "comment": "Ready for check-in",
    "commentsHistory": ["Pending payment", "Payment confirmed"],
    "changeHistory": [
      {
        "timestamp": "2026-01-06T10:00:00.000Z",
        "action": "USER_REGISTERED",
        "description": "User re-registered with updated information via webhook"
      },
      {
        "timestamp": "2026-01-06T11:30:00.000Z",
        "action": "PAYMENT_ADDED",
        "description": "Payment confirmed: 50.00 by admin@example.com"
      },
      {
        "timestamp": "2026-01-06T12:00:00.000Z",
        "action": "COMMENT_UPDATED",
        "description": "Comment changed from \"Pending payment\" to \"Payment confirmed\""
      },
      {
        "timestamp": "2026-01-06T12:30:00.000Z",
        "action": "USER_UPDATED",
        "description": "User data updated via PUT endpoint. Fields changed: comment"
      },
      {
        "timestamp": "2026-01-06T14:00:00.000Z",
        "action": "CHECK_IN_ADDED",
        "description": "User checked in by admin@example.com"
      }
    ]
  }
}
```
