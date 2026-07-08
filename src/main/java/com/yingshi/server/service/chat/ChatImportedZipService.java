package com.yingshi.server.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.domain.chat.ImportedChatEntity;
import com.yingshi.server.domain.chat.ImportedMessageEntity;
import com.yingshi.server.domain.chat.ImportedMessageSearchEntity;
import com.yingshi.server.domain.chat.ImportedParticipantEntity;
import com.yingshi.server.domain.chat.ImportedResourceEntity;
import com.yingshi.server.repository.chat.ImportedChatRepository;
import com.yingshi.server.repository.chat.ImportedMessageRepository;
import com.yingshi.server.repository.chat.ImportedMessageSearchRepository;
import com.yingshi.server.repository.chat.ImportedParticipantRepository;
import com.yingshi.server.repository.chat.ImportedResourceRepository;
import com.yingshi.server.service.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses QCE-format ZIP exports and imports chats, messages, participants,
 * resources, and search rows into the imported-chat tables.
 * <p>
 * Expected ZIP layout:
 * <pre>
 *   manifest.json            (at root or inside a single top-level directory)
 *   chunks/001.jsonl         (one or more JSONL files referenced by the manifest)
 *   resources/...            (media files referenced by resource localPath)
 * </pre>
 */
@Service
public class ChatImportedZipService {

    private static final Logger log = LoggerFactory.getLogger(ChatImportedZipService.class);

    /** Safety limit: 500 MB unzipped content. */
    private static final long MAX_UNCOMPRESSED_BYTES = 500L * 1024 * 1024;

    private final ImportedChatRepository chatRepository;
    private final ImportedMessageRepository messageRepository;
    private final ImportedParticipantRepository participantRepository;
    private final ImportedResourceRepository resourceRepository;
    private final ImportedMessageSearchRepository messageSearchRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;

    public ChatImportedZipService(
            ImportedChatRepository chatRepository,
            ImportedMessageRepository messageRepository,
            ImportedParticipantRepository participantRepository,
            ImportedResourceRepository resourceRepository,
            ImportedMessageSearchRepository messageSearchRepository,
            ObjectStorageService objectStorageService,
            ObjectMapper objectMapper
    ) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.resourceRepository = resourceRepository;
        this.messageSearchRepository = messageSearchRepository;
        this.objectStorageService = objectStorageService;
        this.objectMapper = objectMapper;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Import a QCE ZIP archive.
     *
     * @param zipBytes  raw ZIP bytes
     * @param libraryId target library scope
     * @return stats map with keys: chats, messages, resources, mediaStored
     */
    @Transactional
    public Map<String, Object> importZip(byte[] zipBytes, String libraryId) {
        // 1. Explode the ZIP into an entry-name -> byte[] map
        Map<String, byte[]> zipEntries;
        try {
            zipEntries = readZipEntries(zipBytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read ZIP archive: " + e.getMessage(), e);
        }

        // 2. Locate and parse manifest.json
        JsonNode manifest = findAndParseManifest(zipEntries);
        JsonNode chatsNode = manifest.get("chats");
        if (chatsNode == null || !chatsNode.isArray()) {
            throw new IllegalArgumentException("manifest.json does not contain a 'chats' array");
        }

        // 3. Pre-parse all JSONL chunk files
        Map<String, List<JsonNode>> chunkData = collectChunkData(zipEntries);

        // 4. Process each chat
        int totalMessages = 0;
        int totalResources = 0;
        int totalMediaStored = 0;
        int chatCount = 0;

        for (JsonNode chatNode : chatsNode) {
            String chatStableKey = resolveChatStableKey(chatNode);

            // --- Chat entity (upsert by stable key) ---
            ImportedChatEntity chatEntity = chatRepository
                    .findByLibraryIdAndChatStableKey(libraryId, chatStableKey)
                    .orElseGet(() -> {
                        ImportedChatEntity e = new ImportedChatEntity();
                        e.setId(IdGenerator.newId("ichat"));
                        e.setLibraryId(libraryId);
                        e.setChatStableKey(chatStableKey);
                        return e;
                    });

            chatEntity.setDisplayName(textOrNull(chatNode, "displayName"));
            chatEntity.setChatType(normalizeChatType(textOrNull(chatNode, "chatType")));
            chatEntity.setPeerUid(textOrNull(chatNode, "peerUid"));
            chatEntity.setSelfUid(textOrNull(chatNode, "selfUid"));
            chatEntity.setLastImportAt(Instant.now());

            // --- Participants ---
            List<ImportedParticipantEntity> participants =
                    processParticipants(chatNode, chatEntity.getId(), libraryId, chatStableKey);
            participantRepository.saveAll(participants);

            // --- Messages (from chunk files) ---
            List<JsonNode> allMessages = collectChatMessages(chatNode, chunkData);
            List<ImportedMessageEntity> savedMessages = new ArrayList<>();
            List<ImportedResourceEntity> allResources = new ArrayList<>();
            List<ImportedMessageSearchEntity> allSearchRows = new ArrayList<>();
            int[] mediaCounter = {0};

            for (JsonNode msgNode : allMessages) {
                processMessage(
                        msgNode, chatEntity.getId(), libraryId, chatStableKey,
                        zipEntries, savedMessages, allResources, allSearchRows, mediaCounter
                );
            }

            messageRepository.saveAll(savedMessages);
            resourceRepository.saveAll(allResources);
            messageSearchRepository.saveAll(allSearchRows);

            // Update chat summary fields
            chatEntity.setMessageCount(savedMessages.size());
            chatEntity.setLastInsertedCount(savedMessages.size());
            chatEntity.setLastMessagePreview(buildPreview(allMessages));
            chatRepository.save(chatEntity);

            chatCount++;
            totalMessages += savedMessages.size();
            totalResources += allResources.size();
            totalMediaStored += mediaCounter[0];
        }

        // 5. Return stats
        Map<String, Object> stats = new HashMap<>();
        stats.put("chats", chatCount);
        stats.put("messages", totalMessages);
        stats.put("resources", totalResources);
        stats.put("mediaStored", totalMediaStored);
        return stats;
    }

    // -----------------------------------------------------------------------
    // ZIP reading
    // -----------------------------------------------------------------------

    private Map<String, byte[]> readZipEntries(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            long totalSize = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                    totalSize += len;
                    if (totalSize > MAX_UNCOMPRESSED_BYTES) {
                        throw new IOException(
                                "ZIP content exceeds maximum allowed size of " + MAX_UNCOMPRESSED_BYTES + " bytes");
                    }
                }
                entries.put(name, baos.toByteArray());
            }
        }
        return entries;
    }

    // -----------------------------------------------------------------------
    // Manifest parsing
    // -----------------------------------------------------------------------

    private JsonNode findAndParseManifest(Map<String, byte[]> zipEntries) {
        // Try root-level manifest.json first
        byte[] manifestBytes = zipEntries.get("manifest.json");

        // Otherwise look inside a single top-level directory
        if (manifestBytes == null) {
            for (Map.Entry<String, byte[]> e : zipEntries.entrySet()) {
                String key = e.getKey();
                if (key.endsWith("/manifest.json")) {
                    manifestBytes = e.getValue();
                    break;
                }
            }
        }

        if (manifestBytes == null) {
            throw new IllegalArgumentException("manifest.json not found in ZIP archive");
        }

        try {
            return objectMapper.readTree(manifestBytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse manifest.json: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Chunk collection
    // -----------------------------------------------------------------------

    /**
     * Pre-parse every JSONL chunk file found in the ZIP,
     * returning a map of chunk-path -> list-of-JsonNode.
     */
    private Map<String, List<JsonNode>> collectChunkData(Map<String, byte[]> zipEntries) {
        Map<String, List<JsonNode>> result = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith(".jsonl")) {
                result.put(name, parseJsonl(entry.getValue(), name));
            }
        }
        return result;
    }

    /**
     * Gather all JSONL message nodes for a single chat, in chunk order.
     */
    private List<JsonNode> collectChatMessages(JsonNode chatNode, Map<String, List<JsonNode>> chunkData) {
        List<JsonNode> messages = new ArrayList<>();
        JsonNode chunkFiles = chatNode.get("chunkFiles");
        if (chunkFiles != null && chunkFiles.isArray()) {
            for (JsonNode cf : chunkFiles) {
                String path = cf.asText();
                List<JsonNode> parsed = chunkData.get(path);
                if (parsed != null) {
                    messages.addAll(parsed);
                } else {
                    log.warn("Chunk file '{}' referenced in manifest but not found in ZIP", path);
                }
            }
        }
        return messages;
    }

    private List<JsonNode> parseJsonl(byte[] data, String sourceName) {
        List<JsonNode> nodes = new ArrayList<>();
        String content = new String(data, StandardCharsets.UTF_8);
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                nodes.add(objectMapper.readTree(line));
            } catch (IOException e) {
                log.warn("Skipping malformed JSONL line {} in {}: {}", i + 1, sourceName, e.getMessage());
            }
        }
        return nodes;
    }

    // -----------------------------------------------------------------------
    // Participant processing
    // -----------------------------------------------------------------------

    private List<ImportedParticipantEntity> processParticipants(
            JsonNode chatNode, String chatId, String libraryId, String chatStableKey
    ) {
        List<ImportedParticipantEntity> participants = new ArrayList<>();
        JsonNode participantsNode = chatNode.get("participants");
        if (participantsNode == null || !participantsNode.isArray()) {
            return participants;
        }

        String selfUid = textOrNull(chatNode, "selfUid");

        for (JsonNode pNode : participantsNode) {
            String uid = textOrNull(pNode, "uid");
            String participantStableKey = chatStableKey + ":p:" + (uid != null ? uid : UUID.randomUUID());

            ImportedParticipantEntity entity = participantRepository
                    .findByLibraryIdAndParticipantStableKey(libraryId, participantStableKey)
                    .orElseGet(() -> {
                        ImportedParticipantEntity e = new ImportedParticipantEntity();
                        e.setLibraryId(libraryId);
                        e.setParticipantStableKey(participantStableKey);
                        return e;
                    });

            entity.setChatId(chatId);
            entity.setUid(uid);
            entity.setUin(textOrNull(pNode, "uin"));
            entity.setDisplayName(textOrNull(pNode, "name"));
            entity.setAvatarLocalPath(textOrNull(pNode, "avatarPath"));
            entity.setIsSelf(uid != null && uid.equals(selfUid));

            participants.add(entity);
        }
        return participants;
    }

    // -----------------------------------------------------------------------
    // Message processing
    // -----------------------------------------------------------------------

    /**
     * Process a single JSONL message node: create the message entity,
     * any resource entities (with file upload), and the full-text search row.
     */
    private void processMessage(
            JsonNode msgNode,
            String chatId,
            String libraryId,
            String chatStableKey,
            Map<String, byte[]> zipEntries,
            List<ImportedMessageEntity> savedMessages,
            List<ImportedResourceEntity> allResources,
            List<ImportedMessageSearchEntity> allSearchRows,
            int[] mediaCounter
    ) {
        String sourceMessageId = textOrNull(msgNode, "messageId");
        String messageStableKey = chatStableKey + ":m:" +
                (sourceMessageId != null ? sourceMessageId : UUID.randomUUID());

        // --- Message entity ---
        ImportedMessageEntity msg = new ImportedMessageEntity();
        msg.setLibraryId(libraryId);
        msg.setChatId(chatId);
        msg.setMessageStableKey(messageStableKey);
        msg.setSourceMessageId(sourceMessageId);
        msg.setSenderStableKey(chatStableKey + ":u:" + textOrDefault(msgNode, "senderUid", "unknown"));
        msg.setSenderDisplayName(textOrNull(msgNode, "senderName"));
        msg.setSenderUin(textOrNull(msgNode, "senderUin"));

        long ts = msgNode.has("timestamp") ? msgNode.get("timestamp").asLong(0) : 0;
        msg.setTimestamp(ts > 0 ? Instant.ofEpochMilli(ts) : Instant.now());

        String rawType = textOrDefault(msgNode, "type", "text");
        msg.setMsgType(rawType.toUpperCase());
        msg.setText(textOrNull(msgNode, "text"));
        msg.setRawContentJson(serializeRawContent(msgNode));

        // Reply reference
        JsonNode replyTo = msgNode.get("replyTo");
        if (replyTo != null && !replyTo.isNull()) {
            msg.setReplyRefMessageId(textOrNull(replyTo, "messageId"));
            msg.setReplyRefSenderName(textOrNull(replyTo, "senderName"));
            msg.setReplyRefText(textOrNull(replyTo, "text"));
        }

        // Optional fields
        msg.setJsonTitle(textOrNull(msgNode, "jsonTitle"));
        msg.setJsonSummary(textOrNull(msgNode, "jsonSummary"));
        msg.setCallSummary(textOrNull(msgNode, "callSummary"));
        msg.setRecalled(boolOrFalse(msgNode, "recalled"));
        msg.setSystemMessage(boolOrFalse(msgNode, "systemMessage"));

        // Search text = message text for full-text lookup
        String searchText = textOrNull(msgNode, "text");
        msg.setSearchText(searchText);

        // Save message first so it gets an ID (needed for resources & search)
        msg = messageRepository.save(msg);
        savedMessages.add(msg);

        // --- Search entity ---
        if (searchText != null && !searchText.isBlank()) {
            ImportedMessageSearchEntity search = new ImportedMessageSearchEntity();
            search.setMessageId(msg.getId());
            search.setLibraryId(libraryId);
            search.setChatId(chatId);
            search.setMessageStableKey(messageStableKey);
            search.setSearchText(searchText);
            allSearchRows.add(search);
        }

        // --- Resource entities ---
        JsonNode resourcesNode = msgNode.get("resources");
        if (resourcesNode != null && resourcesNode.isArray()) {
            int ordinal = 0;
            for (JsonNode resNode : resourcesNode) {
                processResource(
                        resNode, msg.getId(), libraryId, chatStableKey, ordinal,
                        zipEntries, allResources, mediaCounter
                );
                ordinal++;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Resource processing
    // -----------------------------------------------------------------------

    private void processResource(
            JsonNode resNode,
            Long messageId,
            String libraryId,
            String chatStableKey,
            int ordinal,
            Map<String, byte[]> zipEntries,
            List<ImportedResourceEntity> allResources,
            int[] mediaCounter
    ) {
        String resType = textOrDefault(resNode, "type", "file");
        String fileName = textOrNull(resNode, "fileName");
        String localPath = textOrNull(resNode, "localPath");
        String md5 = textOrNull(resNode, "md5");

        ImportedResourceEntity resource = new ImportedResourceEntity();
        resource.setLibraryId(libraryId);
        resource.setMessageId(messageId);
        resource.setOrdinal(ordinal);
        resource.setResType(resType.toUpperCase());
        resource.setRenderKind(mapRenderKind(resType));
        resource.setStoredFileName(fileName);
        resource.setMimeType(textOrNull(resNode, "mimeType"));
        resource.setMd5(md5);
        resource.setWidthPx(intOrNull(resNode, "width"));
        resource.setHeightPx(intOrNull(resNode, "height"));
        resource.setDurationSeconds(intOrNull(resNode, "duration"));
        resource.setFileSizeBytes(longOrNull(resNode, "sizeBytes"));

        // Build the target object key
        String storedObjectKey = "chat-imports/" + libraryId + "/" + chatStableKey +
                "/resources/" + UUID.randomUUID() + "_" + safeFileName(fileName);
        resource.setStoredObjectKey(storedObjectKey);

        // Try to find and upload the actual resource file from the ZIP
        byte[] resourceBytes = findResourceInZip(localPath, fileName, zipEntries);
        if (resourceBytes != null && resourceBytes.length > 0) {
            try {
                String contentType = resource.getMimeType();
                if (contentType == null || contentType.isBlank()) {
                    contentType = "application/octet-stream";
                }
                objectStorageService.put(
                        storedObjectKey,
                        contentType,
                        (long) resourceBytes.length,
                        new ByteArrayInputStream(resourceBytes)
                );
                resource.setFileSizeBytes((long) resourceBytes.length);
                mediaCounter[0]++;
                log.debug("Stored resource {} ({} bytes) -> {}", storedObjectKey, resourceBytes.length, fileName);
            } catch (Exception e) {
                log.warn("Failed to store resource file '{}' to object storage: {}",
                        storedObjectKey, e.getMessage());
                // Keep the entity but without confirmed storage
            }
        } else {
            log.debug("Resource file not found in ZIP for localPath='{}', fileName='{}'", localPath, fileName);
        }

        allResources.add(resource);
    }

    /**
     * Attempt to locate a resource file inside the ZIP entries.
     * Tries the localPath first, then falls back to matching by fileName suffix.
     */
    private byte[] findResourceInZip(String localPath, String fileName, Map<String, byte[]> zipEntries) {
        // 1. Try exact localPath match
        if (localPath != null && !localPath.isBlank()) {
            byte[] data = zipEntries.get(localPath);
            if (data != null) {
                return data;
            }
            // Try stripping leading "resources/" prefix variations
            String normalized = localPath.replace('\\', '/');
            data = zipEntries.get(normalized);
            if (data != null) {
                return data;
            }
        }

        // 2. Try to find by matching the fileName at the end of any ZIP entry path
        if (fileName != null && !fileName.isBlank()) {
            String suffix = "/" + fileName;
            for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
                if (entry.getKey().endsWith(suffix) || entry.getKey().equals(fileName)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String resolveChatStableKey(JsonNode chatNode) {
        String peerUid = textOrNull(chatNode, "peerUid");
        if (peerUid != null && !peerUid.isBlank()) {
            return peerUid;
        }
        String chatId = textOrNull(chatNode, "chatId");
        return chatId != null ? chatId : UUID.randomUUID().toString();
    }

    private String normalizeChatType(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        return switch (raw.toLowerCase()) {
            case "private", "friend" -> "PRIVATE";
            case "group" -> "GROUP";
            default -> raw.toUpperCase();
        };
    }

    private String mapRenderKind(String resType) {
        if (resType == null) return "UNKNOWN";
        return switch (resType.toLowerCase()) {
            case "image" -> "IMAGE";
            case "video" -> "VIDEO";
            case "audio" -> "AUDIO";
            case "file" -> "FILE";
            default -> "UNKNOWN";
        };
    }

    private String buildPreview(List<JsonNode> messages) {
        // Walk backwards to find the last non-system, non-recalled text message
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode msg = messages.get(i);
            if (boolOrFalse(msg, "systemMessage") || boolOrFalse(msg, "recalled")) {
                continue;
            }
            String text = textOrNull(msg, "text");
            if (text != null && !text.isBlank()) {
                return text.length() > 200 ? text.substring(0, 200) : text;
            }
        }
        return null;
    }

    /**
     * Serialize the full JSONL line as raw content JSON for audit / replay.
     */
    private String serializeRawContent(JsonNode msgNode) {
        try {
            return objectMapper.writeValueAsString(msgNode);
        } catch (IOException e) {
            log.warn("Failed to serialize raw message content: {}", e.getMessage());
            return null;
        }
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        // Strip path separators
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name;
    }

    // -- JSON node accessors --------------------------------------------------

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return null;
        String s = child.asText();
        return (s != null && !s.isBlank()) ? s : null;
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String s = textOrNull(node, field);
        return s != null ? s : defaultValue;
    }

    private static boolean boolOrFalse(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && child.asBoolean(false);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return null;
        return child.asInt(0);
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return null;
        return child.asLong(0);
    }
}
