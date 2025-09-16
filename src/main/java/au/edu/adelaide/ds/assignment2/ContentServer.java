package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;

public class ContentServer implements Runnable {

    private static final Logger logger = Logger.getLogger(ContentServer.class.getName());
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;
    private static final String WEATHER_FILE = "resources/weather.txt";

    private final LamportClock clock = new LamportClock();
    private final Gson gson = new Gson();

    public void run() {
        try {
            // 1. Read weather file
            logger.info("Reading weather data from file: " + WEATHER_FILE);
            String[] weatherData = readWeatherFile();

            // 2. Build JSON
            Map<String, String> weatherJson = new HashMap<>();

            weatherJson.put("station", weatherData[0]);
            weatherJson.put("temperature", weatherData[1]);
            weatherJson.put("humidity", weatherData[2]);
            weatherJson.put("timestamp", String.valueOf(clock.tick()));  // tick on send

            String jsonString = gson.toJson(weatherJson);
            logger.info("Constructed JSON payload: " + jsonString);

            // 3. Send via PUT
            logger.info("Sending PUT request to Aggregation Server at " + SERVER_HOST + ":" + SERVER_PORT);
            sendPutRequest(jsonString);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error running content server", e);
        }
    }

    private String[] readWeatherFile() throws IOException {
        return Files.readAllLines(Paths.get(WEATHER_FILE)).toArray(new String[0]);
    }

    private void sendPutRequest(String jsonPayload) throws IOException {
        Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // Simulate PUT /weather.json
        out.println("PUT /weather.json");
        out.println("Lamport-Clock: " + clock.getTime());
        out.println("Content-Length: " + jsonPayload.length());
        out.println(); // End of headers
        out.println(jsonPayload);

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String response;
        while ((response = in.readLine()) != null) {
            logger.info("Server response: " + response);
        }
        logger.info("PUT request completed and socket closed.");
        socket.close();

    }

    public static void main(String[] args) {
        ContentServer server = new ContentServer();
        server.run();
    }
}
