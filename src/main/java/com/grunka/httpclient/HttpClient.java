package com.grunka.httpclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("WeakerAccess")
public class HttpClient {
    private static final String TEXT_PLAIN = "text/plain";
    private static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded";
    private static final String APPLICATION_JSON = "application/json";
    private final int connectTimeout;
    private final int readTimeout;

    public HttpClient(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
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
        return post(path, parameters, TEXT_PLAIN);
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
                throw new Error("POST method not supported", e);
            }
            //TODO set content encoding?
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Content-Length", String.valueOf(contentBytes.length));
            return writeRequest(connection::getOutputStream, new ByteArrayInputStream(contentBytes)).thenCompose(x -> readResponse(connection))
                    .exceptionallyCompose(e -> {
                        e = e.getClass() == CompletionException.class ? e.getCause() : e;
                        if (e.getClass() == ConnectException.class || e.getClass() == SocketTimeoutException.class) {
                            return CompletableFuture.failedFuture(e);
                        } else {
                            return readResponse(connection);
                        }
                    });
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
        return readAll(connection::getContentEncoding, connection::getInputStream)
                .thenApply(body -> new HttpResponse(200, body))
                .exceptionallyCompose(e0 -> {
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
                            .exceptionallyCompose(e1 -> {
                                e1 = e1.getClass() == CompletionException.class ? e1.getCause() : e1;
                                return CompletableFuture.failedFuture(e1);
                            });
                });
    }

    private interface ContentEncodingSupplier {
        String get();
    }

    private interface InputStreamSupplier {
        InputStream get() throws IOException;
    }

    private CompletableFuture<String> readAll(ContentEncodingSupplier contentEncodingSupplier, InputStreamSupplier inputStreamSupplier) {
        CompletableFuture<String> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            String contentEncoding = contentEncodingSupplier.get(); //TODO use this when decoding stream
            try (InputStream inputStream = inputStreamSupplier.get()) {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    inputStream.transferTo(outputStream);
                    result.complete(outputStream.toString(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
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
            connection.setRequestProperty("User-Agent", "httpclient/1.0");
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

}
