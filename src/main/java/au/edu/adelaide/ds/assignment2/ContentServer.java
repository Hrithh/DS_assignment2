package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;

/**
 * ContentServer is a replica that:
 * - Reads local weather data from a resource file
 * - Periodically sends JSON-formatted weather data via HTTP PUT
 * - Maintains and sends its Lamport timestamp for logical ordering
 * - Retries failed PUTs up to 3 times
 */
public class ContentServer implements Runnable {

    private static final Logger logger = Logger.getLogger(ContentServer.class.getName());
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;

    private final String replicaId;
    private final String filename;

    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    /**
     * Constructs a ContentServer instance for the given replica.
     * replicaId Unique name of this replica
     * filename File containing weather data
     */
    public ContentServer(String replicaId, String filename) {
        this.replicaId = replicaId;
        this.filename = filename;
    }

    /**
     * Main run loop of the content server:
     * - Reads initial weather data from file
     * - Sends PUT request every 10 seconds
     * - Updates and includes Lamport clock value
     */
    public void run() {
        try {
            logger.info("[" + replicaId + "] Reading weather data from file: " + filename);
            String[] weatherData = readWeatherFile();

            if (weatherData == null || weatherData.length < 4) {
                logger.warning("[" + replicaId + "] Invalid weather data format.");
                return;
            }

            while (true) {
                // Tick Lamport clock and prepare data
                WeatherData data = new WeatherData(
                        weatherData[0],   // station
                        weatherData[1],   // temperature
                        weatherData[2],   // humidity
                        String.valueOf(clock.tick()),
                        replicaId
                );

                String jsonString = gson.toJson(data);
                logger.info("[" + replicaId + "] Sending data: " + jsonString);
                sendPutRequest(jsonString);

                // Wait 10 seconds
                Thread.sleep(10_000);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("[" + replicaId + "] Interrupted and shutting down.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[" + replicaId + "] Error running content server", e);
        }
    }

    /**
     * Reads the local weather data file packaged in the resources folder.
     * Expected format: CSV, one line only: station,temperature,humidity,extra
     * return String array of weather attributes, or empty if unreadable
     */
    private String[] readWeatherFile() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found: " + filename);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = reader.readLine();
            return (line != null) ? line.split(",") : new String[0];
        }
    }

    /**
     * Sends an HTTP PUT request to the AggregationServer with the given JSON payload.
     * Retries up to 3 times if connection fails or server error occurs.
     * Includes "Lamport-Clock" as a custom header for logical timestamp tracking.
     * jsonPayload The serialized WeatherData JSON string
     */
    private void sendPutRequest(String jsonPayload) {
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:9090").openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Lamport-Clock", String.valueOf(clock.getTime()));

                connection.getOutputStream().write(jsonPayload.getBytes());

                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    System.out.println("[" + replicaId + "] Server response: PUT received and recorded");
                    success = true;
                } else {
                    System.out.println("[" + replicaId + "] Server responded with: " + responseCode);
                }

            } catch (IOException e) {
                System.out.println("[" + replicaId + "] PUT failed (attempt " + (attempt + 1) + "): " + e.getMessage());
                try {
                    Thread.sleep(5000);  // wait 1s before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            attempt++;
        }

        if (!success) {
            System.out.println("[" + replicaId + "] PUT request failed after " + maxRetries + " attempts.");
        }
    }

    /**
     * Program entry point for starting a ContentServer replica.
     * Accepts optional CLI args:
     * - args[0]: replicaId (default "replica1")
     * - args[1]: filename (default "weather1.txt")
     */
    public static void main(String[] args) {
        String replicaId = (args.length > 0) ? args[0] : "replica1";
        String filename = (args.length > 1) ? args[1] : "weather1.txt";

        ContentServer server = new ContentServer(replicaId, filename);
        Thread serverThread = new Thread(server);
        serverThread.start();

        // Add shutdown hook to log termination
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.println("[" + replicaId + "] Content server stopped.")
        ));
    }
}
