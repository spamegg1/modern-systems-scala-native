#!/bin/bash

# set environment variables
export GATLING_URL=http://localhost:8080
export GATLING_USERS=1000
export GATLING_REQUESTS=50000
export GATLING_RAMP_TIME=0
echo "Finished setting up environment variables for Gatling simulation."

# interactively compile and run the simulation
echo "Now running the Gatling binary:"
./gatling/bin/gatling.sh
