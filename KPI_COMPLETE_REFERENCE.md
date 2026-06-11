# KPI APIs - Complete Reference Guide

## Overview
This document provides a complete reference for all KPI-related API endpoints, including those for retrieving KPI definitions and managing KPI assignments.

## API Endpoints Summary

### KPI Definitions (Read-Only - No Authentication)

| Method | Endpoint | Description | Authentication |
|--------|----------|-------------|-----------------|
| GET | `/kpi/definitions` | Get all KPI definitions (active only) | No |
| GET | `/kpi/definitions/{kpiId}` | Get specific KPI definition by ID (active only) | No |
| GET | `/kpi/definitions/by-category` | Get KPI definitions by category (active only) | No |

### KPI Management (Write Operations - No Authentication)

| Method | Endpoint | Description | Authentication |
|--------|----------|-------------|-----------------|
| POST | `/kpi/add` | Add a new KPI definition | No |
| DELETE | `/kpi/{kpiId}` | Soft-delete (mark as removed) a KPI definition | No |

### KPI Assignments - Retrieve (Read-Only - No Authentication)

| Method | Endpoint | Description | Authentication |
|--------|----------|-------------|-----------------|
| GET | `/kpi/assignments/{departmentId}` | Get all KPI assignments for a department | No |
| GET | `/kpi/assignments/{departmentId}/details` | Get assignments with full KPI details for a department | No |

### KPI Assignments - Create (Write Operations - No Authentication)

| Method | Endpoint | Description | Authentication |
|--------|----------|-------------|-----------------|
| POST | `/kpi/assign` | Create a new KPI assignment to a department | No |

---

## Quick Reference

### 1. Get All KPI Definitions
```http
GET /kpi/definitions HTTP/1.1
Host: localhost:8085
```

**Response** (200):
```json
{
  "code": 200,
  "description": "Thành công",
  "definitions": [
    {
      "kpi_id": 4,
      "kpi_code": "T1.01",
      "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
      "category": "T – Đào tạo & Người học",
      "unit": "%",
      "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
      "data_source": "P. Đào tạo",
      "frequency": "Năm học"
    }
  ]
}
```

---

### 2. Get Specific KPI Definition
```http
GET /kpi/definitions/4 HTTP/1.1
Host: localhost:8085
```

**Response** (200):
```json
{
  "code": 200,
  "description": "Thành công",
  "kpi_id": 4,
  "kpi_code": "T1.01",
  "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
  "category": "T – Đào tạo & Người học",
  "unit": "%",
  "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
  "data_source": "P. Đào tạo",
  "frequency": "Năm học"
}
```

---

### 3. Get KPI Definitions by Category
```http
GET /kpi/definitions/by-category?category=T%20–%20Đào%20tạo%20&%20Người%20học 
HTTP/1.1
Host: localhost:8085
```

**Response** (200):
```json
{
  "code": 200,
  "description": "Thành công",
  "category": "T – Đào tạo & Người học",
  "definitions": [
    {
      "kpi_id": 4,
      "kpi_code": "T1.01",
      "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
      "category": "T – Đào tạo & Người học",
      "unit": "%",
      "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
      "data_source": "P. Đào tạo",
      "frequency": "Năm học"
    },
    {
      "kpi_id": 5,
      "kpi_code": "T1.02",
      "kpi_name": "Tỷ lệ duy trì sinh viên qua các năm học",
      "category": "T – Đào tạo & Người học",
      "unit": "%",
      "formula": "Số SV tiếp tục học/ tổng SV đầu năm × 100%",
      "data_source": "SIS",
      "frequency": "Năm học"
    }
  ]
}
```

---

### 4. Create New KPI Definition
```http
POST /kpi/add HTTP/1.1
Host: localhost:8085
Content-Type: application/json

{
  "code": "T1.11",
  "name": "Tỷ lệ sinh viên hài lòng về chương trình đào tạo",
  "category": "T – Đào tạo & Người học",
  "unit": "%",
  "formula": "SV hài lòng/ tổng SV khảo sát × 100%",
  "data_source": "Khảo sát trực tuyến",
  "frequency": "Năm học"
}
```

**Response** (201):
```json
{
  "code": 201,
  "description": "Thành công",
  "kpi_id": 65,
  "kpi_code": "T1.11"
}
```

---

### 5. Create KPI Assignment
```http
POST /kpi/assign HTTP/1.1
Host: localhost:8085
Content-Type: application/json

{
  "session_id": "abc123xyz789",
  "kpi_id": 4,
  "department_id": 5,
  "role": "A"
}
```

**Response** (200):
```json
{
  "code": 200,
  "description": "Thành công",
  "assignment_id": 123
}
```

---

### 6. Delete KPI Definition
```http
DELETE /kpi/65 HTTP/1.1
Host: localhost:8085
```

**Response** (200):
```json
{
  "code": 200,
  "description": "Thành công",
  "kpi_id": 65
}
```

---

### 7. Get KPI Assignments for Department
```http
GET /kpi/assignments/5 HTTP/1.1
Host: localhost:8085
```

**Response** (200):
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

---

### 8. Get KPI Assignments with Details
```http
GET /kpi/assignments/5/details HTTP/1.1
Host: localhost:8085
```

**Response** (200):
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
    }
  ]
}
```

---

## Response Status Codes

| Code | Status | Description |
|------|--------|-------------|
| 200 | Success | Request completed successfully |
| 400 | Bad Request | Invalid input parameters |
| 404 | Not Found | Resource not found |
| 500 | Server Error | Database or server error |
| 700 | Unauthorized | Invalid session or not logged in |
| 800 | JSON Error | Invalid JSON format or missing parameters |

---

## KPI Categories Reference

All available KPI categories in the system:

| Code | Category | Description |
|------|----------|-------------|
| T | T – Đào tạo & Người học (Training) | Training & Student Learning |
| G | G – Giảng viên & Nhân lực học thuật (Lecturer) | Lecturer & Academic HR |
| N | N – Nghiên cứu & Chuyển giao (Research) | Research & Technology Transfer |
| H | H – Hỗ trợ người học (Student Support) | Student Support Services |
| C | C – Cải tiến liên tục & CAPA (Continuous Improvement) | Continuous Improvement & CAPA |
| K | K – Kiểm định & Đối sánh (Accreditation) | Accreditation & Benchmarking |
| Q | Q – Quản trị & Nguồn lực (Governance) | Governance & Resources |
| D | D – Chuyển đổi số & Dữ liệu (Digital Quality) | Digital Transformation & Data |

---

## Sample KPI Codes by Category

### Training (T)
- T1.01 - Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch
- T1.02 - Tỷ lệ duy trì sinh viên qua các năm học
- T1.03 - Tỷ lệ sinh viên tốt nghiệp đúng hạn
- T1.04 - Tỷ lệ sinh viên học vượt tiến độ
- T1.05 - Tỷ lệ SV thôi học, bảo lưu
- T1.06 - Tỷ lệ học phần được đánh giá CLO đầy đủ
- T1.07 - Mức độ hài lòng SV về CTĐT
- T1.08 - Tỷ lệ SV được cảnh báo học tập đúng hạn
- T1.09 - Tỷ lệ SV tham gia NCKH
- T1.10 - Tỷ lệ SV đạt giải thưởng học thuật

### Lecturer (G)
- G2.01 - Tỷ lệ GV có trình độ tiến sĩ
- G2.02 - Tỷ lệ GV có chứng chỉ NVSP
- G2.03 - Tỷ lệ GV được tập huấn QA/ OBE
- G2.04 - Tỷ lệ GV hướng dẫn ĐATN
- G2.05 - Số bài báo bình quân mỗi GV
- G2.06 - Tỷ lệ GV tham gia hội thảo quốc tế
- G2.07 - Tỷ lệ GV có chứng chỉ ngoại ngữ
- G2.08 - Tỷ lệ GV có chứng chỉ chuyển đổi số
- G2.09 - Mức độ hài lòng GV về điều kiện làm việc
- G2.10 - Tỷ lệ GV cơ hữu/ SV (FTE)

### Research (N)
- N3.01 - Số đề tài NCKH cấp Bộ/ HV được phê duyệt
- N3.02 - Tỷ lệ đề tài hoàn thành đúng hạn
- N3.03 - Tỷ lệ công bố quốc tế
- N3.04 - Doanh thu từ hoạt động chuyển giao
- N3.05 - Tỷ lệ SV tham gia nghiên cứu với GV
- N3.06 - Tỷ lệ giảng viên có công trình được trích dẫn
- N3.07 - Số sáng chế, giải pháp hữu ích được cấp
- N3.08 - Tỷ lệ bài báo đăng tạp chí uy tín

### Student Support (H)
- H4.01 - Tỷ lệ SV sử dụng dịch vụ tư vấn học tập
- H4.02 - Mức độ hài lòng SV về tư vấn việc làm
- H4.03 - Mức độ hài lòng SV về hoạt động ngoại khóa
- H4.04 - Mức độ hài lòng về thư viện và học liệu số
- H4.05 - Mức độ hài lòng về phòng máy, thí nghiệm
- H4.06 - Tỷ lệ SV tham gia CLB học thuật – kỹ năng
- H4.07 - Tỷ lệ SV tham gia hoạt động cộng đồng
- H4.08 - Tỷ lệ SV có việc làm sau 12 tháng tốt nghiệp

### Continuous Improvement (C)
- C5.01 - Số CAPA được mở mỗi năm
- C5.02 - Tỷ lệ CAPA khép vòng đúng hạn
- C5.03 - Tỷ lệ đề xuất cải tiến được phê duyệt
- C5.04 - Số báo cáo SAR được cập nhật đúng kỳ
- C5.05 - Mức độ hài lòng về phản hồi cải tiến

### Accreditation (K)
- K6.01 - Tỷ lệ CTĐT được kiểm định AUN-QA/ ASIIN
- K6.02 - Tỷ lệ CTĐT có đối sánh benchmarking
- K6.03 - Tỷ lệ Khoa/ Bộ môn được đánh giá nội bộ
- K6.04 - Số báo cáo tự đánh giá cấp cơ sở
- K6.05 - Tỷ lệ minh chứng QA được số hóa

### Governance (Q)
- Q7.01 - Tỷ lệ GV cơ hữu/ SV (FTE)
- Q7.02 - Tỷ lệ chi cho đào tạo/ Nguồn thu
- Q7.03 - Tỷ lệ chi cho NCKH/ Nguồn thu
- Q7.04 - Mức độ hài lòng cán bộ về môi trường làm việc
- Q7.05 - Tỷ lệ GV đạt chuẩn khối lượng giảng dạy
- Q7.06 - Tỷ lệ nhân viên hoàn thành nhiệm vụ
- Q7.07 - Tỷ lệ thu hút nguồn thu ngoài ngân sách

### Digital Quality (D)
- D8.01 - Tỷ lệ học phần được quản lý trên LMS
- D8.02 - Tỷ lệ GV sử dụng LMS thường xuyên
- D8.03 - Tỷ lệ SV truy cập LMS trung bình mỗi tuần
- D8.04 - Tỷ lệ dữ liệu cập nhật trên Dashboard QA
- D8.05 - Tỷ lệ hệ thống dữ liệu có người chịu trách nhiệm
- D8.06 - Mức độ an toàn dữ liệu QA
- D8.07 - Tỷ lệ hồ sơ QA được số hóa
- D8.08 - Tỷ lệ quy trình QA vận hành tự động

---

## Database Structure

### kpi_definitions Table
```sql
CREATE TABLE kpi_definitions (
    id INT PRIMARY KEY IDENTITY(1,1),
    code VARCHAR(10) NOT NULL UNIQUE,
    name NVARCHAR(MAX) NOT NULL,
    category NVARCHAR(100),
    unit VARCHAR(50),
    formula NVARCHAR(MAX),
    data_source NVARCHAR(100),
    frequency NVARCHAR(50),
    is_deleted INT DEFAULT 0 -- Soft-delete flag: 0 = active, 1 = deleted
);
```

### kpi_assignments Table
```sql
CREATE TABLE kpi_assignments (
    assignment_id INT PRIMARY KEY IDENTITY(1,1),
    kpi_id INT,
    department_id INT,
    role CHAR(1),
    assigned_date DATETIME DEFAULT GETDATE(),
    assigned_by INT,
    FOREIGN KEY (kpi_id) REFERENCES kpi_definitions(id)
);
```

---

## Common Use Cases

### Use Case 1: Display KPI List in Dropdown
1. Call `GET /kpi/definitions` to retrieve all KPIs
2. Map response to UI dropdown with `kpi_code` as label and `kpi_id` as value
3. Display full description in tooltip using `kpi_name`, `formula`, and `data_source`

### Use Case 2: Create New KPI Definition
1. Admin/Manager fills KPI form with code, name, category, unit, formula, etc.
2. Call `POST /kpi/add` with all required fields
3. Check response code (201 = success, 409 = code exists, 400 = invalid input)
4. Display confirmation message with new KPI ID to user

### Use Case 3: Assign KPI to Department
1. User selects KPI from dropdown (gets `kpi_id`)
2. User selects department
3. User enters role assignment
4. Call `POST /kpi/assign` with selected values and session ID
5. Receive `assignment_id` on success

### Use Case 4: View KPI Details
1. User clicks on KPI code (e.g., T1.01)
2. Call `GET /kpi/definitions/{kpiId}` using the KPI ID
3. Display all KPI details including formula, data source, and frequency

### Use Case 5: Filter KPIs by Category
1. User selects category from filter (e.g., "T – Đào tạo & Người học")
2. Call `GET /kpi/definitions/by-category?category=<selected_category>`
3. Display filtered KPI list

---

## Implementation Files

All KPI-related implementations are in the package: `com.kpi`

**Files Created:**
- `KpiDefinition.java` - Model class for KPI definitions
- `KpiAssignment.java` - Model class for KPI assignments
- `KpiDefinition.java` - Extended service layer

**Files Modified:**
- `kpiExtend.java` - Added methods for KPI operations
- `kpiServices.java` - Added REST endpoints

**Documentation Files:**
- `KPI_DEFINITIONS_API.md` - Detailed API documentation for GET endpoints
- `KPI_ADD_API.md` - API documentation for creating new KPIs
- `KPI_DELETE_API.md` - API documentation for soft-deleting KPIs
- `KPI_ASSIGNMENTS_BY_ORGANIZATION_API.md` - API documentation for retrieving assignments by organization
- `KPI_ASSIGNMENT_API.md` - API documentation for KPI assignments (create)
- `KPI_COMPLETE_REFERENCE.md` - This complete reference guide

---

## Notes

- All KPI definition GET endpoints (GET) are read-only and require no authentication
- **Soft-Delete Implementation:** All GET queries automatically exclude deleted KPIs (WHERE is_deleted = 0 OR is_deleted IS NULL)
- Deleted KPIs are marked with `is_deleted = 1` and do not appear in read operations
- KPI creation endpoint (POST /kpi/add) requires no authentication
- KPI deletion endpoint (DELETE /kpi/{kpiId}) performs soft-delete (marks as deleted, doesn't remove from DB)
- KPI assignment endpoints require valid user session
- Database is configured for SQL Server with automatic ID generation
- All responses follow standardized JSON format with `code` and `description` fields
- Server runs on port 8085 (configurable in `application.properties`)
