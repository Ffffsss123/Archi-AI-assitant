package com.cwdil.archi.aitranslate.ui;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.cwdil.archi.aitranslate.commands.TranslateViewHandler;

/**
 * Simple preview dialog: show original/translated lines; user clicks OK / Cancel.
 * Can be upgraded later to TableViewer + Checkbox.
 */
public class TranslationPreviewDialog extends Dialog {

    private final List<TranslateViewHandler.TranslatableItem> items;

    public TranslationPreviewDialog(Shell parentShell,
            List<TranslateViewHandler.TranslatableItem> items) {
        super(parentShell);
        this.items = items;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Preview Translations");
        newShell.setSize(700, 500);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));

        Text text = new Text(container,
                SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        StringBuilder sb = new StringBuilder();
        for (TranslateViewHandler.TranslatableItem item : items) {
            sb.append("#").append(item.getId()).append(" [")
              .append(item.getKind()).append("]\n")
              .append("  Original : ").append(item.getOriginal()).append("\n")
              .append("  Translated: ").append(item.getTranslated()).append("\n\n");
        }
        text.setText(sb.toString());

        return container;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Apply", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
}
