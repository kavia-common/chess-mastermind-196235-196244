#!/bin/bash
cd /home/kavia/workspace/code-generation/chess-mastermind-196235-196244/android_frontend
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

