# Loan Service — Development Guide

## Overview

A Spring Boot REST API for loan application processing. The service handles user registration/authentication, loan eligibility evaluation, risk-based interest rate calculation, and offer generation.

---

## Tech Stack

| Layer         | Technology                          |
|---------------|-------------------------------------|
| Language      | Java 17+                            |
| Framework     | Spring Boot 3.x                     |
| Security      | Spring Security + JWT (jjwt)        |
| Database      | PostgreSQL                          |
| ORM           | Spring Data JPA / Hibernate         |
| Build Tool    | Gradle (Kotlin DSL)                 |
| Testing       | JUnit 5, Mockito, MockMvc           |
| Documentation | Swagger / SpringDoc OpenAPI         |

---

## Project Structure

```
loan-service/
├── src/main/java/com/loan/service/
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── LoanApplicationController.java
│   │   └── UserController.java
│   ├── domain/
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── LoanApplication.java
│   │   │   └── LoanOffer.java
│   │   └── enums/
│   │       ├── ApplicationStatus.java
│   │       ├── EmploymentType.java
│   │       ├── LoanPurpose.java
│   │       └── RiskBand.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   └── LoanRequest.java
│   │   └── response/
│   │       ├── AuthResponse.java
│   │       ├── LoanApplicationResponse.java
│   │       ├── UserResponse.java
│   │       └── ErrorResponse.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── BadRequestException.java
│   │   ├── ResourceNotFoundException.java
│   │   └── UnauthorizedException.java
│   ├── mapper/
│   │   └── ObjectConverter.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── LoanApplicationRepository.java
│   │   └── LoanOfferRepository.java
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── JwtTokenProvider.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── LoanApplicationService.java
│   │   ├── UserService.java
│   │   └── impl/
│   │       ├── AuthServiceImpl.java
│   │       ├── LoanApplicationServiceImpl.java
│   │       └── UserServiceImpl.java
│   └── util/
│       └── EmiCalculator.java
├── src/main/resources/
│   └── application.yml
├── src/test/java/com/loan/service/
│   ├── controller/
│   │   ├── AuthControllerTest.java
│   │   ├── LoanApplicationControllerTest.java
│   │   └── UserControllerTest.java
│   ├── mapper/
│   │   └── ObjectConverterTest.java
│   ├── security/
│   │   ├── JwtAuthenticationFilterTest.java
│   │   └── JwtTokenProviderTest.java
│   └── serviceImpl/
│       └── LoanApplicationServiceImplTest.java
└── build.gradle
```

---

## Prerequisites

- **Java 17+** (OpenJDK recommended)
- **PostgreSQL 14+** running locally on port `5432`
- **Gradle 8.x** (wrapper included)

---

## Database Setup

```sql
CREATE DATABASE loan_db;
CREATE USER admin WITH PASSWORD 'role';
GRANT ALL PRIVILEGES ON DATABASE loan_db TO admin;
```

---

## Configuration

### `application.yml`

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/loan_db
    username: admin
    password: role

  jpa:
    hibernate:
      ddl-auto: validate

  jackson:
    default-property-inclusion: non_null

jwt:
  secret: ThisIsASecretKeyForJwtTokenThatIsAtLeast32Characters
  expiration: 86400000   # 24 hours in milliseconds
```

### Environment Variables (Production)

| Variable        | Description              | Default |
|-----------------|--------------------------|---------|
| `DB_URL`        | PostgreSQL JDBC URL      | —       |
| `DB_USERNAME`   | Database username        | —       |
| `DB_PASSWORD`   | Database password        | —       |
| `JWT_SECRET`    | JWT signing key (≥32 chars) | —    |
| `JWT_EXPIRATION`| Token expiry in ms       | 86400000|

---

## Build & Run

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun

# Run skipping tests
./gradlew bootRun -x test
```

Application starts at: `http://localhost:8080`

---

## API Endpoints

### Authentication (Public)

| Method | Endpoint              | Description          |
|--------|-----------------------|----------------------|
| POST   | `/api/auth/register`  | Register new user    |
| POST   | `/api/auth/login`     | Login & get JWT      |

### Loan Applications (Authenticated — Bearer Token Required)

| Method | Endpoint         | Description                 |
|--------|------------------|-----------------------------|
| POST   | `/applications`  | Submit loan application     |
| GET    | `/applications`  | Get user's loan history     |

### User Profile (Authenticated)

| Method | Endpoint          | Description          |
|--------|-------------------|----------------------|
| GET    | `/api/users/me`   | Get current user     |

### Swagger UI

- Swagger: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## API Request / Response Examples

### Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Ashutosh Kumar",
    "email": "ashutosh@example.com",
    "password": "secret123",
    "employmentType": "SALARIED",
    "monthlyIncome": 75000,
    "creditScore": 750,
    "dateOfBirth": "1995-06-15"
  }'
```

**Response** `200 OK`:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "ashutosh@example.com",
    "password": "secret123"
  }'
```

### Apply for Loan

```bash
curl -X POST http://localhost:8080/applications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt-token>" \
  -d '{
    "applicant": {
      "name": "Ashutosh Kumar",
      "age": 30,
      "monthlyIncome": 75000,
      "employmentType": "SALARIED",
      "creditScore": 750
    },
    "loan": {
      "amount": 500000,
      "tenureMonths": 36,
      "purpose": "PERSONAL"
    }
  }'
```

**Approved Response** `201 Created`:
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "APPROVED",
  "riskBand": "LOW",
  "offer": {
    "interestRate": 12,
    "tenureMonths": 36,
    "emi": 16607.15,
    "totalPayable": 597857.40
  }
}
```

**Rejected Response** `201 Created`:
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "REJECTED",
  "riskBand": null,
  "rejectionReasons": [
    "LOW_CREDIT_SCORE",
    "AGE_TENURE_LIMIT_EXCEEDED"
  ]
}
```

---

## Business Rules

### Section 3 — Eligibility (Hard Reject)

| Rule                        | Condition                             | Rejection Reason            |
|-----------------------------|---------------------------------------|-----------------------------|
| Low Credit Score            | `creditScore < 600`                   | `LOW_CREDIT_SCORE`          |
| Age + Tenure Exceeds Limit  | `age + (tenureMonths / 12) > 65`      | `AGE_TENURE_LIMIT_EXCEEDED` |
| EMI Exceeds 60% of Income   | `EMI > 60% × monthlyIncome`          | `EMI_EXCEEDS_60_PERCENT`    |

All applicable reasons are returned together.

### Section 4 — Risk Band Classification

| Credit Score Range | Risk Band |
|--------------------|-----------|
| 750+               | LOW       |
| 650–749            | MEDIUM    |
| 600–649            | HIGH      |

### Section 5 — Interest Rate Calculation

```
Final Rate = Base Rate (12%) + Risk Premium + Employment Premium + Loan Size Premium
```

| Component           | Condition           | Premium |
|---------------------|---------------------|---------|
| Risk — LOW          | Score ≥ 750         | 0%      |
| Risk — MEDIUM       | Score 650–749       | 1.5%    |
| Risk — HIGH         | Score 600–649       | 3%      |
| Employment          | SELF_EMPLOYED       | 1%      |
| Employment          | SALARIED            | 0%      |
| Loan Size           | Amount > ₹10,00,000 | 0.5%   |

### Section 6 — Offer Generation

After eligibility passes, EMI is recalculated with the final interest rate. If `EMI > 50% × monthlyIncome`, the application is rejected with reason `EMI_EXCEEDS_50_PERCENT`.

---

## Validation Rules

### Register Request

| Field          | Rule                        |
|----------------|-----------------------------|
| fullName       | Not blank                   |
| email          | Not blank, valid format     |
| password       | Not blank, min 6 chars      |
| employmentType | `SALARIED`, `SELF_EMPLOYED` |
| monthlyIncome  | Not null, > 0               |
| creditScore    | 300–900                     |
| dateOfBirth    | Not null                    |

### Loan Request

| Field          | Rule                                   |
|----------------|----------------------------------------|
| age            | 21–60                                  |
| creditScore    | 300–900                                |
| loanAmount     | ₹10,000 – ₹50,00,000                  |
| tenureMonths   | 6–360                                  |
| employmentType | `SALARIED`, `SELF_EMPLOYED`            |
| loanPurpose    | `PERSONAL`, `HOME`, `AUTO`             |

---

## Error Responses

All errors follow a consistent format:

```json
{
  "message": "Validation failed",
  "status": 400,
  "timestamp": "2026-04-03T11:30:00",
  "errors": [
    "email: Invalid email format",
    "password: Password must be at least 6 characters"
  ]
}
```

| Status | Scenario                                   |
|--------|--------------------------------------------|
| 400    | Validation failure, invalid request body   |
| 401    | Missing or invalid JWT token               |
| 403    | Authenticated but not authorized           |
| 404    | User or resource not found                 |
| 500    | Unexpected server error                    |

---

## Security

- **Authentication**: JWT Bearer tokens (HMAC-SHA256)
- **Token Expiry**: 24 hours (configurable)
- **Password Storage**: BCrypt hashing
- **Session**: Stateless (no server-side sessions)
- **CSRF**: Disabled (stateless REST API)
- **Public Endpoints**: `/api/auth/**`, Swagger, Actuator health

---

## Testing

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "*.LoanApplicationServiceImplTest"

# Run with verbose output
./gradlew test --info

# Generate HTML test report
# → build/reports/tests/test/index.html
```

### Test Structure

| Test Class                        | Type       | What It Tests                          |
|-----------------------------------|------------|----------------------------------------|
| `AuthControllerTest`              | WebMvcTest | Auth endpoints, validation, error codes|
| `LoanApplicationControllerTest`   | WebMvcTest | Loan endpoints, validation, responses  |
| `UserControllerTest`              | WebMvcTest | User profile endpoint                  |
| `LoanApplicationServiceImplTest`  | Unit       | Eligibility, risk, interest, offers    |
| `ObjectConverterTest`             | Unit       | Entity → Response mapping              |
| `JwtTokenProviderTest`            | Unit       | Token generation & extraction          |
| `JwtAuthenticationFilterTest`     | Unit       | Filter chain behavior                  |

### Testing Notes

- Controller tests use `@WebMvcTest` with `@MockitoBean` for service dependencies
- Unit tests use `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`
- Security filters are disabled in controller tests via `@AutoConfigureMockMvc(addFilters = false)`
- `JwtTokenProviderTest` uses `ReflectionTestUtils` to inject `@Value` fields manually


## Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| 403 on POST requests | Missing/invalid JWT token | Ensure `Authorization: Bearer <token>` header |
| 500 on invalid enum | No `HttpMessageNotReadableException` handler | Add handler in `GlobalExceptionHandler` |
| Validation not triggered | Missing `@Valid` on controller `@RequestBody` | Add `@Valid` annotation |
| `NoSuchBeanDefinitionException` in tests | Using `@Mock` instead of `@MockitoBean` in `@WebMvcTest` | Use `@MockitoBean` for Spring context tests |
| `UnnecessaryStubbingException` | Stubbing a method that's never called | Remove unused `when()` stubs |
| `NullPointerException` in unit tests | Using `@MockitoBean` in non-Spring tests | Use `@Mock` with `@InjectMocks` instead |