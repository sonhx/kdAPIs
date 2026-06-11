# KPI Delete API Documentation

## Overview
This document describes the DELETE API endpoint for soft-deleting (marking as removed) KPI definitions from the system.

## Endpoint

### DELETE /kpi/{kpiId}
Soft-deletes a KPI definition by marking it as deleted (sets `is_deleted = 1`) without actually removing it from the database.

## Request Format

**Method:** DELETE  
**Content-Type:** application/json (no body required)

**URL Format:**
```
DELETE http://localhost:8085/kpi/{kpiId}
```

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| kpiId | integer | Yes | The ID of the KPI to delete (must be > 0) |

## Response Format

### Success Response (Code 200)
```json
{
  "code": 200,
  "description": "Thành công",
  "kpi_id": 65
}
```

### Not Found Error (Code 404)
```json
{
  "code": 404,
  "description": "KPI not found or already deleted"
}
```

### Bad Request (Code 400)
```json
{
  "code": 400,
  "description": "KPI ID is required and must be greater than 0"
}
```

### Server Error (Code 500)
```json
{
  "code": 500,
  "description": "Database error: Connection timeout"
}
```

## Response Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | Success | KPI successfully marked as deleted |
| 400 | Bad Request | Invalid KPI ID (must be > 0) |
| 404 | Not Found | KPI not found or already deleted |
| 500 | Server Error | Database error |

## Soft-Delete Behavior

**Important:** This is a **soft-delete** operation, not a hard-delete:
- The KPI record is **NOT removed** from the database
- The `is_deleted` column is set to `1` to mark it as deleted
- Deleted KPIs are **excluded** from all GET queries by default
- The data remains in the database for auditing and historical purposes
- Deleted KPIs can be restored in the future if needed (by setting `is_deleted = 0`)

### Database Operation
```sql
UPDATE kpi_definitions SET is_deleted = 1 WHERE id = ?
```

## Example Requests

### Example 1: Delete a KPI
```bash
curl -X DELETE http://localhost:8085/kpi/65
```

**Success Response:**
```json
{
  "code": 200,
  "description": "Thành công",
  "kpi_id": 65
}
```

### Example 2: Delete Non-Existent KPI
```bash
curl -X DELETE http://localhost:8085/kpi/9999
```

**Error Response:**
```json
{
  "code": 404,
  "description": "KPI not found or already deleted"
}
```

### Example 3: Delete Already Deleted KPI
```bash
curl -X DELETE http://localhost:8085/kpi/65
```
*After second deletion:*
```json
{
  "code": 404,
  "description": "KPI not found or already deleted"
}
```

## JavaScript/Fetch Example

```javascript
async function deleteKpi(kpiId) {
  try {
    const response = await fetch(`http://localhost:8085/kpi/${kpiId}`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const data = await response.json();

    if (data.code === 200) {
      console.log('KPI deleted successfully!');
      console.log('Deleted KPI ID:', data.kpi_id);
      return data;
    } else if (data.code === 404) {
      console.error('Error: KPI not found or already deleted');
    } else if (data.code === 400) {
      console.error('Error: Invalid KPI ID');
    } else {
      console.error('Server error:', data.description);
    }
  } catch (error) {
    console.error('Network error:', error);
  }
}

// Usage
deleteKpi(65);
```

## Java/RestTemplate Example

```java
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8085/kpi/65";

try {
    ResponseEntity<String> response = restTemplate.exchange(
        url, 
        HttpMethod.DELETE, 
        HttpEntity.EMPTY, 
        String.class
    );
    
    JSONObject result = new JSONObject(response.getBody());
    
    if (result.getInt("code") == 200) {
        System.out.println("KPI deleted successfully! ID: " + result.getInt("kpi_id"));
    } else if (result.getInt("code") == 404) {
        System.out.println("Error: KPI not found or already deleted");
    } else if (result.getInt("code") == 400) {
        System.out.println("Error: Invalid KPI ID");
    }
} catch (Exception e) {
    e.printStackTrace();
}
```

## Python Example

```python
import requests
import json

def delete_kpi(kpi_id):
    url = f"http://localhost:8085/kpi/{kpi_id}"
    
    try:
        response = requests.delete(url, headers={"Content-Type": "application/json"})
        data = response.json()
        
        if data.get("code") == 200:
            print(f"KPI deleted successfully! ID: {data.get('kpi_id')}")
            return data
        elif data.get("code") == 404:
            print("Error: KPI not found or already deleted")
        elif data.get("code") == 400:
            print("Error: Invalid KPI ID")
        else:
            print(f"Server error: {data.get('description')}")
    except Exception as e:
        print(f"Network error: {e}")

# Usage
delete_kpi(65)
```

## Important Notes

### Soft-Delete vs Hard-Delete
- **Soft-Delete (This Implementation):**
  - ✅ Preserves data for auditing
  - ✅ Allows easy restoration
  - ✅ Maintains referential integrity
  - ✅ Tracks deletion history
  - ❌ Does not free database space
  - ❌ Requires explicit filtering in queries

### Impact on Other Endpoints
When a KPI is deleted:
- ❌ Will NOT appear in `GET /kpi/definitions`
- ❌ Will NOT appear in `GET /kpi/definitions/{kpiId}` (returns 404)
- ❌ Will NOT appear in `GET /kpi/definitions/by-category`
- ✅ Existing assignments in `kpi_assignments` table remain intact
- ✅ Can still be retrieved for admin/audit purposes (if separate endpoint added)

### Validation Rules
1. KPI ID must be provided and must be greater than 0
2. KPI must exist and not already be deleted
3. Soft-delete sets `is_deleted = 1`
4. Further deletion attempts on the same KPI return 404

### Transaction Support
- Delete operation is wrapped in `@Transactional` for data consistency
- If the delete fails midway, the transaction is rolled back

## Reverting a Deleted KPI

To restore a deleted KPI (manual database operation):
```sql
UPDATE kpi_definitions SET is_deleted = 0 WHERE id = 65;
```

## Related Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/kpi/definitions` | Get all active KPI definitions |
| GET | `/kpi/definitions/{kpiId}` | Get specific active KPI |
| GET | `/kpi/definitions/by-category` | Get active KPIs by category |
| POST | `/kpi/add` | Add new KPI definition |
| **DELETE** | **/kpi/{kpiId}** | **Delete (soft) KPI definition (THIS ENDPOINT)** |
| POST | `/kpi/assign` | Assign KPI to department |

## API Summary

- **Endpoint:** DELETE /kpi/{kpiId}
- **Authentication:** No
- **Method:** HTTP DELETE
- **Path Variable:** kpiId (Integer, required)
- **Request Body:** None
- **Response Type:** JSON
- **Success Code:** 200
- **Error Codes:** 400, 404, 500

## Migration Notes for Existing Systems

If adding this feature to existing KPI systems:
1. Add `is_deleted` column to `kpi_definitions` table with default value 0
2. Update all existing GET queries to filter out deleted KPIs
3. No existing data is affected (all KPIs default to `is_deleted = 0`)
4. Soft-delete provides backward compatibility with existing data

### SQL Migration
```sql
-- Add is_deleted column if it doesn't exist
ALTER TABLE kpi_definitions
ADD is_deleted INT DEFAULT 0;

-- Ensure all existing records are marked as not deleted
UPDATE kpi_definitions SET is_deleted = 0 WHERE is_deleted IS NULL;
```

## Common Error Scenarios

### Scenario 1: Invalid KPI ID
**Request:**
```bash
curl -X DELETE http://localhost:8085/kpi/-5
```
**Response:**
```json
{
  "code": 400,
  "description": "KPI ID is required and must be greater than 0"
}
```

### Scenario 2: KPI Not Found
**Request:**
```bash
curl -X DELETE http://localhost:8085/kpi/99999
```
**Response:**
```json
{
  "code": 404,
  "description": "KPI not found or already deleted"
}
```

### Scenario 3: Double Delete
**First Delete:**
```bash
curl -X DELETE http://localhost:8085/kpi/65
```
**Success Response:**
```json
{
  "code": 200,
  "description": "Thành công",
  "kpi_id": 65
}
```

**Second Delete (same KPI):**
```bash
curl -X DELETE http://localhost:8085/kpi/65
```
**Error Response (already deleted):**
```json
{
  "code": 404,
  "description": "KPI not found or already deleted"
}
```
