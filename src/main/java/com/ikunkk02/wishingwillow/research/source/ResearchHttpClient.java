package com.ikunkk02.wishingwillow.research.source;

import com.ikunkk02.wishingwillow.research.web.UrlSafetyValidator;
import com.ikunkk02.wishingwillow.research.web.WebResearchBudget;

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
    private final ExecutorService executor;
    private final HttpClient client;
    private final Semaphore permits = new Semaphore(3);
    private final boolean allowLoopbackForTests;
    private final UrlSafetyValidator validator;

    public ResearchHttpClient() {
        this(false);
    }

    ResearchHttpClient(boolean allowLoopbackForTests) {
        this.allowLoopbackForTests = allowLoopbackForTests;
        this.validator = new UrlSafetyValidator(allowLoopbackForTests);
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
        return CompletableFuture.supplyAsync(() -> request(uri, headers, 0, 2, true, false), executor);
    }

    public CompletableFuture<HttpResult> getWeb(URI uri, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> request(uri, headers, 0,
                WebResearchBudget.MAX_REDIRECTS, false, true), executor);
    }

    private HttpResult request(URI initial, Map<String, String> headers, int redirectCount,
                               int maxRedirects, boolean platformOnly, boolean publicWeb) {
        URI uri = validate(initial, platformOnly);
        int attempts = 0;
        int maxAttempts = publicWeb ? 2 : 3;
        while (true) {
            attempts++;
            try {
                permits.acquire();
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json,text/plain,text/markdown,text/html;q=0.8")
                        .header("User-Agent", "Wishing-Willow/1.0.0 Minecraft-Mod-Researcher "
                                + "(+https://github.com/ikunkk02-afk/Wishing-Willow)")
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
                    if (redirectCount >= maxRedirects) {
                        throw new ResearchHttpException("TOO_MANY_REDIRECTS", status);
                    }
                    return request(uri.resolve(response.headers().firstValue("Location").orElseThrow()),
                            headers, redirectCount + 1, maxRedirects, platformOnly, publicWeb);
                }
                String body = readLimited(response.body());
                if (!publicWeb && (status == 429 || status >= 500) && attempts < maxAttempts) {
                    sleep(retryMillis(response, attempts));
                    continue;
                }
                if (publicWeb && status >= 500 && attempts < maxAttempts) {
                    sleep(1000L);
                    continue;
                }
                return new HttpResult(status,
                        response.headers().firstValue("Content-Type").orElse("application/octet-stream"),
                        body, response.headers().map(), uri);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResearchHttpException("INTERRUPTED", 0, exception);
            } catch (IOException exception) {
                if (attempts < maxAttempts) {
                    sleep(1000L << (attempts - 1));
                    continue;
                }
                throw new ResearchHttpException("NETWORK", 0, exception);
            }
        }
    }

    URI validate(URI uri) {
        return validate(uri, true);
    }

    URI validate(URI uri, boolean platformOnly) {
        try {
            return validator.validate(uri, platformOnly);
        } catch (UrlSafetyValidator.UnsafeUrlException exception) {
            throw new ResearchHttpException(exception.getMessage(), 0, exception);
        }
    }

    static boolean isAllowedResolvedAddress(String host, InetAddress address) {
        return UrlSafetyValidator.isAllowedAddress(host, address);
    }

    static boolean isProxyBenchmarkAddress(InetAddress address) {
        return UrlSafetyValidator.isProxyBenchmarkAddress(address);
    }

    static boolean isPublic(InetAddress address) {
        return UrlSafetyValidator.isPublic(address);
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

    public record HttpResult(int status, String contentType, String body,
                             Map<String, List<String>> headers, URI finalUri) {
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
