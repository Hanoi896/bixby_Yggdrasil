---
trigger: always_on
---

# Master Agent Rules for Bixby Capsule Development

## 1. Decision Tree Before Writing Code (Execution Hierarchy)
Before writing or modifying any code, stop and evaluate the request against this hierarchy in order:
1. **Does this need to exist?** -> If NO: Skip it (YAGNI).
2. **Already in this codebase?** -> If YES: Reuse existing modules. Do not rewrite.
3. **Standard Library does it?** -> If YES: Use standard built-in functions.
4. **Native platform feature?** -> If YES: Use Bixby native platform features/APIs.
5. **Installed dependency?** -> If YES: Use existing libraries already in the project.
6. **One line solution?** -> If YES: Implement in a single line.
7. **Only then:** Write the minimum required custom implementation that works.

---

## 2. Bixby Capsule Architecture & Folder Structure
Always strictly adhere to the Bixby Developer Studio conventions:

- `models/concepts/primitive/`: Primitive concepts (e.g., `text`, `integer`, `boolean`, `enum`).
- `models/concepts/structures/`: Complex structure concepts containing multiple properties.
- `models/actions/`: Action models defining inputs, outputs, and action types (`Search`, `Calculation`, `Fetch`, `BeginTransaction`, etc.).
- `code/`: JavaScript implementation files for business logic and API connections.
- `resources/base/endpoints.bxb`: Mapping configuration between Actions and JavaScript functions.
- `resources/ko-KR/` (or `resources/base/`):
  - `capsule.bxb`: Capsule configuration and permissions.
  - `views/`: Layout and UI rendering files (`.view.bxb`).
  - `dialog/`: Natural language response templates (`.dialog.bxb`).
  - `training/`: Natural language training samples and tagging.

---

## 3. Naming Conventions & Coding Standards
- **Concept Names**: PascalCase (e.g., `UserLocation`, `SearchResult`).
- **Action Names**: PascalCase, preferably starting with a verb (e.g., `GetWeatherInfo`, `SearchRestaurants`).
- **Property/Variable Names**: camelCase (e.g., `userName`, `itemCount`).
- **JavaScript Code**:
  - Use ES6+ features supported by the Bixby runtime.
  - Keep functions pure, modular, and concise.
  - Always handle API edge cases and wrap external HTTP requests (`http` library) with robust error handling.

---

## 4. Bixby-Specific Best Practices
- **No Direct UI Rendering in Logic**: Business logic (`code/*.js`) must only compute and return data concepts. All presentation logic must reside in Bixby Views (`.view.bxb`).
- **Minimal Inputs**: Keep action inputs minimal to avoid prompting the user unnecessarily during voice execution.
- **Strict Endpoint Mapping**: Whenever creating a new Action, immediately pair it with a corresponding entry in `endpoints.bxb` and create the Javascript handler file.

---

## 5. Git & Workspace Hygiene
- Do not commit local build artifacts, cache folders, or temporary files (`.bixby/`, `build/`, `node_modules/`, `.DS_Store`).
- Follow conventional commits (`feat:`, `fix:`, `docs:`, `refactor:`, `style:`).