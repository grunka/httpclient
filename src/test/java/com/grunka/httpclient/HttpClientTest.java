package com.grunka.httpclient;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore
public class HttpClientTest {
    private static final Logger LOG = LoggerFactory.getLogger(HttpClientTest.class);

    private ServerSocket serverSocket;
    private int serverPort;

    @Before
    public void setUp() {
        Random random = new Random();
        int attempts = 0;
        while (true) {
            try {
                serverPort = random.nextInt(1024) + 1024;
                serverSocket = new ServerSocket(serverPort);
                break;
            } catch (IOException e) {
                if (attempts++ > 10) {
                    throw new Error("Failed to open server socket", e);
                }
            }
        }
        new Thread(() -> {
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
                                }
                                writer.println("HTTP/1.1 200 OK");
                                writer.println();
                                writer.print("Hello World");
                            }
                        }
                    }
                } catch (IOException e) {
                    if (e instanceof SocketException && e.getMessage().contains("closed")) {
                        LOG.info("Server socket closed");
                    } else {
                        LOG.error("Failed to read request", e);
                    }
                }
            }
        }, "RequestAcceptor").start();
    }

    @After
    public void tearDown() throws Exception {
        serverSocket.close();
    }

    @Test
    public void shouldDoSimpleGet() {
        HttpResponse response = HttpClient.execute(HttpRequest.GET("http://localhost:" + serverPort + "/hello")).join();
        assertTrue(response.isOk());
        assertEquals("Hello World", response.getBody());
    }

    //TODO test errors, strange content, and timeouts of different kinds
}
