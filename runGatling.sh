#!/bin/bash

# set environment variables
export GATLING_URL=http://localhost:8080
export GATLING_USERS=10
export GATLING_REQUESTS=500
export GATLING_RAMP_TIME=0

# interactively compile and run the simulation
./gatling/bin/gatling.sh
