# Archi GenAI Assistant (Archi_UI)

An Archi (ArchiMate) plugin that adds a GenAI Assistant view for model generation, explanations, translation, and workspace signals.

## Features

- GenAI Assistant view with embedded UI (`genai-ui/`) and Java bridge.
- Generate: chat-driven model creation (elements, relationships, views) from JSON responses.
- Explain: auto-generates Summary / Simplified / Detailed explanations for the current view or selection.
- Workspace Signals: shows active view, selection count, and validation status (Archi Hammer).
- Translate: translate current view or all views, with preview and undo (element names, notes, group names, view names, label expressions).
- Sequence Diagram: generate PlantUML sequence diagram text from selected nodes and Triggering relationships.

## Requirements

- Java 17 (`Bundle-RequiredExecutionEnvironment: JavaSE-17`)
- Archi dependencies (`com.archimatetool.*`)
- Network access to `https://generativelanguage.googleapis.com/`

## AI Configuration

Configuration precedence: JVM system properties (`-D...`) > environment variables > optional external properties file.

External file (absolute path):
- `-DARCHI_AI_CONFIG=C:\path\ai.properties`

Required keys:
- Translate (Gemini): `TRANSLATE_API_KEY` (fallback `AI_API_KEY`)
- Explain (Gemini): `EXPLAIN_API_KEY` or `GENAI_API_KEY` (fallback `AI_API_KEY`)
- OpenAI: `OPENAI_API_KEY` (fallback `AI_API_KEY`)

Optional endpoints/models:
- `GEMINI_BASE_URL` (default `https://generativelanguage.googleapis.com`)
- `GEMINI_MODEL` (default `gemini-2.0-flash`)
- `OPENAI_BASE_URL` (default `https://api.openai.com/v1`)
- `OPENAI_MODEL` (default `gpt-5.2`)
- Optional full endpoint overrides: `GEMINI_ENDPOINT_URL`, `OPENAI_ENDPOINT_URL`

Eclipse VM arguments example (copy/paste):
```
-DTRANSLATE_API_KEY=YOUR_TRANSLATE_KEY -DEXPLAIN_API_KEY=YOUR_GEMINI_KEY -DOPENAI_API_KEY=YOUR_OPENAI_KEY -DGEMINI_BASE_URL=https://generativelanguage.googleapis.com -DGEMINI_MODEL=gemini-2.0-flash -DOPENAI_BASE_URL=https://api.openai.com/v1 -DOPENAI_MODEL=gpt-5.2
```

Example `ai.properties`:
```
TRANSLATE_API_KEY=YOUR_TRANSLATE_KEY
EXPLAIN_API_KEY=YOUR_GEMINI_KEY
OPENAI_API_KEY=YOUR_OPENAI_KEY
GEMINI_BASE_URL=https://generativelanguage.googleapis.com
GEMINI_MODEL=gemini-2.0-flash
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-5.2
```

## Usage

1. Install as an Eclipse/Archi plugin (PDE import/run supported).
2. Open the view:
   - Window -> Show View -> GenAI Assistant
   - Menu: `AI Tools` -> `AI-Assistant`
3. Generate a sequence diagram:
   - Select two or more nodes in a view
   - Menu: `AI Tools` -> `Sequence Diagram (Triggering)`
4. Generate:
   - Type a request in chat to create elements, relationships, and optionally a view.
5. Explain:
   - Switch to Explain and choose a language; explanation updates from the current context.
6. Translate:
   - Use the language button to translate current view, all views, or undo the last translation.

## Project Structure

- `plugin.xml`: view/command/menu registrations.
- `genai-ui/`: UI assets (HTML/CSS/JS) loaded by `GenAIAssistantView` via SWT Browser.
- `src/com/cwdil/archi/genai/`: chat, explain, workspace signals, model generation.
- `src/com/cwdil/archi/aitranslate/`: translation, preview, undo.
- `images/`: UI icons and avatars.

## Tests (JUnit)

- Tests live in `Archi_UI.tests/` and run as standard JUnit (no PDE runtime required).
- In Eclipse: right-click a test class and use `Run As > JUnit Test`.

## Notes

- Translation undo only tracks the most recent batch.
- If the embedded browser is unavailable, the view shows a fallback message.
