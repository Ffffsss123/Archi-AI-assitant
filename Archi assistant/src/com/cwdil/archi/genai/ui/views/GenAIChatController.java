package com.cwdil.archi.genai.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPart;

import com.cwdil.archi.genai.services.GenAIResponse;
import com.cwdil.archi.genai.services.GenAIService;
import com.cwdil.archi.genai.services.ModelContextService;
import com.cwdil.archi.genai.services.ModelGenerationService;
import com.cwdil.archi.genai.util.SimpleJsonParser;
import com.cwdil.archi.genai.util.SimpleJsonParser.ParseException;

public class GenAIChatController {

    private static final int HISTORY_LIMIT = 12;

    private final GenAIService aiService;
    private final Browser browser;
    private final ModelContextService contextService;
    private final ModelGenerationService generationService;
    private final List<ChatMessage> history = new ArrayList<>();

    private static final class ChatMessage {
        final String role;
        final String content;

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public GenAIChatController(GenAIService aiService, Browser browser) {
        this.aiService = aiService;
        this.browser = browser;
        this.contextService = new ModelContextService();
        this.generationService = new ModelGenerationService(contextService);
    }

    public void resetHistory() {
        synchronized(history) {
            history.clear();
        }
    }

    public void setHistoryFromJson(String json) {
        List<ChatMessage> messages = parseHistoryJson(json);
        if(messages.isEmpty()) {
            resetHistory();
            return;
        }
        synchronized(history) {
            history.clear();
            history.addAll(messages);
            while(history.size() > HISTORY_LIMIT) {
                history.remove(0);
            }
        }
    }

    private List<ChatMessage> parseHistoryJson(String json) {
        List<ChatMessage> messages = new ArrayList<>();
        if(json == null || json.isBlank()) {
            return messages;
        }
        try {
            Object parsed = SimpleJsonParser.parse(json);
            if(!(parsed instanceof List)) {
                return messages;
            }
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) parsed;
            for(Object item : items) {
                if(!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                String role = stringValue(map.get("role"));
                String content = stringValue(map.get("content"));
                if(role != null && content != null && !role.isBlank() && !content.isBlank()) {
                    messages.add(new ChatMessage(role, content));
                }
            }
        } catch(ParseException ex) {
            System.err.println("Failed to parse history JSON: " + ex.getMessage());
        }
        return messages;
    }

    private String stringValue(Object value) {
        if(value == null) {
            return null;
        }
        if(value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    public void handleUserRequest(String prompt, IWorkbenchPart part, ISelection selection) {
        if(browser == null || browser.isDisposed()) {
            return;
        }

        String cleanedPrompt = prompt == null ? "" : prompt.trim();
        if(cleanedPrompt.isEmpty()) {
            return;
        }
        addHistory("user", cleanedPrompt);
        String contextSummary = contextService.buildContextSummary(part, selection);
        String inventory = null;
        if(shouldIncludeInventory(cleanedPrompt)) {
            inventory = contextService.buildModelInventory(contextService.getModelFromContext(part, selection), 400);
        }
        String fullPrompt = buildStructuredPrompt(contextSummary, inventory);

        aiService.sendPromptAsync(fullPrompt)
            .thenAccept(response -> {
                handleAiResponse(response, part, selection);
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                sendToChat("AI error: " + ex.getMessage());
                return null;
            });
    }

    private void handleAiResponse(String response, IWorkbenchPart part, ISelection selection) {
        GenAIResponse parsed = null;
        try {
            String payload = extractJsonPayload(response);
            parsed = GenAIResponse.parse(payload);
        } catch(ParseException ex) {
            sendToChat(response);
            addHistory("assistant", response);
            sendToChat("AI response format error: no model changes were applied.");
            return;
        }

        String displayText = buildAssistantMessage(parsed);
        if(displayText != null && !displayText.isBlank()) {
            sendToChat(displayText);
            addHistory("assistant", displayText);
        }

        Display display = browser.getDisplay();
        if(display == null) {
            return;
        }
        GenAIResponse parsedResponse = parsed;
        if(hasActions(parsedResponse)) {
            display.asyncExec(() -> {
                ModelGenerationService.ApplyResult result = generationService.apply(parsedResponse, part, selection);
                if(result != null && result.message != null && !result.message.isBlank()) {
                    sendToChat(result.message);
                    addHistory("assistant", result.message);
                }
            });
        }
    }

    private void sendToChat(String text) {
        if(browser == null || browser.isDisposed() || text == null) {
            return;
        }
        Display display = browser.getDisplay();
        if(display == null) {
            return;
        }
        display.asyncExec(() -> {
            if(browser.isDisposed()) {
                return;
            }
            String safeResponse = JsTextEscaper.escape(text);
            browser.execute("window.receiveMessage(\"" + safeResponse + "\")");
        });
    }

    private void addHistory(String role, String content) {
        if(content == null || content.isBlank()) {
            return;
        }
        synchronized(history) {
            history.add(new ChatMessage(role, content));
            while(history.size() > HISTORY_LIMIT) {
                history.remove(0);
            }
        }
    }

    private String buildStructuredPrompt(String contextSummary, String inventory) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an ArchiMate assistant embedded in Archi. ");
        sb.append("You can access the current Archi model context provided here; do not say you cannot access it. ");
        sb.append("Respond with a single JSON object only (no markdown, no code fences). ");
        sb.append("Use this schema:\n");
        sb.append("{");
        sb.append("\"assistant_reply\":\"...\",");
        sb.append("\"follow_up_question\":\"...\",");
        sb.append("\"model\":{\"create\":true|false,\"name\":\"...\"},");
        sb.append("\"elements\":[{\"id\":\"e1\",\"type\":\"BusinessActor\",\"name\":\"...\",\"documentation\":\"...\"}],");
        sb.append("\"relationships\":[{\"id\":\"r1\",\"type\":\"Serving\",\"source\":\"e1\",\"target\":\"e2\",\"name\":\"...\",\"documentation\":\"...\"}],");
        sb.append("\"view\":{\"create\":true|false,\"name\":\"...\",\"include\":[\"e1\",\"e2\",\"r1\"]}");
        sb.append("}\n");
        sb.append("Rules:\n");
        sb.append("- If you need more info, leave elements/relationships empty and set follow_up_question.\n");
        sb.append("- Only create a new model if needed; set model.create=true and provide model.name.\n");
        sb.append("- Use valid ArchiMate type names (e.g., BusinessActor, BusinessProcess, ApplicationComponent, DataObject, Serving, Flow).\n");
        sb.append("- Relationship source/target must reference element ids.\n");
        sb.append("- If no view is needed, set view.create=false.\n");
        sb.append("Context: ").append(contextSummary).append("\n");
        if(inventory != null && !inventory.isBlank()) {
            sb.append("ExistingModelInventory (use these ids, do not create duplicates):\n");
            sb.append(inventory).append("\n");
        }
        sb.append("Conversation:\n");
        synchronized(history) {
            for(ChatMessage msg : history) {
                sb.append(msg.role).append(": ").append(msg.content).append("\n");
            }
        }
        sb.append("assistant:");
        return sb.toString();
    }

    private String buildAssistantMessage(GenAIResponse parsed) {
        String reply = parsed.assistantReply != null ? parsed.assistantReply.trim() : "";
        String question = parsed.followUpQuestion != null ? parsed.followUpQuestion.trim() : "";
        if(reply.isEmpty() && question.isEmpty()) {
            return "";
        }
        if(!question.isEmpty()) {
            if(reply.isEmpty()) {
                return question;
            }
            if(isDuplicateQuestion(reply, question)) {
                return reply;
            }
            return reply + "\n\n" + question;
        }
        return reply;
    }

    private boolean isDuplicateQuestion(String reply, String question) {
        String normalizedReply = reply.trim();
        String normalizedQuestion = question.trim();
        if(normalizedReply.equalsIgnoreCase(normalizedQuestion)) {
            return true;
        }
        return normalizedReply.endsWith(normalizedQuestion);
    }

    private boolean hasActions(GenAIResponse parsed) {
        if(parsed == null) {
            return false;
        }
        if(parsed.model != null && parsed.model.create) {
            return true;
        }
        if(parsed.elements != null && !parsed.elements.isEmpty()) {
            return true;
        }
        if(parsed.relationships != null && !parsed.relationships.isEmpty()) {
            return true;
        }
        return parsed.view != null && parsed.view.create;
    }

    private boolean shouldIncludeInventory(String prompt) {
        if(prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase();
        return lower.contains("你自己读取") ||
               lower.contains("读取模型") ||
               lower.contains("现有元素") ||
               lower.contains("元素清单") ||
               lower.contains("关系清单") ||
               lower.contains("拆分视图") ||
               lower.contains("复用现有") ||
               lower.contains("不要重复");
    }

    private String extractJsonPayload(String response) throws ParseException {
        if(response == null) {
            throw new ParseException("Empty response.");
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        if(start == -1) {
            throw new ParseException("No JSON object found.");
        }
        int end = findMatchingBrace(trimmed, start);
        if(end == -1) {
            throw new ParseException("No JSON object found.");
        }
        String payload = trimmed.substring(start, end + 1);
        return sanitizeJsonPayload(payload);
    }

    private int findMatchingBrace(String text, int start) {
        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for(int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if(inString) {
                if(escape) {
                    escape = false;
                } else if(c == '\\') {
                    escape = true;
                } else if(c == '"') {
                    inString = false;
                }
                continue;
            }
            if(c == '"') {
                inString = true;
            } else if(c == '{') {
                depth++;
            } else if(c == '}') {
                depth--;
                if(depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String sanitizeJsonPayload(String payload) {
        if(payload == null) {
            return null;
        }
        return payload.replace("\\\r\n", "")
                .replace("\\\n", "")
                .replace("\\\r", "");
    }
}
