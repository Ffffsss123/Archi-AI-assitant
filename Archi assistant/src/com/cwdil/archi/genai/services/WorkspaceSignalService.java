package com.cwdil.archi.genai.services;

import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPart;

import com.archimatetool.hammer.validation.Validator;
import com.archimatetool.hammer.validation.issues.AdviceType;
import com.archimatetool.hammer.validation.issues.ErrorType;
import com.archimatetool.hammer.validation.issues.IIssue;
import com.archimatetool.hammer.validation.issues.IIssueCategory;
import com.archimatetool.hammer.validation.issues.OKType;
import com.archimatetool.hammer.validation.issues.WarningType;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;

/**
 * Service to analyze Archi workspace state and produce signals (Validation, Selection).
 */
public class WorkspaceSignalService {

    private static final long VALIDATION_CACHE_MS = 2000;
    
    private long lastValidationTime;
    private SignalData.ValidationSummary lastValidationSummary;
    private IArchimateModel lastValidatedModel;

    public static class SignalData {
        public final String activeView;
        public final int selectedCount;
        public final String checksText;
        public final String checksState;
        public final String checksDetail;

        public SignalData(String activeView, int selectedCount, String checksText, String checksState, String checksDetail) {
            this.activeView = activeView;
            this.selectedCount = selectedCount;
            this.checksText = checksText;
            this.checksState = checksState;
            this.checksDetail = checksDetail;
        }

        public static class ValidationSummary {
            final String text;
            final String state;
            final String detail;
            ValidationSummary(String text, String state, String detail) {
                this.text = text;
                this.state = state;
                this.detail = detail;
            }
        }
    }

    public SignalData getSignals(Object activePartObj, ISelection selection) {
        IWorkbenchPart activePart = (activePartObj instanceof IWorkbenchPart) ? (IWorkbenchPart) activePartObj : null;
        String activeViewTitle = activePart != null ? activePart.getTitle() : "None";
        int selectedCount = getSelectionCount(selection);
        
        IArchimateModel model = getModelFromContext(activePart, selection);
        SignalData.ValidationSummary validation = getValidationSummary(model);

        return new SignalData(activeViewTitle, selectedCount, validation.text, validation.state, validation.detail);
    }

    private int getSelectionCount(ISelection selection) {
        if(selection instanceof IStructuredSelection) {
            return ((IStructuredSelection)selection).size();
        }
        return selection != null && !selection.isEmpty() ? 1 : 0;
    }

    private IArchimateModel getModelFromContext(IWorkbenchPart part, ISelection selection) {
        if(part instanceof IAdaptable) {
            IArchimateModel model = ((IAdaptable)part).getAdapter(IArchimateModel.class);
            if(model != null) {
                return model;
            }
        }

        if(selection instanceof IStructuredSelection) {
            for(Object element : ((IStructuredSelection)selection).toArray()) {
                if(element instanceof IArchimateModel) {
                    return (IArchimateModel)element;
                }
                if(element instanceof IArchimateModelObject) {
                    return ((IArchimateModelObject)element).getArchimateModel();
                }
                if(element instanceof IAdaptable) {
                    IArchimateModel model = ((IAdaptable)element).getAdapter(IArchimateModel.class);
                    if(model != null) {
                        return model;
                    }
                }
            }
        }
        return null;
    }

    private SignalData.ValidationSummary getValidationSummary(IArchimateModel model) {
        if(model == null) {
            return new SignalData.ValidationSummary("No model", "warning", ""); 
        }

        long now = System.currentTimeMillis();
        if(model == lastValidatedModel && lastValidationSummary != null && (now - lastValidationTime) < VALIDATION_CACHE_MS) {
            return lastValidationSummary;
        }

        Validator validator = new Validator(model);
        List<Object> results = validator.validate();
        SignalData.ValidationSummary summary = summarizeResults(results);

        lastValidatedModel = model;
        lastValidationTime = now;
        lastValidationSummary = summary;

        return summary;
    }

    private SignalData.ValidationSummary summarizeResults(List<Object> results) {
        if(results == null || results.isEmpty()) {
            return new SignalData.ValidationSummary("No checks", "warning", ""); 
        }

        boolean ok = false;
        int errors = 0;
        int warnings = 0;
        int advice = 0;
        StringBuilder detailBuilder = new StringBuilder();

        for(Object item : results) {
            if(item instanceof OKType) {
                ok = true;
            }
            else if(item instanceof IIssueCategory category) {
                for(IIssue issue : category.getIssues()) {
                    if(issue instanceof ErrorType) {
                        errors++;
                        appendIssueDetail(detailBuilder, "Error", category.getName(), issue.getDescription());
                    }
                    else if(issue instanceof WarningType) {
                        warnings++;
                        appendIssueDetail(detailBuilder, "Warning", category.getName(), issue.getDescription());
                    }
                    else if(issue instanceof AdviceType) {
                        advice++;
                        appendIssueDetail(detailBuilder, "Advice", category.getName(), issue.getDescription());
                    }
                }
            }
        }

        if(ok || (errors + warnings + advice) == 0) {
            return new SignalData.ValidationSummary("All passing", "success", ""); 
        }

        String text = errors + " errors, " + warnings + " warnings, " + advice + " advice"; 
        String state = errors > 0 ? "error" : "warning";
        String detail = detailBuilder.toString();
        return new SignalData.ValidationSummary(text, state, detail);
    }

    private void appendIssueDetail(StringBuilder sb, String type, String category, String description) {
        // Ensure description is a string
        String desc = description != null ? description.toString() : "(No description)";
        sb.append("<div class='issue-item'>");
        sb.append("<span class='issue-type ").append(type.toLowerCase()).append("'>").append(type).append("</span>");
        if (shouldShowCategory(type, category)) {
            sb.append("<span class='issue-category'>").append(escapeHtml(category)).append("</span>");
        }
        sb.append("<p class='issue-description'>").append(escapeHtml(desc)).append("</p>");
        sb.append("</div>");
    }

    private boolean shouldShowCategory(String type, String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        String normalizedType = type == null ? "" : type.trim();
        String normalizedCategory = category.trim();
        if (normalizedType.isEmpty()) {
            return true;
        }
        String typeLower = normalizedType.toLowerCase();
        String categoryLower = normalizedCategory.toLowerCase();
        if (categoryLower.equals(typeLower)) {
            return false;
        }
        if (categoryLower.equals(typeLower + "s") || categoryLower.equals(typeLower + "es")) {
            return false;
        }
        return true;
    }

    private String escapeHtml(String text) {
        if(text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
