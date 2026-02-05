package com.cwdil.archi.aitranslate.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.model.IDiagramModel; // Ensure this import is present
import com.cwdil.archi.aitranslate.TranslationHistory;

public class UndoTranslationHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        if (!TranslationHistory.canUndo()) {
            MessageDialog.openInformation(
                    Display.getDefault().getActiveShell(),
                    "AI Translate",
                    "There is no translation to undo.");
            return null;
        }

        // 1. Perform the undo logic (restore model data).
        TranslationHistory.undoLast();

        // 2. Force refresh the active editor (refresh UI layer).
        refreshActiveEditor();

        MessageDialog.openInformation(
                Display.getDefault().getActiveShell(),
                "AI Translate",
                "The last translation has been undone.");
        return null;
    }

    // --- Refresh helper after undo ---
    private void refreshActiveEditor() {
        try {
            IEditorPart editor = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow()
                    .getActivePage()
                    .getActiveEditor();

            if (editor instanceof com.archimatetool.editor.diagram.IDiagramModelEditor diagramEditor) {
                org.eclipse.gef.GraphicalViewer viewer = diagramEditor.getGraphicalViewer();
                
                // --- Key refresh step ---
                // Get the diagram model (IDiagramModel).
                IDiagramModel model = diagramEditor.getModel();
                
                if (viewer != null && model != null) {
                    // Pass model to force GEF to rebuild EditParts.
                    // This re-evaluates label expressions like $view{name}.
                    viewer.setContents(model);
                    viewer.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
