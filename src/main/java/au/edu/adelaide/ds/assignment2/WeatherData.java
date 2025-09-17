package au.edu.adelaide.ds.assignment2;

public class WeatherData {
    private String station;
    private String temperature;
    private String humidity;
    private String timestamp;
    private String replica;

    public WeatherData(String station, String temperature, String humidity, String timestamp, String replica) {
        this.station = station;
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
        this.replica = replica;
    }
}