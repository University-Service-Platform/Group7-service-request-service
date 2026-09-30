# service-request-service

USM-G7 Sprint 1 - owns the `ServiceRequest` entity end to end: submission,
triage, reject/escalate, and requester confirmation/close. Backend Dev 1's
half of Group 7's two-service split (see the group's *Sprint 1 Backend
Developer Guide*, particularly §§2-7, which this codebase was built from).

Its sibling service is `work-order-service` (Backend Dev 2's half), which
calls back into this service over REST - never a shared database, never a
cross-schema join.

## Tech stack

- Java 17, Spring Boot 3.2.5
- Spring Web, Spring Data JPA, Spring Security (stateless JWT filter)
- MySQL 8.0 (Flyway-versioned schema, no Hibernate auto-DDL)
- springdoc-openapi (Swagger UI)
- JUnit 5 + Mockito + AssertJ for tests

## Running locally

1. Start the local MySQL container (see the guide, §14 Phase 0 step E, for
   the full walkthrough):

   ```
   docker run --name g7-servicerequest-db -e MYSQL_ROOT_PASSWORD=devpassword \
     -e MYSQL_DATABASE=service_request_db -p 3306:3306 -d mysql:8.0
   ```

2. Run the service (Flyway migrates the schema automatically on startup):

   ```
   mvn spring-boot:run
   ```

   or from Antigravity/VS Code: run `ServiceRequestServiceApplication`.

3. Confirm it's up:

   ```
   curl http://localhost:8081/actuator/health
   ```

4. Swagger UI: http://localhost:8081/swagger-ui.html
   Raw OpenAPI JSON: http://localhost:8081/v3/api-docs

### Environment / config summary (for QA/DevOps's Dockerfile + Compose)

| Item | Value |
| --- | --- |
| Port | 8081 |
| Health path | `/actuator/health` |
| DB connection | `spring.datasource.*` in `application-dev.yml` (swap for env vars in the real Docker Compose) |
| Migrations | Flyway, runs automatically on boot from `src/main/resources/db/migration` |
| Active profile | `dev` (set `SPRING_PROFILES_ACTIVE=dev`, or a `prod`/`docker` profile once QA/DevOps defines one) |
| Build command | `mvn clean package` |
| Test command | `mvn test` (uses Mockito - no live DB required) |
| Run command | `java -jar target/service-request-service-0.1.0-SPRINT1.jar` |

### Minting a test JWT (dev profile only)

Group 5's real identity service isn't wired in yet (see "Placeholder-and-swap"
below). Until it is, `application-dev.yml` turns on a throwaway endpoint that
mints a placeholder token for any role you ask for:

```
curl -X POST http://localhost:8081/api/dev/token \
  -H "Content-Type: application/json" \
  -d '{"userId":"student-1","role":"STUDENT","department":"Faculty of Science"}'
```

Use the returned token as `Authorization: Bearer <token>` on any other
endpoint. Valid `role` values: `STUDENT`, `ACADEMIC_STAFF`, `ADMINISTRATIVE_STAFF`,
`SERVICE_DESK_OFFICER`, `TECHNICIAN`, `SERVICE`.

**This endpoint must never be enabled outside your own machine** - it lets
anyone claim any role. It only exists because `usm.dev-tools.enabled=true`
in `application-dev.yml`; leave that out of any real deployment profile.

### Manual testing against Group 6

When submitting a request with category `FACILITY` or `EQUIPMENT`, `FacilityValidationClient` validates the `location` field against Group 6's resource validation endpoint (`GET /api/resources/code/{code}/validate`). For manual testing via Swagger UI or `curl`, use the confirmed seed codes:

- **Confirmed valid / available resource codes**: `LAB-101`, `AUD-MAIN`, `STUDY-POD-01`, `BASKETBALL-COURT-1` (confirms resource exists and `validForReservation=true`).
- **Negative-path testing ("resource not found")**: `LAB-999` (triggers a "resource not found" response from Group 6).
- **Inactive / unavailable resources**: No confirmed inactive/unavailable code exists yet from Group 6, so that path can currently only be exercised with a mocked response in unit tests.

## API summary

See the live Swagger UI for full request/response schemas. Guide §4.1 has the
full story/FR/role mapping this table is drawn from.

| Method & path | Purpose | Who |
| --- | --- | --- |
| `POST /api/service-requests` | Submit a request | Student, Academic Staff, Admin Staff |
| `GET /api/service-requests?requesterId=&status=&category=` | List/search | Requester (own only), Service Desk (all) |
| `GET /api/service-requests/{id}` | Full detail | Owner or Service Desk |
| `PATCH /api/service-requests/{id}/triage` | Classify + prioritize + assign responsible unit | Service Desk Officer |
| `PATCH /api/service-requests/{id}/reject` | Reject with mandatory reason | Service Desk Officer |
| `PATCH /api/service-requests/{id}/escalate` | Escalate to a different unit | Service Desk Officer |
| `PATCH /api/service-requests/{id}/confirm` | Requester confirms/feedback, closes the request | Original requester only |
| `PATCH /api/service-requests/{id}/status` *(internal)* | work-order-service pushes Assigned/InProgress/Resolved | Service-to-service only - **exclude from the API gateway route table** |
| `GET /api/service-requests/summary?groupBy=` | Counts by status/category/priority/location/unit | Service Desk Officer, Admin Staff |

## Business rules enforced in code (guide §5)

- **BR-01/BR-02** - every protected endpoint requires a role via `@PreAuthorize`; ownership (not just role) is checked in `ServiceRequestServiceImpl` for view/confirm.
- **BR-03** - triage/reject/escalate restricted to `SERVICE_DESK_OFFICER`.
- **BR-05** - reject without `rejectionReason` is a 400 - checked twice (DTO `@NotBlank` *and* defensively in the service layer).
- **BR-06** - the internal `/status` endpoint refuses to mark a request `Assigned` unless it was already triaged (`Acknowledged`/`Escalated`).
- **BR-09** - every status change and its timestamp are set together, in the same method, in the same transaction (see `ServiceRequest`'s `acknowledge`/`markAssigned`/`markResolved`/`reject`/`escalate`/`confirmAndClose` methods) - never a nullable column filled in later.
- **BR-10** - a requester can only view/act on their own requests unless their role is `SERVICE_DESK_OFFICER` (or the internal `SERVICE` role).

## Placeholder-and-swap items (guide §7 / §13)

| Placeholder today | File(s) | Swap when / Status |
| --- | --- | --- |
| JWT signing key + claim names (`sub`/`role`/`department`) | `application.yml` (`services.identity.*`), `ExternalTokenValidator`, `IdentityJwksProvider`, `JwtAuthFilter`, `IdentityValidationClient` | **Done** — RS256/JWKS token verification and live user re-validation (`GET /api/v1/validation/users/{id}`) against Group 5's contract are built and tested. Group 5's real staging service is live at `https://university-identity-service.onrender.com` (API under `/api/v1`, JWKS at `/.well-known/jwks.json`, `iss=university-identity-service`, `aud=university-services-platform`), configurable via `IDENTITY_SERVICE_BASE_URL` (note: Render's free tier has cold starts, so callers should use 60–90s timeouts; `http://localhost:8001` remains the local-dev default); internal HS256 remains for dev and S2S calls |
| `location` as free text | `CreateServiceRequestRequest`, `ServiceRequest.location`, `FacilityValidationClient` | `FacilityValidationClient` is built and wired into `ServiceRequestServiceImpl`. Open: whether `location` should be constrained to match Group 6's resource-code format (e.g. `LAB-101`), since right now it's still free text and nothing enforces the format |
| Status enum names | `RequestStatus` | Tech Lead locks the final names - this is the only file that changes |
| Response envelope / error shape | `GlobalExceptionHandler`, `ApiError` | API Gateway team agrees a shared shape - wrap responses, don't rewrite logic |

### Service-to-service auth (this service <-> work-order-service)

`work-order-service` calls back into this service's internal `/status`
endpoint (and reads request detail) using a JWT signed with the **same**
placeholder secret, claiming `role=SERVICE`. This is a Sprint 1
simplification, not a real trust boundary - the honest limitation to raise
with your Tech Lead is that both services currently share one secret. Once
Group 5 or the API Gateway team defines real service-to-service auth
(mTLS, a gateway-issued service token, etc.), only `JwtProperties` /
`JwtTokenService` and the equivalent files in `work-order-service` change.

## Tests

```
mvn test
```

Unit tests in `ServiceRequestServiceImplTest` cover the BR-03/05/06/09/10
cases above (happy path + the specific invalid-input/unauthorized cases the
BA report's acceptance criteria call out) using Mockito - no live database
needed. `JwtTokenServiceTest` covers the JWT round trip.

Add tests for every new endpoint/rule as you build it (guide §15 Phase 3) -
don't batch them to the end.

## AI assistance disclosure

Initial project scaffolding, entity/DTO/repository/controller/service
skeletons, the JWT filter pattern, first-draft unit tests, and this README
were drafted with AI assistance from a prompt built on this project's own
Sprint 1 BA report and Backend Developer Guide, then reviewed, run, and
adapted here. Per the module's submission rules, update this section (and
your PR descriptions) with specifics as you extend the code yourself.

## What's still open (see guide §14)

- Confirm whether `location` should already reference a Group 6 facility ID and whether format constraints (e.g. matching `LAB-101`) should be enforced.
- Get the final status enum names signed off by your Tech Lead (specifically the unresolved `RequestStatus.CANCELLED` decision: who can cancel and from which statuses).
- Agree the API Gateway base path / response envelope.
- Directory Service dependency: Group 5 confirmed department and service-unit data now originates from a separate Directory Service (URL not yet published; client implementation pending contract/URL).
