# Tasks for the agent (Java / Spring Boot). Run in order, one per session, review before the next.

Start every task with: "Read docs/AGENT_RULES.md and docs/PLAN.md first."
Paths: codebook/schema live in `src/main/resources/survey-comments/`; fake test data in
`src/test/resources/survey-comments/`.

---
## T0. Recon of kdAPIs (READ-ONLY, no changes)
**Prompt**
```
Read docs/AGENT_RULES.md and docs/PLAN.md. Do NOT change any files yet.
Inspect kdAPIs and report:
1. Java version, Spring Boot version, build tool (Maven/Gradle) and how to build/run/test
2. Base package, module/package structure, layering, DTO style (records/Lombok/classes)
3. REST conventions: URL prefix and versioning, error response format, validation, OpenAPI/Swagger
4. Authentication/authorization (Spring Security? roles?) and how admin-only endpoints are done
5. Persistence: database type, JPA/JDBC, migration tool (Flyway/Liquibase), or none
6. Configuration style (application.yml/properties, profiles, @ConfigurationProperties), secrets handling
7. Logging setup, async/executor/scheduler usage, formatter/linter (Spotless/Checkstyle)
8. Test stack (JUnit 4/5, Mockito, MockMvc, Testcontainers) with 2 example tests
9. Existing dependencies relevant here: Jackson, HTTP client, CSV/Excel libs, JSON-schema, Resilience4j,
   any Google/Gemini SDK
Then propose: where the module's package lives, the final endpoint paths, the persistence choice for
runs/results, and the list of new dependencies (with reasons). Wait for my approval.
```
**Done when:** you approved a layout and endpoint design that match kdAPIs.

---
## T1. Skeleton and configuration
**Prompt**
```
Create the module skeleton in the approved package. Copy codebook.md, schema.json and the sample
config into src/main/resources/survey-comments/ and the fake CSVs into src/test/resources/survey-comments/
if not already there. Add a @ConfigurationProperties class bound to the `survey-comments` config
(Gemini api-key from env GEMINI_API_KEY, model, requests-per-minute, max-retries, codebook-version,
prompt-version, redact-staff-names, upload limits, storage). Add empty service interfaces/classes for the
stages in PLAN.md section 4, a CommentClassifier interface, and one smoke test that loads the context
without calling any network API. Do not add dependencies without asking. Never read env/secret files.
```
**Done when:** build and smoke test pass; no secrets in the repo.

---
## T2. Ingest, clean, redact (offline)
**Prompt**
```
Implement ingest, clean and redact for CSV with columns comment_id, survey_id, cohort, comment_text.
- Read UTF-8 with or without BOM; reject other encodings with a clear error.
- Keep comment_id; if missing, generate deterministic ids.
- Drop empty/no-content comments, also without diacritics ("N/A", "Không có ý kiến gì", "ko co y kien").
- Do NOT de-duplicate identical short comments ("Tốt." from different students). Only drop exact duplicate
  submissions if a respondent/submission id column exists.
- Normalise to Unicode NFC with java.text.Normalizer; keep the original text otherwise.
- Redact emails, phone numbers (spaced like "0912 345 678", +84), MSSV, personal names using rules
  (honorifics thầy/cô/anh/chị + capitalised words, optional name list); honour redact-staff-names.
  Never use an external API for redaction.
- Produce clean records with redaction_counts and flags (very_long, very_short).
Unit tests with sample_fake_comments.csv. Log counts only, never text.
```
**Done when:** tests pass; c006 has name/email/phone/MSSV removed; c008 and c012 dropped; c004 and c014
("Tốt.") both kept. Then hand-check a sample of redacted output.

---
## T3. Codebook discovery (dev tool; needs T1, T2)
**Prompt**
```
Read the Google Gen AI Java SDK docs (github.com/googleapis/java-genai) and the Gemini API docs first;
check the latest SDK version on Maven Central and ask before adding the dependency.
Implement a GeminiClient wrapper bean (API key from env) and a discovery service + endpoint
POST /discovery: reads a clean CSV, sends all comments in one long-context request (split only if too
large) and returns a DRAFT theme list for Vietnamese university service-quality feedback (id, Vietnamese
name, definition, 2 examples, boundary rules), starting from codebook.md and proposing merges/splits/new
themes with counts. Never overwrite codebook.md. Mock the client in tests. Use fake or anonymised data only
while on the free tier.
```
**Done when:** you edit the draft by hand into the approved `codebook.md` (bump version) and update the
theme enum in `schema.json` to match.

---
## T4. Classifier + sync runs (needs T1, T2; codebook v1 recommended)
**Prompt**
```
Read the SDK docs for structured output (response schema / JSON mime type) first and cite the pages.
Implement GeminiCommentClassifier behind CommentClassifier:
- Build the prompt from codebook.md (version-stamped), its rules and the Vietnamese language notes;
  evidence must be copied exactly from the comment (no translation); set contains_suggestion.
- One request per comment with structured output (supported schema subset); validate every response
  locally against the full schema.json; re-queue invalid output.
- Rate limit to requests-per-minute; retry with backoff incl. HTTP 429; honour max-retries.
- Cache by hash(comment_text + codebook_version + prompt_version + model).
- Store the result record from PLAN.md section 5 (incl. token usage and versions).
- Route confidence < 0.6, needs_human_review=true or theme "other" to the review queue.
Then add the run lifecycle for SYNC mode: POST /runs (multipart CSV) -> 202 + runId, background processing
on a bounded executor, GET /runs/{runId} (status + counts), GET /runs/{runId}/results and
/review-queue. Secure with kdAPIs auth (admin role). Tests mock the Gemini client; add MockMvc tests.
After I approve, run it on the fake sample and show the output.
```
**Done when:** fake sample classifies; c011 (sarcasm) negative; "Tốt." rows positive with no themes;
"Cũng được."/"Bình thường." neutral; c015 (no diacritics) gets teaching:positive + facilities:negative;
c017 and c023 have contains_suggestion=true; mixed rows have per-theme sentiment. Compare with
`sample_gold_fake.csv` (plumbing test only, not a measure of real accuracy).

---
## T5. Evaluation against gold set
**Prompt**
```
Implement evaluation: load a gold CSV (format gold/gold_template.csv; test with sample_gold_fake.csv) and a
run's results; compute per-theme precision/recall/F1, sentiment accuracy and Cohen's kappa, and return
disagreements (comment_id, gold, predicted, evidence). Expose POST /evaluations (admin only) and a compact
summary. Offline unit tests with small hand-made fixtures.
```
**Done when:** you hand-label 150-200 real (anonymised) comments, evaluate, fix codebook/prompt, and repeat
until PLAN.md section 9 targets are met. Then freeze v1 (model, prompt, codebook).

---
## T6. Batch mode (BLOCKED until paid tier is enabled)
**Prompt**
```
Read the Gemini Batch API docs and the Java SDK's Batches service first. Add BATCH mode behind the same
CommentClassifier flow: build one request per comment keyed by comment_id (inline or file input as the docs
recommend), submit the job, persist the job id and run state, poll with a @Scheduled job, download results,
validate with the same schema, and store the same result records. A restart must resume pending runs.
Add a dry-run that builds requests without sending. Mock in tests.
```
**Done when:** a small batch runs on the paid tier with fake data and matches sync results; restart resumes.

---
## T7. Reporting
**Prompt**
```
Implement report aggregation and GET /runs/{runId}/report producing .xlsx (Apache POI, ask before adding;
CSV fallback): share of commenters per theme (note it sums above 100%), sentiment split per theme, breakdown
by cohort, list of actionable suggestions (contains_suggestion), 2-3 evidence quotes per theme, a review-queue
sheet and a 2-5% random sample for spot checks. No AI-written summary yet.
```
**Done when:** report opens correctly in Excel; numbers match a manual check on the sample.

---
## T8. Hardening, docs, cost
**Prompt**
```
Add: cost estimator from stored token usage (price per million tokens from config), retention cleanup of
stored data (retention-days), OpenAPI docs if kdAPIs uses them, README runbook (run a survey end to end, update
the codebook, re-run the gold set). Review error handling, security (authz on every endpoint, upload validation),
logging (no comment text or secrets, including exceptions) and test coverage. List remaining risks.
```
**Done when:** every item in PLAN.md section 12 is checked.
