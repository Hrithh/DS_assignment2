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

    /**
     * Entry point for GETClient.
     * Usage: java GETClient <host:port>
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java GETClient <host:port>");
            return;
        }

        String[] hostPort = args[0].replace("http://", "").split(":");
        String serverHost = hostPort[0];
        int serverPort = (hostPort.length > 1) ? Integer.parseInt(hostPort[1]) : 4567;

        try (Socket socket = new Socket(serverHost, serverPort);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            logger.info("Connected to Aggregation Server at " + serverHost + ":" + serverPort);

            // --- 1. Send strict HTTP GET request ---
            out.println("GET /weather.json HTTP/1.1");
            out.println("Host: " + serverHost + ":" + serverPort);
            out.println("User-Agent: GETClient/1.0");
            out.println(); // end headers
            out.flush();

            // 2. Read HTTP status line
            String statusLine = in.readLine();
            if (statusLine == null) {
                System.err.println("Server closed connection unexpectedly.");
                return;
            }
            if (statusLine.contains("204")) {
                System.out.println("No weather records available.");
                return;
            }
            if (!statusLine.contains("200")) {
                System.err.println("Server returned an error: " + statusLine);
                return;
            }

            // --- 3. Skip headers ---
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // ignore headers
            }

            // --- 4. Read response body (JSON) ---
            StringBuilder jsonBuilder = new StringBuilder();
            while ((line = in.readLine()) != null) {
                jsonBuilder.append(line);
            }
            String jsonResponse = jsonBuilder.toString().trim();
            logger.info("Received JSON: " + jsonResponse);

            // --- 5. Parse JSON ---
            Gson gson = new Gson();
            List<WeatherRecord> records = gson.fromJson(
                    jsonResponse, new TypeToken<List<WeatherRecord>>() {}.getType()
            );

            // --- 6. Display records ---
            if (records == null || records.isEmpty()) {
                System.out.println("No weather records available.");
            } else {
                System.out.println("Weather Records:");
                for (WeatherRecord r : records) {
                    System.out.printf(
                            "Station: %s | Temp: %s | Humidity: %s | Lamport: %d%n",
                            r.getStation(), r.getTemperature(), r.getHumidity(), r.getLamportTimestamp()
                    );
                }
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Client error", e);
        }
    }
}
