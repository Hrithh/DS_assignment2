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
    private static final String WEATHER_FILE = "/weather.txt";

    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    public void run() {
        try {
            // 1. Read weather file
            logger.info("Reading weather data from file: " + WEATHER_FILE);
            String[] weatherData = readWeatherFile();

            // 2. Build JSON
            WeatherData data = new WeatherData(
                    weatherData[0],
                    weatherData[1],
                    weatherData[2],
                    String.valueOf(clock.tick())
            );

            String jsonString = gson.toJson(data);
            logger.info("Constructed JSON payload: " + jsonString);

            // 3. Send via PUT
            logger.info("Sending PUT request to Aggregation Server at " + SERVER_HOST + ":" + SERVER_PORT);
            sendPutRequest(jsonString);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error running content server", e);
        }
    }

    private String[] readWeatherFile() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("weather.txt");
        if (inputStream == null) {
            throw new FileNotFoundException("weather.txt not found in resources folder.");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines().toArray(String[]::new);
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
                    logger.info("Server response: " + response);
                }

                logger.info("PUT request completed successfully.");
                success = true;

            } catch (IOException e) {
                attempt++;
                logger.warning("Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    logger.info("Retrying in 2 seconds...");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        return;
                    }
                } else {
                    logger.severe("Failed to send PUT after " + maxRetries + " attempts.");
                }
            }
        }
    }

    public static void main(String[] args) {
        ContentServer server = new ContentServer();
        server.run();
    }
}
