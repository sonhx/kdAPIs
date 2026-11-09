# Change Requests & Architecture Evolution

This document records architectural amendments and recommended changes relative to `docs/PLAN.md` and `codebook.md`, adhering to **AGENT_RULES Rule 3**.

---

## CR-001: Model Migration to `gemini-3.8-flash`
- **Component:** `SurveyCommentsProperties`, `GeminiCommentClassifier`, `DiscoveryServiceImpl`
- **Change:** Updated default fallback model string from `gemini-2.5-flash` to `gemini-3.8-flash`.
- **Reason:** During live sample execution, the Google Gemini Developer API returned HTTP 404:
  `"This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use models/gemini-3.8-flash for the latest features and improvements."`
- **Impact:** Backward-compatible; model ID can still be overridden at runtime via `GEMINI_MODEL` environment variable.

---

## CR-002: Token Cost Estimator (`CostService`)
- **Component:** `com.surveycomments.cost`, `POST /runs/{runId}/cost`
- **Change:** Added per-million token pricing parameters (`usdPerMillionInputTokens=0.15`, `usdPerMillionOutputTokens=0.60`, `vndExchangeRate=25400`) to compute run cost in both USD and VND.
- **Reason:** Required by Task T8 and PLAN.md §7 ("Record token usage from each response for cost estimation") for leadership budget auditing.

---

## CR-003: Retention Management & Scheduled Purge
- **Component:** `com.surveycomments.retention`, `POST /maintenance/cleanup`
- **Change:** Added `RetentionService` with automated daily cron (`0 0 2 * * ?` at 2:00 AM) and manual trigger to delete file-based run storage older than `retention-days` (default 90 days).
- **Reason:** PLAN.md §6 privacy rule: prevents permanent retention of student feedback artifacts on local disks.

---

## CR-004: Cohort Retention in `ClassificationResult`
- **Component:** `ClassificationResult.java`, `GeminiCommentClassifier.java`
- **Change:** Preserved the `cohort` string from the input CSV (e.g. `K12`, `K13`, `K14`) inside the classification result model.
- **Reason:** Enables cohort matrix breakdown in the Excel report (Sheet 2) required by PLAN.md §10 without re-reading raw files.

