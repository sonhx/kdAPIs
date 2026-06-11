# KPI Assignment API Documentation

## Overview
This document describes the new POST API endpoint for assigning KPI data to departments.

## Endpoint

### POST /kpi/assign
Saves a KPI assignment to the `kpi_assignments` table in the database.

## Request Format

**Content-Type:** application/json

**Request Body:**
```json
{
  "session_id": "string",
  "kpi_id": integer,
  "department_id": integer,
  "role": "char(1)"
}
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| session_id | string | Yes | User session ID for authentication and authorization |
| kpi_id | integer | Yes | The ID of the KPI to assign (must be > 0) |
| department_id | integer | Yes | The ID of the department to assign to (must be > 0) |
| role | string | Yes | A single character representing the role (char(1)) |

## Response Format

**Success Response (Code 200):**
```json
{
  "code": 200,
  "description": "Thành công",
  "assignment_id": 123
}
```

**Validation Error (Code 400):**
```json
{
  "code": 400,
  "description": "Description of validation error"
}
```

**Authentication Error (Code 700):**
```json
{
  "code": 700,
  "description": "Chưa đăng nhập"
}
```

**JSON Error (Code 800):**
```json
{
  "code": 800,
  "description": "JSON error: Thiếu tham số?"
}
```

**Server Error (Code 500):**
```json
{
  "code": 500,
  "description": "Database error: [specific error message]"
}
```

## Response Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | Success | Assignment created successfully |
| 400 | Bad Request | Invalid input parameters |
| 500 | Server Error | Database error or other server issue |
| 700 | Unauthorized | User not logged in (invalid session) |
| 800 | JSON Error | Missing required JSON parameters |

## Database Table

The assignment is inserted into the `kpi_assignments` table with the following structure:

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| assignment_id | int | No | IDENTITY | Primary key, auto-generated |
| kpi_id | int | Yes | NULL | Foreign key to KPI |
| department_id | int | Yes | NULL | Department ID |
| role | char(1) | Yes | NULL | Role designation |
| assigned_date | datetime | Yes | GETDATE() | Assignment timestamp (auto-set to current date/time) |
| assigned_by | int | Yes | NULL | User ID of who made the assignment |

## Implementation Details

### Files Modified/Created

1. **KpiAssignment.java** (NEW)
   - Model/Entity class representing a KPI assignment
   - Contains all properties with getters and setters
   - Supports both parameterized and default constructors

2. **kpiExtend.java** (UPDATED)
   - Added `saveAssignment()` method
   - Validates all input parameters
   - Executes INSERT query against `kpi_assignments` table
   - Returns the generated assignment_id
   - Includes transaction support (@Transactional)

3. **kpiServices.java** (UPDATED)
   - Added REST controller annotations (@RestController, @RequestMapping)
   - Added `/assign` endpoint with @PostMapping
   - Handles session validation using SessionService
   - Extracts parameters from JSON request
   - Calls kpiExtend.saveAssignment() method
   - Returns appropriate response codes and messages

### Key Features

- **Session Validation**: All requests are validated against the session to ensure user authentication
- **Input Validation**: All parameters are validated before database insertion
- **Error Handling**: Comprehensive error handling with appropriate HTTP response codes
- **Transaction Support**: Database operation is wrapped in a transaction
- **User Tracking**: Automatically records which user made the assignment via `assigned_by`
- **Timestamp**: Assignment timestamp is automatically set to current database time (GETDATE())
- **JSON Response**: All responses follow the project's JSON response format

## Example Usage

### cURL Request
```bash
curl -X POST http://localhost:8085/kpi/assign \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "abc123xyz789",
    "kpi_id": 1,
    "department_id": 5,
    "role": "A"
  }'
```

### JavaScript/Fetch Request
```javascript
const response = await fetch('http://localhost:8085/kpi/assign', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    session_id: 'abc123xyz789',
    kpi_id: 1,
    department_id: 5,
    role: 'A'
  })
});

const data = await response.json();
console.log('Assignment ID:', data.assignment_id);
```

### Java Request
```java
// Using RestTemplate
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8085/kpi/assign";

Map<String, Object> request = new HashMap<>();
request.put("session_id", "abc123xyz789");
request.put("kpi_id", 1);
request.put("department_id", 5);
request.put("role", "A");

ResponseEntity<JSONObject> response = restTemplate.postForEntity(url, request, JSONObject.class);
JSONObject body = response.getBody();
int assignmentId = body.getInt("assignment_id");
```

## Error Scenarios

### Missing Session ID
**Request:**
```json
{
  "kpi_id": 1,
  "department_id": 5,
  "role": "A"
}
```
**Response:**
```json
{
  "code": 800,
  "description": "JSON error: Thiếu tham số?"
}
```

### Invalid Session
**Request:**
```json
{
  "session_id": "invalid_session",
  "kpi_id": 1,
  "department_id": 5,
  "role": "A"
}
```
**Response:**
```json
{
  "code": 700,
  "description": "Chưa đăng nhập"
}
```

### Missing KPI ID
**Request:**
```json
{
  "session_id": "valid_session",
  "department_id": 5,
  "role": "A"
}
```
**Response:**
```json
{
  "code": 400,
  "description": "KPI ID is required and must be greater than 0"
}
```

## API Endpoint Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /kpi/assign | Assign a KPI to a department |

## Notes

- The `assigned_date` is automatically set by the database to the current date and time using GETDATE()
- The `assigned_by` is automatically populated from the authenticated user's session (sst.UserID)
- All requests must include a valid `session_id`
- The database is configured for SQL Server at localhost:1433 with database name `kiemdinh`
- The application runs on port 8085 (configurable in application.properties)
