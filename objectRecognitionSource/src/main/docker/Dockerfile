FROM openjdk:11-jre-slim
RUN mkdir /app
ADD objectRecognitionSource.tar /app
WORKDIR /app
ENTRYPOINT ["./objectRecognitionSource/bin/objectRecognitionSource"]