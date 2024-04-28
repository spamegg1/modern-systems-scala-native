#!/bin/bash

# download Gatling Highcharts Bundle
curl -O https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/3.10.5/gatling-charts-highcharts-bundle-3.10.5-bundle.zip

# unzip, cleanup and rename folder
unzip gatling-charts-highcharts-bundle-3.10.5-bundle.zip
rm gatling-charts-highcharts-bundle-3.10.5-bundle.zip
mv gatling-charts-highcharts-bundle-3.10.5 gatling

echo "installed gatling 3.10.5 to directory ./gatling"

# There is a simulation (written in Java) included by default. Remove that.
rm -rf ./gatling/user-files/simulations/*
echo "removed included-by-default Java simulations in ./gatling/user-files/simulations"

# Place our simulation there.
cp src/main/scala/ch05/examples/loadSimulation.scala ./gatling/user-files/simulations/
echo "copied ch05 load simulation into ./gatling/user-files/simulations/"
echo "now start the http server in another Terminal, and then run the simulation with:"
echo "./runGatling.sh"
