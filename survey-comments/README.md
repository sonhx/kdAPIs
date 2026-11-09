# Survey Comments Analysis Module — Runbook & Documentation

Module for classifying free-text Vietnamese student survey comments into multi-label themes and sentiments using Google Gemini API within the Spring Boot `kdAPIs` ecosystem.

---

## 1. Architecture & Pipeline Overview

```
[CSV Upload] (UTF-8 / BOM)
      │
      ▼
1. INGEST ──────► Validates encoding, headers (comment_id, survey_id, cohort, comment_text)
      │
      ▼
2. CLEAN  ──────► NFC Unicode normalisation, drops empty/no-content phrases ("N/A", "ko co y kien")
      │
      ▼
3. REDACT ──────► Rule-based PII masking: Emails, Vietnamese phone numbers (+84), MSSV, Names
      │
      ▼
4. CLASSIFY ────► Local token bucket RateLimiter, SHA-256 Cache, Google Gen AI SDK (gemini-3.8-flash)
      │
      ▼
5. VALIDATE ────► Local schema validation against schema.json; route low-confidence to review-queue
      │
      ▼
6. PERSIST ─────► Thread-safe run storage with progress tracking and token usage accounting
      │
      ▼
7. REPORT ──────► 5-Sheet Excel (.xlsx) workbook via Apache POI & CSV summary
```

---

## 2. Security & Environment Setup

### 2.1 API Key Configuration
Per AGENT_RULES Rule 4, the Gemini API key **must never** be committed, written to YAML/properties, or logged.

Set the key as an environment variable in your production host or IDE run configuration:
```bash
# Linux / macOS / Docker
export GEMINI_API_KEY="AIzaSy..."

# Windows PowerShell (Current session)
$env:GEMINI_API_KEY="AIzaSy..."

# Windows User Registry (Persistent across reboots)
setx GEMINI_API_KEY "AIzaSy..."
```

### 2.2 Model Selection
Google Gemini Developer API defaults to `gemini-3.8-flash`:
```properties
# Override in application.properties or via OS environment variable:
survey-comments.gemini.model=${GEMINI_MODEL:gemini-3.8-flash}
survey-comments.gemini.requests-per-minute=${GEMINI_RPM:10}
survey-comments.gemini.max-retries=5
```

### 2.3 Authentication
All endpoints require an active `kdAPIs` admin session (`UserType != 4`). Pass the session ID via:
- HTTP Header: `X-Session-Id: <session-token>`
- Or Query Parameter: `?session_id=<session-token>`

---

## 3. REST API Reference

| Method | Endpoint | Description |
|:---|:---|:---|
| `POST` | `/api/v1/survey-comments/runs` | Upload survey CSV (multipart `file`), `surveyId`, `mode=SYNC` $\rightarrow$ `202 Accepted` + `runId` |
| `GET`  | `/api/v1/survey-comments/runs/{runId}` | Check run status, current stage, and progress counters |
| `GET`  | `/api/v1/survey-comments/runs/{runId}/results` | Retrieve classified comments JSON |
| `GET`  | `/api/v1/survey-comments/runs/{runId}/review-queue` | Download CSV of comments requiring human review |
| `GET`  | `/api/v1/survey-comments/runs/{runId}/report` | Download 5-sheet `.xlsx` report (`format=xlsx`) or summary (`format=csv`) |
| `GET`  | `/api/v1/survey-comments/runs/{runId}/cost` | View token usage breakdown and estimated USD / VND cost |
| `POST` | `/api/v1/survey-comments/discovery` | Discover emergent candidate themes from raw CSV |
| `POST` | `/api/v1/survey-comments/evaluations` | Evaluate run accuracy against gold CSV standard |
| `POST` | `/api/v1/survey-comments/maintenance/cleanup` | Trigger manual retention purge of expired runs |

---

## 4. Operator Runbook

### Workflow 1: Running a Survey End-to-End

1. **Submit Survey Run:**
   ```bash
   curl -X POST "http://localhost:8080/kdAPIs/api/v1/survey-comments/runs" \
        -H "X-Session-Id: $ADMIN_SESSION" \
        -F "file=@survey_comments_k14.csv" \
        -F "surveyId=survey-2026-spring" \
        -F "mode=SYNC"
   ```
   *Response:*
   ```json
   {
     "status": "ACCEPTED",
     "runId": "692c5780-f2c9-462f-90e6-52504ae67f67",
     "surveyId": "survey-2026-spring",
     "totalComments": 0,
     "classifiedComments": 0
   }
   ```

2. **Monitor Progress:**
   ```bash
   curl -X GET "http://localhost:8080/kdAPIs/api/v1/survey-comments/runs/692c5780-f2c9-462f-90e6-52504ae67f67" \
        -H "X-Session-Id: $ADMIN_SESSION"
   ```
   *Response when complete:*
   ```json
   {
     "runId": "692c5780-f2c9-462f-90e6-52504ae67f67",
     "status": "DONE",
     "totalComments": 250,
     "cleanedComments": 232,
     "classifiedComments": 232,
     "reviewQueueSize": 12
   }
   ```

3. **Download Excel Report:**
   ```bash
   curl -O -J "http://localhost:8080/kdAPIs/api/v1/survey-comments/runs/692c5780-f2c9-462f-90e6-52504ae67f67/report" \
        -H "X-Session-Id: $ADMIN_SESSION"
   ```
   *Generated Sheets:*
   - **Sheet 1 (Tổng quan & Chủ đề):** Share of commenters per theme ($>100\%$ sum highlighted), sentiment split, evidence quotes.
   - **Sheet 2 (Phân tích theo khóa):** Cohort matrix $\times$ sentiment & suggestions.
   - **Sheet 3 (Kiến nghị & Đề xuất):** Filtered suggestions for leadership review.
   - **Sheet 4 (Hàng đợi kiểm duyệt):** Items flagged for manual verification.
   - **Sheet 5 (Mẫu kiểm tra ngẫu nhiên):** Deterministic 5% spot-check audit sample.

4. **Check Token Usage & Cost:**
   ```bash
   curl -X GET "http://localhost:8080/kdAPIs/api/v1/survey-comments/runs/692c5780-f2c9-462f-90e6-52504ae67f67/cost" \
        -H "X-Session-Id: $ADMIN_SESSION"
   ```
   *Response:*
   ```json
   {
     "runId": "692c5780-f2c9-462f-90e6-52504ae67f67",
     "totalClassifiedComments": 232,
     "totalInputTokens": 365000,
     "totalOutputTokens": 28400,
     "totalTokens": 393400,
     "costUsd": 0.0718,
     "costVnd": 1824
   }
   ```

---

### Workflow 2: Updating Codebook & Gold-Set Evaluation

1. **Discover Draft Themes from New Data:**
   ```bash
   curl -X POST "http://localhost:8080/kdAPIs/api/v1/survey-comments/discovery" \
        -H "X-Session-Id: $ADMIN_SESSION" \
        -F "file=@past_survey.csv" \
        -F "surveyId=survey-discovery"
   ```
2. **Review Discovered Themes:** Human experts update `src/main/resources/survey-comments/codebook.md`. Bump codebook version (e.g. `v0.3-draft`).
3. **Run Evaluation Against Gold Dataset:**
   ```bash
   curl -X POST "http://localhost:8080/kdAPIs/api/v1/survey-comments/evaluations" \
        -H "X-Session-Id: $ADMIN_SESSION" \
        -F "goldFile=@sample_gold.csv" \
        -F "runId=692c5780-f2c9-462f-90e6-52504ae67f67"
   ```
   *Returns:* Sentiment accuracy, Cohen's kappa ($\kappa$), macro & micro $F_1$, and disagreements list.

---

## 5. Cost Estimation Reference

Configured in `application.properties`:
- Input rate: **\$0.15 / 1M tokens**
- Output rate: **\$0.60 / 1M tokens**
- Exchange rate: **25,400 VND / USD**

| Comments Count | Avg Input Tokens | Avg Output Tokens | Total Tokens | Estimated USD | Estimated VND |
|:---|:---|:---|:---|:---|:---|
| **100** | ~160,000 | ~12,000 | ~172,000 | **\$0.031** | ~790 đ |
| **500** | ~800,000 | ~60,000 | ~860,000 | **\$0.156** | ~3,960 đ |
| **2,000** | ~3,200,000 | ~240,000 | ~3,440,000 | **\$0.624** | ~15,850 đ |
| **10,000** | ~16,000,000 | ~1,200,000 | ~17,200,000 | **\$3.120** | ~79,250 đ |

*Note: In-memory SHA-256 caching eliminates API token consumption for identical repeated feedback (e.g. "Tốt.", "Không có ý kiến").*

---

## 6. Retention & Data Lifecycle

- Artifacts are stored under `./survey-comments-data/runs/{runId}/`.
- Automated daily scheduled job (`0 0 2 * * ?` at 2:00 AM) deletes runs older than `survey-comments.storage.retention-days=90`.
- Manual purge can be triggered anytime via `POST /api/v1/survey-comments/maintenance/cleanup`.

---

## 7. Definition of Done Checklist (PLAN.md §12)

- [x] CSV upload $\rightarrow$ background run $\rightarrow$ results in SYNC mode on fake sample
- [x] Redaction tests pass (emails, phones, MSSV, names); PII verified removed before LLM call
- [x] Gold-set evaluation implemented (precision/recall/F1 per theme, Cohen's kappa, disagreements)
- [ ] Batch mode on paid tier (T6: deferred until billing account is linked to Google Cloud project)
- [x] Report (.xlsx) + review queue endpoints implemented with Apache POI
- [x] Endpoints secured with `kdAPIs` custom session auth (`requireAdmin`); no secrets in code or logs
- [x] README runbook and token cost estimator complete
