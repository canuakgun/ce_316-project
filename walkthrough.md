# IAE — Full User Guide

## What is IAE?

The **Integrated Assignment Environment** automates lecturer grading workflows:

1. Students submit source-code **ZIP files** named `STUDENTID.zip` (e.g. `20230602024.zip`).
2. IAE **extracts**, **compiles**, and **runs** each submission against your **test cases**.
3. It compares the program's stdout to your expected output and produces a **pass/fail report**.

---

## Step 0 — First Launch

On first launch, IAE automatically seeds **4 built-in language configurations**:

| Name | Compiler | Compiled? | Source file |
|---|---|---|---|
| **C Language** | `gcc` | ✅ Yes | `main.c` |
| **C++ Language** | `g++` | ✅ Yes | `main.cpp` |
| **Python 3** | `python` | ❌ Interpreted | `main.py` |
| **Java (Single-File)** | `java` | ❌ Interpreted (Java 11+) | `Main.java` |

> [!NOTE]
> `gcc` and `g++` must be on your system `PATH` (e.g. via MinGW on Windows). Python and Java must also be on PATH.

---

## Step 1 — Manage Configurations

> **Menu → Configuration → Manage Configurations**

Configurations tell IAE *how* to compile and run a language.

### Built-in C config (ready to use)
| Field | Value |
|---|---|
| Name | `C Language` |
| Compiler Path | `gcc` |
| Compile Args | `-o {OUTPUT_PATH} {SOURCE_FILE}` |
| Source File | `main.c` |
| Output Binary | `output` |
| Interpreted | ☐ |

### Creating a custom config (example: C with sanitizers)
1. Click **New** on the left panel.
2. Fill in:
   - **Name:** `C Language (ASAN)`
   - **Compiler Path:** `gcc`
   - **Compile Args:** `-o {OUTPUT_PATH} {SOURCE_FILE} -fsanitize=address`
   - **Source File:** `main.c`
   - **Output Binary:** `output`
3. Click **Test** — a dialog shows `gcc --version` output to confirm the path works.
4. Click **Save Configuration**.

### Placeholders in Compile Args
| Placeholder | Expands to |
|---|---|
| `{SOURCE_FILE}` | Absolute path to the student's source file |
| `{OUTPUT_PATH}` | Absolute path where the compiled binary should be placed |

### Import / Export configs
Use **⬇ Export…** to save a `.iaeconfig` JSON file and share it with a colleague.  
Use **⬆ Import…** to load that file on another machine — it gets a fresh database ID automatically.

---

## Step 2 — Prepare Student Submissions

Submissions must be **ZIP files** named with the **11-digit student ID**:

```
submissions/
  ├── 20230602024.zip   ← contains main.c
  ├── 20230602025.zip   ← contains main.c
  └── 20230602026.zip
```

Each ZIP **must contain** the source file that matches the configuration  
(e.g. `main.c` for "C Language"). The zip may have subdirectories; IAE will search for the file.

> [!IMPORTANT]
> ZIP files named anything other than an 11-digit number (e.g. `homework1.zip`) will be marked as **SKIPPED** — this is by design.

---

## Step 3 — Create a Project

> **Toolbar → ⊕ New Project** (or **File → New Project…**)

Fill in the **Project Setup** dialog:

| Field | Example |
|---|---|
| **Project Name** | `HW1 — Hello World` |
| **Configuration** | `C Language` |
| **Submissions Dir** | `C:\Desktop\HW1_Submissions` (click Browse) |

### Add Test Cases

Click **+ Add** to add a row. Double-click a cell to edit inline.

| Input Args | Expected Output | Description |
|---|---|---|
| *(empty)* | `Hello, World!` | Basic output check |
| `Alice` | `Hello, Alice!` | Name argument |
| `42` | `Result: 42` | Number argument |

> [!TIP]
> **Input Args** are passed as command-line arguments to the student's program.  
> **Expected Output** is the exact stdout the program must produce (trailing newline is trimmed during comparison).

Click **Create Project** — the project is saved and appears in the **left sidebar**.

---

## Step 4 — Run the Project

1. Select the project in the sidebar (left panel) — it becomes highlighted.
2. Click **▶ Run Project** in the toolbar.
3. The **Run Progress** dialog opens, showing:
   - A **progress bar** (determinate — shows `X/Y`).
   - A **live log** with a status icon per student.
4. When finished, the dialog closes and the **Results table** is populated.

---

## Step 5 — Interpret Results

The results table uses colour coding:

| Colour | Status | Meaning |
|---|---|---|
| 🟢 Green | `SUCCESS` | All test cases passed |
| 🔴 Red | `COMPILE_ERROR` or `WRONG_OUTPUT` | Compile failed or output didn't match |
| 🟠 Orange | `RUNTIME_ERROR` | Program crashed or timed out |
| 🟡 Yellow | `SKIPPED` | ZIP couldn't be associated with a student ID |

Click any row to expand the **Detail** pane at the bottom — it shows:
- The **actual output** the student's program produced.
- A **diff** (`-` expected, `+` actual) showing exactly where the output diverged.
- The **error message** (compiler stderr, exception message, or skip reason).

---

## Step 6 — Edit or Delete a Project

Select a project in the sidebar, then click:

- **✎ Edit** — Opens the project dialog pre-filled. Change name, config, directory, or test cases. Click **Save Changes**.
- **✕ Delete** — A confirmation dialog asks before permanently deleting the project **and all its run reports**.

---

## Typical Workflow Example

```
1. Configuration → Manage Configurations → verify "C Language" is present
2. ⊕ New Project
     Name: "Assignment 1 — Calculator"
     Config: C Language
     Submissions Dir: C:\HW1\submissions
     Test cases:
       Args: "3 + 4"    Expected: "7"
       Args: "10 - 6"   Expected: "4"
3. ▶ Run Project
4. Review results:
     20230602024 → ✅ SUCCESS
     20230602025 → ❌ WRONG_OUTPUT  (got "7.0" instead of "7")
     20230602026 → ✗ COMPILE_ERROR  (see Detail pane for stderr)
     20230602027 → — SKIPPED  (ZIP was named incorrectly)
5. ✎ Edit project to add/fix a test case, then re-run
```

---

## Tips & Troubleshooting

| Problem | Solution |
|---|---|
| `COMPILE_ERROR` for all students | Click a row → check Detail pane. Often `gcc not found` → add gcc to PATH. |
| Run button fires the pipeline multiple times | Fixed in latest version — each click is now guarded. |
| Progress bar shows 0% | Make sure the submissions directory exists and contains `.zip` files. |
| Config dialog Save button stays visible when no config is selected | Fixed — the whole form now hides correctly. |
| Student ZIP is SKIPPED | Rename the ZIP to exactly `STUDENTID.zip` (11 digits, no other chars). |
| Running the same project twice shows duplicate rows | Fixed — observer is now cleaned up after each run. |

---

## Database Location

IAE stores everything (projects, configs, reports) in a local SQLite database:

```
%APPDATA%\IAE\iae.db
```

You can open this with any SQLite viewer (e.g. **DB Browser for SQLite**) to inspect data directly.
