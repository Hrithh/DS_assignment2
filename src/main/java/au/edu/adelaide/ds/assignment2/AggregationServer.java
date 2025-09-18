package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.logging.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * AggregationServer stores weather data sent by ContentServers and
 * provides aggregated JSON data to GETClients.
 * .
 * Features:
 * - Persistent storage with crash recovery
 * - Lamport clock for logical ordering
 * - HTTP-like status codes:
 *   201 (Created), 200 (OK), 204 (No Content),
 *   400 (Bad Request), 500 (Internal Server Error)
 * - Removes expired records (30s) using a background cleanup thread
 */
public class AggregationServer {

    private static final Logger logger = Logger.getLogger(AggregationServer.class.getName());
    private static final String DATA_FILE = "weather_data.json";
    private static final long EXPIRY_DURATION_MS = 30_000; // 30 seconds
    private static final long CLEANUP_INTERVAL_MS = 5000;  // 5 seconds

    private final int port;
    private final List<WeatherRecord> weatherData = Collections.synchronizedList(new ArrayList<>());
    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    /**
     * Constructs an AggregationServer listening on the given port.
     *
     * @param port TCP port for server to listen on
     */
    public AggregationServer(int port) {
        this.port = port;
    }

    /**
     * Starts the server:
     * - Restores data from disk if available
     * - Accepts GET and PUT requests via raw socket
     * - Runs cleanup in background
     */
    public void start() {
        loadFromFile();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Aggregation Server started on port " + port);
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
     * Starts a background thread that periodically removes expired records
     * (older than EXPIRY_DURATION_MS) and persists updates to disk.
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                    long now = System.currentTimeMillis();

                    synchronized (weatherData) {
                        int before = weatherData.size();

                        boolean removed = weatherData.removeIf(record ->
                                now - record.getReceivedTime() > EXPIRY_DURATION_MS
                        );

                        int after = weatherData.size();
                        if (removed) {
                            logger.info("Cleanup: removed " + (before - after) + " expired record(s)");
                            saveToFile();
                        } else {
                            logger.fine("Cleanup: no expired records at " + now);
                        }
                    }
                } catch (InterruptedException e) {
                    logger.warning("Cleanup thread interrupted.");
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /** Handles a single client connection (GET/PUT only). */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        ClientHandler(Socket socket) { this.socket = socket; }

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
                    out.println();
                    out.println("Only GET and PUT supported.");
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Client handler error", e);
            }
        }
    }

    /**
     * Handles HTTP-like PUT requests from ContentServers.
     * <p>
     * Status codes:
     * - 201 → First time a record from this station
     * - 200 → Update to existing station
     * - 204 → Empty payload
     * - 400 → Missing headers / bad request
     * - 500 → Malformed JSON / unexpected error
     */
    private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
        try {
            //TEMPORARY injection for testing 500
            //if (true) throw new RuntimeException("Simulated failure");
            // 1. Read headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }

            // 2. Extract Lamport timestamp
            int receivedTimestamp;
            try {
                receivedTimestamp = Integer.parseInt(headers.get("Lamport-Clock"));
            } catch (NumberFormatException | NullPointerException e) {
                logger.warning("Invalid or missing Lamport-Clock header: " + headers.get("Lamport-Clock"));
                out.println("HTTP/1.1 400 Bad Request");
                out.println("Content-Type: text/plain");
                out.println("Content-Length: 0");
                out.println();
                out.flush();
                return;
            }
            clock.update(receivedTimestamp);

            // 3. Read Content-Length
            int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
            if (contentLength == 0) {
                out.println("HTTP/1.1 204 No Content");
                out.println("Content-Length: 0");
                out.println();
                out.flush();
                return;
            }

            // 4. Read JSON payload
            char[] buffer = new char[contentLength];
            int read = in.read(buffer, 0, contentLength);
            if (read != contentLength) {
                throw new IOException("Incomplete payload read");
            }
            String payload = new String(buffer);

            // 5. Parse JSON
            Map<String, Object> json = gson.fromJson(payload, Map.class);
            if (json == null || !json.containsKey("id")) {
                logger.warning("Invalid record: missing required fields -> " + json);
                out.println("HTTP/1.1 400 Bad Request");
                out.println("Content-Type: text/plain");
                out.println("Content-Length: 0");
                out.println();
                out.flush();
                return;
            }

            // 6. Process and save record
            boolean isNew = processRecord(json);
            saveToFile();

            // 7. Send success response
            if (isNew) {
                out.println("HTTP/1.1 201 Created");
            } else {
                out.println("HTTP/1.1 200 OK");
            }
            out.println("Content-Type: text/plain");
            out.println("Content-Length: 0");
            out.println();
            out.flush();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling PUT request", e);
            out.println("HTTP/1.1 500 Internal Server Error");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: 0");
            out.println();
            out.flush();
        }
    }

    /**
     * Processes a single JSON weather record.
     *
     * @param json map containing parsed weather record fields
     * @return true if this is the first record from the station (201),
     *         false if it was an update (200)
     */
    private boolean processRecord(Map<String, Object> json) {
        try {
            String station = (String) json.get("id");
            String temperature = String.valueOf(json.get("air_temp"));
            String humidity = String.valueOf(json.get("rel_hum"));
            String replicaId = (String) json.get("replicaId"); // optional

            if (station == null || temperature == null || humidity == null) {
                logger.warning("Invalid record: missing required fields -> " + json);
                return false;
            }

            WeatherRecord record = new WeatherRecord(
                    station,
                    temperature,
                    humidity,
                    replicaId,
                    clock.getTime(),
                    System.currentTimeMillis()
            );

            synchronized (weatherData) {
                Optional<WeatherRecord> existing = weatherData.stream()
                        .filter(r -> r.getStation().equals(station))
                        .findFirst();

                existing.ifPresent(weatherData::remove);
                weatherData.add(record);

                logger.info("Stored weather data from station: " + station +
                        " (replica=" + replicaId + ") @ timestamp " + clock.getTime());

                return existing.isEmpty(); // true if new, false if update
            }

        } catch (Exception e) {
            logger.warning("Failed to process record: " + json);
            return false;
        }
    }

    /**
     * Handles HTTP-like GET requests.
     * - Returns 204 if no records exist
     * - Otherwise returns JSON array of records (200 OK)
     */
    private void handleGetRequest(PrintWriter out) {
        List<WeatherRecord> snapshot;
        synchronized (weatherData) {
            snapshot = new ArrayList<>(weatherData);
        }

        if (snapshot.isEmpty()) {
            out.println("HTTP/1.1 204 No Content");
            out.println("Content-Length: 0");
            out.println();
            out.flush();
            logger.info("GET request: no valid records (sent 204)");
            return;
        }

        String json = gson.toJson(snapshot);
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + json.getBytes().length);
        out.println();
        out.println(json);
        out.flush();
        logger.info("GET request: sent " + snapshot.size() + " record(s)");
    }

    /** Returns valid (non-expired) records sorted by Lamport timestamp. */
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
        validRecords.sort(Comparator.comparingInt(WeatherRecord::getLamportTimestamp));
        return validRecords;
    }

    /** Saves data atomically to disk (weather records + Lamport clock). */
    private synchronized void saveToFile() {
        try {
            File tempFile = new File(DATA_FILE + ".tmp");
            try (Writer writer = new FileWriter(tempFile)) {
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("clock", clock.getTime());
                snapshot.put("records", weatherData);
                gson.toJson(snapshot, writer);
            }
            File mainFile = new File(DATA_FILE);
            if (mainFile.exists() && !mainFile.delete()) {
                throw new IOException("Failed to delete old data file");
            }
            if (!tempFile.renameTo(mainFile)) {
                throw new IOException("Failed to rename temp file to main file");
            }
        } catch (IOException e) {
            logger.severe("Failed to save data: " + e.getMessage());
        }
    }

    /**
     * Loads persisted data from disk (if any).
     * Restores weather records and Lamport clock value.
     */
    private synchronized void loadFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            Map<String, Object> snapshot =
                    gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());

            weatherData.clear();

            if (snapshot != null) {
                // restore clock (shift back by 1 to avoid double increment)
                Number clockValue = (Number) snapshot.get("clock");
                if (clockValue != null) {
                    clock.setTime(Math.max(0, clockValue.intValue() - 1));
                }

                List<Map<String, Object>> rawRecords = (List<Map<String, Object>>) snapshot.get("records");
                if (rawRecords != null) {
                    for (Map<String, Object> r : rawRecords) {
                        String station = (String) r.get("station");
                        String temperature = (String) r.get("temperature");
                        String humidity = (String) r.get("humidity");
                        String replicaId = (String) r.get("replicaId");

                        Number lamport = (Number) r.get("lamportTimestamp");
                        Number received = (Number) r.get("receivedTime");

                        WeatherRecord record = new WeatherRecord(
                                station,
                                temperature,
                                humidity,
                                replicaId,
                                lamport != null ? lamport.intValue() : 0,
                                received != null ? received.longValue() : System.currentTimeMillis()
                        );
                        weatherData.add(record);
                    }
                }
            }

            logger.info("Restored " + weatherData.size() +
                    " records and clock=" + clock.getTime() + " from " + DATA_FILE);
        }
        catch (IOException e) {
            logger.severe("Failed to load data: " + e.getMessage());
        }
    }

    /** Main entry point. Default port = 4567, or first CLI arg. */
    public static void main(String[] args) {
        int port = 4567;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warning("Invalid port argument, using default 4567.");
            }
        }
        new AggregationServer(port).start();
    }
}
