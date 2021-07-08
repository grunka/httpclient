package com.grunka.httpclient;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpClientTest {
    private static ServerSocket serverSocket;
    private static int serverPort;
    private static Thread serverThread;
    private static final Map<String, List<String>> headers = new TreeMap<>(String::compareToIgnoreCase);
    private static final Map<String, String> response = new HashMap<>();
    private static final Map<String, String> request = new HashMap<>();

    @BeforeClass
    public static void beforeClass() {
        Random random = new Random();
        int attempts = 0;
        while (true) {
            try {
                serverPort = random.nextInt(1024) + 1024;
                System.out.println("Trying port " + serverPort);
                serverSocket = new ServerSocket(serverPort);
                break;
            } catch (IOException e) {
                if (attempts++ > 10) {
                    throw new Error("Failed to open server socket", e);
                }
            }
        }
        serverThread = new Thread(() -> {
            while (true) {
                try {
                    if (serverSocket.isClosed()) {
                        break;
                    }
                    try (Socket socket = serverSocket.accept()) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                                String in;
                                while ((in = reader.readLine()).length() > 0) {
                                    System.out.println(in);
                                    int colon = in.indexOf(':');
                                    if (colon != -1) {
                                        String line = in;
                                        headers.compute(line.substring(0, colon).trim(), (key, list) -> {
                                            if (list == null) {
                                                list = new ArrayList<>();
                                            }
                                            list.add(line.substring(colon + 1).trim());
                                            return list;
                                        });
                                    } else {
                                        if (in.startsWith("GET")) {
                                            request.put("method", "GET");
                                            request.put("path", in.split(" ")[1]);
                                        }
                                        if (in.startsWith("POST")) {
                                            request.put("method", "POST");
                                            request.put("path", in.split(" ")[1]);
                                        }
                                    }
                                }
                                writer.println("HTTP/1.1 " + response.get("code"));
                                writer.println("Content-Length: " + response.get("content").length());
                                writer.println();
                                writer.print(response.get("content"));
                            }
                        }
                    }
                } catch (IOException e) {
                    if (e instanceof SocketException && e.getMessage().contains("closed")) {
                        System.out.println("Server socket closed");
                    } else {
                        System.err.println("Failed to read request");
                        e.printStackTrace(System.err);
                    }
                }
            }
        }, "RequestAcceptor");
        serverThread.start();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        serverSocket.close();
        serverThread.join();
    }

    @Before
    public void setUp() {
        request.clear();
        response.clear();
        response.put("code", "200 OK");
        response.put("content", "");
    }

    @Test
    public void shouldDoSimpleGet() {
        response.put("content", "Hello World!");
        HttpResponse response = HttpClient.execute(HttpRequest.GET("http://localhost:" + serverPort + "/hello")).join();
        assertTrue(response.isOk());
        assertEquals("Hello World!", response.getBody());
        assertTrue(headers.get("user-agent").contains("com.grunka.httpclient/1.0"));
        assertEquals("/hello", request.get("path"));
    }

    @Test
    public void shouldDoSimpleGetAndHandleError() {
        response.put("code", "500 ERROR");
        response.put("content", "Goodbye");
        HttpResponse response = HttpClient.execute(HttpRequest.GET("http://localhost:" + serverPort + "/hello")).join();
        assertFalse(response.isOk());
        assertEquals("Goodbye", response.getBody());
        assertTrue(headers.get("user-agent").contains("com.grunka.httpclient/1.0"));
        assertEquals("/hello", request.get("path"));
    }

    //TODO test errors, strange content, and timeouts of different kinds
}
