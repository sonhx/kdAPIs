# KPI Definitions API Documentation

## Overview
This document describes the new GET API endpoints for retrieving KPI definitions from the database.

## Base URL
```
http://localhost:8085/kpi
```

## Endpoints

### 1. GET /kpi/definitions
**Description**: Retrieve all KPI definitions

**Method**: GET  
**Authentication**: No  
**Response Code**: 200

**Response Format (Success)**:
```json
{
  "code": 200,
  "description": "Thành công",
  "definitions": [
    {
      "kpi_id": 4,
      "kpi_code": "T1.01",
      "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
      "category": "T – Đào tạo & Người học (Training)",
      "unit": "%",
      "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
      "data_source": "P. Đào tạo",
      "frequency": "Năm học"
    },
    {
      "kpi_id": 5,
      "kpi_code": "T1.02",
      "kpi_name": "Tỷ lệ duy trì sinh viên qua các năm học",
      "category": "T – Đào tạo & Người học (Training)",
      "unit": "%",
      "formula": "Số SV tiếp tục học/ tổng SV đầu năm × 100%",
      "data_source": "SIS",
      "frequency": "Năm học"
    }
  ]
}
```

**Example cURL**:
```bash
curl -X GET http://localhost:8085/kpi/definitions
```

---

### 2. GET /kpi/definitions/{kpiId}
**Description**: Retrieve a specific KPI definition by ID

**Method**: GET  
**Authentication**: No  
**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| kpiId | integer | Yes | The ID of the KPI |

**Response Format (Success)**:
```json
{
  "code": 200,
  "description": "Thành công",
  "kpi_id": 4,
  "kpi_code": "T1.01",
  "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
  "category": "T – Đào tạo & Người học (Training)",
  "unit": "%",
  "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
  "data_source": "P. Đào tạo",
  "frequency": "Năm học"
}
```

**Response Format (Not Found)**:
```json
{
  "code": 404,
  "description": "KPI not found"
}
```

**Example cURL**:
```bash
curl -X GET http://localhost:8085/kpi/definitions/4
```

---

### 3. GET /kpi/definitions/by-category
**Description**: Retrieve KPI definitions filtered by category

**Method**: GET  
**Authentication**: No  
**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| category | string | Yes | The category to filter by (e.g., "T – Đào tạo & Người học") |

**Response Format (Success)**:
```json
{
  "code": 200,
  "description": "Thành công",
  "category": "T – Đào tạo & Người học (Training)",
  "definitions": [
    {
      "kpi_id": 4,
      "kpi_code": "T1.01",
      "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
      "category": "T – Đào tạo & Người học (Training)",
      "unit": "%",
      "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
      "data_source": "P. Đào tạo",
      "frequency": "Năm học"
    },
    {
      "kpi_id": 5,
      "kpi_code": "T1.02",
      "kpi_name": "Tỷ lệ duy trì sinh viên qua các năm học",
      "category": "T – Đào tạo & Người học (Training)",
      "unit": "%",
      "formula": "Số SV tiếp tục học/ tổng SV đầu năm × 100%",
      "data_source": "SIS",
      "frequency": "Năm học"
    }
  ]
}
```

**Example cURL**:
```bash
curl -X GET "http://localhost:8085/kpi/definitions/by-category?category=T%20–%20Đào%20tạo%20&%20Người%20học%20(Training)"
```

---

## Response Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | Success | Request completed successfully |
| 404 | Not Found | KPI definition not found (only for single KPI endpoint) |
| 500 | Server Error | Database error or other server issue |

## Available KPI Categories

| Category Code | Category Name |
|---------------|--------------|
| T | T – Đào tạo & Người học (Training) |
| G | G – Giảng viên & Nhân lực học thuật (Lecturer) |
| N | N – Nghiên cứu & Chuyển giao (Research) |
| H | H – Hỗ trợ người học (Student Support) |
| C | C – Cải tiến liên tục & CAPA (Continuous Improvement) |
| K | K – Kiểm định & Đối sánh (Accreditation) |
| Q | Q – Quản trị & Nguồn lực (Governance) |
| D | D – Chuyển đổi số & Dữ liệu (Digital Quality) |

## KPI Definition Fields

| Field | Type | Description |
|-------|------|-------------|
| kpi_id | integer | Unique identifier for the KPI |
| kpi_code | string | Code identifier (e.g., T1.01, G2.05) |
| kpi_name | string | Description/name of the KPI |
| category | string | Category classification |
| unit | string | Unit of measurement (%, Điểm, Số lượng, Tỷ lệ, Bài, etc.) |
| formula | string | Calculation formula |
| data_source | string | Source system or department providing the data |
| frequency | string | Reporting frequency (Năm học, Học kỳ, Quý, Hàng năm) |

## Database Table

```sql
-- Assumed table structure for kpi_definitions
CREATE TABLE kpi_definitions (
    id INT PRIMARY KEY IDENTITY(1,1),
    code VARCHAR(10) NOT NULL,
    name NVARCHAR(MAX) NOT NULL,
    category NVARCHAR(100),
    unit VARCHAR(50),
    formula NVARCHAR(MAX),
    data_source NVARCHAR(100),
    frequency NVARCHAR(50)
);
```

## Error Handling

**Example Error Response**:
```json
{
  "code": 500,
  "description": "Server error: Connection timeout"
}
```

## Example Usage

### JavaScript/Fetch - Get All KPI Definitions
```javascript
async function getAllKpis() {
  try {
    const response = await fetch('http://localhost:8085/kpi/definitions');
    const data = await response.json();
    
    if (data.code === 200) {
      console.log('All KPIs:', data.definitions);
      data.definitions.forEach(kpi => {
        console.log(`${kpi.kpi_code}: ${kpi.kpi_name}`);
      });
    }
  } catch (error) {
    console.error('Error:', error);
  }
}
```

### JavaScript/Fetch - Get Specific KPI
```javascript
async function getKpiById(kpiId) {
  try {
    const response = await fetch(`http://localhost:8085/kpi/definitions/${kpiId}`);
    const data = await response.json();
    
    if (data.code === 200) {
      console.log('KPI Details:', {
        code: data.kpi_code,
        name: data.kpi_name,
        category: data.category,
        unit: data.unit,
        formula: data.formula
      });
    } else if (data.code === 404) {
      console.log('KPI not found');
    }
  } catch (error) {
    console.error('Error:', error);
  }
}
```

### JavaScript/Fetch - Get KPI by Category
```javascript
async function getKpisByCategory(category) {
  try {
    const encodedCategory = encodeURIComponent(category);
    const response = await fetch(
      `http://localhost:8085/kpi/definitions/by-category?category=${encodedCategory}`
    );
    const data = await response.json();
    
    if (data.code === 200) {
      console.log(`KPIs in category ${category}:`, data.definitions);
    }
  } catch (error) {
    console.error('Error:', error);
  }
}
```

### Java - Get All KPI Definitions
```java
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8085/kpi/definitions";

ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
JSONObject data = new JSONObject(response.getBody());

if (data.getInt("code") == 200) {
    JSONArray definitions = data.getJSONArray("definitions");
    for (int i = 0; i < definitions.length(); i++) {
        JSONObject kpi = definitions.getJSONObject(i);
        System.out.println(kpi.getString("kpi_code") + ": " + kpi.getString("kpi_name"));
    }
}
```

## Implementation Details

### Files Created/Modified

1. **KpiDefinition.java** (NEW)
   - Model/Entity class representing a KPI definition
   - Contains all properties with getters and setters
   - Used for mapping database results to objects

2. **kpiExtend.java** (UPDATED)
   - Added `getKpiDefinitions()` method - retrieves all KPI definitions
   - Added `getKpiDefinitionById(Integer kpiId)` method - retrieves specific KPI by ID
   - Added `getKpiDefinitionsByCategory(String category)` method - retrieves KPIs by category
   - All methods return JSONArray or JSONObject for JSON API responses

3. **kpiServices.java** (UPDATED)
   - Added `@GetMapping("/definitions")` endpoint
   - Added `@GetMapping("/definitions/{kpiId}")` endpoint
   - Added `@GetMapping("/definitions/by-category")` endpoint
   - All endpoints follow project's JSON response format
   - Includes comprehensive JavaDoc comments

## Integration Notes

- All endpoints are stateless and don't require authentication
- Responses follow the project's JSON response format with `code` and `description` fields
- The API assumes a table named `kpi_definitions` in the database
- Database column mappings are:
  - `id` → `kpi_id`
  - `code` → `kpi_code`
  - `name` → `kpi_name`
  - `category` → `category`
  - `unit` → `unit`
  - `formula` → `formula`
  - `data_source` → `data_source`
  - `frequency` → `frequency`

## Related APIs

- **POST /kpi/assign** - Assign KPI to a department (requires authentication)
- **GET /kpi/definitions** - Get all KPI definitions (this endpoint)
- **GET /kpi/definitions/{kpiId}** - Get specific KPI definition (this endpoint)
- **GET /kpi/definitions/by-category** - Get KPIs by category (this endpoint)
