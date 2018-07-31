@echo off
@if not "%1" == "" (
	echo gradlew run --args %1
	gradlew run --args %1
) else (
	echo gradlew run
	gradlew run
)