package com.ikunkk02.wishingwillow.research.source;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchHttpClientTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void rejectsPrivateAddressesAndUnsafeSchemes() throws Exception {
        assertFalse(ResearchHttpClient.isPublic(InetAddress.getByName("127.0.0.1")));
        assertFalse(ResearchHttpClient.isPublic(InetAddress.getByName("192.168.1.5")));
        ResearchHttpClient client = new ResearchHttpClient();
        assertThrows(ResearchHttpClient.ResearchHttpException.class,
                () -> client.validate(URI.create("file:///tmp/test")));
        assertThrows(ResearchHttpClient.ResearchHttpException.class,
                () -> client.validate(URI.create("https://127.0.0.1/test")));
    }

    @Test
    void acceptsClashFakeIpOnlyForFixedPlatformHosts() throws Exception {
        InetAddress fakeIp = InetAddress.getByName("198.18.0.150");
        assertFalse(ResearchHttpClient.isPublic(fakeIp));
        assertTrue(ResearchHttpClient.isAllowedResolvedAddress("api.modrinth.com", fakeIp));
        assertFalse(ResearchHttpClient.isAllowedResolvedAddress("untrusted.example", fakeIp));
    }

    @Test
    void honorsRateLimitRetryAndCapsAttempts() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "research-http-test-server");
            thread.setDaemon(true);
            return thread;
        }));
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/test", exchange -> {
            int current = calls.incrementAndGet();
            byte[] body = (current < 3 ? "limited" : "ok").getBytes(StandardCharsets.UTF_8);
            if (current < 3) exchange.getResponseHeaders().add("Retry-After", "0");
            exchange.sendResponseHeaders(current < 3 ? 429 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ResearchHttpClient client = new ResearchHttpClient(true);
        var response = client.get(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/test"),
                Map.of()).join();
        assertEquals(200, response.status());
        assertEquals("ok", response.body());
        assertEquals(3, calls.get());
    }

    @Test
    void neverExceedsThreeConcurrentRequests() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "research-http-test-server");
            thread.setDaemon(true);
            return thread;
        }));
        AtomicInteger current = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        server.createContext("/slow", exchange -> {
            int active = current.incrementAndGet();
            maximum.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(75L);
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                current.decrementAndGet();
                exchange.close();
            }
        });
        server.start();
        ResearchHttpClient client = new ResearchHttpClient(true);
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow");
        var futures = java.util.stream.IntStream.range(0, 9).mapToObj(index -> client.get(uri, Map.of())).toArray(java.util.concurrent.CompletableFuture[]::new);
        java.util.concurrent.CompletableFuture.allOf(futures).join();
        assertTrue(maximum.get() <= 3);
        assertTrue(maximum.get() > 0);
    }

    @Test
    void rejectsOversizedResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            byte[] body = "x".repeat(ResearchHttpClient.MAX_RESPONSE_BYTES + 1).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ResearchHttpClient client = new ResearchHttpClient(true);
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/large");
        assertThrows(java.util.concurrent.CompletionException.class, () -> client.get(uri, Map.of()).join());
    }

    @Test
    void publicWebDoesNotRetry403Or429() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/blocked", exchange -> {
            calls.incrementAndGet();
            byte[] body = "Cloudflare Challenge".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ResearchHttpClient.HttpResult result = new ResearchHttpClient(true).getWeb(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/blocked"), Map.of()).join();

        assertEquals(429, result.status());
        assertEquals(1, calls.get());
    }

    @Test
    void revalidatesAndCapsEveryRedirectHop() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "/redirect");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        ResearchHttpClient client = new ResearchHttpClient(true);
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");

        java.util.concurrent.CompletionException failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> client.getWeb(uri, Map.of()).join());
        assertTrue(failure.getCause().getMessage().contains("TOO_MANY_REDIRECTS"));
        assertEquals(5, calls.get());
    }
}
