FROM amazoncorretto:11-alpine-jdk
RUN apk update && apk add --upgrade libcrypto3 libssl3
RUN mkdir /app
ADD objectRecognitionSource.tar /app
WORKDIR /app
ENTRYPOINT ["./objectRecognitionSource/bin/objectRecognitionSource"]