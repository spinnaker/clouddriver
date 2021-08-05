FROM openjdk:11
COPY . /tmp
WORKDIR /tmp
CMD ["java", "-jar", "JavaPassFromConsole.jar"]



