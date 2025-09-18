# ==============
# DS Assignment 2 - Automation
# ==============

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

# --------------------
# Automated test helpers
# --------------------

# Test 204 (no records, should return 204)
test-204:
	@echo ">>> Running 204 test (expect no records)..."
	@mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.GETClient" "-Dexec.args=localhost:4567" | grep "204" || echo "FAILED: Expected 204"

# Test 400 (malformed payload)
test-400:
	@echo ">>> Running 400 test (expect Bad Request)..."
	@mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.ContentServer" "-Dexec.args=localhost:4567 bad_weather.txt replica1" | grep "400" || echo "FAILED: Expected 400"

# Test 500 (simulate server crash / forced failure)
test-500:
	@echo ">>> Running 500 test (expect Internal Server Error)..."
	@mvn exec:java "-Dexec.mainClass=au.edu.adelaide.ds.assignment2.ContentServer" "-Dexec.args=localhost:4567 crash.txt replica1" | grep "500" || echo "FAILED: Expected 500"

# Full test run
test: test-204 test-400 test-500
	@echo ">>> All tests attempted."

# Kill stray Java processes (useful if something hangs)
kill:
	@echo ">>> Killing Java processes..."
	@pkill -f "au.edu.adelaide.ds.assignment2" || true
