/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.cwdil.archi.genai.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.cwdil.archi.genai.ui.views.GenAIAssistantView;

/**
 * Show GenAI Assistant view.
 */
public class ShowGenAIAssistantHandler extends AbstractHandler {

    /**
     * Explicit default constructor to ensure safe initialization.
     */
    public ShowGenAIAssistantHandler() {
        super();
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if(window == null) {
            System.err.println("No active workbench window."); //$NON-NLS-1$
            return null;
        }

        IWorkbenchPage page = window.getActivePage();
        if(page == null) {
            System.err.println("No active workbench page."); //$NON-NLS-1$
            return null;
        }

        try {
            org.eclipse.ui.IViewPart viewPart = page.findView(GenAIAssistantView.ID);
            // If view exists and is currently visible (active or in stack), hide it.
            // We use isPartVisible to check visibility.
            if(viewPart != null && page.isPartVisible(viewPart)) {
                page.hideView(viewPart);
            } else {
                page.showView(GenAIAssistantView.ID);
            }
        }
        catch(PartInitException ex) {
            throw new ExecutionException("Unable to open GenAI Assistant view.", ex); //$NON-NLS-1$
        }

        return null;
    }
}
