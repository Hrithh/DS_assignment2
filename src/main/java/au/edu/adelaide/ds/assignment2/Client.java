package au.edu.adelaide.ds.assignment2;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Client {

    private static final Logger logger = Logger.getLogger(Client.class.getName());
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9090;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            logger.info("Connected to Aggregation Server at " + SERVER_HOST + ":" + SERVER_PORT);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            //Send GET request
            out.println("GET /weather.json");
            out.println(); // End of headers

            //Read status line
            String statusLine = in.readLine();
            if (statusLine == null || !statusLine.contains("200")) {
                System.err.println("Server returned an error: " + statusLine);
                return;
            }

            //Skip headers until empty line
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Skip HTTP headers
            }

            //Read JSON response
            StringBuilder jsonBuilder = new StringBuilder();
            while ((line = in.readLine()) != null) {
                jsonBuilder.append(line);
            }

            String jsonResponse = jsonBuilder.toString();
            logger.info("Received JSON: " + jsonResponse);

            //Parse and display JSON
            Gson gson = new Gson();
            List<WeatherRecord> records = gson.fromJson(
                    jsonResponse, new TypeToken<List<WeatherRecord>>() {}.getType()
            );

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
