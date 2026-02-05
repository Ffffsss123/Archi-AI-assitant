package com.cwdil.archi.aitranslate.commands;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.diagram.DiagramEditorInput;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.cwdil.archi.aitranslate.TranslationHistory;
import com.cwdil.archi.aitranslate.ui.TranslationLanguageDialog;
import com.cwdil.archi.aitranslate.ui.TranslationPreviewDialog;

// Inherit from TranslateViewHandler to reuse logic.
public class TranslateAllViewsHandler extends TranslateViewHandler {

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

        IDiagramModel currentDiagram = dei.getDiagramModel();
        if (currentDiagram == null) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "No diagram model available.");
            return null;
        }

        // 2. Get the entire model (Archimate Model).
        IArchimateModel model = currentDiagram.getArchimateModel();
        if (model == null) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "Diagram is not attached to a model.");
            return null;
        }

        List<IDiagramModel> diagrams = model.getDiagramModels();
        if (diagrams == null || diagrams.isEmpty()) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "No views were found in this model.");
            return null;
        }

        // 3. Collect content from all views.
        List<TranslatableItem> allItems = new ArrayList<>();
        
        for (IDiagramModel view : diagrams) {
            // A. Collect items inside the view (notes, labels, etc.).
            List<TranslatableItem> items = collectItems(view);
            
            // --- Added: include the view name itself ---
            String viewName = view.getName();
            if (viewName != null && !viewName.isBlank()) {
                // Use a temporary id; we will reassign sequential ids later.
                items.add(0, new TranslatableItem(
                        "temp_id",
                        "Diagram Name", 
                        viewName,
                        view, 
                        PropertyKind.DIAGRAM_NAME // Ensure this is DIAGRAM_NAME
                ));
            }
            // ------------------------------------------

            if (items.isEmpty()) {
                continue;
            }

            // B. Prefix kind with view name for the preview list.
            String prefix = (viewName == null || viewName.isBlank()) ? "Untitled View" : viewName;
            for (TranslatableItem item : items) {
                // Example: "Layered View / Note"
                item.setKind(prefix + " / " + item.getKind());
            }
            
            allItems.addAll(items);
        }

        if (allItems.isEmpty()) {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), "AI Translate", "No translatable texts were found in any view.");
            return null;
        }

        // 4. Reassign ids to keep them sequential.
        int id = 1;
        for (TranslatableItem item : allItems) {
            item.setId(String.valueOf(id++));
        }

        // 5. Choose language.
        TranslationLanguageDialog langDialog = new TranslationLanguageDialog(Display.getDefault().getActiveShell(), "zh-CN");
        if (langDialog.open() != TranslationLanguageDialog.OK) {
            return null;
        }
        String targetLang = langDialog.getTargetLang();

        // 6. Call the Gemini API.
        try {
            // Reuses the parent newline placeholder logic.
            translateWithGemini(allItems, targetLang);
        } catch (Exception ex) {
            ex.printStackTrace();
            MessageDialog.openError(Display.getDefault().getActiveShell(),
                    "AI Translate",
                    "Calling Gemini API failed: " + ex.getMessage() + "\nFalling back to local fake translation.");
            fakeTranslate(allItems);
        }

        // 7. Preview confirmation.
        TranslationPreviewDialog dlg = new TranslationPreviewDialog(Display.getDefault().getActiveShell(), allItems);
        if (dlg.open() != TranslationPreviewDialog.OK) {
            return null;
        }

        // 8. Record history and apply.
        TranslationHistory.record(allItems);
        
        // Get the CommandStack from the model for undo support
        org.eclipse.gef.commands.CommandStack commandStack = (org.eclipse.gef.commands.CommandStack) model.getAdapter(org.eclipse.gef.commands.CommandStack.class);
        applyTranslations(allItems, commandStack);
        
        // --- Refresh the current editor for immediate feedback ---
        // Even though all views changed, refresh the active one to show results.
        refreshEditor(editor);
        // ------------------------------------
        
        return null;
    }
}
