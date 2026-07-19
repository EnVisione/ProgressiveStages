package com.enviouse.progressivestages.server.editor;

import com.enviouse.progressivestages.common.api.ProgressiveStagesRehaulAPI;
import com.enviouse.progressivestages.common.config.ConfigPaths;
import com.enviouse.progressivestages.common.rehaul.catalog.CatalogQuery;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionMetadataRegistry;
import com.enviouse.progressivestages.common.rehaul.schema.EditorSchemaRegistry;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EditorSessionService {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_REQUEST_BYTES = 1024 * 1024;
    public static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final long SESSION_MILLIS = 30 * 60 * 1000L;
    private static final EditorSessionService INSTANCE = new EditorSessionService();
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, EditorDraft> drafts = new ConcurrentHashMap<>();
    private volatile EditorApplyService applyService;

    private EditorSessionService() {}

    public static EditorSessionService get() { return INSTANCE; }

    public synchronized EditorSessionOpen open(ServerPlayer operator) {
        if (!operator.hasPermissions(3)) throw new SecurityException("Operator permission is required");
        cleanup();
        long configuration = StageFileLoader.getInstance().getCompiledSnapshot().revision();
        long catalog = com.enviouse.progressivestages.common.rehaul.catalog.EditorCatalogService.get().snapshot().revision();
        UUID draftId = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(draftId, operator.getUUID(), configuration, catalog, loadFiles());
        drafts.put(draftId, draft);
        persist(draft);
        return openSession(operator, draft);
    }

    public synchronized EditorSessionOpen resume(ServerPlayer operator, UUID draftId) {
        if (!operator.hasPermissions(3)) throw new SecurityException("Operator permission is required");
        cleanup();
        EditorDraft draft = drafts.computeIfAbsent(draftId, this::loadDraft);
        if (draft == null) throw new IllegalArgumentException("The editor draft was not found");
        if (!draft.owner().equals(operator.getUUID()) && !draft.collaborators().contains(operator.getUUID())) {
            throw new SecurityException("The operator cannot resume this draft");
        }
        return openSession(operator, draft);
    }

    private EditorSessionOpen openSession(ServerPlayer operator, EditorDraft draft) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID sessionId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Session session = new Session(sessionId, operator.getUUID(), draft.id(), hash(secret), now,
            now + SESSION_MILLIS, now, draft.baseConfigurationRevision(), draft.baseCatalogRevision());
        sessions.put(sessionId, session);
        if (applyService == null) applyService = new EditorApplyService(ConfigPaths.rootDirectory());
        return new EditorSessionOpen(sessionId, secret, draft.id(), session.expiresAt,
            draft.baseConfigurationRevision(), draft.baseCatalogRevision(), PROTOCOL_VERSION);
    }

    public String handle(ServerPlayer operator, UUID sessionId, String secret, String requestJson) {
        if (requestJson == null || requestJson.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            return error("request_too_large", "The editor request is too large");
        }
        Session session;
        try { session = authorize(operator, sessionId, secret); }
        catch (RuntimeException failure) { return error("unauthorized", failure.getMessage()); }
        JsonObject request;
        try { request = JsonParser.parseString(requestJson).getAsJsonObject(); }
        catch (RuntimeException failure) { return error("invalid_json", "The editor request is not valid JSON"); }
        String action = string(request, "action", "bootstrap");
        EditorDraft draft = drafts.get(session.draftId);
        if (draft == null) return error("missing_draft", "The editor draft no longer exists");
        try {
            Object response = switch (action) {
                case "bootstrap" -> bootstrap(session, draft);
                case "catalog" -> catalog(request);
                case "mutate" -> mutate(operator, draft, request);
                case "undo" -> revision(draft.undo(operator.getUUID(), number(request, "revision", -1)));
                case "redo" -> revision(draft.redo(operator.getUUID(), number(request, "revision", -1)));
                case "validate" -> EditorDraftValidator.validate(draft.files(), draft.revision());
                case "review" -> Map.of("revision", draft.revision(), "diff", draft.diff(),
                    "validation", EditorDraftValidator.validate(draft.files(), draft.revision()));
                case "apply" -> apply(operator.server, operator.getUUID(), draft, request);
                case "rollback" -> applyService.rollback(operator.server, operator.getUUID(),
                    string(request, "transaction", ""), bool(request, "confirmed", false));
                case "scaffold" -> scaffold(operator, draft, request);
                case "duplicate_stage" -> duplicateStage(operator, draft, request);
                case "delete_stage" -> deleteStage(operator, draft, request);
                case "rename_stage" -> renameStage(operator, draft, request);
                case "move_stage" -> moveStage(operator, draft, request);
                case "archive_stage" -> archiveStage(operator, draft, request);
                case "restore_stage" -> restoreStage(operator, draft, request);
                case "export_stage" -> exportStage(draft, request);
                case "import_stage" -> importStage(operator, draft, request);
                case "collaborator_add" -> collaborator(operator, draft, request, true);
                case "collaborator_remove" -> collaborator(operator, draft, request, false);
                case "simulate" -> simulate(draft, request);
                case "close" -> { revoke(sessionId); yield Map.of("closed", true); }
                default -> Map.of("error", "unknown_action", "explanation", "Unknown editor action");
            };
            return GSON.toJson(response);
        } catch (EditorDraft.DraftConflictException conflict) {
            return GSON.toJson(Map.of("error", "draft_conflict", "currentRevision", conflict.currentRevision()));
        } catch (RuntimeException failure) {
            return error("request_failed", failure.getMessage());
        }
    }

    public List<EditorSessionView> sessions() {
        cleanup();
        return sessions.values().stream().map(Session::view)
            .sorted(Comparator.comparingLong(EditorSessionView::createdAt)).toList();
    }

    public List<UUID> drafts(UUID operator) {
        loadPersistedDraftIds().forEach(id -> drafts.computeIfAbsent(id, this::loadDraft));
        return drafts.values().stream().filter(java.util.Objects::nonNull)
            .filter(draft -> draft.owner().equals(operator) || draft.collaborators().contains(operator))
            .map(EditorDraft::id).sorted().toList();
    }

    public synchronized boolean discard(UUID actor, UUID draftId) {
        EditorDraft draft = drafts.computeIfAbsent(draftId, this::loadDraft);
        if (draft == null || !draft.owner().equals(actor)) return false;
        drafts.remove(draftId);
        sessions.entrySet().removeIf(entry -> entry.getValue().draftId.equals(draftId));
        try { return Files.deleteIfExists(draftPath(draftId)); }
        catch (IOException error) { return false; }
    }

    public synchronized EditorApplyResult rollback(MinecraftServer server, UUID actor,
                                                   String transaction, boolean confirmed) {
        if (applyService == null) applyService = new EditorApplyService(ConfigPaths.rootDirectory());
        return applyService.rollback(server, actor, transaction, confirmed);
    }

    public boolean revoke(UUID sessionId) {
        Session removed = sessions.remove(sessionId);
        return removed != null;
    }

    public void revokePlayer(UUID player) {
        sessions.entrySet().removeIf(entry -> entry.getValue().owner.equals(player));
    }

    public synchronized void shutdown() {
        sessions.clear();
        drafts.clear();
        applyService = null;
    }

    private Object bootstrap(Session session, EditorDraft draft) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("protocol", PROTOCOL_VERSION);
        out.put("session", session.view());
        out.put("draft", draftView(draft));
        out.put("schemas", EditorSchemaRegistry.get().all());
        out.put("extensions", ExtensionMetadataRegistry.get().snapshot());
        out.put("capabilities", ProgressiveStagesRehaulAPI.capabilities());
        var catalog = com.enviouse.progressivestages.common.rehaul.catalog.EditorCatalogService.get().snapshot();
        out.put("catalog", Map.of("revision", catalog.revision(), "configurationRevision", catalog.configurationRevision(),
            "ids", catalog.catalogIds(), "checksum", catalog.checksum(), "providerErrors", catalog.providerErrors()));
        return out;
    }

    private Object catalog(JsonObject request) {
        ResourceLocation id = ResourceLocation.parse(string(request, "catalog", "progressivestages:items"));
        Map<String, String> filters = new LinkedHashMap<>();
        if (request.has("filters") && request.get("filters").isJsonObject()) {
            request.getAsJsonObject("filters").entrySet().forEach(entry -> filters.put(entry.getKey(), entry.getValue().getAsString()));
        }
        CatalogQuery query = new CatalogQuery(id, string(request, "field", "editor"), string(request, "mode", "id"),
            string(request, "text", ""), filters, string(request, "sort", "relevance"),
            (int) Math.max(1, Math.min(100, number(request, "pageSize", 25))), string(request, "cursor", ""),
            number(request, "catalogRevision", 0));
        return com.enviouse.progressivestages.common.rehaul.catalog.EditorCatalogService.get().search(query);
    }

    private Object mutate(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String content = request.has("content") && !request.get("content").isJsonNull()
            ? request.get("content").getAsString() : null;
        long revision = draft.mutate(operator.getUUID(), number(request, "revision", -1),
            string(request, "path", ""), content);
        persist(draft);
        return Map.of("revision", revision, "diff", draft.diff(), "canUndo", draft.canUndo(), "canRedo", draft.canRedo());
    }

    private Object scaffold(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        ResourceLocation stage = ResourceLocation.parse(string(request, "stage", ""));
        String folder = stage.toString().replace(':', '_').replace('/', '_');
        long revision = number(request, "revision", -1);
        String stageToml = "[schema]\nversion = 4\n\n[stage]\nid = \"" + stage + "\"\ndisplay_name = \"New Stage\"\ndescription = \"Describe this stage\"\n";
        revision = draft.mutate(operator.getUUID(), revision, "stages/" + folder + "/stage.toml", stageToml);
        revision = draft.mutate(operator.getUUID(), revision, "stages/" + folder + "/rules.toml", "[items]\nlocked = []\n");
        revision = draft.mutate(operator.getUUID(), revision, "stages/" + folder + "/progression.toml", "# Add grants, revokes, rewards, and challenges here.\n");
        persist(draft);
        return Map.of("revision", revision, "files", draft.files(), "diff", draft.diff());
    }

    private Object duplicateStage(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String source = stageFolder(string(request, "source", ""));
        ResourceLocation target = ResourceLocation.parse(string(request, "stage", ""));
        String folder = "stages/" + target.toString().replace(':', '_').replace('/', '_') + "/";
        long revision = number(request, "revision", -1);
        Map<String, String> files = draft.files();
        List<String> matches = files.keySet().stream().filter(path -> path.startsWith(source)).sorted().toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("The source stage package was not found");
        String oldId = stageId(files.get(source + "stage.toml"));
        for (String path : matches) {
            String content = files.get(path);
            if (!oldId.isBlank()) content = content.replace(oldId, target.toString());
            revision = draft.mutate(operator.getUUID(), revision, folder + path.substring(source.length()), content);
        }
        persist(draft);
        return draftView(draft);
    }

    private Object deleteStage(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String folder = stageFolder(string(request, "folder", ""));
        long revision = number(request, "revision", -1);
        List<String> matches = draft.files().keySet().stream().filter(path -> path.startsWith(folder)).sorted().toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("The stage package was not found");
        for (String path : matches) revision = draft.mutate(operator.getUUID(), revision, path, null);
        persist(draft);
        return draftView(draft);
    }

    private Object renameStage(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String folder = stageFolder(string(request, "folder", ""));
        ResourceLocation target = ResourceLocation.parse(string(request, "stage", ""));
        String oldId = stageId(draft.files().get(folder + "stage.toml"));
        if (oldId.isBlank()) throw new IllegalArgumentException("The stage package has no stage id");
        String nextFolder = "stages/" + target.toString().replace(':', '_').replace('/', '_') + "/";
        long revision = number(request, "revision", -1);
        Map<String, String> files = draft.files();
        for (String path : files.keySet().stream().sorted().toList()) {
            String content = files.get(path);
            String updated = content.replace(oldId, target.toString());
            if (path.startsWith(folder)) {
                revision = draft.mutate(operator.getUUID(), revision, nextFolder + path.substring(folder.length()), updated);
                revision = draft.mutate(operator.getUUID(), revision, path, null);
            } else if (!updated.equals(content)) revision = draft.mutate(operator.getUUID(), revision, path, updated);
        }
        persist(draft);
        return draftView(draft);
    }

    private Object moveStage(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String source = stageFolder(string(request, "folder", ""));
        String destination = stageDestination(string(request, "destination", ""));
        relocate(operator, draft, source, destination, number(request, "revision", -1));
        return draftView(draft);
    }

    private Object archiveStage(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String source = stageFolder(string(request, "folder", ""));
        String name = source.substring(0, source.length() - 1);
        name = name.substring(name.lastIndexOf('/') + 1);
        relocate(operator, draft, source, "stages/.editor-archive/" + name + "/",
            number(request, "revision", -1));
        return draftView(draft);
    }

    private Object restoreStage(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String source = stageFolder(string(request, "folder", ""));
        if (!source.startsWith("stages/.editor-archive/")) {
            throw new IllegalArgumentException("Select an archived stage package first");
        }
        String name = source.substring("stages/.editor-archive/".length());
        relocate(operator, draft, source, "stages/" + name, number(request, "revision", -1));
        return draftView(draft);
    }

    private Object exportStage(EditorDraft draft, JsonObject request) {
        String source = stageFolder(string(request, "folder", ""));
        Map<String, String> exported = new LinkedHashMap<>();
        draft.files().entrySet().stream().filter(entry -> entry.getKey().startsWith(source))
            .sorted(Map.Entry.comparingByKey()).forEach(entry ->
                exported.put(entry.getKey().substring(source.length()), entry.getValue()));
        if (exported.isEmpty()) throw new IllegalArgumentException("The stage package was not found");
        return Map.of("folder", source, "files", Map.copyOf(exported));
    }

    private Object importStage(ServerPlayer operator, EditorDraft draft, JsonObject request) {
        String destination = stageDestination(string(request, "destination", ""));
        if (!request.has("files") || !request.get("files").isJsonObject()) {
            throw new IllegalArgumentException("An imported package requires a files object");
        }
        Map<String, String> imported = stringMap(request.getAsJsonObject("files"));
        if (!imported.containsKey("stage.toml")) throw new IllegalArgumentException("An imported package requires stage.toml");
        long revision = number(request, "revision", -1);
        for (Map.Entry<String, String> entry : imported.entrySet()) {
            if (!Set.of("stage.toml", "rules.toml", "progression.toml").contains(entry.getKey())) {
                throw new IllegalArgumentException("An imported package contains an unsupported file");
            }
            revision = draft.mutate(operator.getUUID(), revision, destination + entry.getKey(), entry.getValue());
        }
        persist(draft);
        return draftView(draft);
    }

    private void relocate(ServerPlayer operator, EditorDraft draft, String source, String destination,
                          long expectedRevision) {
        Map<String, String> files = draft.files();
        List<String> matches = files.keySet().stream().filter(path -> path.startsWith(source)).sorted().toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("The stage package was not found");
        if (files.keySet().stream().anyMatch(path -> path.startsWith(destination))) {
            throw new IllegalArgumentException("The destination stage package already exists");
        }
        long revision = expectedRevision;
        for (String path : matches) {
            revision = draft.mutate(operator.getUUID(), revision,
                destination + path.substring(source.length()), files.get(path));
            revision = draft.mutate(operator.getUUID(), revision, path, null);
        }
        persist(draft);
    }

    private Object collaborator(ServerPlayer operator, EditorDraft draft, JsonObject request, boolean add) {
        UUID collaborator = UUID.fromString(string(request, "player", ""));
        if (add) draft.addCollaborator(operator.getUUID(), collaborator);
        else draft.removeCollaborator(operator.getUUID(), collaborator);
        persist(draft);
        return Map.of("collaborators", draft.collaborators());
    }

    private Object simulate(EditorDraft draft, JsonObject request) {
        DraftValidation validation = EditorDraftValidator.validate(draft.files(), draft.revision());
        String category = string(request, "category", "");
        String target = string(request, "target", "");
        return Map.of("validation", validation, "category", category, "target", target,
            "diff", draft.diff(), "explanation", validation.valid()
                ? "The candidate compiles. Apply is still required before live player decisions change"
                : "Simulation is blocked until validation succeeds");
    }

    private EditorApplyResult apply(MinecraftServer server, UUID actor, EditorDraft draft, JsonObject request) {
        long current = StageFileLoader.getInstance().getCompiledSnapshot().revision();
        EditorApplyResult result = applyService.apply(server, actor, draft, current,
            bool(request, "confirmed", false));
        if (result.success()) {
            draft.acceptApplied(result.configurationRevision());
            persist(draft);
            EditorApplyChat.broadcast(server, actor, result);
        }
        return result;
    }

    private Session authorize(ServerPlayer operator, UUID sessionId, String secret) {
        cleanup();
        Session session = sessions.get(sessionId);
        if (session == null || !session.owner.equals(operator.getUUID())) throw new SecurityException("The editor session is unavailable");
        if (!operator.hasPermissions(3)) { revoke(sessionId); throw new SecurityException("Operator permission was lost"); }
        if (!MessageDigest.isEqual(session.secretHash, hash(secret))) throw new SecurityException("The editor session secret is invalid");
        session.lastAccessAt = System.currentTimeMillis();
        return session;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private Map<String, String> loadFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        Path root = ConfigPaths.rootDirectory();
        Path main = ConfigPaths.mainConfig();
        try {
            if (Files.isRegularFile(main)) files.put("progressivestages.toml", Files.readString(main));
            Path stages = ConfigPaths.stagesDirectory();
            if (Files.isDirectory(stages)) {
                try (var paths = Files.walk(stages)) {
                    for (Path path : paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".toml"))
                            .filter(path -> !EditorPaths.isMigrationPath(root, path)).toList()) {
                        files.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path));
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not create an editor draft. " + error.getMessage(), error);
        }
        return Map.copyOf(files);
    }

    private void persist(EditorDraft draft) {
        Path directory = draftsDirectory();
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(draft.id() + ".json"), GSON.toJson(draftView(draft)));
        } catch (IOException ignored) {}
    }

    private static Map<String, Object> draftView(EditorDraft draft) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", draft.id());
        out.put("owner", draft.owner());
        out.put("revision", draft.revision());
        out.put("baseConfigurationRevision", draft.baseConfigurationRevision());
        out.put("baseCatalogRevision", draft.baseCatalogRevision());
        out.put("files", draft.files());
        out.put("baseFiles", draft.baseFiles());
        out.put("diff", draft.diff());
        out.put("canUndo", draft.canUndo());
        out.put("canRedo", draft.canRedo());
        out.put("updatedAt", draft.updatedAt());
        out.put("collaborators", draft.collaborators());
        return out;
    }

    private EditorDraft loadDraft(UUID id) {
        try {
            Path path = draftPath(id);
            if (!Files.isRegularFile(path)) return null;
            JsonObject value = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            UUID owner = UUID.fromString(value.get("owner").getAsString());
            Map<String, String> files = stringMap(value.getAsJsonObject("files"));
            Map<String, String> base = value.has("baseFiles")
                ? stringMap(value.getAsJsonObject("baseFiles")) : files;
            java.util.Set<UUID> collaborators = new java.util.LinkedHashSet<>();
            if (value.has("collaborators")) value.getAsJsonArray("collaborators").forEach(entry ->
                collaborators.add(UUID.fromString(entry.getAsString())));
            return EditorDraft.recover(id, owner, value.get("baseConfigurationRevision").getAsLong(),
                value.get("baseCatalogRevision").getAsLong(), base, files,
                value.get("revision").getAsLong(), value.get("updatedAt").getAsLong(), collaborators);
        } catch (RuntimeException | IOException error) {
            return null;
        }
    }

    private List<UUID> loadPersistedDraftIds() {
        try {
            if (!Files.isDirectory(draftsDirectory())) return List.of();
            try (var paths = Files.list(draftsDirectory())) {
                return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replace(".json", ""))
                    .map(value -> { try { return UUID.fromString(value); } catch (RuntimeException error) { return null; } })
                    .filter(java.util.Objects::nonNull).toList();
            }
        } catch (IOException error) {
            return List.of();
        }
    }

    private static Map<String, String> stringMap(JsonObject object) {
        Map<String, String> result = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        return result;
    }

    private static String stageId(String toml) {
        if (toml == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "(?m)^\\s*id\\s*=\\s*\"([^\"]+)\"").matcher(toml);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String stageFolder(String input) {
        String value = input == null ? "" : input.replace('\\', '/');
        java.nio.file.Path normalized = java.nio.file.Path.of(value).normalize();
        String result = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || normalized.startsWith("..") || !result.startsWith("stages/")
                || result.equals("stages")) throw new IllegalArgumentException("Invalid stage package folder");
        return result.endsWith("/") ? result : result + "/";
    }

    private static String stageDestination(String input) {
        String value = input == null ? "" : input.trim().toLowerCase(java.util.Locale.ROOT).replace('\\', '/');
        if (value.startsWith("stages/")) value = value.substring("stages/".length());
        java.nio.file.Path normalized = java.nio.file.Path.of(value).normalize();
        String result = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || normalized.startsWith("..") || result.isBlank()
                || !result.matches("[a-z0-9_./-]+") || result.startsWith(".")) {
            throw new IllegalArgumentException("Invalid stage package destination");
        }
        return "stages/" + (result.endsWith("/") ? result : result + "/");
    }

    private static Path draftsDirectory() { return ConfigPaths.rootDirectory().resolve(".editor-drafts"); }
    private static Path draftPath(UUID id) { return draftsDirectory().resolve(id + ".json"); }

    private static Map<String, Object> revision(long revision) { return Map.of("revision", revision); }
    private static String error(String code, String explanation) { return GSON.toJson(Map.of("error", code, "explanation", explanation == null ? "" : explanation)); }
    private static String string(JsonObject object, String key, String fallback) { return object.has(key) ? object.get(key).getAsString() : fallback; }
    private static long number(JsonObject object, String key, long fallback) { return object.has(key) ? object.get(key).getAsLong() : fallback; }
    private static boolean bool(JsonObject object, String key, boolean fallback) { return object.has(key) ? object.get(key).getAsBoolean() : fallback; }

    private static byte[] hash(String secret) {
        try { return MessageDigest.getInstance("SHA-256").digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static final class Session {
        final UUID id;
        final UUID owner;
        final UUID draftId;
        final byte[] secretHash;
        final long createdAt;
        final long expiresAt;
        volatile long lastAccessAt;
        final long baseConfigurationRevision;
        final long baseCatalogRevision;

        Session(UUID id, UUID owner, UUID draftId, byte[] secretHash, long createdAt, long expiresAt,
                long lastAccessAt, long baseConfigurationRevision, long baseCatalogRevision) {
            this.id = id;
            this.owner = owner;
            this.draftId = draftId;
            this.secretHash = secretHash.clone();
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.lastAccessAt = lastAccessAt;
            this.baseConfigurationRevision = baseConfigurationRevision;
            this.baseCatalogRevision = baseCatalogRevision;
        }

        EditorSessionView view() {
            return new EditorSessionView(id, owner, draftId, createdAt, expiresAt, lastAccessAt,
                baseConfigurationRevision, baseCatalogRevision);
        }
    }
}
