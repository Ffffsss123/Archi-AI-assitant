package com.cwdil.archi.genai.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.Test;

import com.cwdil.archi.genai.storage.LocalChatHistoryStore;
import com.cwdil.archi.genai.storage.LocalChatHistoryStore.ListResult;
import com.cwdil.archi.genai.storage.LocalChatHistoryStore.Message;
import com.cwdil.archi.genai.storage.LocalChatHistoryStore.Session;

public class LocalChatHistoryStoreTest {

    @Test
    public void storesSessionsPerUser() throws Exception {
        Path tempDir = Files.createTempDirectory("chat-history-test");
        try {
            LocalChatHistoryStore store = new LocalChatHistoryStore(tempDir.toFile());
            Session userA = store.createSession("user-a", "Alpha");
            store.createSession("user-b", "Beta");

            ListResult listA = store.listSessions("user-a", 10, null);
            assertEquals(1, listA.sessions.size());
            assertEquals(userA.id, listA.sessions.get(0).id);

            ListResult listB = store.listSessions("user-b", 10, null);
            assertEquals(1, listB.sessions.size());
            assertEquals("Beta", listB.sessions.get(0).title);
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void appendMessageUpdatesLastMessageAt() throws Exception {
        Path tempDir = Files.createTempDirectory("chat-history-test");
        try {
            LocalChatHistoryStore store = new LocalChatHistoryStore(tempDir.toFile());
            Session session = store.createSession("user-a", "");
            Message message = new Message("m1", "user", "hello", "2025-01-01T00:00:00Z");
            store.appendMessage("user-a", session.id, message);

            List<Message> messages = store.getMessages("user-a", session.id);
            assertEquals(1, messages.size());
            assertEquals("hello", messages.get(0).content);

            ListResult list = store.listSessions("user-a", 10, null);
            assertEquals(1, list.sessions.size());
            assertEquals("2025-01-01T00:00:00Z", list.sessions.get(0).lastMessageAt);
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void renameSessionUpdatesTitle() throws Exception {
        Path tempDir = Files.createTempDirectory("chat-history-test");
        try {
            LocalChatHistoryStore store = new LocalChatHistoryStore(tempDir.toFile());
            Session session = store.createSession("user-a", "Old title");
            Session updated = store.renameSession("user-a", session.id, "New title");

            assertNotNull(updated);
            assertEquals("New title", updated.title);

            ListResult list = store.listSessions("user-a", 10, null);
            assertEquals("New title", list.sessions.get(0).title);
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if(path == null || !Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(item -> {
                    try {
                        Files.deleteIfExists(item);
                    }
                    catch(IOException ex) {
                        // Ignore cleanup failure.
                    }
                });
    }
}
