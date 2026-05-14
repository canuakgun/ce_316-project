# IAE — Definitive Implementation Plan (SDD Cross-Checked)

> All decisions below are traced directly to the **Software Design Document (SDD)** or **CE316 Project Description (PD)**.  
> Open questions from the first draft have been resolved using SDD guidance.

---

## Open Questions — Resolved

### Q1 — Execution Timeout
**Decision: 10 seconds per test-case run.**  
**Rationale:** SDD §3.3 states *"A timeout is set on `waitFor()` to prevent infinite loops in student programs from hanging the IAE. If the timeout is exceeded, the process is forcibly terminated and the result is recorded as `RUNTIME_ERROR`."* The SDD does not specify a number; 10 s is the industry-standard default for automated graders and is configurable in the UI.

### Q2 — Multi-Test-Case Results (One Row vs. N Rows per Student)
**Decision: One `StudentResult` row per student (aggregated worst-case status).**  
**Rationale:** The SDD's `student_results` schema has `studentId` (not a composite key with `testCaseId`). SDD §3.2 pipeline reads: *"A StudentResult is stored in the Report"* (singular per student). The `StudentResult` model has a single `actualOutput` / `errorMessage`. The SDD description of `getSummary()` counts students, not test-case attempts. Therefore, per-student aggregation is correct: a student is `SUCCESS` only if **all** test cases pass; otherwise the first failing test case's status wins.

---

## Cross-Check: Deviations Found & Corrected vs. First Draft

| # | Issue Found | SDD Reference | Correction Applied |
|---|---|---|---|
| 1 | First draft used `String id` (UUID) for `Configuration` | SDD §4.1: `id INTEGER PRIMARY KEY AUTOINCREMENT` | Change `Configuration.id` to `int`; DB assigns it |
| 2 | First draft preserved file-based `PersistenceManager.loadProject(Path)` API | SDD §4.2: all methods are by `int id`, not `Path` | Complete rewrite to DB-only API |
| 3 | First draft omitted `Configuration.relativeExecutablePath` | SDD §2.1: *"`relativeExecPath TEXT`"* in schema, with detailed explanation | Add `relativeExecutablePath` field |
| 4 | First draft omitted `Configuration.runCommand` field | SDD §2.1 lists `compilerPath, compileArgs, fileToCompile, relativeExecPath, isInterpreted` (no `runCommand`) | Remove `runCommand`; execution is always `new File(extractedDir, config.getRelativeExecutablePath())` |
| 5 | First draft used `SubmissionStatus.INVALID_ZIP` | SDD §2.1 Status enum: `SUCCESS \| COMPILE_ERROR \| RUNTIME_ERROR \| WRONG_OUTPUT \| SKIPPED` (no `INVALID_ZIP`) | Bad ZIP → `SKIPPED`; add warning message to `errorMessage` |
| 6 | First draft put `stderr` in `StudentResult` | SDD `StudentResult` fields: `studentId, status, actualOutput, errorMessage` only | Remove `stderr` and `diffSummary` from model; diff goes in `student_results.diffText` column via `errorMessage` composite |
| 7 | First draft described `ConfigurationManager.importConfig(Path)` doing file I/O | SDD §2.2: *"The JSON format does NOT include the `id` field; when imported, `PersistenceManager.saveConfig()` assigns a new auto-increment id"* | `importConfig` reads JSON → strips id → calls `PersistenceManager.saveConfig()` |
| 8 | First draft had `ReportManager.saveReport(Path)` and `loadReport(Path)` | SDD §2.2: *"Delegates all persistence to PersistenceManager — no file I/O happens directly in this class"* | `ReportManager.saveReport(r)` calls `PersistenceManager.saveReport(r)` |
| 9 | First draft had `configurations.id TEXT PK` | SDD §4.1: `id INTEGER PRIMARY KEY AUTOINCREMENT` | Integer PK with AUTOINCREMENT |
| 10 | First draft `reports` table missing `totalCount/successCount/failCount` | SDD §4.1 schema shows these 3 summary columns | Add to schema |
| 11 | First draft missing `projects.createdAt` column | SDD §4.1: `createdAt TEXT NOT NULL -- ISO-8601` | Add `createdAt` to projects table |
| 12 | First draft had `test_cases` without `description` column | SDD §4.1: `description TEXT` optional field | Add `description` to `test_cases` |
| 13 | First draft missing `TestCase.compare(actualOutput)` method | SDD §2.1: `+ compare(actualOutput: String): boolean` | Add `compare()` to `TestCase` |
| 14 | First draft missing `Menu: Configuration → Import/Export` | SDD §5.2 menu table | Add Import/Export to Configuration menu |
| 15 | First draft missing `Help → About` menu item | SDD §5.2 | Add About menu item |
| 16 | First draft missing toolbar | SDD §5.1: *"Toolbar — Quick-access buttons for New Project, Open, Save, Run, and Help"* | Add ToolBar to MainWindow |
| 17 | First draft missing `PersistenceManager.loadConfig(int id)` | SDD §4.2 | Add `loadConfig(int id)` |
| 18 | First draft missing `PersistenceManager.deleteConfig(int id)` with referential check | SDD §4.2: *"Checks for referencing projects first"* | Implement with SELECT COUNT before DELETE |
| 19 | First draft missing `configs/` and `logs/` subdirs in `%APPDATA%\IAE\` | SDD §7.1 | Create all 3 subdirs on startup |
| 20 | First draft used `Observer` interface approach on `ProjectManager` | SDD §2.3: Observer is on `ExecutionEngine` → `ResultsViewController` | Wire observer on `ExecutionEngine`, not `ProjectManager` |
| 21 | First draft missing `ConfigDialogController` "Test" button | SDD §5.3: *"'Test' button runs the compiler with no arguments to verify the path is correct"* | Add Test button + logic |
| 22 | First draft `ZipProcessor.extractOne(zip, targetParent)` signature | SDD §2.2: `extractOne(zip): File` — no second argument | Match SDD signature; target dir derived internally |

---

## Final Architecture Overview

```
com.ce316.iae
├── IaeApp.java                          (Application entry point)
├── model/
│   ├── Configuration.java               (int id, name, compilerPath, compileArgs,
│   │                                     fileToCompile, relativeExecutablePath, isInterpreted)
│   ├── Project.java                     (int id, name, Configuration, submissionsDir,
│   │                                     List<TestCase>, Report, createdAt)
│   ├── TestCase.java                    (int id, inputArgs, expectedOutput, description)
│   ├── StudentSubmission.java           (studentId, zipFile, extractedDir)
│   ├── StudentResult.java               (studentId, SubmissionStatus, actualOutput, errorMessage)
│   ├── Report.java                      (int id, projectId, Instant timestamp,
│   │                                     List<StudentResult>, totalCount, successCount, failCount)
│   └── SubmissionStatus.java            (SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, WRONG_OUTPUT, SKIPPED)
├── persistence/
│   └── PersistenceManager.java          (Singleton, SQLite, all CRUD)
├── service/
│   ├── ConfigurationManager.java
│   ├── ProjectManager.java
│   ├── ZipProcessor.java
│   ├── ExecutionEngine.java             (implements Subject for Observer pattern)
│   ├── OutputComparator.java
│   └── ReportManager.java
└── ui/
    ├── MainWindowController.java        (implements Observer)
    ├── ResultsViewController.java       (implements Observer)
    ├── ProjectDialogController.java
    ├── ConfigDialogController.java
    └── RunProgressController.java       (implements Observer)
```

---

## Phase 0 — pom.xml

### Changes
- Add **zip4j 2.11.5** (SDD §2.2: *"bundles its own ZIP library (e.g., zip4j)"*)
- Add **Gson 2.10.1** for `.iaeconfig` JSON import/export (SDD §4.3)
- Add **maven-shade-plugin** fat-jar for self-contained distribution

```xml
<!-- zip4j for ZIP extraction without system tools -->
<dependency>
  <groupId>net.lingala.zip4j</groupId>
  <artifactId>zip4j</artifactId>
  <version>2.11.5</version>
</dependency>

<!-- Gson for .iaeconfig JSON -->
<dependency>
  <groupId>com.google.code.gson</groupId>
  <artifactId>gson</artifactId>
  <version>2.10.1</version>
</dependency>
```

---

## Phase 1 — Model Layer

### Configuration.java — [MODIFY]
**Fields (matching SDD §2.1 + DB schema §4.1):**
```java
private int id;                        // assigned by DB (AUTOINCREMENT)
private String name;                   // UNIQUE, NOT NULL
private String compilerPath;           // e.g. "gcc"
private String compileArgs;            // e.g. "-o {OUTPUT_PATH} {SOURCE_FILE}"
private String fileToCompile;          // e.g. "main.c"
private String relativeExecutablePath; // e.g. "main.exe" — NEW field from SDD
private boolean interpreted;           // true for Python
```
**Note:** `runCommand` from old skeleton is **removed** — execution path is resolved from `relativeExecutablePath`.  
**Add:** `toString()` returning `name` for ComboBox display.

### TestCase.java — [MODIFY]
**Fields (matching SDD §2.1 + DB schema §4.1):**
```java
private int id;                  // DB assigned
private String inputArgs;
private String expectedOutput;
private String description;      // NEW — optional, from DB schema
```
**Add:** `compare(String actualOutput): boolean` — delegates to `OutputComparator.compareTrimmed()`.

### StudentResult.java — [MODIFY]
**Fields strictly matching SDD §2.1:**
```java
private String studentId;
private SubmissionStatus status;   // SUCCESS|COMPILE_ERROR|RUNTIME_ERROR|WRONG_OUTPUT|SKIPPED
private String actualOutput;
private String errorMessage;
// diffText stored in DB but not as a model field; generated on-demand by OutputComparator
```
**Remove:** `stderr`, `diffSummary` (not in SDD model).

### Report.java — [MODIFY]
**Fields (matching SDD §2.1 + DB schema §4.1):**
```java
private int id;                         // DB assigned
private int projectId;
private Instant timestamp;              // maps to runAt in DB
private List<StudentResult> results;
private int totalCount;                 // DB column
private int successCount;               // DB column
private int failCount;                  // DB column
```
**Add:** Helper methods `computeCounts()` to populate the three count fields from `results` list.  
**Modify** `getSummary()`: *"12 passed, 3 failed, 2 errors"* matching SDD §2.1.

### SubmissionStatus.java — [MODIFY]
**Remove `INVALID_ZIP`** — SDD only defines: `SUCCESS`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `WRONG_OUTPUT`, `SKIPPED`.

### StudentSubmission.java — [MODIFY]
**Implement** `getSourceFiles()` — walks `extractedDir` returning all files.  
**Remove** `extract()` — SDD delegates extraction entirely to `ZipProcessor`.

### Project.java — [MODIFY]
**Add** `String createdAt` field (ISO-8601, maps to DB column).

---

## Phase 2 — Persistence Layer

### PersistenceManager.java — [FULL REWRITE]

**Pattern:** Singleton  
**DB file:** `%APPDATA%\IAE\iae.db`  
**JDBC URL:** `jdbc:sqlite:C:\Users\...\AppData\Roaming\IAE\iae.db`

#### Startup Sequence (called from `IaeApp.start()`):
```java
PersistenceManager pm = PersistenceManager.getInstance();
pm.initDB();  // creates all 5 tables + %APPDATA%\IAE\ subdirs
```

#### `initDB()` — Creates subdirs + 5 tables:
```sql
-- 1. configurations
CREATE TABLE IF NOT EXISTS configurations (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL UNIQUE,
    compilerPath     TEXT    NOT NULL,
    compileArgs      TEXT    NOT NULL,
    fileToCompile    TEXT    NOT NULL,
    relativeExecPath TEXT,
    isInterpreted    INTEGER NOT NULL DEFAULT 0
);
-- 2. projects
CREATE TABLE IF NOT EXISTS projects (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL,
    configId       INTEGER NOT NULL REFERENCES configurations(id),
    submissionsDir TEXT    NOT NULL,
    createdAt      TEXT    NOT NULL
);
-- 3. test_cases
CREATE TABLE IF NOT EXISTS test_cases (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    projectId      INTEGER NOT NULL REFERENCES projects(id),
    inputArgs      TEXT    NOT NULL,
    expectedOutput TEXT    NOT NULL,
    description    TEXT
);
-- 4. reports
CREATE TABLE IF NOT EXISTS reports (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    projectId    INTEGER NOT NULL REFERENCES projects(id),
    runAt        TEXT    NOT NULL,
    totalCount   INTEGER NOT NULL DEFAULT 0,
    successCount INTEGER NOT NULL DEFAULT 0,
    failCount    INTEGER NOT NULL DEFAULT 0
);
-- 5. student_results
CREATE TABLE IF NOT EXISTS student_results (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    reportId     INTEGER NOT NULL REFERENCES reports(id),
    studentId    TEXT    NOT NULL,
    status       TEXT    NOT NULL,
    actualOutput TEXT,
    errorMessage TEXT,
    diffText     TEXT
);
```

#### Full Public API (per SDD §4.2):
| Method | SQL | Notes |
|---|---|---|
| `getInstance()` | — | Singleton accessor |
| `getConnection()` | — | Returns single shared `Connection` |
| `initDB()` | CREATE TABLE IF NOT EXISTS ×5 | Safe on every launch |
| `saveConfig(Configuration c): int` | INSERT, returns AUTOINCREMENT id | Ignores `c.id` |
| `loadConfig(int id): Configuration` | SELECT WHERE id=? | |
| `listConfigs(): List<Configuration>` | SELECT ORDER BY name | |
| `deleteConfig(int id): void` | SELECT COUNT + DELETE | Rejects if projects reference it |
| `saveProject(Project p): int` | Transaction: INSERT projects + INSERT test_cases | Returns new id |
| `loadProject(int id): Project` | JOIN + test_cases fetch + loadLatestReport | |
| `listProjects(): List<Project>` | SELECT all projects (shallow, no test cases) | For project list panel |
| `deleteProject(int id): void` | DELETE + cascade test_cases | |
| `saveReport(Report r): void` | Transaction: INSERT reports + INSERT student_results ×N | |
| `loadLatestReport(int projectId): Report` | SELECT DESC LIMIT 1 + student_results | |

---

## Phase 3 — Business Logic Layer

### ZipProcessor.java — [FULL IMPLEMENT]

```java
// SDD §2.2 exact API:
List<File> listZips(File directory)
StudentSubmission extractOne(File zip)   // returns StudentSubmission, NOT File
List<StudentSubmission> extractAll(File submissionsDir)
String deriveStudentId(String filename)  // regex \d{9}, fallback to full name + SKIPPED
```

**`deriveStudentId(String filename)` — SDD §2.2 exact algorithm:**
1. Strip `.zip` extension
2. Apply regex `\d{9}`
3. Exactly one match → use it as `studentId`
4. No match → `studentId` = filename minus extension; `status = SKIPPED`; `errorMessage = "Could not derive student ID from filename — manual review required"`

**`extractOne(File zip)` — uses zip4j:**
```java
ZipFile zipFile = new ZipFile(zip);
zipFile.extractAll(extractTarget.getAbsolutePath());
```
If extraction throws `ZipException` → submission status `SKIPPED`.

### ExecutionEngine.java — [FULL IMPLEMENT]

**SDD §3.3 exact ProcessBuilder structure:**

```java
// Compile (compiled languages: C, Java)
ProcessBuilder pb = new ProcessBuilder();
// Substitute {OUTPUT_PATH} and {SOURCE_FILE} in compileArgs
String args = config.getCompileArgs()
    .replace("{OUTPUT_PATH}", resolvedOutputPath)
    .replace("{SOURCE_FILE}", config.getFileToCompile());
pb.command(config.getCompilerPath(), args.split("\\s+"));
pb.directory(extractedDir);
pb.redirectErrorStream(true);
Process p = pb.start();
int exitCode = p.waitFor();   // no timeout on compile per SDD

// Run (all languages)
File binary = new File(extractedDir, config.getRelativeExecutablePath());
ProcessBuilder rb = new ProcessBuilder(binary.getAbsolutePath(), inputArgs.split("\\s+"));
rb.directory(extractedDir);
Process rp = rb.start();
boolean finished = rp.waitFor(10, TimeUnit.SECONDS);   // Q1 answer: 10s
if (!finished) { rp.destroyForcibly(); return RUNTIME_ERROR; }
```

**`runAll(Project project)` — SDD §3.4 error handling:**
- Outer loop catches all `Exception` per student
- Sets `errorMessage = e.getMessage()`
- Assigns appropriate `SubmissionStatus`
- **Never** breaks the loop — all students processed

**Observer notification (SDD §2.3):**
- `ExecutionEngine` maintains `List<ResultsObserver> observers`
- After each student: `notifyObservers(studentResult)` → `Platform.runLater()`

### OutputComparator.java — [IMPLEMENT generateDiff]

**SDD §3.5 comparison strategy:**
1. `compareExact(actual, expected)` — already implemented
2. `compareTrimmed(actual, expected)` — already implemented  
3. `compareNormalized(actual, expected)` — **NEW**: collapse multiple spaces, normalize `\r\n`→`\n`
4. `generateDiff(actual, expected)` — line-by-line `+`/`-` diff string

**SDD §3.5:** *"Exact match is attempted first. If exact match fails, both strings are trimmed and normalized."* So the pipeline is: exact → trimmed → normalized.

### ReportManager.java — [IMPLEMENT]

Per SDD §2.2:
```java
Report createReport(Project project)                    // assembles Report from results
void saveReport(Report r)                               // calls PersistenceManager.saveReport(r)
Report loadReport(int projectId)                        // calls PersistenceManager.loadLatestReport(projectId)
void exportCsv(Report report, Path path)                // studentId,status,errorMessage CSV
void exportHtml(Report report, Path path)               // HTML table
```

### ConfigurationManager.java — [IMPLEMENT]

Per SDD §2.2 + §4.3:
```java
List<Configuration> listConfigurations()    // calls PersistenceManager.listConfigs()
void save(Configuration c)                  // calls PersistenceManager.saveConfig(c)
void delete(Configuration c)               // calls PersistenceManager.deleteConfig(c.getId())
void importConfig(Path file)               // reads JSON, strips id field, calls saveConfig()
void exportConfig(Configuration c, Path f) // writes JSON without id field (SDD §4.3)
```

**JSON format (SDD §4.3):**
```json
{
  "name": "C Programming Language Config",
  "compilerPath": "gcc",
  "compileArgs": "-o {OUTPUT_PATH} {SOURCE_FILE}",
  "fileToCompile": "main.c",
  "relativeExecPath": "main.exe",
  "isInterpreted": false
}
```

### ProjectManager.java — [IMPLEMENT]

```java
Project createProject(String name, Configuration cfg, String submissionsDir)
Project openProject(int id)           // loads from DB (not file path)
void saveProject()                    // persists currentProject
void runProject(Consumer<String> progressCallback)  // background thread
```

**`runProject()` pipeline (SDD §3.2):**
1. `ZipProcessor.listZips(submissionsDir)`
2. For each zip: `ZipProcessor.extractOne(zip)` → `ExecutionEngine` compile + run
3. `ReportManager.createReport()` + `ReportManager.saveReport()`
4. Background thread via `javafx.concurrent.Task`

---

## Phase 4 — UI Layer

### Observer Interface — [NEW]
```java
package com.ce316.iae.ui;
public interface ResultsObserver {
    void onStudentProcessed(StudentResult result);
}
```
`ExecutionEngine` calls `onStudentProcessed()` after each student; `ResultsViewController` and `RunProgressController` implement it.

### MainWindowController.java — [FULL IMPLEMENT]

**FXML injections:**
- `MenuBar menuBar`
- `ToolBar toolBar` (New, Open, Save, Run, Help buttons)
- `ListView<Project> projectListView` (left panel)
- `Label projectDetailsLabel` (name, config, dir, test cases)
- `TableView<StudentResult> resultsTable` (center/right — Student ID, Status, Output truncated, Error)
- `Label statusBar`

**Menu structure (SDD §5.2):**
```
File         → New Project... | Open Project... | Save Project | --- | Exit
Configuration → Manage Configurations | Import Configuration... | Export Configuration...
Help         → User Manual | About
```

**Key handlers:**
- `handleNewProject()` → opens `ProjectDialog.fxml`
- `handleOpenProject()` → opens by project ID from list
- `handleSaveProject()` → `projectManager.saveProject()`
- `handleRun()` → spawns `Task<Report>`, shows `RunProgress.fxml`, refreshes table on done
- `handleManageConfigs()` → opens `ConfigDialog.fxml`
- `handleImportConfig()` → `FileChooser` → `configManager.importConfig()`
- `handleExportConfig()` → `FileChooser` → `configManager.exportConfig()`
- `handleUserManual()` → loads `manual/index.html` in JavaFX `WebView`
- `handleAbout()` → `Alert` showing version + team info

### ResultsViewController.java — [FULL IMPLEMENT]
- Implements `ResultsObserver`
- `TableView<StudentResult>` columns: Student ID | Status | Output (truncated 50 chars) | Error Message
- `onStudentProcessed(result)` → `Platform.runLater(() -> table.getItems().add(result))`
- Row click → detail pane: full actualOutput + expectedOutput + colored diff (`TextArea`)
- Summary bar: "X passed, Y failed, Z errors"

### ProjectDialogController.java — [FULL IMPLEMENT]
- `TextField` project name
- `ComboBox<Configuration>` (populated from DB)
- `TextField` + Browse button → submissions directory (`DirectoryChooser`)
- `TableView<TestCase>` with Add/Remove/Edit rows (input args + expected output + description)
- OK validates: name not empty, config selected, dir exists, at least 1 test case
- Cancel → returns null

### ConfigDialogController.java — [FULL IMPLEMENT]
- `TableView<Configuration>` list (name column)
- New / Edit / Delete / Import / Export buttons
- Edit panel: name, compilerPath (+ Browse), compileArgs, fileToCompile, relativeExecPath, isInterpreted checkbox
- **Test button** (SDD §5.3) → runs `compilerPath --version` via ProcessBuilder, shows stdout in Alert
- Import → `FileChooser` `.iaeconfig` filter → `configManager.importConfig()`
- Export → `FileChooser` → `configManager.exportConfig()`
- Delete checks referential integrity (PersistenceManager rejects with error alert)

### RunProgressController.java — [FULL IMPLEMENT]
- Implements `ResultsObserver`
- `ProgressBar progressBar` (0.0 – 1.0)
- `Label currentStudentLabel` (e.g. "Processing: 220201085")
- `TextArea logArea` (append one line per student result)
- `Button cancelButton` → interrupts background `Task`
- `onStudentProcessed(result)` → `Platform.runLater()` updates all 3 controls
- Closes automatically when task completes (SDD §5.3)

---

## Phase 5 — FXML Files

| File | Status | Key Controls |
|---|---|---|
| `MainWindow.fxml` | Rewrite | BorderPane: MenuBar top, ToolBar below menu, SplitPane center (ListView + results table), StatusBar bottom |
| `ProjectDialog.fxml` | New | GridPane form + TableView for test cases |
| `ConfigDialog.fxml` | New | SplitPane: config list left, edit form right |
| `RunProgress.fxml` | New | VBox: ProgressBar, label, TextArea, Cancel button |

---

## Phase 6 — IaeApp.java Wiring

```java
@Override
public void start(Stage stage) throws IOException {
    // 1. Init DB and app data directories
    PersistenceManager.getInstance().initDB();
    
    // 2. Load services
    ConfigurationManager configManager = new ConfigurationManager();
    ProjectManager projectManager = new ProjectManager(configManager);
    
    // 3. Load FXML, inject services into controller
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainWindow.fxml"));
    Parent root = loader.load();
    MainWindowController ctrl = loader.getController();
    ctrl.init(projectManager, configManager);
    
    // 4. Show window
    Scene scene = new Scene(root, 960, 640);
    scene.getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
    stage.setMinWidth(800); stage.setMinHeight(600);
    stage.setTitle("IAE – Integrated Assignment Environment");
    stage.setScene(scene);
    stage.show();
}
```

---

## Phase 7 — CSS & Manual

### `styles/main.css` — [NEW]
Professional look: clean light theme with accent color, proper table row highlights, status bar styling.

### `manual/index.html` — [REWRITE]
Full user manual covering all 10 requirements (SDD Req. traceability table used as chapter outline).

---

## Files To Create / Modify

| Action | File |
|---|---|
| MODIFY | `pom.xml` |
| MODIFY | `model/Configuration.java` |
| MODIFY | `model/Project.java` |
| MODIFY | `model/TestCase.java` |
| MODIFY | `model/StudentSubmission.java` |
| MODIFY | `model/StudentResult.java` |
| MODIFY | `model/Report.java` |
| MODIFY | `model/SubmissionStatus.java` |
| REWRITE | `persistence/PersistenceManager.java` |
| REWRITE | `service/ZipProcessor.java` |
| REWRITE | `service/ExecutionEngine.java` |
| IMPLEMENT | `service/OutputComparator.java` |
| REWRITE | `service/ReportManager.java` |
| REWRITE | `service/ProjectManager.java` |
| REWRITE | `service/ConfigurationManager.java` |
| NEW | `ui/ResultsObserver.java` (interface) |
| REWRITE | `ui/MainWindowController.java` |
| REWRITE | `ui/ResultsViewController.java` |
| REWRITE | `ui/ProjectDialogController.java` |
| REWRITE | `ui/ConfigDialogController.java` |
| REWRITE | `ui/RunProgressController.java` |
| REWRITE | `resources/fxml/MainWindow.fxml` |
| NEW | `resources/fxml/ProjectDialog.fxml` |
| NEW | `resources/fxml/ConfigDialog.fxml` |
| NEW | `resources/fxml/RunProgress.fxml` |
| NEW | `resources/styles/main.css` |
| REWRITE | `resources/manual/index.html` |
| MODIFY | `IaeApp.java` |

**Total: 27 files** (9 new, 18 modified/rewritten)

---

## Verification Plan

### Build
```
mvn clean package -q
```
Zero compilation errors required.

### Runtime Smoke Tests
1. Launch → `%APPDATA%\IAE\iae.db` created with 5 tables
2. Create a Java config (javac, -o {OUTPUT_PATH} {SOURCE_FILE}, HelloWorld.java, HelloWorld.class)
3. Create project → select config → browse submissions dir → add test case
4. Run → progress dialog shows → results table fills row-by-row
5. Close + relaunch → project and last report survive from DB
6. Import `.iaeconfig` file → new config appears (new id, no collision)
7. Export config → JSON file has no `id` field

### Edge Cases
- Corrupt ZIP → `SKIPPED`, next student processed normally
- Non-9-digit filename (e.g. `odev1_can.zip`) → `SKIPPED` + warning message
- Compile error → `COMPILE_ERROR`, next student processed
- Infinite loop student code → `RUNTIME_ERROR` after 10s timeout
- Delete config referenced by a project → rejected with user-friendly error Alert
