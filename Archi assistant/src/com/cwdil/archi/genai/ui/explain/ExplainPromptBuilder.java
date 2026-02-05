package com.cwdil.archi.genai.ui.explain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExplainPromptBuilder {

    private static final int MAX_LIST_ITEMS = 30;
    private static final int MAX_REL_ITEMS = 30;

    private static final Map<String, String> LANGUAGE_LABELS = Map.of(
            "en", "English",
            "zh-CN", "Chinese (Simplified)",
            "zh-TW", "Chinese (Traditional)",
            "ja", "Japanese",
            "fr", "French",
            "de", "German",
            "es", "Spanish"
    );

    public String buildPrompt(ExplainContext context, String languageCode) {
        String languageLabel = LANGUAGE_LABELS.getOrDefault(languageCode, languageCode);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an enterprise architecture explainer.\n");
        prompt.append("Write in ").append(languageLabel).append(".\n");
        prompt.append("Explain the business meaning for non-technical stakeholders.\n");
        prompt.append("Avoid technical jargon and explain any unavoidable terms.\n");
        prompt.append("Only use the provided context; do not invent details.\n");
        prompt.append("Output format exactly:\n");
        prompt.append("SIMPLIFIED:\n- 3 to 5 concise bullets.\n");
        if(!context.hasSelection) {
            prompt.append("DETAILED:\n- At least 12 bullets, very detailed; each bullet must mention impact and dependency.\n");
        }
        else {
            prompt.append("DETAILED:\n- 6 to 10 bullets with more context and impacts.\n");
        }
        if(context.selectedCount == 1) {
            prompt.append("SUMMARY:\n1 to 2 sentences, only explain the selected element.\n\n");
        }
        else if(context.selectedCount >= 2) {
            prompt.append("SUMMARY:\n1 to 2 sentences, only explain the selected elements themselves.\n\n");
        }
        else {
            prompt.append("SUMMARY:\n1 to 2 sentences.\n\n");
        }

        prompt.append("Context:\n");
        prompt.append("View name: ").append(context.viewName).append("\n");
        prompt.append("View type: ").append(context.viewType).append("\n");
        prompt.append("Selected count: ").append(context.selectedCount).append("\n");

        if(context.hasSelection && !context.selectedItems.isEmpty()) {
            prompt.append("Selected items:\n");
            for(String item : limitList(context.selectedItems, MAX_LIST_ITEMS)) {
                prompt.append("- ").append(item).append("\n");
            }
        }

        if(!context.hasSelection) {
            prompt.append("Provide a very detailed explanation of all elements and their relationships.\n");
            prompt.append("Each point must state interactions, dependencies, and business impact.\n");
            prompt.append("View elements count: ").append(context.viewElementCount).append("\n");
            if(!context.viewItems.isEmpty()) {
                prompt.append("View elements (sample):\n");
                for(String item : limitList(context.viewItems, MAX_LIST_ITEMS)) {
                    prompt.append("- ").append(item).append("\n");
                }
            }

            prompt.append("Relationships count: ").append(context.relationshipCount).append("\n");
            if(!context.relationships.isEmpty()) {
                prompt.append("Relationships (sample):\n");
                for(String item : limitList(context.relationships, MAX_REL_ITEMS)) {
                    prompt.append("- ").append(item).append("\n");
                }
            }
        }

        return prompt.toString();
    }

    private List<String> limitList(List<String> items, int limit) {
        List<String> list = new ArrayList<>();
        for(String item : items) {
            if(item == null || item.isBlank()) {
                continue;
            }
            list.add(item);
            if(list.size() >= limit) {
                break;
            }
        }
        return list;
    }
}
