#!/usr/bin/bash

# Usage: ./bump_version.sh <version> <type>
# Example: ./bump_version.sh 1.2 major  -> 2.0
#          ./bump_version.sh 1.2 minor  -> 1.3
#          ./bump_version.sh 1.2 dev    -> 1.2-dev.a3f4b2c1

if [ $# -ne 2 ]; then
    echo "Usage: $0 <version> <type>"
    echo "  version: x.y format (e.g., 1.2)"
    echo "  type:    major | minor | dev"
    exit 1
fi

version="$1"
type="$2"

# Validate version format x.y
if ! [[ "$version" =~ ^[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must be in x.y format (e.g., 1.2)" >&2
    exit 1
fi

# Split into major (x) and minor (y)
x="${version%.*}"
y="${version#*.}"

case "$type" in
    major)
        x=$((x + 1))
        y=0
        echo "${x}.${y}"
        ;;
    minor)
        y=$((y + 1))
        echo "${x}.${y}"
        ;;
    dev)
        # Keep input version, append random 8-char hex hash
        hash=$(tr -dc 'a-f0-9' < /dev/urandom | head -c 8)
        echo "${version}-dev.${hash}"
        ;;
    *)
        echo "Error: type must be 'major', 'minor', or 'dev'" >&2
        exit 1
        ;;
esac