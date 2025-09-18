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
 * - Sends a strict HTTP GET request to /weather.json
 * - Parses the JSON response containing a list of weather records
 * - Displays the records in a human-readable format
 */
public class GETClient {

    private static final Logger logger = Logger.getLogger(GETClient.class.getName());
    private static final Gson gson = new Gson();
    private static final int DEFAULT_PORT = 4567;

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.severe("Usage: java GETClient <host:port>");
            return;
        }

        String[] hostPort = args[0].replace("http://", "").split(":");
        String serverHost = hostPort[0];
        int serverPort = (hostPort.length > 1) ? Integer.parseInt(hostPort[1]) : DEFAULT_PORT;

        try (Socket socket = new Socket(serverHost, serverPort);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            logger.info("Connected to Aggregation Server at " + serverHost + ":" + serverPort);

            sendGetRequest(out, serverHost, serverPort);
            handleResponse(in);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "GETClient error", e);
        }
    }

    /**
     * Sends an HTTP GET request for /weather.json.
     */
    private static void sendGetRequest(PrintWriter out, String host, int port) {
        out.println("GET /weather.json HTTP/1.1");
        out.println("Host: " + host + ":" + port);
        out.println("User-Agent: GETClient/1.0");
        out.println(); // End headers
        out.flush();
    }

    /**
     * Reads and processes the server response.
     */
    private static void handleResponse(BufferedReader in) throws IOException {
        String statusLine = in.readLine();
        if (statusLine == null) {
            logger.warning("Server closed connection unexpectedly.");
            return;
        }

        int statusCode = parseStatusCode(statusLine);
        switch (statusCode) {
            case 200:
                skipHeaders(in);
                String jsonResponse = readBody(in);
                parseAndDisplay(jsonResponse);
                break;
            case 204:
                System.out.println("No weather records available.");
                break;
            default:
                logger.warning("Server returned error: " + statusLine);
        }
    }

    /**
     * Extracts HTTP status code from response line.
     */
    private static int parseStatusCode(String statusLine) {
        try {
            return Integer.parseInt(statusLine.split(" ")[1]);
        } catch (Exception e) {
            logger.warning("Invalid status line: " + statusLine);
            return -1;
        }
    }

    /**
     * Skips HTTP headers until empty line.
     */
    private static void skipHeaders(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            // Skip headers
        }
    }

    /**
     * Reads full JSON body from server response.
     */
    private static String readBody(BufferedReader in) throws IOException {
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            jsonBuilder.append(line);
        }
        return jsonBuilder.toString().trim();
    }

    /**
     * Parses JSON into WeatherRecord objects and displays them.
     */
    private static void parseAndDisplay(String jsonResponse) {
        logger.info("Received JSON: " + jsonResponse);

        List<WeatherRecord> records = gson.fromJson(
                jsonResponse, new TypeToken<List<WeatherRecord>>() {}.getType()
        );

        if (records == null || records.isEmpty()) {
            System.out.println("No weather records available.");
            return;
        }

        System.out.println("Weather Records:");
        for (WeatherRecord r : records) {
            System.out.printf(
                    "Station: %s | Temp: %s | Humidity: %s | Lamport: %d%n",
                    r.getStation(), r.getTemperature(), r.getHumidity(), r.getLamportTimestamp()
            );
        }
    }
}
