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

mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.AggregationServer"

### 2. **Start a Content Server (Replica)**

You can run multiple replicas using different IDs and input files

first replica
mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.ContentServer" "-Dexec.args=replica1 weather1.txt"

second replica
mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.ContentServer" "-Dexec.args=replica2 weather2.txt"

Each Content Server:
- Reads local weather data from a file
- Embeds a Lamport timestamp
- Sends data via a PUT request to the Aggregation Server

### 3. **Start the Client**

mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.GETClient"

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

👉 First PUT → server responds **201 Created**  
👉 Subsequent PUTs (same station) → server responds **200 OK**

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

⏳ Wait 30s (expiry timeout)

**Terminal 3**
- `make client`

👉 Server responds **204 No Content**

</details>

## Test Procedure

### 1. 201 Created / 200 OK 

Terminal 1
- make build
- make server

Terminal 2  
- make content1

Terminal 3
- make client

First PUT -> server responds 201 Created
Subsequent PUTs(same station)->server responds 200 OK

### 2. 204 No Content (30s expiry)

Terminal 1
- make build
- make server

Terminal 2
- make content1
- After few updates, ctrl+c

Wait 30s(expiry timeout)

Terminal 3
- make client

### 3. 400 Bad Request

Terminal 1
- make build
- make server

Edit weather1.txt to contain:
{"badField": "oops"}

Terminal 2
- make content1

### 4. 500 Internal Server Error

Uncomment line 153 AggregationServer.java

Terminal 1
- make build
- make server

### 5. Persistence Test

Terminal 1
- make build
- make server

Terminal 2
- make content1

Terminal 1
- stop server, ctrl+c
- make server (restart)


