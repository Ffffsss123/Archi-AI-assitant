package com.cwdil.archi.genai.ui.explain;

import java.util.ArrayList;
import java.util.List;

public class ExplainContext {

    public String viewName;
    public String viewType;
    public boolean hasSelection;
    public int selectedCount;
    public int viewElementCount;
    public int relationshipCount;
    public final List<String> selectedItems = new ArrayList<>();
    public final List<String> viewItems = new ArrayList<>();
    public final List<String> relationships = new ArrayList<>();

    public String contextKey(String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(language).append("|");
        sb.append(viewName).append("|");
        sb.append(viewType).append("|");
        sb.append(selectedCount).append("|");
        for(String item : selectedItems) {
            sb.append(item).append("|");
        }
        return sb.toString();
    }
}
