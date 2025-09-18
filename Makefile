# Default target
all: build

# Clean and compile project
build:
	mvn clean compile

# Run AggregationServer
server:
	mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.AggregationServer"

# Run ContentServer 1 (replica1 with weather1.txt)
content1:
	mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.ContentServer" "-Dexec.args=localhost:4567 weather1.txt replica1"

# Run ContentServer 2 (replica2 with weather2.txt)
content2:
	mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.ContentServer" "-Dexec.args=localhost:4567 weather2.txt replica2"

# Run GETClient
client:
	mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.GETClient" "-Dexec.args=localhost:4567"

# Kill stray Java processes (useful if something hangs)
kill:
	@echo ">>> Killing Java processes..."
	@pkill -f "au.edu.adelaide.ds.assignment2" || true
