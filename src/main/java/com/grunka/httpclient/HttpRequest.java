package com.grunka.httpclient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@SuppressWarnings("unused")
public class HttpRequest {
    enum HttpRequestType {
        GET, POST;
    }

    final HttpRequestType type;
    final String url;
    final long connectTimeout;
    final long readTimeout;
    final String accept;
    final String contentType;
    final Charset charset;
    final boolean disconnect;
    final String postContent;

    private HttpRequest(HttpRequestType type, String url, long connectTimeout, long readTimeout, String accept, String contentType, Charset charset, boolean disconnect, String postContent) {
        this.type = type;
        this.url = url;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.accept = accept;
        this.contentType = contentType;
        this.charset = charset;
        this.disconnect = disconnect;
        this.postContent = postContent;
    }

    private HttpRequest(HttpRequestType type, String url) {
        this(type, url, 5000, 10_000, null, HttpClient.APPLICATION_JSON, StandardCharsets.UTF_8, false, null);
    }

    public static HttpRequest GET(String url) {
        return new HttpRequest(HttpRequestType.GET, url);
    }

    public static HttpRequest POST(String url) {
        return new HttpRequest(HttpRequestType.POST, url);
    }

    public HttpRequest json(String json) {
        if (type != HttpRequestType.POST) {
            throw new IllegalArgumentException("Cannot post JSON in a non POST request");
        }
        if (Objects.equals(json, this.postContent)) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, HttpClient.APPLICATION_JSON, HttpClient.APPLICATION_JSON, charset, disconnect, json);
    }

    public HttpRequest form(Parameters parameters) {
        if (type != HttpRequestType.POST) {
            throw new IllegalArgumentException("Cannot post form in a non POST request");
        }
        String form = parameters.toString();
        if (Objects.equals(form, this.postContent)) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, HttpClient.FORM_URL_ENCODED, charset, disconnect, form);
    }

    public HttpRequest content(String content) {
        if (type != HttpRequestType.POST) {
            throw new IllegalArgumentException("Cannot post form in a non POST request");
        }
        if (Objects.equals(content, this.postContent)) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, contentType, charset, disconnect, content);
    }

    public HttpRequest readTimeout(long readTimeout) {
        if (readTimeout == this.readTimeout) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, contentType, charset, disconnect, postContent);
    }

    public HttpRequest connectTimeout(long connectTimeout) {
        if (connectTimeout == this.connectTimeout) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, contentType, charset, disconnect, postContent);
    }

    public HttpRequest charset(Charset charset) {
        Objects.requireNonNull(charset, "Charset cannot be null");
        if (Objects.equals(charset, this.charset)) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, contentType, charset, disconnect, postContent);
    }

    public HttpRequest accept(String accept) {
        if (Objects.equals(accept, this.accept)) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, contentType, charset, disconnect, postContent);
    }

    public HttpRequest contentType(String contentType) {
        Objects.requireNonNull(contentType, "Content-Type is not allowed to be null");
        if (Objects.equals(contentType, this.contentType)) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, contentType, charset, disconnect, postContent);
    }

    public HttpRequest disconnect() {
        if (disconnect) {
            return this;
        }
        return new HttpRequest(type, url, connectTimeout, readTimeout, accept, contentType, charset, true, postContent);
    }
}
