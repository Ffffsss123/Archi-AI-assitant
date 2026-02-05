package com.cwdil.archi.sequence.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.diagram.DiagramEditorInput;
import com.archimatetool.model.IDiagramModel;
import com.cwdil.archi.sequence.SequenceDiagramBuilder;
import com.cwdil.archi.sequence.SequenceDiagramBuilder.Result;
import com.cwdil.archi.sequence.ui.SequenceDiagramDialog;

public class GenerateSequenceDiagramHandler extends AbstractHandler {

    private final SequenceDiagramBuilder builder = new SequenceDiagramBuilder();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if(window == null) {
            System.err.println("No active workbench window.");
            return null;
        }

        IWorkbenchPage page = window.getActivePage();
        IEditorPart editor = page != null ? page.getActiveEditor() : null;
        IDiagramModel diagram = resolveDiagram(editor);
        ISelection selection = resolveSelection(window, page);

        Result result = builder.build(diagram, selection);
        Shell shell = window.getShell();
        if(!result.success) {
            MessageDialog.openInformation(shell, "Sequence Diagram", result.message);
            return null;
        }

        SequenceDiagramDialog dialog = new SequenceDiagramDialog(shell, result.plantUml);
        dialog.open();
        return null;
    }

    private IDiagramModel resolveDiagram(IEditorPart editor) {
        if(editor == null) {
            return null;
        }
        IEditorInput input = editor.getEditorInput();
        if(input instanceof DiagramEditorInput dei) {
            return dei.getDiagramModel();
        }
        return editor.getAdapter(IDiagramModel.class);
    }

    private ISelection resolveSelection(IWorkbenchWindow window, IWorkbenchPage page) {
        ISelection selection = window.getSelectionService().getSelection();
        if(isValidSelection(selection)) {
            return selection;
        }
        ISelection editorSelection = resolveSelectionFromEditor(page != null ? page.getActiveEditor() : null);
        if(isValidSelection(editorSelection)) {
            return editorSelection;
        }
        ISelection partSelection = resolveSelectionFromPart(page != null ? page.getActivePart() : null);
        if(isValidSelection(partSelection)) {
            return partSelection;
        }
        return selection;
    }

    private ISelection resolveSelectionFromEditor(IEditorPart editor) {
        if(editor == null || editor.getSite() == null) {
            return null;
        }
        ISelectionProvider provider = editor.getSite().getSelectionProvider();
        return provider != null ? provider.getSelection() : null;
    }

    private ISelection resolveSelectionFromPart(org.eclipse.ui.IWorkbenchPart part) {
        if(part == null || part.getSite() == null) {
            return null;
        }
        ISelectionProvider provider = part.getSite().getSelectionProvider();
        return provider != null ? provider.getSelection() : null;
    }

    private boolean isValidSelection(ISelection selection) {
        return selection instanceof org.eclipse.jface.viewers.IStructuredSelection structured
                && !structured.isEmpty();
    }
}
