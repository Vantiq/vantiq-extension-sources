FROM amazoncorretto:11-alpine-jdk
RUN apk update && apk add --upgrade libcrypto3 libssl3
RUN mkdir /app
ADD jmsSource.tar /app
WORKDIR /app
ENTRYPOINT ["./jmsSource/bin/jmsSource"]