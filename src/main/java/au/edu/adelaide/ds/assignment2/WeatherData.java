package au.edu.adelaide.ds.assignment2;

public class WeatherData {
    public String station;
    public String temperature;
    public String humidity;
    public String timestamp;

    public WeatherData(String station, String temperature, String humidity, String timestamp) {
        this.station = station;
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
    }
}
