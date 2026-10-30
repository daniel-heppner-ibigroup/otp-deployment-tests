# OTP deployment tests

Copy the example configuration and set the deployment URL to your OTP instance:

```sh
cp config.example.kdl config.kdl
```

The local `config.kdl` is excluded from Git. The container reads it from
`/app/config.kdl`; it is mounted read only and is not included in the image.

```sh
docker compose -f compose.example.yaml up --build
```

Test results and assertion failures are written to the console logs. The
application is available on port 8080, with `/health`, `/metrics`, and `/run-tests`
for manually running all suites.

JSON suites and extension rules are documented in [JSON test suites](docs/json-test-suites.md).

## Building

The runner requires the companion `otp-java-client` **3.0.0-SNAPSHOT**. Build and
install that checkout locally before building this project:

```sh
cd ../otp-java-client
mvn -Dtest='*Test,!IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false -Dgpg.skip=true -Dmaven.javadoc.skip=true install
cd ../otp-deployment-tests
mvn test package
```

A clean CI or Docker build needs this snapshot in its configured artifact
repository or a published version containing these changes, followed by an
update to `otp.client.version` in `pom.xml`.
