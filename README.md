# RITMO Movesense DataLogger

## About

An adapted version of the Movesense DataLogger sample app found [here](https://bitbucket.org/movesense/movesense-mobile-lib/src/master/).

## Functionality

This version adds the following functionality:
- Logging multiple sensors per device at the same time (i.e., IMU & ECG)
- Disconnect from connected devices on start scan (avoids need for phone restart in order for Movesense device to be visible again)
- UI improvements

Based on version 1.4 from Movesense, therefore this includes the option to download logged data from devices as raw SBEM files, as well as JSON.

Currently only supports logging of IMU at 208Hz and ECG at 500Hz. Other combinations of sensors can be added in res ➔ values ➔ strings.xml (delimited by :)(requires rebuild of app from source code).
