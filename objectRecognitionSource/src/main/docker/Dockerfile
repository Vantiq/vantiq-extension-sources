FROM openjdk:11-jre-slim
RUN mkdir /app
RUN mkdir /app/models
ADD objectRecognitionSource.tar /app
ADD *.meta /app/models/
ADD *.pb /app/models/
ADD *.names /app/models/
WORKDIR /app
ENTRYPOINT ["./objectRecognitionSource/bin/objectRecognitionSource"]