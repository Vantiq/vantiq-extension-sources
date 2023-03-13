FROM amazoncorretto:11-alpine-jdk
RUN mkdir /app
ADD camelConnector.tar /app
WORKDIR /app
ENTRYPOINT ["./camelConnector/bin/camelConnector"]