FROM amazoncorretto:17-alpine-jdk
RUN apk update && apk add --upgrade libcrypto3 libssl3
RUN mkdir /app
ADD camelConnector.tar /app
WORKDIR /app
ENTRYPOINT ["./camelConnector/bin/camelConnector"]