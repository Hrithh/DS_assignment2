package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;

public class ContentServer implements Runnable {

    private static final Logger logger = Logger.getLogger(ContentServer.class.getName());
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;

    private final String replicaId;
    private final String filename;

    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    public ContentServer(String replicaId, String filename) {
        this.replicaId = replicaId;
        this.filename = filename;
    }

    public void run() {
        try {
            logger.info("[" + replicaId + "] Reading weather data from file: " + filename);
            String[] weatherData = readWeatherFile();

            if (weatherData == null || weatherData.length < 4) {
                logger.warning("[" + replicaId + "] Invalid weather data format.");
                return;
            }

            // Tick clock and build payload
            WeatherData data = new WeatherData(
                    weatherData[0],
                    weatherData[1],
                    weatherData[2],
                    String.valueOf(clock.tick()),
                    replicaId
            );

            String jsonString = gson.toJson(data);
            logger.info("[" + replicaId + "] Constructed JSON payload: " + jsonString);

            // Send PUT request
            logger.info("[" + replicaId + "] Sending PUT request to Aggregation Server at " + SERVER_HOST + ":" + SERVER_PORT);
            sendPutRequest(jsonString);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[" + replicaId + "] Error running content server", e);
        }
    }

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

    private void sendPutRequest(String jsonPayload) {
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Construct the PUT request
                out.println("PUT /weather.json");
                out.println("Lamport-Clock: " + clock.getTime());
                out.println("Content-Length: " + jsonPayload.length());
                out.println(); // End of headers
                out.println(jsonPayload);

                // Read response
                String response;
                while ((response = in.readLine()) != null) {
                    logger.info("[" + replicaId + "] Server response: " + response);
                }

                logger.info("PUT request completed successfully.");
                success = true;

            } catch (IOException e) {
                attempt++;
                logger.warning("[" + replicaId + "] Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    logger.info("[" + replicaId + "] Retrying in 2 seconds...");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        return;
                    }
                } else {
                    logger.severe("[" + replicaId + "] Failed to send PUT after " + maxRetries + " attempts.");
                }
            }
        }
    }

    public static void main(String[] args) {
        String replicaId = (args.length > 0) ? args[0] : "replica1";
        String filename = (args.length > 1) ? args[1] : "weather1.txt";
        ContentServer server = new ContentServer(replicaId, filename);
        server.run();
    }
}
