# KPI Organization/Department Assignments API Documentation

## Overview
This document describes the GET API endpoints for retrieving KPI assignments assigned to an organization/department.

## Base URL
```
http://localhost:8085/kpi
```

## Endpoints

### 1. GET /kpi/assignments/{departmentId}
**Description**: Retrieve all KPI assignments for a specific department/organization (basic info only)

**Method**: GET  
**Authentication**: No  
**Response Code**: 200 (Success), 400 (Bad Request), 500 (Server Error)

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| departmentId | integer | Yes | The ID of the department/organization |

**Response Format (Success - 200)**:
```json
{
  "code": 200,
  "description": "Thành công",
  "department_id": 5,
  "assignments": [
    {
      "assignment_id": 1,
      "kpi_id": 4,
      "department_id": 5,
      "role": "A",
      "assigned_date": "2026-06-10T10:30:00.000+0000",
      "assigned_by": 10000000
    },
    {
      "assignment_id": 2,
      "kpi_id": 5,
      "department_id": 5,
      "role": "B",
      "assigned_date": "2026-06-09T14:15:30.000+0000",
      "assigned_by": 10000000
    }
  ]
}
```

**Response Format (Bad Request - 400)**:
```json
{
  "code": 400,
  "description": "Department ID is required and must be greater than 0"
}
```

**Response Format (Server Error - 500)**:
```json
{
  "code": 500,
  "description": "Server error: Connection timeout"
}
```

**Example cURL**:
```bash
curl -X GET http://localhost:8085/kpi/assignments/5
```

---

### 2. GET /kpi/assignments/{departmentId}/details
**Description**: Retrieve all KPI assignments for a department/organization with full KPI details (includes KPI code, name, category)

**Method**: GET  
**Authentication**: No  
**Response Code**: 200 (Success), 400 (Bad Request), 500 (Server Error)

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| departmentId | integer | Yes | The ID of the department/organization |

**Response Format (Success - 200)**:
```json
{
  "code": 200,
  "description": "Thành công",
  "department_id": 5,
  "assignments": [
    {
      "assignment_id": 1,
      "kpi_id": 4,
      "kpi_code": "T1.01",
      "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
      "category": "T – Đào tạo & Người học",
      "department_id": 5,
      "role": "A",
      "assigned_date": "2026-06-10T10:30:00.000+0000",
      "assigned_by": 10000000
    },
    {
      "assignment_id": 2,
      "kpi_id": 5,
      "kpi_code": "T1.02",
      "kpi_name": "Tỷ lệ duy trì sinh viên qua các năm học",
      "category": "T – Đào tạo & Người học",
      "department_id": 5,
      "role": "B",
      "assigned_date": "2026-06-09T14:15:30.000+0000",
      "assigned_by": 10000000
    }
  ]
}
```

**Example cURL**:
```bash
curl -X GET http://localhost:8085/kpi/assignments/5/details
```

---

## Response Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | Success | Request completed successfully |
| 400 | Bad Request | Invalid department ID (must be > 0) |
| 500 | Server Error | Database error or server issue |

## Assignment Object Fields

### Basic Assignment (from endpoint 1)
| Field | Type | Description |
|-------|------|-------------|
| assignment_id | integer | Unique ID of the assignment |
| kpi_id | integer | ID of the assigned KPI |
| department_id | integer | ID of the department/organization |
| role | string | Role designation (char, e.g., "A", "B") |
| assigned_date | timestamp | When the assignment was created (UTC) |
| assigned_by | integer | User ID who made the assignment |

### Assignment with Details (from endpoint 2)
**All fields from Basic Assignment plus:**
| Field | Type | Description |
|-------|------|-------------|
| kpi_code | string | KPI code (e.g., T1.01) |
| kpi_name | string | Full name/description of the KPI |
| category | string | KPI category |

## Usage Examples

### Example 1: Get basic assignments for department 5
```bash
curl -X GET http://localhost:8085/kpi/assignments/5
```

**Response**:
```json
{
  "code": 200,
  "description": "Thành công",
  "department_id": 5,
  "assignments": [
    {
      "assignment_id": 1,
      "kpi_id": 4,
      "department_id": 5,
      "role": "A",
      "assigned_date": "2026-06-10T10:30:00.000+0000",
      "assigned_by": 10000000
    }
  ]
}
```

### Example 2: Get assignments with KPI details
```bash
curl -X GET http://localhost:8085/kpi/assignments/5/details
```

### Example 3: JavaScript/Fetch
```javascript
async function getOrganizationKpis(departmentId) {
  try {
    const response = await fetch(`http://localhost:8085/kpi/assignments/${departmentId}`);
    const data = await response.json();
    
    if (data.code === 200) {
      console.log(`Found ${data.assignments.length} KPIs assigned to department ${departmentId}`);
      data.assignments.forEach(assignment => {
        console.log(`KPI ID: ${assignment.kpi_id}, Role: ${assignment.role}`);
      });
    } else {
      console.error('Error:', data.description);
    }
  } catch (error) {
    console.error('Network error:', error);
  }
}

// Usage
getOrganizationKpis(5);
```

### Example 4: JavaScript/Fetch with details
```javascript
async function getOrganizationKpisWithDetails(departmentId) {
  try {
    const response = await fetch(`http://localhost:8085/kpi/assignments/${departmentId}/details`);
    const data = await response.json();
    
    if (data.code === 200) {
      console.log('Detailed KPI Assignments:');
      data.assignments.forEach(assignment => {
        console.log({
          code: assignment.kpi_code,
          name: assignment.kpi_name,
          category: assignment.category,
          role: assignment.role
        });
      });
    }
  } catch (error) {
    console.error('Error:', error);
  }
}
```

### Example 5: Java/RestTemplate
```java
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8085/kpi/assignments/5";

try {
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
    JSONObject data = new JSONObject(response.getBody());
    
    if (data.getInt("code") == 200) {
        JSONArray assignments = data.getJSONArray("assignments");
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject assignment = assignments.getJSONObject(i);
            System.out.println("KPI ID: " + assignment.getInt("kpi_id"));
        }
    }
} catch (Exception e) {
    e.printStackTrace();
}
```

### Example 6: Python
```python
import requests

def get_organization_kpis(department_id):
    url = f"http://localhost:8085/kpi/assignments/{department_id}"
    
    try:
        response = requests.get(url)
        data = response.json()
        
        if data.get("code") == 200:
            print(f"Department {department_id} has {len(data['assignments'])} assigned KPIs")
            for assignment in data['assignments']:
                print(f"  KPI ID: {assignment['kpi_id']}, Role: {assignment['role']}")
        else:
            print(f"Error: {data.get('description')}")
    except Exception as e:
        print(f"Network error: {e}")

# Usage
get_organization_kpis(5)
```

### Example 7: Python with details
```python
def get_organization_kpis_with_details(department_id):
    url = f"http://localhost:8085/kpi/assignments/{department_id}/details"
    
    try:
        response = requests.get(url)
        data = response.json()
        
        if data.get("code") == 200:
            print(f"Detailed KPI Assignments for Department {department_id}:")
            for assignment in data['assignments']:
                print(f"  {assignment['kpi_code']}: {assignment['kpi_name']}")
                print(f"    Category: {assignment['category']}, Role: {assignment['role']}")
    except Exception as e:
        print(f"Error: {e}")
```

## Common Use Cases

### Use Case 1: Display assigned KPIs in a dashboard
1. Call `GET /kpi/assignments/{departmentId}/details` to get full KPI info
2. Display the KPI codes and names in a table/list
3. Show the assignment date and assigned role

### Use Case 2: Check if a KPI is assigned to a department
1. Call `GET /kpi/assignments/{departmentId}`
2. Search for the desired `kpi_id` in the assignments array
3. If found, the KPI is assigned; otherwise, it's not

### Use Case 3: List all roles/designations for a department's KPIs
1. Call `GET /kpi/assignments/{departmentId}`
2. Iterate through assignments and collect unique `role` values
3. Display available roles for that department

## Error Scenarios

### Scenario 1: Invalid department ID
**Request**:
```bash
curl -X GET http://localhost:8085/kpi/assignments/0
```

**Response**:
```json
{
  "code": 400,
  "description": "Department ID is required and must be greater than 0"
}
```

### Scenario 2: Department with no assignments
**Request**:
```bash
curl -X GET http://localhost:8085/kpi/assignments/999
```

**Response**:
```json
{
  "code": 200,
  "description": "Thành công",
  "department_id": 999,
  "assignments": []
}
```

### Scenario 3: Database connection error
**Response**:
```json
{
  "code": 500,
  "description": "Server error: Connection refused"
}
```

## API Endpoint Summary

| Method | Endpoint | Description | Purpose |
|--------|----------|-------------|---------|
| GET | `/kpi/assignments/{departmentId}` | Get basic assignments | Quick lookup of assigned KPI IDs |
| GET | `/kpi/assignments/{departmentId}/details` | Get assignments with details | Display full KPI information |

## Related Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/kpi/definitions` | Get all available KPI definitions |
| GET | `/kpi/definitions/{kpiId}` | Get specific KPI definition |
| GET | `/kpi/definitions/by-category` | Get KPIs filtered by category |
| POST | `/kpi/assign` | Create new KPI assignment |
| DELETE | `/kpi/{kpiId}` | Soft-delete a KPI |

## Notes

- **No Authentication Required**: These endpoints do not require session validation
- **Read-Only**: These are GET endpoints; no data is modified
- **Ordering**: Assignments are returned in descending order by `assignment_id` (newest first)
- **LEFT JOIN**: The `/details` endpoint uses a LEFT JOIN, so it returns assignments even if the KPI definition doesn't exist in the database
- **Department ID**: The "department_id" in the system represents an organization/department entity
- **Performance**: For large numbers of assignments, consider pagination (can be added if needed)

## Future Enhancements

Potential improvements:
- Add pagination support (limit, offset parameters)
- Add filtering by role
- Add date range filtering
- Add search by KPI code/name
- Add sorting options
