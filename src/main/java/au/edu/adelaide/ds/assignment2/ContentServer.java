package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;

/**
 * ContentServer is a replica that:
 * - Reads weather data from a file (key:value entries)
 * - Periodically sends one record as JSON via HTTP PUT
 * - Maintains and sends Lamport timestamp for ordering
 * - Retries failed PUTs up to 3 times
 */
public class ContentServer implements Runnable {

    private static final Logger logger = Logger.getLogger(ContentServer.class.getName());

    private final String serverHost;
    private final int serverPort;
    private final String filename;
    private final String replicaId;

    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    public ContentServer(String serverHost, int serverPort, String filename, String replicaId) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.filename = filename;
        this.replicaId = replicaId;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Map<String, String> record = readWeatherFile();

                if (record.isEmpty()) {
                    logger.warning("[" + replicaId + "] No valid record found in " + filename);
                } else {
                    // Tick Lamport once, store value
                    int lamportValue = clock.tick();
                    record.put("lamport", String.valueOf(lamportValue));
                    record.put("replicaId", replicaId);

                    // Convert single record to JSON
                    String jsonPayload = gson.toJson(record);  //"{\"badField\":\"oops\"}"; for 400 test otherwise gson.toJson(record);
                    logger.info("[" + replicaId + "] Sending payload: " + jsonPayload);

                    // Pass lamportValue to PUT request
                    sendPutRequest(jsonPayload);
                }

                Thread.sleep(10_000); // send every 10 seconds
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("[" + replicaId + "] Interrupted and shutting down.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[" + replicaId + "] Error running content server", e);
        }
    }

    /**
     * Reads a single weather record from a file formatted as key:value pairs.
     * - Stops after the first valid "id:..." entry
     * - Rejects any entry missing "id"
     *
     * @return Map<String, String> representing one weather record
     * @throws IOException if file not found or invalid
     */
    private Map<String, String> readWeatherFile() throws IOException {
        InputStream inputStream = new FileInputStream(filename); // read from filesystem
        Map<String, String> record = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(":", 2);
                if (parts.length < 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();

                if (key.equalsIgnoreCase("id") && !record.isEmpty()) {
                    // Found new entry → stop after the first one
                    break;
                }
                record.put(key, value);
            }
        }

        // Validate required field
        if (!record.containsKey("id")) {
            throw new IOException("Invalid feed: missing id field");
        }

        return record;
    }

    /**
     * Sends an HTTP PUT request with JSON payload to AggregationServer.
     * Retries up to 3 times on failure.
     */
    private void sendPutRequest(String jsonPayload) {
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            try (Socket socket = new Socket(serverHost, serverPort);
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Build headers
                String request =
                        "PUT /weather.json HTTP/1.1\r\n" +
                                "User-Agent: ATOMClient/1/0\r\n" +
                                "Host: " + serverHost + ":" + serverPort + "\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: " + jsonPayload.getBytes().length + "\r\n" +
                                "Lamport-Clock: " + clock.getTime() + "\r\n" +
                                "\r\n" +
                                jsonPayload;

                // --- Log raw request so you can compare with assignment spec ---
                logger.info("\n--- RAW PUT REQUEST ---\n" + request + "\n------------------------");

                // Send request
                out.print(request);
                out.flush();

                // Read response
                String statusLine = in.readLine();
                if (statusLine == null) {
                    logger.warning("[" + replicaId + "] No response from server.");
                    return;
                }

                if (statusLine.contains("400")) {
                    logger.warning("[" + replicaId + "] Server rejected request (400 Bad Request)");
                } else if (statusLine.contains("500")) {
                    logger.severe("[" + replicaId + "] Server error (500 Internal Server Error)");
                } else if (statusLine.contains("201")) {
                    logger.info("[" + replicaId + "] Server accepted PUT (201 Created)");
                    success = true;
                } else if (statusLine.contains("200")) {
                    logger.info("[" + replicaId + "] Server accepted PUT (200 OK)");
                    success = true;
                } else if (statusLine.contains("204")) {
                    logger.info("[" + replicaId + "] No Content (204) – empty payload sent");
                    success = true;
                } else {
                    logger.warning("[" + replicaId + "] Unexpected response: " + statusLine);
                }

            } catch (IOException e) {
                logger.warning("[" + replicaId + "] PUT failed (attempt " + (attempt + 1) + "): " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            attempt++;
        }

        if (!success) {
            logger.severe("[" + replicaId + "] PUT request failed after " + maxRetries + " attempts.");
        }
    }

    /**
     * Program entry point.
     * Usage: java ContentServer <host:port> <filename> [replicaId]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java ContentServer <host:port> <filename> [replicaId]");
            return;
        }

        String[] hostPort = args[0].split(":");
        String serverHost = hostPort[0];
        int serverPort = Integer.parseInt(hostPort[1]);

        String filename = args[1];
        String replicaId = (args.length > 2) ? args[2] : "replica1";

        ContentServer server = new ContentServer(serverHost, serverPort, filename, replicaId);
        Thread serverThread = new Thread(server);
        serverThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.println("[" + replicaId + "] Content server stopped.")
        ));
    }
}
