# The server's image (backend spec §11.1).
#
# The distribution is built on the host first:
#
#     ./gradlew :backend:installDist
#
# and only copied here. Building inside the image would need the Android SDK, because the
# wire types the server shares with the app live in a multiplatform module.
FROM eclipse-temurin:25-jre

RUN useradd --system --no-create-home anatomy
COPY build/install/backend /app
USER anatomy
EXPOSE 8080
ENTRYPOINT ["/app/bin/backend"]
