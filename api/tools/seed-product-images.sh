#!/usr/bin/env sh
set -eu

demo_api_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$demo_api_root"
if command -v mvn >/dev/null 2>&1; then
    mvn -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/demo-image-classpath.txt
else
    ./mvnw -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/demo-image-classpath.txt
fi
demo_separator=':'
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) demo_separator=';' ;; esac
demo_classpath="$(cat target/demo-image-classpath.txt)${demo_separator}target/classes"
java --class-path "$demo_classpath" tools/SeedProductImages.java "$@"
