package au.edu.adelaide.ds.assignment2;

public class WeatherRecord {
    private final String station;
    private final String temperature;
    private final String humidity;
    private final int lamportTimestamp;
    private final long receivedTime; // Used for 30s expiry

    public WeatherRecord(String station, String temperature, String humidity, int lamportTimestamp, long receivedTime) {
        this.station = station;
        this.temperature = temperature;
        this.humidity = humidity;
        this.lamportTimestamp = lamportTimestamp;
        this.receivedTime = receivedTime;
    }

    // Getters
    public String getStation() {
        return station;
    }

    public String getTemperature() {
        return temperature;
    }

    public String getHumidity() {
        return humidity;
    }

    public int getLamportTimestamp() {
        return lamportTimestamp;
    }

    public long getReceivedTime() {
        return receivedTime;
    }

    //debugging/logging
    public String toString() {
        return String.format("Station: %s, Temp: %s, Humidity: %s, Lamport: %d, Received: %d",
                station, temperature, humidity, lamportTimestamp, receivedTime);
    }
}
