# Agent rules for the survey-comments module (Java / Spring Boot)

Read this file and `docs/PLAN.md` at the start of EVERY task.

1. **Follow kdAPIs.** Match its Java version, build tool (Maven/Gradle), package naming,
   layering (controller/service/repository), DTO style, exception handling (e.g. @ControllerAdvice),
   validation, logging, formatting tools (Spotless/Checkstyle if present) and test setup.
   Read existing modules before writing new code. Keep the new code in its own package.
2. **One task at a time.** Do only the task requested. Present a short plan first and wait
   for approval before writing code or running commands.
3. **Never edit** `codebook.md`, `schema.json`, `docs/PLAN.md` or this file. Propose changes in
   `docs/CHANGE_REQUESTS.md` with the reason.
4. **Secrets.** Never read, print, log or commit `.env`, `application-local.*` or any file holding
   the API key. The key comes only from the `GEMINI_API_KEY` environment variable. Never put it
   in code, YAML, test fixtures, prompts or chat.
5. **Data.** Use only the fake files in `src/test/resources/survey-comments/` unless the owner says
   otherwise. Never send unredacted text to any API. Never log comment text (ids and counts only),
   including in exceptions and debug logs.
6. **Do not guess APIs.** Read the current Google docs and the Google Gen AI Java SDK
   documentation (github.com/googleapis/java-genai) before writing Gemini code. Look up the
   latest released SDK version on Maven Central instead of using a remembered number.
   Say which pages you used.
7. **Ask before** adding or upgrading dependencies (list each with the reason), changing shared
   kdAPIs classes/config, adding database migrations, opening network connections, or running
   builds/commands that change the environment.
8. **Security.** New endpoints must use kdAPIs' existing authentication/authorization and be
   restricted to an admin-type role. Validate uploads (size, type, UTF-8 with or without BOM).
9. **Tests.** Every task adds JUnit 5 tests that pass without network access (mock the
   Gemini client behind an interface). `mvn test` / `gradlew test` must pass.
10. **Small, reviewable changes.** At the end of each task summarise what changed, how to run it,
    and what is still uncertain.
