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
    public static final String TEXT_PLAIN = "text/plain";
    public static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded";
    public static final String APPLICATION_JSON = "application/json";

    public static class Configuration {
        public static final Configuration DEFAULT = new Configuration(5000, 10_000, null, APPLICATION_JSON, StandardCharsets.UTF_8, false);

        public final long connectTimeout;
        public final long readTimeout;
        public final String accept;
        public final String contentType;
        public final Charset charset;
        public final boolean disconnect;

        public Configuration(long connectTimeout, long readTimeout, String accept, String contentType, Charset charset, boolean disconnect) {
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.accept = accept;
            this.contentType = contentType;
            this.charset = charset;
            this.disconnect = disconnect;
        }

        public Configuration disconnect() {
            if (disconnect) {
                return this;
            }
            return new Configuration(connectTimeout, readTimeout, accept, contentType, charset, true);
        }

        public Configuration readTimeout(long readTimeout) {
            if (readTimeout == this.readTimeout) {
                return this;
            }
            return new Configuration(connectTimeout, readTimeout, accept, contentType, charset, disconnect);
        }

        public Configuration connectTimeout(long connectTimeout) {
            if (connectTimeout == this.connectTimeout) {
                return this;
            }
            return new Configuration(connectTimeout, readTimeout, accept, contentType, charset, disconnect);
        }

        public Configuration charset(Charset charset) {
            if (Objects.equals(charset, this.charset)) {
                return this;
            }
            return new Configuration(connectTimeout, readTimeout, accept, contentType, charset, disconnect);
        }

        public Configuration accept(String accept) {
            if (Objects.equals(accept, this.accept)) {
                return this;
            }
            return new Configuration(connectTimeout, readTimeout, accept, contentType, charset, disconnect);
        }

        public Configuration contentType(String contentType) {
            Objects.requireNonNull(contentType, "Content-Type is not allowed to be null");
            if (Objects.equals(contentType, this.contentType)) {
                return this;
            }
            return new Configuration(connectTimeout, readTimeout, accept, contentType, charset, disconnect);
        }
    }

    private HttpClient() {
    }

    public static CompletableFuture<HttpResponse> get(String path) {
        return get(path, Configuration.DEFAULT);
    }

    public static CompletableFuture<HttpResponse> getJson(String path) {
        return get(path, Configuration.DEFAULT.accept(APPLICATION_JSON));
    }

    public static CompletableFuture<HttpResponse> get(String path, Configuration configuration) {
        return openConnection(path, configuration).thenCompose(connection -> HttpClient.readResponse(connection, configuration));
    }

    public static CompletableFuture<HttpResponse> postJson(String path, String json) {
        return postContent(path, json, Configuration.DEFAULT.contentType(APPLICATION_JSON).accept(APPLICATION_JSON));
    }

    public static CompletableFuture<HttpResponse> post(String path, Parameters parameters) {
        return post(path, parameters, Configuration.DEFAULT);
    }

    public static CompletableFuture<HttpResponse> post(String path, Parameters parameters, Configuration configuration) {
        return postContent(path, parameters.toString(), configuration.contentType(FORM_URL_ENCODED));
    }

    private static CompletableFuture<HttpResponse> postContent(String path, String content, Configuration configuration) {
        byte[] contentBytes = content.getBytes(configuration.charset);
        return openConnection(path, configuration).thenCompose(connection -> {
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                return CompletableFuture.failedFuture(new Error("POST method not supported", e));
            }
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", configuration.contentType + "; charset=" + configuration.charset.name());
            connection.setRequestProperty("Content-Length", String.valueOf(contentBytes.length));
            return writeRequest(connection::getOutputStream, new ByteArrayInputStream(contentBytes))
                    .thenCompose(x -> readResponse(connection, configuration))
                    .<CompletableFuture<HttpResponse>>handle((r, e) -> {
                        if (r != null) {
                            return CompletableFuture.completedFuture(r);
                        }
                        e = e.getClass() == CompletionException.class ? e.getCause() : e;
                        if (e.getClass() == ConnectException.class || e.getClass() == SocketTimeoutException.class) {
                            return CompletableFuture.failedFuture(e);
                        } else {
                            return readResponse(connection, configuration);
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

    private static CompletableFuture<HttpResponse> readResponse(HttpURLConnection connection, Configuration configuration) {
        return readAll(connection::getContentType, connection::getInputStream, configuration)
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
                    return readAll(connection::getContentEncoding, connection::getErrorStream, configuration)
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
                    if (configuration.disconnect) {
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
        HttpClient httpClient = new HttpClient(1000, 1000);
        httpClient.get("https://www.google.com").thenAccept(response -> {
            System.out.println("response.getBody() = " + response.getBody());
        }).join();
    }

    private static CompletableFuture<String> readAll(ContentTypeSupplier contentTypeSupplier, InputStreamSupplier inputStreamSupplier, Configuration configuration) {
        CompletableFuture<String> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            String contentType = contentTypeSupplier.get();
            Charset charset = parseCharsetFromContentType(contentType, configuration.charset);
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

    private static CompletableFuture<HttpURLConnection> openConnection(String path, Configuration configuration) {
        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e) {
            return CompletableFuture.failedFuture(e);
        }
        return openConnection(url).thenApply(connection -> {
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("User-Agent", "com.grunka.httpclient/1.0");
            if (configuration.accept != null) {
                connection.setRequestProperty("Accept", configuration.accept);
            }
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout((int) configuration.connectTimeout);
            connection.setReadTimeout((int) configuration.readTimeout);
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
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
            return Map.of();
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {

        }
    }
}
