# The Anvil workbench as an image. Build it from the repository root after building the workbench JAR:
#
#   ./gradlew :anvil-workbench:workbench-server:shadowJar
#   docker build -f anvil-workbench/workbench-server/docker/Dockerfile -t anvil-workbench .
#
# Run it on the host network, with the host's Docker socket, and with its data directory mounted at the same path
# inside and outside: the workbench starts the scenarios' containers on the host's Docker, which mounts their
# workspaces by the workbench's own paths and publishes their ports on the host's loopback.
#
#   docker run -d --name anvil-workbench --network host \
#     -v /var/run/docker.sock:/var/run/docker.sock \
#     -v /srv/anvil-workbench:/srv/anvil-workbench \
#     anvil-workbench
#   docker exec anvil-workbench workbench pair
FROM eclipse-temurin:25-jre

ENV ANVIL_WORKBENCH_DATA=/srv/anvil-workbench
COPY anvil-workbench/workbench-server/build/libs/anvil-workbench-*.jar /opt/anvil/anvil-workbench.jar
RUN printf '#!/bin/sh\nexec java -jar /opt/anvil/anvil-workbench.jar "$@"\n' > /usr/local/bin/workbench \
	&& chmod +x /usr/local/bin/workbench

EXPOSE 7400
ENTRYPOINT ["workbench"]
CMD ["run"]
