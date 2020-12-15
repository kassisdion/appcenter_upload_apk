#!/bin/bash

echo "compiling..."
kotlinc src/main.kt -include-runtime -d out/main.jar

echo "launching..."
java -jar out/main.jar
