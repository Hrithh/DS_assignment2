package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.logging.*;

import com.google.gson.Gson;

/**
 * AggregationServer acts as the central server for weather data aggregation.
 * - Handles PUT requests from content servers (adds weather records)
 * - Handles GET requests from clients (returns aggregated JSON)
 * - Maintains Lamport clock to preserve logical ordering of updates
 * - Removes expired weather records every 30 seconds
 * - Logs all key events and errors for debugging
 */
public class AggregationServer {

    private static final int PORT = 9090;
    private static final long EXPIRY_DURATION_MS = 30_000; // Weather data expires after 30 seconds
    private static final long CLEANUP_INTERVAL_MS = 5000;  // Cleanup runs every 5 seconds
    private static final Logger logger = Logger.getLogger(AggregationServer.class.getName());

    // Shared weather data list, synchronized for thread safety
    private final List<WeatherRecord> weatherData = Collections.synchronizedList(new ArrayList<>());
    private final LamportClock clock = new LamportClock();  // Logical clock for ordering updates
    private final Gson gson = new Gson();                   // JSON serializer/deserializer


    /**
     * Starts the Aggregation Server:
     * - Listens for incoming connections on the specified port
     * - Spawns a thread for each client connection
     * - Starts the background cleanup thread to purge old records
     */
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

    /**
     * Starts a daemon thread that periodically removes expired records from the weather data list.
     * Special case: This thread will continue running in the background and not block JVM shutdown.
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS); // Run every 5 seconds
                    long now = System.currentTimeMillis();

                    synchronized (weatherData) {
                        weatherData.removeIf(record ->
                                now - record.getReceivedTime() > EXPIRY_DURATION_MS
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

    /**
     * ClientHandler is responsible for handling a single client connection.
     * Supports only GET and PUT requests. Any other method will return 400.
     */
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

    /**
     * Handles a PUT request:
     * - Reads and parses headers
     * - Updates Lamport clock
     * - Parses JSON payload
     * - Adds weather record to shared list
     * - Responds with 200 OK or 500 on failure
     * Special cases:
     * - Invalid or missing Lamport-Clock returns 400
     * - Incomplete JSON body or parsing failure returns 500
     */
    private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
        try {
            // 1. Read headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }

            // 2. Extract Lamport timestamp (default to 0 if missing)
            int receivedTimestamp;
            try {
                receivedTimestamp = Integer.parseInt(headers.get("Lamport-Clock"));
            } catch (NumberFormatException | NullPointerException e) {
                logger.warning("Invalid or missing Lamport-Clock header: " + headers.get("Lamport-Clock"));
                out.println("HTTP/1.1 400 Bad Request");
                out.println();
                out.println("Invalid or missing Lamport-Clock header");
                return;
            }
            clock.update(receivedTimestamp);

            // 3. Read Content-Length
            int contentLength = Integer.parseInt(headers.get("Content-Length"));

            // 4. Read JSON payload from input stream
            char[] buffer = new char[contentLength];
            int read = in.read(buffer, 0, contentLength);
            if (read != contentLength) {
                throw new IOException("Incomplete payload read: expected " + contentLength + " chars, got " + read);
            }
            String payload = new String(buffer);

            // 5. Parse JSON as Map<String, Object>
            Map<String, Object> json = gson.fromJson(payload, Map.class);

            // 6. Extract fields
            String station = (String) json.get("station");
            String temperature = String.valueOf(json.get("temperature")); // Allow string or numeric
            String humidity = String.valueOf(json.get("humidity"));

            // 7. Create and store WeatherRecord
            WeatherRecord record = new WeatherRecord(
                    station,
                    temperature,
                    humidity,
                    clock.getTime(),
                    System.currentTimeMillis()
            );
            weatherData.add(record);
            logger.info("Stored weather data from station: " + station + " @ timestamp " + clock.getTime());

            // 8. Send 200 OK response
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println();
            out.println("PUT received and recorded");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling PUT request", e);
            out.println("HTTP/1.1 500 Internal Server Error");
            out.println();
            out.println("Error processing PUT request");
        }
    }

    /**
     * Handles a GET request:
     * - Filters expired records (>30 seconds)
     * - Sorts remaining by Lamport timestamp
     * - Returns a JSON array of weather records
     * Responds with 200 OK or 500 on server error.
     */
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

    /**
     * Filters and returns all non-expired WeatherRecord entries.
     * Records are sorted by Lamport timestamp in ascending order.
     * return List of valid WeatherRecords
     */
    private List<WeatherRecord> getValidSortedRecords() {
        long now = System.currentTimeMillis();
        List<WeatherRecord> validRecords = new ArrayList<>();

        synchronized (weatherData) {
            for (WeatherRecord record : weatherData) {
                if (now - record.getReceivedTime() <= EXPIRY_DURATION_MS) {
                    validRecords.add(record);
                }
            }
        }

        // Logical ordering based on Lamport timestamps
        validRecords.sort(Comparator.comparingInt(WeatherRecord::getLamportTimestamp));
        return validRecords;
    }

    /**
     * Main entry point for the AggregationServer.
     * Starts the server on the default port (9090).
     */
    public static void main(String[] args) {
        AggregationServer server = new AggregationServer();
        server.start();

    }

}
