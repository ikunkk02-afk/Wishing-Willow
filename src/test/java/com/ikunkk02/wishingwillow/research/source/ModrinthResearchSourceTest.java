package com.ikunkk02.wishingwillow.research.source;

import com.ikunkk02.wishingwillow.research.ModFingerprint;
import com.ikunkk02.wishingwillow.research.ModFingerprintServiceTest;
import com.ikunkk02.wishingwillow.research.ResearchSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthResearchSourceTest {
    private HttpServer server;
    private String api;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "modrinth-test-server");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        api = "http://127.0.0.1:" + server.getAddress().getPort() + "/v2";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void resolvesHashBeforeProjectWithoutSearching() {
        AtomicInteger searches = new AtomicInteger();
        server.createContext("/v2/version_file/", exchange -> respond(exchange, 200, "{\"project_id\":\"project1\"}"));
        server.createContext("/v2/project/project1", exchange -> respond(exchange, 200, project()));
        server.createContext("/v2/search", exchange -> {
            searches.incrementAndGet();
            respond(exchange, 200, "{\"hits\":[]}");
        });
        var source = new ModrinthResearchSource(new ResearchHttpClient(true), api);
        var result = source.research(ModFingerprintServiceTest.info(
                        "watcher_mod", "1.0", "watcher_mod.jar", "Watcher horror"),
                new ModFingerprint("watcher_mod", "1.0", "watcher_mod.jar", "a".repeat(128))).join();
        assertTrue(result.identified());
        assertTrue(result.sources().contains(ResearchSource.MODRINTH_HASH));
        assertEquals(0, searches.get());
    }

    @Test
    void fallsBackAfter404AndRejectsLowConfidence() {
        server.createContext("/v2/version_file/", exchange -> respond(exchange, 404, "{}"));
        AtomicInteger mode = new AtomicInteger();
        server.createContext("/v2/search", exchange -> respond(exchange, 200, mode.get() == 0 ? exactHits() : lowHits()));
        server.createContext("/v2/project/project1", exchange -> respond(exchange, 200, project()));
        var source = new ModrinthResearchSource(new ResearchHttpClient(true), api);
        var mod = ModFingerprintServiceTest.info("watcher_mod", "1.0", "watcher_mod-1.0.jar", "Watcher horror creature");
        var fingerprint = new ModFingerprint("watcher_mod", "1.0", "watcher_mod-1.0.jar", "b".repeat(128));
        var exact = source.research(mod, fingerprint).join();
        assertTrue(exact.identified());
        assertTrue(exact.sources().contains(ResearchSource.MODRINTH_SEARCH));
        mode.set(1);
        var unresolved = source.research(mod, fingerprint).join();
        assertFalse(unresolved.identified());
    }

    private static String exactHits() {
        return "{\"hits\":[{\"project_id\":\"project1\",\"project_type\":\"mod\","
                + "\"title\":\"watcher mod\",\"slug\":\"watcher-mod\",\"author\":\"author\","
                + "\"description\":\"Watcher horror creature\",\"categories\":[\"forge\"],"
                + "\"versions\":[\"1.20.1\"]}]}";
    }

    private static String lowHits() {
        return "{\"hits\":[{\"project_id\":\"wrong\",\"project_type\":\"mod\","
                + "\"title\":\"Machines\",\"slug\":\"machines\",\"author\":\"other\","
                + "\"description\":\"Technology\",\"categories\":[\"forge\"],"
                + "\"versions\":[\"1.20.1\"]}]}";
    }

    private static String project() {
        return "{\"id\":\"project1\",\"slug\":\"watcher-mod\",\"title\":\"Watcher Mod\","
                + "\"description\":\"Watcher horror\",\"body\":\"Adds a stalking watcher.\","
                + "\"categories\":[\"forge\",\"adventure\"],\"additional_categories\":[\"mobs\"],"
                + "\"source_url\":null,\"wiki_url\":null,\"issues_url\":null,"
                + "\"loaders\":[\"forge\"],\"game_versions\":[\"1.20.1\"],"
                + "\"license\":{\"id\":\"MIT\",\"name\":\"MIT\",\"url\":null}}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }
}
