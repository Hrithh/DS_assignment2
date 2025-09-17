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

    private final List<WeatherRecord> weatherData = Collections.synchronizedList(new ArrayList<>());
    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Aggregation Server started on port " + PORT);

            startCleanupThread();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from " + clientSocket.getRemoteSocketAddress());

                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server error", e);
        }
    }

    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // Run every 5 seconds
                    long now = System.currentTimeMillis();

                    synchronized (weatherData) {
                        weatherData.removeIf(record ->
                                now - record.getReceivedTime() > 30_000
                        );
                    }

                    logger.info("Expired records cleaned up.");
                } catch (InterruptedException e) {
                    logger.warning("Cleanup thread interrupted.");
                    break;
                }
            }
        });

        cleanupThread.setDaemon(true); // Daemon so it doesn’t block JVM shutdown
        cleanupThread.start();
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
        try {
            // 🔹 Read headers first
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }
            // 1. Extract Lamport timestamp
            int receivedTimestamp = Integer.parseInt(headers.getOrDefault("Lamport-Clock", "0"));
            clock.update(receivedTimestamp);

            // 2. Read Content-Length
            int contentLength = Integer.parseInt(headers.get("Content-Length"));

            // 3. Read JSON payload
            char[] buffer = new char[contentLength];
            in.read(buffer, 0, contentLength);
            String payload = new String(buffer);

            // 4. Parse JSON
            Map<String, String> json = gson.fromJson(payload, Map.class);

            String station = json.get("station");
            String temperature = json.get("temperature");
            String humidity = json.get("humidity");

            // 5. Create and store record
            WeatherRecord record = new WeatherRecord(
                    station, temperature, humidity,
                    clock.getTime(), System.currentTimeMillis()
            );

            weatherData.add(record);
            logger.info("Stored weather data from station: " + station + " @ timestamp " + clock.getTime());

            // 6. Respond to ContentServer
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println();
            out.println("PUT received and recorded");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling PUT request", e);
            out.println("HTTP/1.1 500 Internal Server Error");
            out.println();
            out.println("Error processing PUT");
        }
    }

    private void handleGetRequest(PrintWriter out) {
        logger.info("Handling GET request...");

        try {
            List<WeatherRecord> validRecords = getValidSortedRecords();

            String jsonResponse = gson.toJson(validRecords);

            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + jsonResponse.length());
            out.println(); // End of headers
            out.println(jsonResponse);

            logger.info("GET response sent with " + validRecords.size() + " records.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling GET request", e);
            out.println("HTTP/1.1 500 Internal Server Error");
            out.println();
            out.println("Error processing GET request");
        }
    }

    private List<WeatherRecord> getValidSortedRecords() {
        long now = System.currentTimeMillis();
        List<WeatherRecord> validRecords = new ArrayList<>();

        synchronized (weatherData) {
            for (WeatherRecord record : weatherData) {
                if (now - record.getReceivedTime() <= 30_000) {
                    validRecords.add(record);
                }
            }
        }

        // Sort by Lamport timestamp
        validRecords.sort(Comparator.comparingInt(WeatherRecord::getLamportTimestamp));
        return validRecords;
    }

    public static void main(String[] args) {
        AggregationServer server = new AggregationServer();
        server.start();

    }

}
