# Building a Vantiq Extension Source — Agent Guide

This repository provides the tools to build a **Vantiq extension source** (a.k.a. *Enterprise
Connector* / *external source*). This file is the entry point for an automated coding agent: it
orients you, then hands off to the per-language SDK README, which is the self-contained build guide
(quickstart, gotchas, `server.config`).

## The two halves of an extension source

1. **Vantiq side** (defined in your Vantiq namespace, *not* in this repo): a `sourceimpls`
   entry registering a custom source **type** (`baseType: EXTENSION`,
   `verticle: service:extensionSource`), a `sources` instance of that type, and the VAIL /
   Visual event handlers that react to it. Template for the impl definition:
   `testConnector/src/test/resources/testConnectorImpl.json`.
2. **Connector** (this repo): the external program that connects to Vantiq over a WebSocket.
   Build it with one of the SDKs below.

## Build a connector — pick an SDK, then follow its README

Each SDK README is self-contained: a minimal quickstart, the canonical example to copy, the
SDK-specific gotchas, and the `server.config` contract. **Read its quickstart *and* its Gotchas
section before writing code** — the gotchas are non-obvious traps (async deadlocks, logging setup,
config sharing) that compile cleanly and then fail silently.

- **Java** — `extjsdk/README.md`. Class `io.vantiq.extjsdk.ExtensionWebSocketClient`. Copy the
  `testConnector/` example (`TestConnectorMain` / `TestConnectorCore` /
  `TestConnectorHandleConfiguration`) — the smallest complete, runnable connector.
- **Python** — `extpsdk/README.md`. Module `vantiqconnectorsdk`. Copy
  `pythonExecSource/src/main/python/pyExecConnector.py` — the only first-party Python example.

## The WebSocket protocol

Documented in the root `README.md` → **Operations** section: `connectExtension`,
`reconnectRequired`, `configureExtension`, `publish`, `notification`, `query`. The SDKs surface
these as the handler callbacks (and `sendNotification` for outbound source events).

## Build & run

```
./gradlew extjsdk:fatJar          # build the Java SDK fat jar -> extjsdk/build/libs/extjsdk-fat.jar
./gradlew <connector>:assemble    # build an example connector -> <connector>/build/distributions/*.{zip,tar}
#   then unpack and run bin/<connector> from a directory containing serverConfig/server.config
```

- Build with **JDK 11+** (some components require it; Java **8** is the source level). Use the
  wrapper `./gradlew` (Gradle 8.9) — not a system `gradle`.
- There are **no release tags**; pin to a `master` commit SHA.
- Do not commit or push changes unless explicitly asked.
