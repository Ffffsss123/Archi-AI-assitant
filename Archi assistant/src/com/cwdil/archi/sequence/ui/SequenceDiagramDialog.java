package com.cwdil.archi.sequence.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class SequenceDiagramDialog extends Dialog {

    private final String plantUml;

    public SequenceDiagramDialog(Shell parentShell, String plantUml) {
        super(parentShell);
        this.plantUml = plantUml != null ? plantUml : "";
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Sequence Diagram (PlantUML)");
        newShell.setSize(700, 500);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite)super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));

        Text text = new Text(container,
                SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        text.setText(plantUml);

        return container;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CLIENT_ID + 1, "Copy", false);
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if(buttonId == IDialogConstants.CLIENT_ID + 1) {
            copyToClipboard(plantUml);
            return;
        }
        super.buttonPressed(buttonId);
    }

    private void copyToClipboard(String text) {
        if(text == null || text.isBlank()) {
            return;
        }
        Clipboard clipboard = new Clipboard(getShell().getDisplay());
        try {
            clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
        }
        finally {
            clipboard.dispose();
        }
    }
}
