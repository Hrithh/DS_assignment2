package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.logging.*;

import com.google.gson.Gson;

public class AggregationServer {

    private static final int PORT = 9090;
    private static final Logger logger = Logger.getLogger(AggregationServer.class.getName());

    private final LamportClock clock = new LamportClock();
    private final Map<String, WeatherRecord> weatherData = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Aggregation Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from " + clientSocket.getRemoteSocketAddress());

                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server error", e);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String requestLine = in.readLine();
                if (requestLine == null) return;

                if (requestLine.startsWith("PUT")) {
                    handlePutRequest(in, out);
                } else if (requestLine.startsWith("GET")) {
                    handleGetRequest(out);
                } else {
                    out.println("HTTP/1.1 400 Bad Request");
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Client handler error", e);
            }
        }
    }

    // Placeholder methods
    private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
        logger.info("Handling PUT request...");
        // [You will implement this in the next step]
    }

    private void handleGetRequest(PrintWriter out) {
        logger.info("Handling GET request...");
        // [You will implement this in the next step]
    }

    public static void main(String[] args) {
        AggregationServer server = new AggregationServer();
        server.start();

    }

    // Simple record class to store weather info
    private static class WeatherRecord {
        String station;
        String temperature;
        String humidity;
        long timestamp;
        long lamportTime;

        public WeatherRecord(String station, String temperature, String humidity, long timestamp, long lamportTime) {
            this.station = station;
            this.temperature = temperature;
            this.humidity = humidity;
            this.timestamp = timestamp;
            this.lamportTime = lamportTime;
        }
    }
}
