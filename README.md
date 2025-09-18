# Weather Aggregation System – DS Assignment 2

This project implements a **Distributed Weather Aggregation System** using Java. It simulates a set of weather **Content Servers (replicas)** that send weather data to a central **Aggregation Server**, which serves **Clients** via `GET` requests. The system supports **Lamport Clocks**, **fault tolerance**, and **record expiry** logic.

---

## Requirements

- **Java 11.0.28 (LTS)**
- **Maven 3.9.9**
- vendor: Eclipse Adoptium

---

## Dependencies

- Managed via pom.xml:
- gson (Google JSON)
- Java standard libraries (no external REST frameworks used)

## How to Run the System

Make sure you're inside the `DS_AT2` project root.

### 1. **Start the Aggregation Server**

`mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.AggregationServer"`  
`make server`

### 2. **Start a Content Server (Replica)**

You can run multiple replicas using different IDs and input files:  
`mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.ContentServer" "-Dexec.args=localhost:4567 weather1.txt replica1"`

first replica:  
`make content1`

second replica:  
`make content2`

Each Content Server:
- Reads local weather data from a file
- Embeds a Lamport timestamp
- Sends data via a PUT request to the Aggregation Server

### 3. **Start the Client**

`mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.GETClient" "-Dexec.args=localhost:4567"`  
`make client`

---

## Test Procedure

<details>
  <summary><strong>1. 201 Created / 200 OK</strong></summary>

**Terminal 1**
- `make build`
- `make server`

**Terminal 2**
- `make content1`

**Terminal 3**
- `make client`

First PUT → server responds **201 Created**  
Subsequent PUTs (same station) → server responds **200 OK**

</details>

---

<details>
  <summary><strong>2. 204 No Content (30s expiry)</strong></summary>

**Terminal 1**
- `make build`
- `make server`

**Terminal 2**
- `make content1`
- After few updates, `ctrl+c`

Wait 30s (expiry timeout)

**Terminal 3**
- `make client`

Server responds **204 No Content**

</details>

---

<details>
  <summary><strong>3. 400 Bad Request</strong></summary>

**Terminal 1**
- `make build`
- `make server`

Edit `weather1.txt` to contain:
{"badField": "oops"}

**Terminal 2**
- make content1
</details>

---

<details>
  <summary><strong>4. 500 Internal Server Error</strong></summary>

Uncomment line 145 AggregationServer.java

**Terminal 1**
- `make build`
- `make server`

</details>

---

<details>
  <summary><strong>5. Persistence Test</strong></summary>

**Terminal 1**
- `make build`
- `make server`

**Terminal 2**
- `make content`

**Terminal 1**
- `stop server, ctrl+c`
- `make server(restart`

</details>

---

<details>
  <summary><strong>Multiple Content Servers (Replication / Fault Tolerance)</strong></summary>

- `Start two ContentServers (replica1 and replica2) with different input files.`
- `Confirm that GETClient shows both stations.`
- `Kill one replica → wait 30s → confirm expired records disappear while the other replica’s remain.`
- `This shows your system handles multiple sources and expiry correctly.`

</details>

---

<details>
  <summary><strong>Lamport Clock Ordering</strong></summary>

- `Start content1 and content2 simultaneously.`
- `Confirm that GETClient shows records sorted by Lamport timestamp, not by arrival order.`
- `Useful to prove logical time ordering works across replicas.`

</details>

---

<details>
  <summary><strong>Invalid File / Missing ID (Content Server)</strong></summary>

- `Modify weather1.txt to remove the id: line.`
- `ContentServer should refuse to send or AggregationServer should reject with 400 Bad Request.`
- `This checks validation of the input feed.`

</details>

---

<details>
  <summary><strong>Crash Recovery (Persistence)</strong></summary>

- `Start content1 → let it send data → stop AggregationServer.`
- `Restart AggregationServer.`
- `Confirm weather_data.json restores into memory and GETClient can still fetch old records.`

</details>

---

<details>
  <summary><strong>Expiry Without Restart</strong></summary>

- `Run content1 → stop it after one update.`
- `Wait 30s.`
- `Run GETClient.`
- `Confirm expired data is gone and GETClient shows 204 No Content if no replicas are alive.`

</details>

---

<details>
  <summary><strong>Edge Case: Empty File</strong></summary>

- `Provide a weather.txt file with only whitespace.`
- `ContentServer should fail gracefully or AggregationServer should return 204 No Content.`

</details>
