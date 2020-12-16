#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
cd "$parent_path"

while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -apiToken)
    APITOKEN="$2"
    shift
    shift
    ;;
    -ownerName)
    OWNERNAME="$2"
    shift
    shift
    ;;
    -appName)
    APPNAME="$2"
    shift
    shift
    ;;
    -file)
    FILE="$2"
    shift
    shift
    ;;
    *)
    echo "Unknown argument: $1"
    exit 1
    ;;
esac
done

[ -z "$APITOKEN" ] && echo "missing parameter -apiToken" && exit 1
[ -z "$OWNERNAME" ] && echo "missing parameter -ownerName" && exit 1
[ -z "$APPNAME" ] && echo "missing parameter -appName" && exit 1
[ -z "$FILE" ] && echo "missing parameter -file" && exit 1

echo "compiling..."
kotlinc src/main.kt -include-runtime -d out/main.jar

echo "launching..."
java -jar out/main.jar -apiToken "$APITOKEN" -ownerName "$OWNERNAME" -appName "$APPNAME" -file "$FILE"
