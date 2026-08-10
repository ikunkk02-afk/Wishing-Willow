package com.ikunkk02.wishingwillow.research.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ResearchHttpClient {
    public static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final Set<String> TRUSTED_HOSTS = Set.of(
            "api.modrinth.com", "api.curseforge.com", "api.github.com",
            "github.com", "raw.githubusercontent.com", "modrinth.com",
            "curseforge.com", "www.curseforge.com"
    );
    private final ExecutorService executor;
    private final HttpClient client;
    private final Semaphore permits = new Semaphore(3);
    private final boolean allowLoopbackForTests;

    public ResearchHttpClient() {
        this(false);
    }

    ResearchHttpClient(boolean allowLoopbackForTests) {
        this.allowLoopbackForTests = allowLoopbackForTests;
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "wishing-willow-research-http-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(3, factory);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public CompletableFuture<HttpResult> get(URI uri, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> request(uri, headers, 0), executor);
    }

    private HttpResult request(URI initial, Map<String, String> headers, int redirectCount) {
        URI uri = validate(initial);
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                permits.acquire();
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json,text/plain,text/markdown,text/html;q=0.8")
                        .header("User-Agent", "ikunkk02-afk/Wishing-Willow/1.0.0")
                        .GET();
                headers.forEach(builder::header);
                HttpResponse<InputStream> response;
                try {
                    response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                } finally {
                    permits.release();
                }
                int status = response.statusCode();
                if (status >= 300 && status < 400 && response.headers().firstValue("Location").isPresent()) {
                    close(response.body());
                    if (redirectCount >= 2) {
                        throw new ResearchHttpException("TOO_MANY_REDIRECTS", status);
                    }
                    return request(uri.resolve(response.headers().firstValue("Location").orElseThrow()),
                            headers, redirectCount + 1);
                }
                String body = readLimited(response.body());
                if ((status == 429 || status >= 500) && attempts < 3) {
                    sleep(retryMillis(response, attempts));
                    continue;
                }
                return new HttpResult(status,
                        response.headers().firstValue("Content-Type").orElse("application/octet-stream"),
                        body, response.headers().map());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResearchHttpException("INTERRUPTED", 0, exception);
            } catch (IOException exception) {
                if (attempts < 3) {
                    sleep(1000L << (attempts - 1));
                    continue;
                }
                throw new ResearchHttpException("NETWORK", 0, exception);
            }
        }
    }

    URI validate(URI uri) {
        boolean schemeAllowed = "https".equalsIgnoreCase(uri.getScheme())
                || (allowLoopbackForTests && "http".equalsIgnoreCase(uri.getScheme()));
        if (!schemeAllowed || uri.getUserInfo() != null
                || uri.getHost() == null || (!allowLoopbackForTests && uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new ResearchHttpException("UNSAFE_URL", 0);
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!TRUSTED_HOSTS.contains(host) && !allowLoopbackForTests) {
            throw new ResearchHttpException("UNTRUSTED_HOST", 0);
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isAllowedResolvedAddress(host, address) && !allowLoopbackForTests) {
                    throw new ResearchHttpException("UNSAFE_ADDRESS", 0);
                }
            }
        } catch (IOException exception) {
            throw new ResearchHttpException("DNS", 0, exception);
        }
        return uri;
    }

    static boolean isAllowedResolvedAddress(String host, InetAddress address) {
        // Clash and similar TUN proxies intentionally synthesize 198.18.0.0/15 answers. This
        // exception is limited to compile-time platform hosts; URI IP literals never reach it.
        return isPublic(address) || (TRUSTED_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                && isProxyBenchmarkAddress(address));
    }

    static boolean isProxyBenchmarkAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address instanceof Inet4Address && (bytes[0] & 0xff) == 198
                && ((bytes[1] & 0xff) == 18 || (bytes[1] & 0xff) == 19);
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && (b == 0 || b == 168)) || (a == 198 && (b == 18 || b == 19))
                    || (a == 203 && b == 0));
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) != 0xfc && !(first == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8);
        }
        return false;
    }

    private static String readLimited(InputStream stream) throws IOException {
        try (InputStream input = stream) {
            byte[] data = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (data.length > MAX_RESPONSE_BYTES) {
                throw new ResearchHttpException("RESPONSE_TOO_LARGE", 0);
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void close(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private static long retryMillis(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("").strip();
        if (!value.isEmpty()) {
            try {
                return Math.min(30_000L, Math.max(0L, Long.parseLong(value) * 1000L));
            } catch (NumberFormatException ignored) {
                try {
                    long millis = Duration.between(ZonedDateTime.now(),
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis();
                    return Math.min(30_000L, Math.max(0L, millis));
                } catch (RuntimeException ignoredDate) {
                }
            }
        }
        return 1000L << (attempt - 1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(Math.min(millis, 30_000L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResearchHttpException("INTERRUPTED", 0, exception);
        }
    }

    public record HttpResult(int status, String contentType, String body, Map<String, List<String>> headers) {
    }

    public static final class ResearchHttpException extends RuntimeException {
        private final String code;
        private final int status;

        ResearchHttpException(String code, int status) {
            this(code, status, null);
        }

        ResearchHttpException(String code, int status, Throwable cause) {
            super(code, cause);
            this.code = code;
            this.status = status;
        }

        public String code() {
            return code;
        }

        public int status() {
            return status;
        }
    }
}
