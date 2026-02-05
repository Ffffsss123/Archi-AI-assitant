package com.cwdil.archi.aitranslate.commands;

import java.util.ArrayList;
import java.util.List;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;


import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import com.archimatetool.editor.model.commands.EObjectFeatureCommand;
import com.archimatetool.editor.model.commands.FeatureCommand;

import com.archimatetool.editor.diagram.DiagramEditorInput;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;
import com.cwdil.archi.aitranslate.ui.TranslationPreviewDialog;
import com.archimatetool.model.IDiagramModelContainer;
import com.cwdil.archi.aitranslate.TranslationHistory;
import com.cwdil.archi.aitranslate.ui.TranslationLanguageDialog;
import com.archimatetool.model.IFeature;
import com.archimatetool.model.IFeatures;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IArchimateRelationship;
import com.cwdil.archi.genai.config.GeminiApiConfig;
import com.cwdil.archi.genai.config.SecretProvider;

/**
 * Handler: collect texts in current view, run fake "AI" translation,
 * show preview dialog, then write back to EMF model.
 */
public class TranslateViewHandler extends AbstractHandler {
    private static final String TRANSLATE_API_KEY = "TRANSLATE_API_KEY";
    private static final String COMMON_API_KEY = "AI_API_KEY";

    // Simple enum to mark which property gets updated.
    public enum PropertyKind {
        ELEMENT_NAME,
        NOTE_CONTENT,
        GROUP_NAME,
        DIAGRAM_NAME,
        LABEL_EXPRESSION,
        DOCUMENTATION
    }

    // Data model for a translatable item.
    public static class TranslatableItem {
        private String id;
        private String kind;
        private String original;
        private String translated;
        private final EObject owner;
        private final PropertyKind propertyKind;

        TranslatableItem(String id, String kind, String original,
                         EObject owner, PropertyKind propertyKind) {
            this.setId(id);
            this.setKind(kind);
            this.setOriginal(original);
            this.owner = owner;
            this.propertyKind = propertyKind;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getOriginal() {
            return original;
        }

        public void setOriginal(String original) {
            this.original = original;
        }

        public String getTranslated() {
            return translated;
        }

        public void setTranslated(String translated) {
            this.translated = translated;
        }

        public EObject getOwner() {
            return owner;
        }

        public PropertyKind getPropertyKind() {
            return propertyKind;
        }
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // 1. Get the active editor.
        IEditorPart editor = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage()
                .getActiveEditor();

        if (editor == null) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "No active editor.");
            return null;
        }

        IEditorInput input = editor.getEditorInput();
        if (!(input instanceof DiagramEditorInput dei)) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "Active editor is not a diagram editor.");
            return null;
        }

        IDiagramModel diagram = dei.getDiagramModel();
        if (diagram == null) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "No diagram model available.");
            return null;
        }

        // 2. Collect translatable text from the current view.
        List<TranslatableItem> items = collectItems(diagram);
        if (items.isEmpty()) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "No translatable texts were found.");
            return null;
        }

        TranslationLanguageDialog langDialog = new TranslationLanguageDialog(Display.getDefault().getActiveShell(), "zh-CN");
        if (langDialog.open() != TranslationLanguageDialog.OK) {
            return null;
        }

        String targetLang = langDialog.getTargetLang();

        // 3. Call AI translation.
        try {
            translateWithGemini(items, targetLang);
        } catch (Exception ex) {
            ex.printStackTrace();
            MessageDialog.openError(Display.getDefault().getActiveShell(), "AI Translate", "AI Error: " + ex.getMessage() + "\nUsing fake translation.");
            fakeTranslate(items);
        }

        // 4. Preview dialog.
        TranslationPreviewDialog dlg = new TranslationPreviewDialog(Display.getDefault().getActiveShell(), items);
        if (dlg.open() != TranslationPreviewDialog.OK) {
            return null;
        }

        // 5. Get the CommandStack for undo support
        CommandStack commandStack = (CommandStack) diagram.getAdapter(CommandStack.class);

        // 6. Write back to the model.
        TranslationHistory.record(items);
        applyTranslations(items, commandStack);

        // --- Key refresh step ---
        // Force refresh so dynamic labels like $view{name} update.
        refreshEditor(editor); 
        // ---------------------------

        return null;
    }

    /**
     * Traverse the diagram and collect element names, note content, and group names.
     */
    protected List<TranslatableItem> collectItems(IDiagramModel diagram) {
        List<TranslatableItem> list = new ArrayList<>();
        int[] counter = new int[]{1};
        
        if (diagram.getName() != null && !diagram.getName().isBlank()) {
            String id = String.valueOf(counter[0]++);
            list.add(new TranslatableItem(
                    id,
                    "Diagram Name",
                    diagram.getName(),
                    diagram, // owner is the diagram itself
                    PropertyKind.DIAGRAM_NAME
            ));
        }

        for (Object obj : diagram.getChildren()) {
            if (obj instanceof IDiagramModelObject dmo) {
                collectFromObjectRecursive(dmo, list, counter);
            }
        }
        return list;
    }

    protected void collectFromObjectRecursive(IDiagramModelObject dmo,
                                            List<TranslatableItem> list,
                                            int[] counter) {

        // 1) ArchiMate element names for diagram objects.
        if (dmo instanceof IDiagramModelArchimateObject archiObj) {
            IArchimateElement el = archiObj.getArchimateElement();
            if (el != null) {
                // 1a. Element Name
                String name = el.getName();
                if (name != null && !name.isBlank()) {
                    String id = String.valueOf(counter[0]++);
                    list.add(new TranslatableItem(
                            id,
                            el.eClass().getName(),  // type name, e.g. ApplicationComponent
                            name,
                            el,
                            PropertyKind.ELEMENT_NAME
                    ));
                }
                
                // 1b. Element Documentation
                String doc = el.getDocumentation();
                if (doc != null && !doc.isBlank()) {
                    list.add(new TranslatableItem(
                            String.valueOf(counter[0]++),
                            el.eClass().getName() + " Doc",
                            doc,
                            el,
                            PropertyKind.DOCUMENTATION
                    ));
                }
            }
        }

        // 2) Handle connections/relationships from this node
        for (IDiagramModelConnection conn : dmo.getSourceConnections()) {
            if (conn instanceof IDiagramModelArchimateConnection archiConn) {
                IArchimateRelationship rel = archiConn.getArchimateRelationship();
                if (rel != null) {
                    // 2a. Relationship Name
                    String relName = rel.getName();
                    if (relName != null && !relName.isBlank()) {
                        list.add(new TranslatableItem(
                                String.valueOf(counter[0]++),
                                "Relationship Name",
                                relName,
                                rel,
                                PropertyKind.ELEMENT_NAME
                        ));
                    }

                    // 2b. Relationship Documentation
                    String relDoc = rel.getDocumentation();
                    if (relDoc != null && !relDoc.isBlank()) {
                        list.add(new TranslatableItem(
                                String.valueOf(counter[0]++),
                                "Relationship Doc",
                                relDoc,
                                rel,
                                PropertyKind.DOCUMENTATION
                        ));
                    }
                }
                
                // 2c. Connection Label Expression
                for (IFeature feature : conn.getFeatures()) {
                    if ("labelExpression".equals(feature.getName())) {
                        String val = feature.getValue();
                        // Only translate if not a system variable like ${type}
                        if (val != null && !val.isBlank() && !val.contains("${")) {
                            list.add(new TranslatableItem(
                                    String.valueOf(counter[0]++),
                                    "Conn Label",
                                    val,
                                    conn,
                                    PropertyKind.LABEL_EXPRESSION
                            ));
                        }
                    }
                }
            }
        }

        // 3) Note content
        if (dmo instanceof IDiagramModelNote note) {
            String content = note.getContent();
            if (content != null && !content.isBlank()) {
                String id = String.valueOf(counter[0]++);
                list.add(new TranslatableItem(
                        id,
                        "Note",
                        content,
                        note,
                        PropertyKind.NOTE_CONTENT
                ));
            }
        }

        // 4) Group name and documentation
        if (dmo instanceof IDiagramModelGroup group) {
            String name = group.getName();
            if (name != null && !name.isBlank()) {
                list.add(new TranslatableItem(
                        String.valueOf(counter[0]++),
                        "Group",
                        name,
                        group,
                        PropertyKind.GROUP_NAME
                ));
            }
            
            String doc = group.getDocumentation();
            if (doc != null && !doc.isBlank()) {
                list.add(new TranslatableItem(
                        String.valueOf(counter[0]++),
                        "Group Doc",
                        doc,
                        group,
                        PropertyKind.DOCUMENTATION
                ));
            }
        }
        
        // 5) Check label expressions on objects
        // IDiagramModelObject exposes features via IFeatures.
        for (IFeature feature : dmo.getFeatures()) {
            if ("labelExpression".equals(feature.getName())) {
                String val = feature.getValue();
                if (val != null && !val.isBlank()) {
                    String id = String.valueOf(counter[0]++);
                    list.add(new TranslatableItem(
                            id,
                            "Label Exp",
                            val,
                            dmo,
                            PropertyKind.LABEL_EXPRESSION
                    ));
                }
            }
        }

        if (dmo instanceof IDiagramModelContainer container) {
            for (IDiagramModelObject child : container.getChildren()) {
                collectFromObjectRecursive(child, list, counter);
            }
        }
    }

    /**
     * Placeholder "AI translation": use a simple transform for now.
     * Example: append " [T]" or apply another visible change.
     */
    protected void fakeTranslate(List<TranslatableItem> items) {
        for (TranslatableItem item : items) {
            if (item.getOriginal() == null || item.getOriginal().isBlank()) {
                item.setTranslated(item.getOriginal());
            } else {
                // Simple demo: reverse the string and add a marker.
                StringBuilder sb = new StringBuilder(item.getOriginal());
                sb.reverse();
                item.setTranslated(sb.toString() + " [T]");
                // Replace with a real AI call later.
            }
        }
    }
    
    /**
     * Use Gemini to translate item originals into targetLang.
     * Simplified: model returns one line per translation, then map line-by-line.
     */
    protected void translateWithGemini(List<TranslatableItem> items,
            String targetLang) throws Exception {

String apiKey = SecretProvider.getWithFallback(TRANSLATE_API_KEY, COMMON_API_KEY);
if (apiKey == null || apiKey.isBlank()) {
throw new IllegalStateException(
        SecretProvider.missingKeyMessage(TRANSLATE_API_KEY, COMMON_API_KEY));
}

// Use a placeholder for newlines to preserve line structure.
final String NL_PLACEHOLDER = "___NEWLINE___";

StringBuilder prompt = new StringBuilder();
prompt.append("You are a translation engine.\n")
.append("Translate the following UI labels to ").append(targetLang).append(".\n")
.append("IMPORTANT RULES:\n")
.append("1. Return ONLY the translated labels, one per line.\n")
.append("2. Maintain the exact same order.\n")
.append("3. Do not translate text inside ${} or $view{}. Keep variables like $view{property:Name} exactly as they are.\n")
// Tell the model to keep the placeholder unchanged.
.append("4. The text '" + NL_PLACEHOLDER + "' represents a newline. Keep it exactly as '" + NL_PLACEHOLDER + "' in the translation, do not replace it with an actual newline.\n\n");

for (TranslatableItem item : items) {
String text = item.getOriginal() == null ? "" : item.getOriginal().trim();

// --- Replace real newlines with the placeholder ---
// replaceAll("\\R", ...) matches all newline sequences.
String flattenedText = text.replaceAll("\\R", NL_PLACEHOLDER);

prompt.append(flattenedText).append("\n");
}

String promptText = prompt.toString();
String escaped = jsonEscape(promptText);

String requestBody = """
{
"contents": [
{
"parts": [
{ "text": "%s" }
]
}
]
}
""".formatted(escaped);

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
.uri(GeminiApiConfig.resolveEndpointUri())
.header("Content-Type", "application/json")
.header("X-goog-api-key", apiKey)
.POST(HttpRequest.BodyPublishers.ofString(
requestBody, StandardCharsets.UTF_8))
.build();

HttpResponse<String> response =
client.send(request, HttpResponse.BodyHandlers.ofString());

if (response.statusCode() / 100 != 2) {
throw new RuntimeException(
"HTTP " + response.statusCode() + ": " + response.body());
}

String body = response.body();
String resultText = extractFirstTextFromGeminiResponse(body);

if (resultText == null) {
throw new RuntimeException("No 'text' found in response.");
}

// Unescape JSON sequences.
resultText = resultText.replace("\\n", "\n").replace("\\\"", "\"");

String[] lines = resultText.split("\\R");
int n = Math.min(items.size(), lines.length);

for (int i = 0; i < n; i++) {
String translatedLine = lines[i].trim();

// --- Restore placeholder back to newlines ---
String restoredText = translatedLine.replace(NL_PLACEHOLDER, "\n");

if (!restoredText.isEmpty()) {
items.get(i).setTranslated(restoredText);
} else {
items.get(i).setTranslated(items.get(i).getOriginal());
}
}

// Optional alignment check: warn if line counts differ.
if (lines.length != items.size()) {
System.err.println("Warning: Translated line count (" + lines.length + 
      ") does not match item count (" + items.size() + ").");
}
}

    /**
     * Minimal JSON string escaping for this use case.
     */
    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    
    /**
     * Non-strict JSON parsing: extract the first "text" field.
     * Intended for demo use only.
     */
    private static String extractFirstTextFromGeminiResponse(String body) {
        if (body == null) return null;

        int idx = body.indexOf("\"text\"");
        if (idx == -1) return null;

        idx = body.indexOf(':', idx);
        if (idx == -1) return null;

        int start = body.indexOf('"', idx + 1);
        if (start == -1) return null;
        start++;

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else {
                if (c == '\\') {
                    sb.append(c);
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }


    /**
     * Write confirmed translations back to the EMF model using CommandStack for undo support.
     */
    protected void applyTranslations(List<TranslatableItem> items, CommandStack commandStack) {
        for (TranslatableItem item : items) {
            if (item.getTranslated() == null
                    || item.getTranslated().isBlank()
                    || item.getOriginal().equals(item.getTranslated())) {
                continue; // Skip unchanged items
            }

            EObject owner = item.getOwner();
            String newValue = item.getTranslated();
            Command cmd = null;

            switch (item.getPropertyKind()) {
                case ELEMENT_NAME -> {
                    if (owner instanceof IArchimateElement el) {
                        cmd = new EObjectFeatureCommand("Translate Name", el, IArchimatePackage.Literals.NAMEABLE__NAME, newValue);
                    } else if (owner instanceof IArchimateRelationship rel) {
                        cmd = new EObjectFeatureCommand("Translate Name", rel, IArchimatePackage.Literals.NAMEABLE__NAME, newValue);
                    }
                }
                case DOCUMENTATION -> {
                    if (owner instanceof IArchimateElement el) {
                        cmd = new EObjectFeatureCommand("Translate Documentation", el, IArchimatePackage.Literals.DOCUMENTABLE__DOCUMENTATION, newValue);
                    } else if (owner instanceof IArchimateRelationship rel) {
                        cmd = new EObjectFeatureCommand("Translate Documentation", rel, IArchimatePackage.Literals.DOCUMENTABLE__DOCUMENTATION, newValue);
                    } else if (owner instanceof IDiagramModelGroup group) {
                        cmd = new EObjectFeatureCommand("Translate Documentation", group, IArchimatePackage.Literals.DOCUMENTABLE__DOCUMENTATION, newValue);
                    }
                }
                case NOTE_CONTENT -> {
                    if (owner instanceof IDiagramModelNote note) {
                        cmd = new EObjectFeatureCommand("Translate Note", note, IArchimatePackage.Literals.TEXT_CONTENT__CONTENT, newValue);
                    }
                }
                case GROUP_NAME -> {
                    if (owner instanceof IDiagramModelGroup group) {
                        cmd = new EObjectFeatureCommand("Translate Group Name", group, IArchimatePackage.Literals.NAMEABLE__NAME, newValue);
                    }
                }
                case DIAGRAM_NAME -> {
                    if (owner instanceof IDiagramModel diagram) {
                        cmd = new EObjectFeatureCommand("Translate Diagram Name", diagram, IArchimatePackage.Literals.NAMEABLE__NAME, newValue);
                    }
                }
                case LABEL_EXPRESSION -> {
                    // Label expressions are stored as features on IFeatures objects
                    if (owner instanceof IFeatures featuresProvider) {
                        // Use FeatureCommand for proper undo support
                        cmd = new FeatureCommand("Translate Label Expression", featuresProvider, "labelExpression", newValue, "");
                    }
                }
            }

            // Execute the command via CommandStack if available
            if (cmd != null) {
                if (commandStack != null) {
                    commandStack.execute(cmd);
                } else {
                    // Fallback if no CommandStack available
                    cmd.execute();
                }
            }
        }
    }
    /**
     * Force refresh so dynamic labels (e.g. $view{name}) update immediately.
     */
    protected void refreshEditor(IEditorPart editorPart) {
        // Ensure this is an Archi diagram editor.
        if (editorPart instanceof com.archimatetool.editor.diagram.IDiagramModelEditor diagramEditor) {
            
            // 1. Get the GraphicalViewer.
            org.eclipse.gef.GraphicalViewer viewer = diagramEditor.getGraphicalViewer();
            
            // 2. Get the diagram model for the active editor.
            // Note: use the editor model rather than viewer.getContents().
            IDiagramModel model = diagramEditor.getModel();

            if (viewer != null && model != null) {
                // 3. Reset viewer contents.
                // This tells GEF to rebuild the UI from the model.
                // Rebuild reads the latest diagram name for label expressions.
                viewer.setContents(model);
                
                // 4. Flush viewer (optional).
                viewer.flush();
            }
        }
    }
}
