# Survey Comment Analysis Module: Plan (v2, Java / Spring Boot)

## 1. Goal
Classify free-text Vietnamese student comments into **themes (multi-label)** and **sentiment
(overall + per theme)** with the Gemini API, as a module inside the existing Spring Boot
REST project **kdAPIs**.

## 2. Context and assumptions
- University students answering a school service-quality survey: course content, lecturers,
  infrastructure, support, administration. **Language: Vietnamese only (confirmed).**
- ~50 surveys/year; thousands of participants each; comments optional, so roughly hundreds to
  ~2,000 comments per survey (confirm with real data).
- Results may arrive 1-2 days later, so asynchronous batch processing is acceptable.
- kdAPIs: Java, Spring Boot, REST. Task T0 discovers the exact Java version, build tool,
  conventions, auth, database and test setup. Follow them; do not impose a new style.

## 3. Key decisions
1. **One request per comment** returns both themes and sentiment (enables per-theme sentiment).
2. **Fixed, human-approved codebook** (`codebook.md`). The model may not invent themes;
   `other` exists for genuinely unmatched content.
3. **Multi-label**: max 3 themes per comment, mandatory evidence quote, optional `primary` flag,
   plus `contains_suggestion`.
4. **No embeddings/clustering** at this volume.
5. **Sync mode for development, Batch mode for production.**
6. **Gemini behind an interface** (`CommentClassifier`) so it can be mocked in tests and swapped later.
7. **Asynchronous runs**: uploading a CSV creates a *run*; processing happens in the background;
   clients poll the run.

## 4. Architecture (adapt names to kdAPIs in T0)
Suggested package layout under kdAPIs' base package, e.g. `...surveycomments`:
```
api/        controllers + request/response DTOs
config/     @ConfigurationProperties, beans (Gemini Client, executors)
ingest/     CSV parsing (UTF-8, BOM-safe), validation
clean/      no-content filtering, Unicode NFC normalisation
redact/     rule-based redaction (emails, phones, MSSV, names)
classify/   CommentClassifier interface, GeminiCommentClassifier, prompt builder, schema validation, cache
batch/      Gemini batch job submission, persisted job state, scheduled poller
evaluate/   gold-set metrics
report/     aggregation, xlsx/csv export, review queue
run/        Run entity/state (PENDING, CLEANING, CLASSIFYING, WAITING_BATCH, DONE, FAILED)
```
### Proposed REST endpoints (adapt prefix, versioning, error format and auth to kdAPIs)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/survey-comments/runs` | multipart CSV + `surveyId` + `mode` (SYNC/BATCH) -> `202` + `runId` |
| GET | `/api/survey-comments/runs/{runId}` | status + progress counts |
| GET | `/api/survey-comments/runs/{runId}/results` | classified results (JSON / JSONL) |
| GET | `/api/survey-comments/runs/{runId}/review-queue` | items needing human review (CSV) |
| GET | `/api/survey-comments/runs/{runId}/report` | aggregated report (.xlsx) |
| POST | `/api/survey-comments/discovery` | draft codebook from a cleaned CSV (dev tool) |
| POST | `/api/survey-comments/evaluations` | gold CSV + runId -> metrics + disagreements |
Security: these endpoints handle student data. Use kdAPIs' existing auth and restrict to an admin role.

### Java and library notes (ask before adding any dependency)
- Gemini: official `com.google.genai:google-genai` (look up the latest version; use Gemini
  Developer API with API key from `GEMINI_API_KEY`). Its Batches service covers batch jobs;
  structured output is supported. Read the SDK docs, do not rely on memory.
- Likely helpers: Jackson (already in Spring), a JSON Schema validator, Apache Commons CSV,
  Apache POI (xlsx), `java.text.Normalizer` (NFC), JUnit 5 + Mockito + MockMvc. Reuse what kdAPIs
  already depends on. Rate limiting: simple token bucket or Resilience4j if already present.
- Persist runs/results in kdAPIs' database if it has one (with migrations, only after approval);
  otherwise use the configured storage directory. Decide in T0.
- Background work: bounded thread pool (`@Async` / `TaskExecutor`); batch polling via `@Scheduled`;
  state persisted so a restart can resume.

## 5. Data flow and contracts
```
upload CSV -> ingest -> clean -> redact -> classify (sync | batch) -> validate -> store -> report/evaluate
```
Clean record: `comment_id, survey_id, cohort, comment_text (redacted), redaction_counts, flags`.
Result record: fields of `schema.json` + `run_id, model, prompt_version, codebook_version,
timestamp, usage{input_tokens, output_tokens}`.
- CSV from Excel is often UTF-8 **with BOM** (or other encodings): handle BOM; reject non-UTF-8
  with a clear message.
- Clean step drops no-content comments ("N/A", "Không có ý kiến", "ko co y kien") and normalises to NFC.
  Do NOT de-duplicate identical short comments (two students may both write "Tốt."); only drop
  exact duplicate submissions if a respondent/submission id exists.

## 6. Privacy and data-handling rules
- The current key is **free tier**: Google may use free-tier content to improve its products and
  humans may review it (outside EEA/CH/UK). **Only fake or fully anonymised text while on free tier.**
- Before real student data: (a) paid tier, (b) redaction verified by tests and a manual sample
  check, (c) institution rules on student data checked.
- Redaction happens **before** any API call. Raw uploads are processed in memory and not stored
  (or stored only under an explicit, short retention). Never use an external API/LLM for redaction.
- Always redact: student names, emails, phone numbers (incl. spaced Vietnamese formats and +84),
  MSSV. Staff/lecturer names follow `redact-staff-names` (policy TBD). Vietnamese names are hard
  to detect with rules alone (honorific + capitalised words, optional name list), so rule-based
  redaction is imperfect: hand-check a sample before real data.
- Logs contain ids and counts, never comment text (also in exceptions).
- The API key is never in source, YAML or logs.

## 7. Gemini-specific notes
- Structured output: send the schema (supported subset) and validate every response locally
  against the full `schema.json`.
- Model id from config; pin an exact version; compare a Flash-Lite-class and a Flash-class model
  on the gold set, especially on no-diacritics, teencode and sarcasm rows.
- Temperature: follow the docs' recommendation for the chosen model; verify stability by running
  the gold set twice.
- Free tier has strict rate limits: throttle (`requests-per-minute`) and back off on HTTP 429.
- **Batch API is a paid-tier feature**: T6 is blocked until billing is enabled.
- Record token usage from each response for cost estimation.

## 8. Vietnamese-specific handling
- Keep original text for evidence quotes; never "fix" diacritics. Evidence is copied exactly, not translated.
- Expect missing diacritics, teencode (ko, đc, j...), abbreviations (gv, sv, pđt, ktx), emoticons.
- Lukewarm idioms ("cũng được", "bình thường", "tạm ổn") are neutral. Sarcasm and polite softening are
  common (see codebook language notes).
- Theme ids are English; theme display names and reports are Vietnamese.
- `contains_suggestion` marks actionable feedback ("Mong nhà trường..."); report it separately.

## 9. Evaluation
- Gold set: 150-200 hand-labelled comments (multi-theme), format `gold/gold_template.csv`.
- Metrics: precision/recall/F1 per theme; sentiment accuracy and Cohen's kappa; disagreement list.
- Starting target: sentiment agreement >= 85%; per-theme P/R acceptable to the owner. Judge by whether
  important themes are found, not exact set match.
- Re-run whenever model, prompt or codebook changes.

## 10. Reporting
- Share of *commenters* mentioning each theme (sums above 100%; say so in the report).
- Sentiment split per theme; breakdown by cohort; list of actionable suggestions; trend vs earlier
  surveys when available; 2-3 evidence quotes per theme (from redacted text).
- Review queue: `other`, confidence < 0.6, `needs_human_review`, plus a 2-5% random sample.
- Any AI-written executive summary must be checked against the numbers.

## 11. Non-goals
No UI, no embeddings, no automatic codebook edits, no real student data during development.

## 12. Definition of done (v1)
- [ ] CSV upload -> background run -> results, in SYNC mode, on the fake sample
- [ ] Redaction tests pass (emails, phones, MSSV, names); manual sample check done
- [ ] Gold-set evaluation implemented and meets agreed targets
- [ ] Batch mode works on paid tier, resumes after restart
- [ ] Report + review queue endpoints
- [ ] Endpoints secured with kdAPIs auth; no comment text or secrets in logs
- [ ] README runbook and cost estimator; versions recorded in every result
