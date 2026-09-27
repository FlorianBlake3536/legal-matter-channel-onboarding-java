#!/bin/sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/legal-matter-onboarding-example"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java' -print)
java -cp "$BUILD_DIR" example.legal.onboarding.MatterOnboardingExample
rm -rf "$BUILD_DIR"
