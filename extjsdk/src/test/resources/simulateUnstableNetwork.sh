#!/bin/sh
# shellcheck disable=SC3005,SC3018

# Shell script for Mac OS that will turn off then on wifi to monkey with network.
# This allows for manual testing for connector disconnection recovery.

for ((i = 0; i < 100; i++)) do
    echo "Attempt $i";
    echo "disabling network";
    networksetup -setnetworkserviceenabled Wi-Fi off;
    echo "sleeping for 10 seconds";
    sleep 10;
    echo "reconnecting network";
    networksetup -setnetworkserviceenabled Wi-Fi on;
    echo "sleeping for a minute";
    sleep 60;
done
