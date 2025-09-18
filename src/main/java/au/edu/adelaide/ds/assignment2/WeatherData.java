package au.edu.adelaide.ds.assignment2;

public class WeatherData {
    private String station;
    private String temperature;
    private String humidity;
    private String lamport;
    private String replicaId;

    public WeatherData(String station, String temperature, String humidity, String lamport, String replicaId) {
        this.station = station;
        this.temperature = temperature;
        this.humidity = humidity;
        this.lamport = lamport;
        this.replicaId = replicaId;
    }
}
