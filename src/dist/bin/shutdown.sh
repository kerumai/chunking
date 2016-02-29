#!/usr/bin/env bash

jps | grep Server | cut -f1 -d" " | xargs kill
