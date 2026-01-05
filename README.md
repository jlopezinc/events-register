# events-register
A simple REST api to query and check in users at events

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
