package com.cwdil.archi.aitranslate.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class TranslationLanguageDialog extends Dialog {

    private final String defaultTarget;
    private Text targetText;
    private String targetLang;

    public TranslationLanguageDialog(Shell parentShell, String defaultTarget) {
        super(parentShell);
        this.defaultTarget = defaultTarget;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Translation Language");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(2, false));

        Label targetLabel = new Label(container, SWT.NONE);
        targetLabel.setText("Target language (e.g. zh-CN)");
        targetLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        targetText = new Text(container, SWT.BORDER);
        targetText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        targetText.setText(defaultTarget == null ? "" : defaultTarget);

        return container;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void okPressed() {
        targetLang = targetText.getText().trim();
        if (targetLang.isEmpty()) {
            return;
        }
        super.okPressed();
    }

    public String getTargetLang() {
        return targetLang;
    }
}
