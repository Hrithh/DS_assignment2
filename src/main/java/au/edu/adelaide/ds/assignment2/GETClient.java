package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * GETClient connects to the AggregationServer and performs a GET request.
 * - Parses the JSON response containing a list of weather records
 * - Displays the records in a human-readable format
 */
public class GETClient {

    private static final Logger logger = Logger.getLogger(GETClient.class.getName());
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;

    /**
     * Entry point for GETClient.
     * Connects to the server, sends GET request, receives JSON response,
     * and prints weather records to console.
     */
    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            logger.info("Connected to Aggregation Server at " + SERVER_HOST + ":" + SERVER_PORT);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 1. Send HTTP GET request
            out.println("GET /weather.json");
            out.println(); // End of headers

            // 2. Read HTTP status line
            String statusLine = in.readLine();
            if (statusLine == null || !statusLine.contains("200")) {
                System.err.println("Server returned an error: " + statusLine);
                return;
            }

            // 3. Skip remaining HTTP headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Skip HTTP headers
            }

            // 4. Read full JSON response
            StringBuilder jsonBuilder = new StringBuilder();
            while ((line = in.readLine()) != null) {
                jsonBuilder.append(line);
            }

            String jsonResponse = jsonBuilder.toString();
            logger.info("Received JSON: " + jsonResponse);

            // 5. Parse JSON into WeatherRecord objects
            Gson gson = new Gson();
            List<WeatherRecord> records = gson.fromJson(
                    jsonResponse, new TypeToken<List<WeatherRecord>>() {}.getType()
            );

            // 6. Display weather records
            System.out.println("Weather Records:");
            for (WeatherRecord r : records) {
                System.out.printf("Station: %s | Temp: %s | Humidity: %s | Timestamp: %d%n",
                        r.getStation(), r.getTemperature(), r.getHumidity(), r.getLamportTimestamp());
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE,"Client error", e);
        }
    }
}
