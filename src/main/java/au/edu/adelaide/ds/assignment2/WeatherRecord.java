package au.edu.adelaide.ds.assignment2;

/**
 * WeatherRecord represents a single weather entry stored in the AggregationServer.
 * It holds:
 * - station ID (id from content server)
 * - temperature
 * - humidity
 * - replicaId (optional, for tracking which content server sent it)
 * - Lamport timestamp (for ordering)
 * - receivedTime (for 30s expiry)
 */
public class WeatherRecord {
    private final String station;           // station ID
    private final String temperature;       // air_temp
    private final String humidity;          // rel_hum
    private final String replicaId;         // optional, which content server sent it
    private final int lamportTimestamp;     // Lamport logical clock
    private final long receivedTime;        // used for expiry

    public WeatherRecord(String station, String temperature, String humidity,
                         String replicaId, int lamportTimestamp, long receivedTime) {
        this.station = station;
        this.temperature = temperature;
        this.humidity = humidity;
        this.replicaId = replicaId;
        this.lamportTimestamp = lamportTimestamp;
        this.receivedTime = receivedTime;
    }

    // --- Getters ---
    public String getStation() {
        return station;
    }

    public String getTemperature() {
        return temperature;
    }

    public String getHumidity() {
        return humidity;
    }

    public String getReplicaId() {
        return replicaId;
    }

    public int getLamportTimestamp() {
        return lamportTimestamp;
    }

    public long getReceivedTime() {
        return receivedTime;
    }

    // --- For debugging/logging ---
    @Override
    public String toString() {
        return String.format(
                "Station: %s, Temp: %s, Humidity: %s, Replica: %s, Lamport: %d, Received: %d",
                station, temperature, humidity, replicaId, lamportTimestamp, receivedTime
        );
    }
}
