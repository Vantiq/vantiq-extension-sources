FROM amazoncorretto:11-alpine-jdk
RUN apk update && apk add --upgrade libcrypto3 libssl3
RUN mkdir /app
ADD testConnector.tar /app
WORKDIR /app
ENTRYPOINT ["./testConnector/bin/testConnector"]