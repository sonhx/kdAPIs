# KPI Add/Creation API Documentation

## Overview
This document describes the POST API endpoint for creating new KPI definitions in the system.

## Endpoint

### POST /kpi/add
Creates a new KPI definition and stores it in the `kpi_definitions` table.

## Request Format

**Content-Type:** application/json

**Request Body:**
```json
{
  "code": "T1.11",
  "name": "Tỷ lệ sinh viên hài lòng về chương trình đào tạo",
  "category": "T – Đào tạo & Người học (Training)",
  "unit": "%",
  "formula": "SV hài lòng/ tổng SV khảo sát × 100%",
  "data_source": "Khảo sát trực tuyến",
  "frequency": "Năm học"
}
```

### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| code | string | Yes | Unique KPI code (e.g., T1.01, G2.05) - must not already exist |
| name | string | Yes | KPI name/description (Vietnamese) |
| category | string | Yes | KPI category (see category list below) |
| unit | string | Yes | Unit of measurement (%, Điểm, Số lượng, Tỷ lệ, Bài, etc.) |
| formula | string | Yes | Calculation formula or definition |
| data_source | string | Yes | Source of data collection (e.g., SIS, LMS, P. Đào tạo) |
| frequency | string | Yes | Reporting frequency (Năm học, Học kỳ, Quý, Hàng năm) |

**All fields are required and must not be empty or whitespace only.**

## Response Format

### Success Response (Code 201)
```json
{
  "code": 201,
  "description": "Thành công",
  "kpi_id": 65,
  "kpi_code": "T1.11"
}
```

### Validation Error (Code 400)
```json
{
  "code": 400,
  "description": "KPI code is required"
}
```

### Conflict Error (Code 409 - Code Already Exists)
```json
{
  "code": 409,
  "description": "KPI code already exists"
}
```

### JSON Error (Code 800 - Invalid JSON)
```json
{
  "code": 800,
  "description": "JSON error: Thiếu tham số?"
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
| 201 | Created | KPI definition created successfully |
| 400 | Bad Request | Missing or invalid required parameters |
| 409 | Conflict | KPI code already exists in database |
| 500 | Server Error | Database error or other server issue |
| 800 | JSON Error | Invalid JSON format or missing fields |

## Validation Rules

1. **code**
   - Required
   - Must be non-empty and not just whitespace
   - Must be unique (not already exist in database)
   - Typical format: Letter(s) + Number(s) + dot + Number(s) (e.g., T1.01, G2.10)

2. **name**
   - Required
   - Must be non-empty and not just whitespace
   - Should be descriptive Vietnamese text

3. **category**
   - Required
   - Must be non-empty and not just whitespace
   - Should match one of the available categories

4. **unit**
   - Required
   - Must be non-empty and not just whitespace
   - Common values: %, Điểm, Số lượng, Tỷ lệ, Bài, etc.

5. **formula**
   - Required
   - Must be non-empty and not just whitespace
   - Should describe the calculation method

6. **data_source**
   - Required
   - Must be non-empty and not just whitespace
   - Examples: SIS, LMS, P. Đào tạo, Khảo sát trực tuyến, etc.

7. **frequency**
   - Required
   - Must be non-empty and not just whitespace
   - Common values: Năm học, Học kỳ, Quý, Hàng năm, etc.

## Available KPI Categories

| Category Code | Full Category Name |
|---------------|-------------------|
| T | T – Đào tạo & Người học (Training) |
| G | G – Giảng viên & Nhân lực học thuật (Lecturer) |
| N | N – Nghiên cứu & Chuyển giao (Research) |
| H | H – Hỗ trợ người học (Student Support) |
| C | C – Cải tiến liên tục & CAPA (Continuous Improvement) |
| K | K – Kiểm định & Đối sánh (Accreditation) |
| Q | Q – Quản trị & Nguồn lực (Governance) |
| D | D – Chuyển đổi số & Dữ liệu (Digital Quality) |

## Example Requests

### Example 1: Add Training-Related KPI
```bash
curl -X POST http://localhost:8085/kpi/add \
  -H "Content-Type: application/json" \
  -d '{
    "code": "T1.11",
    "name": "Tỷ lệ sinh viên hài lòng về chương trình đào tạo",
    "category": "T – Đào tạo & Người học (Training)",
    "unit": "%",
    "formula": "SV hài lòng/ tổng SV khảo sát × 100%",
    "data_source": "Khảo sát trực tuyến",
    "frequency": "Năm học"
  }'
```

**Success Response:**
```json
{
  "code": 201,
  "description": "Thành công",
  "kpi_id": 65,
  "kpi_code": "T1.11"
}
```

### Example 2: Add Lecturer-Related KPI
```bash
curl -X POST http://localhost:8085/kpi/add \
  -H "Content-Type: application/json" \
  -d '{
    "code": "G2.11",
    "name": "Tỷ lệ GV tham gia tập huấn phương pháp giảng dạy mới",
    "category": "G – Giảng viên & Nhân lực học thuật (Lecturer)",
    "unit": "%",
    "formula": "GV tham dự tập huấn/ tổng GV × 100%",
    "data_source": "P. TCCB-LĐ",
    "frequency": "Năm học"
  }'
```

### Example 3: Duplicate Code Error
```bash
curl -X POST http://localhost:8085/kpi/add \
  -H "Content-Type: application/json" \
  -d '{
    "code": "T1.01",
    "name": "Some new KPI with existing code",
    "category": "T – Đào tạo & Người học (Training)",
    "unit": "%",
    "formula": "Test formula",
    "data_source": "Test source",
    "frequency": "Năm học"
  }'
```

**Error Response:**
```json
{
  "code": 409,
  "description": "KPI code already exists"
}
```

### Example 4: Missing Required Field
```bash
curl -X POST http://localhost:8085/kpi/add \
  -H "Content-Type: application/json" \
  -d '{
    "code": "T1.12",
    "name": "Test KPI"
  }'
```

**Error Response:**
```json
{
  "code": 400,
  "description": "Category is required"
}
```

## JavaScript/Fetch Example

```javascript
async function addKpiDefinition(kpiData) {
  try {
    const response = await fetch('http://localhost:8085/kpi/add', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(kpiData)
    });

    const data = await response.json();

    if (data.code === 201) {
      console.log('KPI created successfully!');
      console.log('New KPI ID:', data.kpi_id);
      console.log('KPI Code:', data.kpi_code);
      return data;
    } else if (data.code === 409) {
      console.error('Error: KPI code already exists');
    } else if (data.code === 400) {
      console.error('Error:', data.description);
    } else {
      console.error('Server error:', data.description);
    }
  } catch (error) {
    console.error('Network error:', error);
  }
}

// Usage
const newKpi = {
  code: "T1.11",
  name: "Tỷ lệ sinh viên hài lòng về chương trình đào tạo",
  category: "T – Đào tạo & Người học (Training)",
  unit: "%",
  formula: "SV hài lòng/ tổng SV khảo sát × 100%",
  data_source: "Khảo sát trực tuyến",
  frequency: "Năm học"
};

addKpiDefinition(newKpi);
```

## Java/RestTemplate Example

```java
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8085/kpi/add";

Map<String, String> kpiData = new HashMap<>();
kpiData.put("code", "T1.11");
kpiData.put("name", "Tỷ lệ sinh viên hài lòng về chương trình đào tạo");
kpiData.put("category", "T – Đào tạo & Người học (Training)");
kpiData.put("unit", "%");
kpiData.put("formula", "SV hài lòng/ tổng SV khảo sát × 100%");
kpiData.put("data_source", "Khảo sát trực tuyến");
kpiData.put("frequency", "Năm học");

try {
    ResponseEntity<String> response = restTemplate.postForEntity(url, kpiData, String.class);
    JSONObject result = new JSONObject(response.getBody());
    
    if (result.getInt("code") == 201) {
        System.out.println("KPI Created! ID: " + result.getInt("kpi_id"));
    } else if (result.getInt("code") == 409) {
        System.out.println("Error: KPI code already exists");
    } else {
        System.out.println("Error: " + result.getString("description"));
    }
} catch (Exception e) {
    e.printStackTrace();
}
```

## Database Operation

When a KPI is successfully created:

1. **INSERT** operation is performed with all provided fields
2. **IDENTITY** value (auto-generated ID) is retrieved and returned as `kpi_id`
3. Original `code` value is returned as `kpi_code` for confirmation
4. Response status is 201 (Created)

### SQL Equivalent
```sql
INSERT INTO kpi_definitions (code, name, category, unit, formula, data_source, frequency)
VALUES ('T1.11', 'Tỷ lệ sinh viên hài lòng về chương trình đào tạo', 
        'T – Đào tạo & Người học (Training)', '%', 
        'SV hài lòng/ tổng SV khảo sát × 100%', 'Khảo sát trực tuyến', 'Năm học');

-- Returns the auto-generated kpi_id
SELECT IDENT_CURRENT('kpi_definitions') as kpi_id;
```

## Important Notes

- **Whitespace Trimming**: All input fields are automatically trimmed of leading/trailing whitespace
- **Uniqueness Check**: KPI codes must be unique; attempting to create a duplicate will return code 409
- **Data Persistence**: The KPI is immediately persisted to the database with a transaction
- **No Authentication**: This endpoint does NOT require session authentication
- **Response Code 201**: HTTP status 201 indicates resource created (not 200 like other successful operations)
- **Auto-generated ID**: The system automatically generates an ID for the new KPI definition

## Common Error Scenarios

### Scenario 1: Empty or Whitespace-Only Field
**Request:**
```json
{
  "code": "T1.11",
  "name": "   ",
  "category": "T – Đào tạo & Người học (Training)",
  "unit": "%",
  "formula": "Formula here",
  "data_source": "Source here",
  "frequency": "Năm học"
}
```
**Response:**
```json
{
  "code": 400,
  "description": "KPI name is required"
}
```

### Scenario 2: Duplicate Code
**Request (second call with same code)**:
```json
{
  "code": "T1.01",
  "name": "Different name",
  "category": "T – Đào tạo & Người học (Training)",
  "unit": "%",
  "formula": "Different formula",
  "data_source": "Different source",
  "frequency": "Năm học"
}
```
**Response:**
```json
{
  "code": 409,
  "description": "KPI code already exists"
}
```

### Scenario 3: Invalid JSON
**Request:**
```
POST body: {not valid json}
```
**Response:**
```json
{
  "code": 800,
  "description": "JSON error: Thiếu tham số?"
}
```

## Related Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/kpi/definitions` | Get all KPI definitions |
| GET | `/kpi/definitions/{kpiId}` | Get specific KPI by ID |
| GET | `/kpi/definitions/by-category` | Get KPIs filtered by category |
| **POST** | **/kpi/add** | **Add new KPI definition (THIS ENDPOINT)** |
| POST | `/kpi/assign` | Assign KPI to department |

## Implementation Details

**Files Modified:**
- `kpiExtend.java` - Added `addKpiDefinition()` method
- `kpiServices.java` - Added `POST /kpi/add` endpoint

**Key Features:**
- ✅ Comprehensive input validation
- ✅ Uniqueness check on KPI code
- ✅ Automatic ID generation
- ✅ Transaction support
- ✅ Detailed error messages
- ✅ Database constraint enforcement
