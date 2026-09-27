#!/bin/sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/legal-matter-onboarding-classes"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java src/test/java -name '*.java' -print)
java -cp "$BUILD_DIR" example.legal.onboarding.MatterOnboardingTest
rm -rf "$BUILD_DIR"
