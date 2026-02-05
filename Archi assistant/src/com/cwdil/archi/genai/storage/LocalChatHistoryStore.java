package com.cwdil.archi.genai.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cwdil.archi.genai.util.SimpleJsonParser;
import com.cwdil.archi.genai.util.SimpleJsonParser.ParseException;

public final class LocalChatHistoryStore {

    public static final class Session {
        public final String id;
        public final String title;
        public final String createdAt;
        public final String lastMessageAt;

        public Session(String id, String title, String createdAt, String lastMessageAt) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.lastMessageAt = lastMessageAt;
        }
    }

    public static final class Message {
        public final String id;
        public final String role;
        public final String content;
        public final String createdAt;

        public Message(String id, String role, String content, String createdAt) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    public static final class ListResult {
        public final List<Session> sessions;
        public final String nextCursor;
        public final boolean hasMore;

        public ListResult(List<Session> sessions, String nextCursor, boolean hasMore) {
            this.sessions = sessions;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }
    }

    public static final class ImportResult {
        public final int sessionsImported;
        public final int messagesImported;

        public ImportResult(int sessionsImported, int messagesImported) {
            this.sessionsImported = sessionsImported;
            this.messagesImported = messagesImported;
        }
    }

    private static final int MAX_PAGE_SIZE = 50;

    private final File rootDir;
    private final Object lock = new Object();

    public LocalChatHistoryStore(File stateDir) {
        if(stateDir == null) {
            throw new IllegalArgumentException("stateDir is required.");
        }
        this.rootDir = new File(stateDir, "chat-history");
    }

    public ListResult listSessions(String userId, int limit, String cursor) {
        int safeLimit = clamp(limit, 1, MAX_PAGE_SIZE);
        synchronized(lock) {
            List<Session> sessions = readSessions(userId);
            sessions.sort(Comparator.comparing((Session s) -> safeString(s.createdAt)).reversed());
            if(cursor != null && !cursor.isBlank()) {
                List<Session> filtered = new ArrayList<>();
                for(Session session : sessions) {
                    if(session != null && safeString(session.createdAt).compareTo(cursor) < 0) {
                        filtered.add(session);
                    }
                }
                sessions = filtered;
            }
            boolean hasMore = sessions.size() > safeLimit;
            List<Session> page = sessions.subList(0, Math.min(safeLimit, sessions.size()));
            String nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).createdAt;
            return new ListResult(new ArrayList<>(page), nextCursor, hasMore);
        }
    }

    public Session createSession(String userId, String title) {
        synchronized(lock) {
            List<Session> sessions = readSessions(userId);
            String now = Instant.now().toString();
            Session session = new Session(UUID.randomUUID().toString(), safeString(title), now, now);
            sessions.add(session);
            writeSessions(userId, sessions);
            return session;
        }
    }

    public List<Message> getMessages(String userId, String sessionId) {
        synchronized(lock) {
            return readMessages(userId, sessionId);
        }
    }

    public Message appendMessage(String userId, String sessionId, Message message) {
        if(message == null || sessionId == null || sessionId.isBlank()) {
            return null;
        }
        synchronized(lock) {
            List<Message> messages = readMessages(userId, sessionId);
            Set<String> existingIds = new LinkedHashSet<>();
            for(Message existing : messages) {
                if(existing != null && existing.id != null) {
                    existingIds.add(existing.id);
                }
            }
            if(message.id == null || !existingIds.contains(message.id)) {
                messages.add(message);
            }
            writeMessages(userId, sessionId, messages);
            updateSessionAfterMessage(userId, sessionId, message.createdAt);
            return message;
        }
    }

    public Session renameSession(String userId, String sessionId, String title) {
        if(sessionId == null || sessionId.isBlank()) {
            return null;
        }
        synchronized(lock) {
            List<Session> sessions = readSessions(userId);
            Session updated = null;
            List<Session> next = new ArrayList<>(sessions.size());
            for(Session session : sessions) {
                if(session != null && sessionId.equals(session.id)) {
                    updated = new Session(session.id, safeString(title), session.createdAt, session.lastMessageAt);
                    next.add(updated);
                } else if(session != null) {
                    next.add(session);
                }
            }
            if(updated != null) {
                writeSessions(userId, next);
            }
            return updated;
        }
    }

    public boolean deleteSession(String userId, String sessionId) {
        if(sessionId == null || sessionId.isBlank()) {
            return false;
        }
        boolean removed = false;
        synchronized(lock) {
            List<Session> sessions = readSessions(userId);
            List<Session> next = new ArrayList<>(sessions.size());
            for(Session session : sessions) {
                if(session != null && sessionId.equals(session.id)) {
                    removed = true;
                } else if(session != null) {
                    next.add(session);
                }
            }
            if(removed) {
                writeSessions(userId, next);
            }
        }
        File messagesFile = messagesFile(userId, sessionId);
        if(messagesFile != null) {
            try {
                Files.deleteIfExists(messagesFile.toPath());
            }
            catch(IOException ex) {
                return removed;
            }
        }
        return removed;
    }

    public String getStoragePreference(String userId) {
        synchronized(lock) {
            File prefsFile = prefsFile(userId);
            if(prefsFile == null || !prefsFile.isFile()) {
                return null;
            }
            String json = readFile(prefsFile);
            if(json == null || json.isBlank()) {
                return null;
            }
            Object parsed;
            try {
                parsed = SimpleJsonParser.parse(json);
            }
            catch(ParseException ex) {
                return null;
            }
            if(!(parsed instanceof Map)) {
                return null;
            }
            Map<?, ?> map = (Map<?, ?>) parsed;
            return stringValue(map.get("storage_preference"));
        }
    }

    public String setStoragePreference(String userId, String value) {
        synchronized(lock) {
            File prefsFile = prefsFile(userId);
            if(prefsFile == null) {
                return null;
            }
            String preference = safeString(value);
            String json = "{"
                    + "\"storage_preference\":" + jsonString(preference)
                    + "}";
            writeFile(prefsFile, json);
            return preference;
        }
    }

    public ImportResult importStorage(String userId,
                                     List<Session> incomingSessions,
                                     Map<String, List<Message>> incomingMessages,
                                     String storagePreference) {
        synchronized(lock) {
            int sessionCount = 0;
            int messageCount = 0;
            Map<String, Session> mergedSessions = new LinkedHashMap<>();
            for(Session session : readSessions(userId)) {
                if(session != null && session.id != null) {
                    mergedSessions.put(session.id, session);
                }
            }
            if(incomingSessions != null) {
                for(Session session : incomingSessions) {
                    if(session == null || session.id == null || session.id.isBlank()) {
                        continue;
                    }
                    Session existing = mergedSessions.get(session.id);
                    if(existing == null) {
                        mergedSessions.put(session.id, session);
                        sessionCount++;
                        continue;
                    }
                    String title = !safeString(existing.title).isBlank()
                            ? existing.title
                            : session.title;
                    String createdAt = !safeString(existing.createdAt).isBlank()
                            ? existing.createdAt
                            : session.createdAt;
                    String lastMessageAt = maxTimestamp(existing.lastMessageAt, session.lastMessageAt);
                    mergedSessions.put(session.id, new Session(session.id, title, createdAt, lastMessageAt));
                }
            }
            if(!mergedSessions.isEmpty()) {
                writeSessions(userId, new ArrayList<>(mergedSessions.values()));
            }
            if(incomingMessages != null) {
                for(Map.Entry<String, List<Message>> entry : incomingMessages.entrySet()) {
                    String sessionId = entry.getKey();
                    if(sessionId == null || sessionId.isBlank()) {
                        continue;
                    }
                    List<Message> incoming = entry.getValue();
                    if(incoming == null || incoming.isEmpty()) {
                        continue;
                    }
                    List<Message> existing = readMessages(userId, sessionId);
                    Map<String, Message> merged = new LinkedHashMap<>();
                    for(Message message : existing) {
                        if(message != null && message.id != null) {
                            merged.put(message.id, message);
                        }
                    }
                    for(Message message : incoming) {
                        if(message == null) {
                            continue;
                        }
                        String id = message.id != null ? message.id : UUID.randomUUID().toString();
                        if(!merged.containsKey(id)) {
                            merged.put(id, new Message(id, message.role, message.content, message.createdAt));
                            messageCount++;
                        }
                    }
                    List<Message> mergedList = new ArrayList<>(merged.values());
                    mergedList.sort(Comparator.comparing(m -> safeString(m.createdAt)));
                    writeMessages(userId, sessionId, mergedList);
                    updateSessionAfterMessage(userId, sessionId, latestMessageTimestamp(mergedList));
                    if(!mergedSessions.containsKey(sessionId)) {
                        Session fallback = new Session(sessionId, "", earliestMessageTimestamp(mergedList),
                                latestMessageTimestamp(mergedList));
                        mergedSessions.put(sessionId, fallback);
                    }
                }
                if(!mergedSessions.isEmpty()) {
                    writeSessions(userId, new ArrayList<>(mergedSessions.values()));
                }
            }
            if(storagePreference != null && !storagePreference.isBlank()) {
                setStoragePreference(userId, storagePreference);
            }
            return new ImportResult(sessionCount, messageCount);
        }
    }

    public static List<Session> parseSessions(Object value) {
        if(!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> items = (List<?>) value;
        List<Session> sessions = new ArrayList<>();
        for(Object item : items) {
            Session session = parseSession(item);
            if(session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    public static List<Message> parseMessages(Object value) {
        if(!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> items = (List<?>) value;
        List<Message> messages = new ArrayList<>();
        for(Object item : items) {
            Message message = parseMessage(item);
            if(message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    public static Map<String, List<Message>> parseMessageMap(Object value) {
        if(!(value instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<?, ?> map = (Map<?, ?>) value;
        Map<String, List<Message>> result = new LinkedHashMap<>();
        for(Map.Entry<?, ?> entry : map.entrySet()) {
            String sessionId = stringValue(entry.getKey());
            if(sessionId == null || sessionId.isBlank()) {
                continue;
            }
            List<Message> messages = parseMessages(entry.getValue());
            if(!messages.isEmpty()) {
                result.put(sessionId, messages);
            }
        }
        return result;
    }

    public static Session parseSession(Object value) {
        if(!(value instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        String id = stringValue(map.get("id"));
        if(id == null || id.isBlank()) {
            return null;
        }
        String title = safeString(stringValue(map.get("title")));
        String createdAt = safeString(stringValue(map.get("created_at")));
        String lastMessageAt = safeString(stringValue(map.get("last_message_at")));
        if(createdAt.isBlank()) {
            createdAt = Instant.now().toString();
        }
        if(lastMessageAt.isBlank()) {
            lastMessageAt = createdAt;
        }
        return new Session(id, title, createdAt, lastMessageAt);
    }

    public static Message parseMessage(Object value) {
        if(!(value instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        String content = safeString(stringValue(map.get("content")));
        if(content.isBlank()) {
            return null;
        }
        String id = stringValue(map.get("id"));
        if(id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        String role = safeString(stringValue(map.get("role")));
        if(role.isBlank()) {
            role = "user";
        }
        String createdAt = safeString(stringValue(map.get("created_at")));
        if(createdAt.isBlank()) {
            createdAt = Instant.now().toString();
        }
        return new Message(id, role, content, createdAt);
    }

    private List<Session> readSessions(String userId) {
        File sessionsFile = sessionsFile(userId);
        if(sessionsFile == null || !sessionsFile.isFile()) {
            return new ArrayList<>();
        }
        String json = readFile(sessionsFile);
        if(json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        Object parsed;
        try {
            parsed = SimpleJsonParser.parse(json);
        }
        catch(ParseException ex) {
            return new ArrayList<>();
        }
        return new ArrayList<>(parseSessions(parsed));
    }

    private void writeSessions(String userId, List<Session> sessions) {
        File sessionsFile = sessionsFile(userId);
        if(sessionsFile == null) {
            return;
        }
        String json = toJsonSessions(sessions);
        writeFile(sessionsFile, json);
    }

    private List<Message> readMessages(String userId, String sessionId) {
        File messagesFile = messagesFile(userId, sessionId);
        if(messagesFile == null || !messagesFile.isFile()) {
            return new ArrayList<>();
        }
        String json = readFile(messagesFile);
        if(json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        Object parsed;
        try {
            parsed = SimpleJsonParser.parse(json);
        }
        catch(ParseException ex) {
            return new ArrayList<>();
        }
        return new ArrayList<>(parseMessages(parsed));
    }

    private void writeMessages(String userId, String sessionId, List<Message> messages) {
        File messagesFile = messagesFile(userId, sessionId);
        if(messagesFile == null) {
            return;
        }
        String json = toJsonMessages(messages);
        writeFile(messagesFile, json);
    }

    private void updateSessionAfterMessage(String userId, String sessionId, String createdAt) {
        String timestamp = safeString(createdAt);
        if(timestamp.isBlank()) {
            timestamp = Instant.now().toString();
        }
        List<Session> sessions = readSessions(userId);
        Session updated = null;
        List<Session> next = new ArrayList<>(sessions.size() + 1);
        for(Session session : sessions) {
            if(session != null && sessionId.equals(session.id)) {
                String lastMessageAt = maxTimestamp(session.lastMessageAt, timestamp);
                updated = new Session(session.id, session.title, session.createdAt, lastMessageAt);
                next.add(updated);
            } else if(session != null) {
                next.add(session);
            }
        }
        if(updated == null) {
            Session session = new Session(sessionId, "", timestamp, timestamp);
            next.add(session);
        }
        writeSessions(userId, next);
    }

    private File sessionsFile(String userId) {
        File userDir = userDir(userId, false);
        if(userDir == null) {
            return null;
        }
        return new File(userDir, "sessions.json");
    }

    private File messagesFile(String userId, String sessionId) {
        if(sessionId == null || sessionId.isBlank()) {
            return null;
        }
        File userDir = userDir(userId, false);
        if(userDir == null) {
            return null;
        }
        File messagesDir = new File(userDir, "messages");
        if(!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        return new File(messagesDir, sanitizeId(sessionId) + ".json");
    }

    private File prefsFile(String userId) {
        File userDir = userDir(userId, true);
        if(userDir == null) {
            return null;
        }
        return new File(userDir, "preferences.json");
    }

    private File userDir(String userId, boolean create) {
        String safeUserId = sanitizeId(userId);
        if(safeUserId.isBlank()) {
            return null;
        }
        if(!rootDir.exists() && create) {
            rootDir.mkdirs();
        }
        File dir = new File(rootDir, safeUserId);
        if(create && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private String toJsonSessions(List<Session> sessions) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if(sessions != null) {
            boolean first = true;
            for(Session session : sessions) {
                if(session == null) {
                    continue;
                }
                if(!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("{");
                sb.append("\"id\":").append(jsonString(session.id)).append(",");
                sb.append("\"title\":").append(jsonString(session.title)).append(",");
                sb.append("\"created_at\":").append(jsonString(session.createdAt)).append(",");
                sb.append("\"last_message_at\":").append(jsonString(session.lastMessageAt));
                sb.append("}");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if(messages != null) {
            boolean first = true;
            for(Message message : messages) {
                if(message == null) {
                    continue;
                }
                if(!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("{");
                sb.append("\"id\":").append(jsonString(message.id)).append(",");
                sb.append("\"role\":").append(jsonString(message.role)).append(",");
                sb.append("\"content\":").append(jsonString(message.content)).append(",");
                sb.append("\"created_at\":").append(jsonString(message.createdAt));
                sb.append("}");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String readFile(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
        catch(IOException ex) {
            return null;
        }
    }

    private void writeFile(File file, String content) {
        if(file == null) {
            return;
        }
        File parent = file.getParentFile();
        if(parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Path target = file.toPath();
        try {
            Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.writeString(temp, content == null ? "" : content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch(AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch(IOException ex) {
            // Ignore failed writes.
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static String sanitizeId(String value) {
        if(value == null) {
            return "";
        }
        String trimmed = value.trim();
        if(trimmed.isBlank()) {
            return "";
        }
        return trimmed.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static String stringValue(Object value) {
        if(value == null) {
            return null;
        }
        if(value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    private static String jsonString(String value) {
        return "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        if(value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch(c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if(c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String maxTimestamp(String a, String b) {
        String left = safeString(a);
        String right = safeString(b);
        if(left.isBlank()) {
            return right;
        }
        if(right.isBlank()) {
            return left;
        }
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static String latestMessageTimestamp(List<Message> messages) {
        String latest = "";
        if(messages == null) {
            return latest;
        }
        for(Message message : messages) {
            latest = maxTimestamp(latest, message != null ? message.createdAt : "");
        }
        return latest;
    }

    private static String earliestMessageTimestamp(List<Message> messages) {
        String earliest = "";
        if(messages == null) {
            return earliest;
        }
        for(Message message : messages) {
            String ts = message != null ? safeString(message.createdAt) : "";
            if(ts.isBlank()) {
                continue;
            }
            if(earliest.isBlank() || ts.compareTo(earliest) < 0) {
                earliest = ts;
            }
        }
        return earliest;
    }
}
