#!/bin/sh


javacPath="$JAVA_HOME/bin/javac"
javaPath="$JAVA_HOME/bin/java"


if [ ! -f "./bunch-git-hook/src/main/java/org/jetbrains/bunches/precommitHook/BunchPreCommitHook.java" ]; then
    echo "Pre-commit hook .java file was not found"
    exit 0
fi

if [ ! -f "$javaPath" ]; then
    echo "'java' ($javaPath) was not found, pre-commit hook is disabled"
    exit 0
fi

if [ ! -f "$javacPath" ]; then
    echo "'javac' ($javacPath) was not found, pre-commit hook is disabled"
    exit 0
fi



mkdir -p ./build/preCommitHook
"$javacPath" -d ./build/preCommitHook ./bunch-git-hook/src/main/java/org/jetbrains/bunches/precommitHook/BunchPreCommitHook.java
cd ./build/preCommitHook

"$javaPath" org.jetbrains.bunches.precommitHook.BunchPreCommitHook
returnCode=$?

cd ../..

exit $returnCode