package com.grunka.httpclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("WeakerAccess")
public class HttpClient {
    public static final String TEXT_PLAIN = "text/plain";
    public static final String ANY = "*/*";
    public static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded";
    public static final String APPLICATION_JSON = "application/json";

    private HttpClient() {
    }

    public static CompletableFuture<HttpResponse> execute(HttpRequest request) {
        switch (request.type) {
            case GET:
                return openConnection(request).thenCompose(connection -> readResponse(connection, request));
            case POST:
                return postContent(request);
            default:
                throw new IllegalArgumentException("Can only handle GET and POST");
        }
    }

    private static CompletableFuture<HttpResponse> postContent(HttpRequest request) {
        byte[] contentBytes = request.postContent.getBytes(request.charset);
        return openConnection(request).thenCompose(connection -> {
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                return CompletableFuture.failedFuture(new Error("POST method not supported", e));
            }
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", request.contentType + "; charset=" + request.charset.name());
            connection.setRequestProperty("Content-Length", String.valueOf(contentBytes.length));
            return writeRequest(connection::getOutputStream, new ByteArrayInputStream(contentBytes))
                    .thenCompose(x -> readResponse(connection, request))
                    .<CompletableFuture<HttpResponse>>handle((r, e) -> {
                        if (r != null) {
                            return CompletableFuture.completedFuture(r);
                        }
                        e = e.getClass() == CompletionException.class ? e.getCause() : e;
                        if (e.getClass() == ConnectException.class || e.getClass() == SocketTimeoutException.class) {
                            return CompletableFuture.failedFuture(e);
                        } else {
                            return readResponse(connection, request);
                        }
                    }).thenCompose(x -> x);
        });
    }

    private interface OutputStreamSupplier {
        OutputStream get() throws IOException;
    }

    private static CompletableFuture<Void> writeRequest(OutputStreamSupplier outputStreamSupplier, InputStream contentBytes) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try (OutputStream outputStream = outputStreamSupplier.get()) {
                contentBytes.transferTo(outputStream);
            } catch (IOException e) {
                result.completeExceptionally(e);
                return;
            }
            result.complete(null);
        });
        return result;
    }

    private static CompletableFuture<HttpResponse> readResponse(HttpURLConnection connection, HttpRequest request) {
        return readAll(connection::getContentType, connection::getInputStream, request)
                .thenApply(body -> new HttpResponse(200, body))
                .<CompletableFuture<HttpResponse>>handle((response, e0) -> {
                    if (response != null) {
                        return CompletableFuture.completedFuture(response);
                    }
                    e0 = e0.getClass() == CompletionException.class ? e0.getCause() : e0;
                    Class<? extends Throwable> exceptionClass = e0.getClass();
                    if (exceptionClass == ConnectException.class || exceptionClass == SocketTimeoutException.class) {
                        return CompletableFuture.failedFuture(e0);
                    }
                    int responseCode;
                    try {
                        responseCode = connection.getResponseCode();
                    } catch (IOException ioException) {
                        return CompletableFuture.failedFuture(ioException);
                    }
                    return readAll(connection::getContentEncoding, connection::getErrorStream, request)
                            .thenApply(body -> new HttpResponse(responseCode, body))
                            .<CompletableFuture<HttpResponse>>handle((r, e1) -> {
                                if (r != null) {
                                    return CompletableFuture.completedFuture(r);
                                }
                                e1 = e1.getClass() == CompletionException.class ? e1.getCause() : e1;
                                return CompletableFuture.failedFuture(e1);
                            }).thenCompose(x -> x);
                })
                .thenCompose(x -> x)
                .whenComplete((r, t) -> {
                    if (request.disconnect) {
                        connection.disconnect();
                    }
                });
    }

    private interface ContentTypeSupplier {
        String get();
    }

    private interface InputStreamSupplier {
        InputStream get() throws IOException;
    }

    public static void main(String[] args) {
        Instant start = Instant.now();
        List<CompletableFuture<String>> bodies = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            bodies.add(HttpClient.execute(HttpRequest.POST("http://www.example.com").json("{}").charset(StandardCharsets.ISO_8859_1).disconnect()).thenApply(HttpResponse::getBody));
        }
        bodies.forEach(CompletableFuture::join);
        Duration duration = Duration.between(start, Instant.now());
        System.out.println("duration.toMillis() = " + duration.toMillis());
    }

    private static CompletableFuture<String> readAll(ContentTypeSupplier contentTypeSupplier, InputStreamSupplier inputStreamSupplier, HttpRequest request) {
        CompletableFuture<String> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            String contentType = contentTypeSupplier.get();
            Charset charset = parseCharsetFromContentType(contentType, request.charset);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                try (InputStream inputStream = inputStreamSupplier.get()) {
                    if (inputStream == null) {
                        result.complete(null);
                        return;
                    }
                    inputStream.transferTo(outputStream);
                }
                result.complete(outputStream.toString(charset));
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    private static Charset parseCharsetFromContentType(String contentType, Charset defaultCharset) {
        if (contentType == null) {
            return defaultCharset;
        }
        String charsetString = contentType.toUpperCase();
        int index = charsetString.indexOf("CHARSET=");
        if (index != -1) {
            charsetString = charsetString.substring(index + 8);
            int nextSemicolon = charsetString.indexOf(";");
            if (nextSemicolon != -1) {
                charsetString = charsetString.substring(0, nextSemicolon);
            }
            charsetString = charsetString.trim();
            try {
                return Charset.forName(charsetString);
            } catch (UnsupportedCharsetException ignore) {
            }
        }
        return defaultCharset;
    }

    private static CompletableFuture<HttpURLConnection> openConnection(HttpRequest request) {
        URL url;
        try {
            url = new URL(request.url);
        } catch (MalformedURLException e) {
            return CompletableFuture.failedFuture(e);
        }
        return openConnection(url).thenApply(connection -> {
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("User-Agent", "com.grunka.httpclient/1.0");
            connection.setRequestProperty("Accept", request.accept);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout((int) request.connectTimeout);
            connection.setReadTimeout((int) request.readTimeout);
            return connection;
        });
    }

    private static CompletableFuture<HttpURLConnection> openConnection(URL url) {
        CompletableFuture<HttpURLConnection> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                CookieHandler.setDefault(NullCookieHandler.INSTANCE);
                URLConnection connection = url.openConnection();
                if (!(connection instanceof HttpURLConnection)) {
                    result.completeExceptionally(new MalformedURLException("Expected HttpURLConnection, got " + connection.getClass().getName()));
                } else {
                    result.complete((HttpURLConnection) connection);
                }
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    private static class NullCookieHandler extends CookieHandler {
        public static final NullCookieHandler INSTANCE = new NullCookieHandler();

        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
            return Map.of();
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) {

        }
    }
}
