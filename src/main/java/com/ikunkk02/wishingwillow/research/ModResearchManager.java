package com.ikunkk02.wishingwillow.research;

import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.research.ai.ModKnowledgeInterpreter;
import com.ikunkk02.wishingwillow.research.ai.ResearchAnalysisResult;
import com.ikunkk02.wishingwillow.research.registry.RegistryScanner;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import com.ikunkk02.wishingwillow.research.source.CurseForgeResearchSource;
import com.ikunkk02.wishingwillow.research.source.MetadataResearchSource;
import com.ikunkk02.wishingwillow.research.source.ModrinthResearchSource;
import com.ikunkk02.wishingwillow.research.source.ResearchHttpClient;
import com.ikunkk02.wishingwillow.research.source.SourceResearchResult;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ModResearchManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModResearchManager INSTANCE = new ModResearchManager();

    private final ModKnowledgeBase knowledgeBase = new ModKnowledgeBase();
    private final ModKnowledgeCache cache = new ModKnowledgeCache();
    private final ModScanner scanner = new ModScanner();
    private final ModFingerprintService fingerprints = new ModFingerprintService();
    private final ModRelevanceClassifier classifier = new ModRelevanceClassifier();
    private final ResearchHttpClient http = new ResearchHttpClient();
    private final MetadataResearchSource metadataSource = new MetadataResearchSource(http);
    private final ModrinthResearchSource modrinthSource = new ModrinthResearchSource(http);
    private final CurseForgeResearchSource curseForgeSource = new CurseForgeResearchSource(http);
    private final ModKnowledgeInterpreter interpreter = new ModKnowledgeInterpreter(AiService.getInstance());
    private final ExecutorService workers = Executors.newFixedThreadPool(2, threadFactory("wishing-willow-research-"));
    private final Semaphore aiPermit = new Semaphore(1);
    private final AtomicLong generation = new AtomicLong();
    private volatile RegistrySnapshot registrySnapshot = RegistrySnapshot.empty();
    private volatile List<InstalledModInfo> installedMods = List.of();
    private volatile Set<String> dependencyModIds = Set.of();
    @Nullable
    private volatile LocalPlayer currentPlayer;
    private volatile boolean paused;

    private ModResearchManager() {
    }

    public static ModResearchManager getInstance() {
        return INSTANCE;
    }

    public ModKnowledgeBase knowledgeBase() {
        return knowledgeBase;
    }

    public Map<RegistryEntryType, Integer> registryCounts(String modId) {
        return Map.copyOf(registrySnapshot.countsForMod(modId));
    }

    public void start() {
        long run = generation.incrementAndGet();
        knowledgeBase.setState(KnowledgeBaseState.RUNNING);
        workers.execute(() -> scanInstalled(run));
    }

    public void onWorldJoin(LocalPlayer player) {
        currentPlayer = player;
        RegistrySnapshot copied = RegistryScanner.scan(player, installedMods);
        registrySnapshot = copied;
        long run = generation.get();
        workers.execute(() -> {
            cache.saveRegistry(copied);
            scheduleEligible(run, false);
        });
    }

    public void onWorldLogout() {
        currentPlayer = null;
    }

    public void rescan() {
        start();
    }

    public void retryFailed() {
        long run = generation.get();
        for (KnowledgeEntry entry : knowledgeBase.snapshot().entries()) {
            if (entry.state() == ResearchState.FAILED || entry.state() == ResearchState.PARTIAL
                    || entry.state() == ResearchState.WAITING_FOR_AI) {
                schedule(entry.withState(ResearchState.NOT_STARTED), run, false);
            }
        }
    }

    public boolean researchMod(String modId) {
        KnowledgeEntry entry = knowledgeBase.findMod(modId);
        if (entry == null) {
            return false;
        }
        schedule(entry.withResearch(entry.category(), ResearchState.NOT_STARTED, KnowledgeLevel.UNKNOWN,
                Set.of(ResearchSource.LOCAL_METADATA), List.of(), null, registrySnapshot.digest(), ""),
                generation.get(), true);
        return true;
    }

    public void pause() {
        paused = true;
        knowledgeBase.setPaused(true);
        knowledgeBase.setState(KnowledgeBaseState.PAUSED);
        cache.saveIndex(knowledgeBase.snapshot());
    }

    public void resume() {
        paused = false;
        knowledgeBase.setPaused(false);
        knowledgeBase.setState(KnowledgeBaseState.RUNNING);
        scheduleEligible(generation.get(), false);
    }

    public void resumeWaitingForAi() {
        if (AiConfigManager.getInstance().get().isConfigured()) {
            resume();
        }
    }

    public boolean clearCache() {
        generation.incrementAndGet();
        boolean cleared = cache.clear();
        start();
        return cleared;
    }

    private void scanInstalled(long run) {
        try {
            List<ScannedMod> scanned = scanner.scan();
            Map<Path, String> hashesByPath = new HashMap<>();
            List<KnowledgeEntry> entries = new ArrayList<>();
            List<InstalledModInfo> publicMods = new ArrayList<>();
            Set<String> requiredMods = new HashSet<>();
            scanned.forEach(mod -> requiredMods.addAll(mod.publicInfo().dependencies()));
            dependencyModIds = Set.copyOf(requiredMods);
            for (ScannedMod mod : scanned) {
                if (run != generation.get()) {
                    return;
                }
                InstalledModInfo info = mod.publicInfo();
                publicMods.add(info);
                Path normalized = mod.localPath().toAbsolutePath().normalize();
                String hash = hashesByPath.get(normalized);
                if (hash == null) {
                    hash = fingerprints.fingerprint(mod).sha512();
                    hashesByPath.put(normalized, hash);
                }
                ModFingerprint fingerprint = new ModFingerprint(info.modId(), info.version(), info.fileName(), hash);
                ModRelevanceClassifier.Classification classification = classifier.classify(
                        info, registrySnapshot, List.of(), dependencyModIds.contains(info.modId()));
                KnowledgeEntry cached = cache.load(fingerprint);
                KnowledgeEntry entry;
                if (cached != null) {
                    entry = new KnowledgeEntry(1, info, fingerprint, cached.category(), cached.state(),
                            cached.knowledgeLevel(), cached.sources(), cached.documents(), cached.knowledge(),
                            cached.registryDigest(), cached.errorCode(), cached.updatedAt());
                    if (isInFlight(entry.state())) {
                        entry = entry.withResearch(entry.category(), ResearchState.PARTIAL,
                                entry.knowledgeLevel(), entry.sources(), entry.documents(), entry.knowledge(),
                                entry.registryDigest(), "INTERRUPTED_PREVIOUS_SESSION");
                    }
                } else {
                    entry = KnowledgeEntry.scanned(info, fingerprint, classification.category());
                }
                if (classification.ignored()) {
                    entry = entry.withResearch(classification.category(), ResearchState.IGNORED,
                            entry.knowledgeLevel(), entry.sources(), entry.documents(), entry.knowledge(),
                            registrySnapshot.digest(), "");
                }
                entries.add(entry);
            }
            installedMods = List.copyOf(publicMods);
            knowledgeBase.replaceAll(entries);
            cache.saveIndex(knowledgeBase.snapshot());
            LocalPlayer player = currentPlayer;
            if (player != null) {
                // Namespace ownership depends on the completed Loader scan, so refresh on the client thread.
                Minecraft.getInstance().execute(() -> {
                    if (player == currentPlayer && run == generation.get()) {
                        onWorldJoin(player);
                    }
                });
            } else if (!registrySnapshot.digest().equals(RegistrySnapshot.empty().digest())) {
                scheduleEligible(run, false);
            } else {
                updateOverallState();
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Wishing Willow mod scan failed safely: {}", exception.getClass().getSimpleName());
            knowledgeBase.setState(KnowledgeBaseState.PARTIAL_READY);
        }
    }

    private void scheduleEligible(long run, boolean force) {
        if (paused || run != generation.get()) {
            return;
        }
        for (KnowledgeEntry entry : knowledgeBase.snapshot().entries()) {
            if (entry.state() == ResearchState.IGNORED && !force) {
                continue;
            }
            if (entry.state() == ResearchState.READY && entry.knowledge() != null) {
                if (!entry.registryDigest().equals(registrySnapshot.digest())) {
                    KnowledgeEntry verified = reverify(entry);
                    publish(verified, run);
                }
                continue;
            }
            if (entry.state() == ResearchState.ANALYZING || entry.state() == ResearchState.FETCHING
                    || entry.state() == ResearchState.IDENTIFYING) {
                continue;
            }
            schedule(entry, run, force);
        }
        updateOverallState();
    }

    private void schedule(KnowledgeEntry entry, long run, boolean force) {
        publish(entry.withState(ResearchState.IDENTIFYING), run);
        workers.execute(() -> researchOne(entry, run, force));
    }

    private void researchOne(KnowledgeEntry entry, long run, boolean force) {
        if (paused || run != generation.get()) {
            return;
        }
        try {
            List<ResearchDocument> documents = new ArrayList<>();
            Set<ResearchSource> sources = new LinkedHashSet<>();
            List<String> remoteCategories = new ArrayList<>();
            double identityConfidence = 0.0;
            KnowledgeLevel identityLevel = KnowledgeLevel.UNKNOWN;

            if (!entry.documents().isEmpty() && entry.state() == ResearchState.WAITING_FOR_AI) {
                documents.addAll(entry.documents());
                sources.addAll(entry.sources());
                identityLevel = entry.knowledgeLevel();
                identityConfidence = sources.contains(ResearchSource.MODRINTH_HASH) ? 1.0
                        : identityLevel == KnowledgeLevel.IDENTIFIED ? 0.82 : 0.0;
            } else {
                publish(entry.withState(ResearchState.FETCHING), run);
                SourceResearchResult metadata = metadataSource.research(entry.installed(), entry.fingerprint()).join();
                documents.addAll(metadata.documents());
                sources.addAll(metadata.sources());

                SourceResearchResult remote = modrinthSource.research(entry.installed(), entry.fingerprint()).join();
                if (!remote.identified()) {
                    remote = curseForgeSource.research(entry.installed(), entry.fingerprint()).join();
                }
                if (remote.identified()) {
                    identityConfidence = remote.matchConfidence();
                    identityLevel = KnowledgeLevel.IDENTIFIED;
                    documents.addAll(remote.documents());
                    sources.addAll(remote.sources());
                    remoteCategories.addAll(remote.categories());
                }
            }

            ModRelevanceClassifier.Classification refined = classifier.classify(
                    entry.installed(), registrySnapshot, remoteCategories,
                    dependencyModIds.contains(entry.installed().modId()));
            if (refined.ignored() && !force) {
                publish(entry.withResearch(refined.category(), ResearchState.IGNORED, identityLevel,
                        sources, documents, null, registrySnapshot.digest(), ""), run);
                return;
            }

            AiConfig aiConfig = AiConfigManager.getInstance().get();
            if (!aiConfig.isConfigured()) {
                publish(entry.withResearch(refined.category(), ResearchState.WAITING_FOR_AI, identityLevel,
                        sources, documents, null, registrySnapshot.digest(), "NOT_CONFIGURED"), run);
                return;
            }
            publish(entry.withResearch(refined.category(), ResearchState.ANALYZING, identityLevel,
                    sources, documents, null, registrySnapshot.digest(), ""), run);
            aiPermit.acquire();
            ResearchAnalysisResult analysis;
            try {
                analysis = interpreter.analyze(aiConfig, entry.installed(), documents, sources,
                        registrySnapshot, identityConfidence).join();
            } finally {
                aiPermit.release();
            }
            if (analysis.knowledge() == null) {
                publish(entry.withResearch(refined.category(), ResearchState.PARTIAL, identityLevel,
                        sources, documents, null, registrySnapshot.digest(), analysis.errorCode()), run);
                return;
            }
            ModKnowledge knowledge = analysis.knowledge();
            publish(entry.withResearch(knowledge.category(), ResearchState.READY, knowledge.knowledgeLevel(),
                    knowledge.researchSources(), documents, knowledge, registrySnapshot.digest(), ""), run);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            publish(entry.withResearch(entry.category(), ResearchState.PARTIAL, entry.knowledgeLevel(),
                    entry.sources(), entry.documents(), entry.knowledge(), registrySnapshot.digest(), "INTERRUPTED"), run);
        } catch (RuntimeException exception) {
            publish(entry.withResearch(entry.category(), ResearchState.PARTIAL, entry.knowledgeLevel(),
                    entry.sources(), entry.documents(), entry.knowledge(), registrySnapshot.digest(),
                    safeError(exception)), run);
        }
    }

    private KnowledgeEntry reverify(KnowledgeEntry entry) {
        ModKnowledge old = entry.knowledge();
        if (old == null) {
            return entry;
        }
        List<ModFeature> features = new ArrayList<>();
        int verifiedCount = 0;
        for (ModFeature feature : old.features()) {
            RegistryEntryType type = registryType(feature.type());
            List<String> candidates = type == null ? List.of() : feature.registryCandidates().stream()
                    .filter(id -> registrySnapshot.contains(type, id)).toList();
            List<VerifiedRegistryResource> verified = type == null ? List.of() : candidates.stream()
                    .map(id -> new VerifiedRegistryResource(type, id)).toList();
            verifiedCount += verified.size();
            features.add(new ModFeature(feature.name(), feature.type(), feature.description(),
                    feature.possibleCapabilities(), candidates, verified, feature.confidence()));
        }
        KnowledgeLevel level = verifiedCount > 0 ? KnowledgeLevel.VERIFIED : KnowledgeLevel.UNDERSTOOD;
        ModKnowledge updated = new ModKnowledge(old.schemaVersion(), old.modId(), old.name(), old.version(),
                old.category(), old.summary(), old.horrorScore(), old.wishRelevance(), old.themes(), features,
                old.availableCapabilities(), old.researchConfidence(), old.researchSources(), level,
                registrySnapshot.digest());
        return entry.withResearch(updated.category(), ResearchState.READY, level, entry.sources(),
                entry.documents(), updated, registrySnapshot.digest(), "");
    }

    private void publish(KnowledgeEntry entry, long run) {
        if (run != generation.get()) {
            return;
        }
        knowledgeBase.put(entry);
        cache.save(entry);
        updateOverallState();
        cache.saveIndex(knowledgeBase.snapshot());
    }

    private void updateOverallState() {
        if (paused) {
            knowledgeBase.setState(KnowledgeBaseState.PAUSED);
            return;
        }
        KnowledgeBaseSnapshot snapshot = knowledgeBase.snapshot();
        boolean running = snapshot.entries().stream().anyMatch(entry -> switch (entry.state()) {
            case SCANNING, IDENTIFYING, FETCHING, ANALYZING, VERIFYING, NOT_STARTED -> true;
            default -> false;
        });
        if (running) {
            knowledgeBase.setState(KnowledgeBaseState.RUNNING);
        } else if (snapshot.entries().stream().anyMatch(entry -> entry.state() == ResearchState.WAITING_FOR_AI)) {
            knowledgeBase.setState(KnowledgeBaseState.LOCAL_ONLY);
        } else if (snapshot.entries().stream().anyMatch(entry -> entry.state() == ResearchState.PARTIAL
                || entry.state() == ResearchState.FAILED)) {
            knowledgeBase.setState(KnowledgeBaseState.PARTIAL_READY);
        } else {
            knowledgeBase.setState(KnowledgeBaseState.READY);
        }
    }

    private static RegistryEntryType registryType(FeatureType type) {
        return switch (type) {
            case ENTITY -> RegistryEntryType.ENTITY;
            case ITEM -> RegistryEntryType.ITEM;
            case BLOCK -> RegistryEntryType.BLOCK;
            case EFFECT -> RegistryEntryType.EFFECT;
            case DIMENSION -> RegistryEntryType.DIMENSION;
            case STRUCTURE -> RegistryEntryType.STRUCTURE;
            case SOUND -> RegistryEntryType.SOUND;
            default -> null;
        };
    }

    private static boolean isInFlight(ResearchState state) {
        return switch (state) {
            case SCANNING, IDENTIFYING, FETCHING, ANALYZING, VERIFYING -> true;
            default -> false;
        };
    }

    private static String safeError(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
