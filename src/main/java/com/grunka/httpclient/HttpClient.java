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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("WeakerAccess")
public class HttpClient {
    private static final String TEXT_PLAIN = "text/plain";
    private static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CHARSET = "; charset=UTF-8";
    private final int connectTimeout;
    private final int readTimeout;
    private final Map<String, Integer> domainKeepAliveMax;

    //TODO want to be able to kill connections after X seconds based on url
    public HttpClient(int connectTimeout, int readTimeout) {
        this(connectTimeout, readTimeout, Map.of());
    }

    public HttpClient(int connectTimeout, int readTimeout, Map<String, Integer> domainKeepAliveMax) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.domainKeepAliveMax = domainKeepAliveMax;
    }

    public CompletableFuture<HttpResponse> get(String path) {
        return get(path, TEXT_PLAIN);
    }

    public CompletableFuture<HttpResponse> getJson(String path) {
        return get(path, APPLICATION_JSON);
    }

    public CompletableFuture<HttpResponse> get(String path, String accept) {
        return openConnection(path, accept).thenCompose(this::readResponse);
    }

    public CompletableFuture<HttpResponse> postJson(String path, String json) {
        return postJson(path, json, APPLICATION_JSON);
    }

    public CompletableFuture<HttpResponse> postJson(String path, String json, String accept) {
        return postContent(path, APPLICATION_JSON, json, accept);
    }

    public CompletableFuture<HttpResponse> post(String path, Parameters parameters) {
        return postContent(path, FORM_URL_ENCODED, parameters.toString(), TEXT_PLAIN);
    }

    public CompletableFuture<HttpResponse> post(String path, Parameters parameters, String accept) {
        return postContent(path, FORM_URL_ENCODED, parameters.toString(), accept);
    }

    private CompletableFuture<HttpResponse> postContent(String path, String contentType, String content, String accept) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        return openConnection(path, accept).thenCompose(connection -> {
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                return CompletableFuture.failedFuture(new Error("POST method not supported", e));
            }
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType + CHARSET);
            connection.setRequestProperty("Content-Length", String.valueOf(contentBytes.length));
            return writeRequest(connection::getOutputStream, new ByteArrayInputStream(contentBytes))
                    .thenCompose(x -> readResponse(connection))
                    .<CompletableFuture<HttpResponse>>handle((r, e) -> {
                        if (r != null) {
                            return CompletableFuture.completedFuture(r);
                        }
                        e = e.getClass() == CompletionException.class ? e.getCause() : e;
                        if (e.getClass() == ConnectException.class || e.getClass() == SocketTimeoutException.class) {
                            return CompletableFuture.failedFuture(e);
                        } else {
                            return readResponse(connection);
                        }
                    }).thenCompose(x -> x);
        });
    }

    private interface OutputStreamSupplier {
        OutputStream get() throws IOException;
    }

    private CompletableFuture<Void> writeRequest(OutputStreamSupplier outputStreamSupplier, InputStream contentBytes) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try (OutputStream outputStream = outputStreamSupplier.get()) {
                contentBytes.transferTo(outputStream);
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
            result.complete(null);
        });
        return result;
    }

    private CompletableFuture<HttpResponse> readResponse(HttpURLConnection connection) {
        return readAll(connection::getContentType, connection::getInputStream)
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
                    return readAll(connection::getContentEncoding, connection::getErrorStream)
                            .thenApply(body -> new HttpResponse(responseCode, body))
                            .<CompletableFuture<HttpResponse>>handle((r, e1) -> {
                                if (r != null) {
                                    return CompletableFuture.completedFuture(r);
                                }
                                e1 = e1.getClass() == CompletionException.class ? e1.getCause() : e1;
                                return CompletableFuture.failedFuture(e1);
                            }).thenCompose(x -> x);
                }).thenCompose(x -> x);
    }

    private interface ContentTypeSupplier {
        String get();
    }

    private interface InputStreamSupplier {
        InputStream get() throws IOException;
    }

    public static void main(String[] args) {
        HttpClient httpClient = new HttpClient(1000, 1000);
        httpClient.get("https://www.google.com").thenAccept(response -> {
            System.out.println("response.getBody() = " + response.getBody());
        }).join();
    }

    private CompletableFuture<String> readAll(ContentTypeSupplier contentTypeSupplier, InputStreamSupplier inputStreamSupplier) {
        CompletableFuture<String> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            String contentType = contentTypeSupplier.get();
            Charset charset = parseCharsetFromContentType(contentType, StandardCharsets.UTF_8);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                try (InputStream inputStream = inputStreamSupplier.get()) {
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

    private CompletableFuture<HttpURLConnection> openConnection(String path, String accept) {
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            return CompletableFuture.failedFuture(e);
        }
        return openConnection(url).thenApply(connection -> {
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("User-Agent", "com.grunka.httpclient/1.0");
            connection.setRequestProperty("Accept", accept);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            return connection;
        });
    }

    private CompletableFuture<HttpURLConnection> openConnection(URL url) {
        CompletableFuture<HttpURLConnection> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                CookieHandler.setDefault(new NullCookieHandler());
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
        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
            return Map.of();
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {

        }
    }
}
