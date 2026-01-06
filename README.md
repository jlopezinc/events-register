# events-register
A simple REST api to query and check in users at events

## Documentation

- [README.md](README.md) - Main documentation with API usage examples
- [CHANGE_HISTORY.md](CHANGE_HISTORY.md) - Detailed implementation guide for the unified change history audit trail

## to run locally
Use java 17 (graal)

Example:
```sdk use java 17.0.8-graal```

# running pointing to local dynamo
```
mvn quarkus:dev -Dquarkus.dynamodb.aws.credentials.type=static -Dquarkus.dynamodb.aws.credentials.static-provider.access-key-id=b5e1u -Dquarkus.dynamodb.aws.credentials.static-provider.secret-access-key=pzral -Dquarkus.dynamodb.endpoint-override=http://localhost:8000
```

# running locally with SAM
```shell
sam local start-api --template target/sam.jvm.yaml
```

# Test curls
This section has examples for this service endpoints.

If you are using the Quarkus dev mode, use http://localhost:8080. For sam local, use http://localhost:3000 

## Webhook to register a new user
```shell
curl -X POST 'http://localhost:8080/v1/ttamigosnatal2023/webhook' \
-H 'x-api-key:7KVjU7bQmy' \
-H 'content-type:application/json' \
-d '{
    "created_at": "1700255760634",
    "formId": "176ApWzf50S01l4ROdEfIFfGpJ-EiBmCns5jgz3IqhGY",
    "formName": "Registo XI Encontro TT Amigos do Natal",
    "formUrl": "https://docs.google.com/forms/d/e/1FAIpQLSeiUW1SBf4afmODFtqMzA0oeQLZVLO7FLC5zf6u6J9ULktTYA/viewform",
    "submittedAt": "1700255754572",
    "driverName": "Test driver",
    "driverCc": "12808562",
    "address": "3230 - 269 Penela",
    "phoneNumber": "916165469",
    "vehicleType": "Jipe",
    "vehiclePlate": "22-FX-53",
    "vehicleBrand": "Land Rover Discovery",
    "guestsNumber": "2",
    "guestsNames": "Teste ocupante",
    "guestsCc": "111122111",
    "accept": "Sim",
    "payment": "<a href='https://drive.google.com/open?id=1yc37Z6FqIQPmUAUSf_XZ5OvYalmVIqHl'>File 1<plain>https://drive.google.com/open?id=1yc37Z6FqIQPmUAUSf_XZ5OvYalmVIqHl</plain></a>",
    "email": "test@gmail.com",
    "comment": "any comment about the registration, optional"
}'
```

**Comment Field Behavior (B2B webhook):**
- When updating an existing user (same email), if the `comment` field has a different value than before, the previous comment is automatically moved to `commentsHistory` before updating with the new value.
- The `commentsHistory` field in the metadata accumulates all past values of the comment field (deprecated - use `changeHistory` instead).
- If there is no prior comment or if the comment was not set, it simply updates as usual without adding to history.
- **NEW**: All user-level mutations are now tracked in the unified `changeHistory` array (see Change History section below).

## Get event counters
```shell
curl -X GET 'http://localhost:8080/v1/ttamigosnatal2023/counters' \
-H 'x-api-key:7KVjU7bQmy'
```

## Admin: Reconcile event counters
This endpoint recalculates all counters for an event by traversing all users in DynamoDB and counting the actual registrations, check-ins, and payments. It returns the before/after counter values. Use this endpoint when counters may have gone out of sync due to errors or bugs.

```shell
curl -X POST 'http://localhost:8080/v1/admin/reconcile-counters/ttamigosnatal2023' \
-H 'x-api-key:7KVjU7bQmy'
```

Response includes:
- `status`: "success" if reconciliation completed
- `eventId`: The event that was reconciled
- `before`: Counter values before reconciliation
- `after`: Counter values after reconciliation  
- `message`: Summary of what was done

## Send a specific email for a registered user
```shell
curl -X POST 'http://localhost:8080/v1/ttamigosnatal2023/jlopez.inc@gmail.com/sendEmail/almostThere' -H 'x-api-key:7KVjU7bQmy'

```

## Update user data
Updates user data including personal details, vehicle information, guests, payment, and comments. This endpoint is intended for frontend applications. All fields except email and event name can be updated.

```shell
curl -X PUT 'http://localhost:8080/v1/ttamigosnatal2023/test@example.com' \
-H 'x-api-key:7KVjU7bQmy' \
-H 'content-type:application/json' \
-d '{
    "driverName": "Updated Driver Name",
    "driverCc": "12345678",
    "phoneNumber": "912345678",
    "vehicleType": "Jipe",
    "vehiclePlate": "AB-12-34",
    "vehicleBrand": "Toyota Land Cruiser",
    "guestsNumber": 2,
    "guestsNames": "Guest One<BR/>Guest Two",
    "guestsCc": "11111111<BR/>22222222",
    "payment": "payment_proof_url",
    "comment": "Payment confirmed - ready for check-in",
    "paid": true
}'
```

**Updatable Fields**:
- `driverName`: Driver's full name
- `driverCc`: Driver's ID/CC number
- `phoneNumber`: Contact phone number
- `vehicleType`: Type of vehicle (Jipe/car, Mota/motorcycle, Quad)
- `vehiclePlate`: Vehicle license plate
- `vehicleBrand`: Vehicle make/brand
- `guestsNumber`: Number of guests
- `guestsNames`: Guest names separated by `<BR/>`, newline, or comma
- `guestsCc`: Guest ID numbers separated by `<BR/>`, newline, or comma
- `payment`: Payment proof URL or reference
- `comment`: Admin comment/notes
- `paid`: Payment status (true/false)

**Note**: All fields are optional. Only provide the fields you want to update. The email and event name cannot be changed.

**Response**: Returns the updated user object with full metadata.

**Comment History Behavior**:
- When the comment is changed, the previous value is automatically moved to `commentsHistory` (deprecated - see Change History below)
- If there is no previous comment or it's empty, no entry is added to the history
- The `commentsHistory` field accumulates all past values of the comment field (deprecated)
- Frontend applications can retrieve the `commentsHistory` array to display comment history to admin users
- **NEW**: Use the unified `changeHistory` array for a complete audit trail of all user mutations (see Change History section below)

# Change History Audit Trail

The application now implements a unified `changeHistory` audit trail that tracks all modifications to a user's registration data. This provides a comprehensive timeline of all user-level mutations.

## Structure

Each entry in the `changeHistory` array is an object with the following fields:

- **timestamp**: ISO 8601 formatted string (e.g., `"2026-01-06T15:30:00.000Z"`)
- **action**: Type of action performed (see Action Types below)
- **description**: Human-friendly summary of what changed

## Action Types

The following action types are currently tracked:

- `USER_REGISTERED`: User was re-registered (webhook called with existing user email)
- `USER_UPDATED`: User data was updated via PUT endpoint
- `PAYMENT_ADDED`: Payment information was added or updated
- `CHECK_IN_ADDED`: User was checked in at the event
- `CHECK_IN_REMOVED`: User's check-in was cancelled
- `COMMENT_UPDATED`: User comment was changed (backward compatibility with commentsHistory)

## Order

Change history entries are stored in **chronological order (oldest first)**. This allows administrators to track the complete lifecycle of a user's registration by reading the array from beginning to end.

## Examples

**Example 1: User Lifecycle**
```json
{
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
      "description": "Comment changed from \"Pending payment\" to \"Ready for check-in\""
    },
    {
      "timestamp": "2026-01-06T14:00:00.000Z",
      "action": "CHECK_IN_ADDED",
      "description": "User checked in by admin@example.com"
    }
  ]
}
```

**Example 2: User Update**
```json
{
  "changeHistory": [
    {
      "timestamp": "2026-01-06T13:00:00.000Z",
      "action": "USER_UPDATED",
      "description": "User data updated via PUT endpoint. Fields changed: phoneNumber, vehicle, people"
    }
  ]
}
```

## Extensibility

The `changeHistory` structure is designed to be extensible for future enhancements such as:
- `modifiedFields`: Array of specific field names that were changed
- `actor`: Who performed the action (user ID, admin email, system, etc.)
- `previousValue` / `newValue`: For detailed field-level tracking
- Additional metadata as needed

## Backward Compatibility

The legacy `commentsHistory` field (string array) is maintained for backward compatibility but is deprecated. New code should use `changeHistory` for a complete audit trail. The `commentsHistory` field will continue to be populated when comments are changed.

## Transactional Consistency

Change history entries are added during the same database transaction as the user mutation, ensuring consistency between the user's current state and their audit trail.

# building and deploying (native)

## build native
```shell
mvn install -Dnative -DskipTests -Dquarkus.native.container-build=true
```

## Testing locally
```shell
sam local start-api --template target/sam.native.yaml
```

## Deploying to AWS
Choose your profile (`aws configure sso`), if you need to, and run:
```shell
sam deploy -t sam.native.yaml --profile <your_profile>
```
Use `-g` for a guided deploy (like if you are doing it the first time).
