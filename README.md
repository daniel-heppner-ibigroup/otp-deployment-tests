# OTP deployment tests

Copy the example configuration and set the deployment URL to your OTP instance:

```sh
cp config.example.kdl config.kdl
```

The local `config.kdl` is excluded from Git. The container reads it from
`/app/config.kdl`; it is mounted read only and is not included in the image.
The example Compose file also keeps generated reports in the local `results/`
directory.

```sh
docker compose -f compose.example.yaml up --build
```

The application is available on port 8080, including `/health`, `/metrics`, and
`/reports`. See [report details](docs/reports.md) for report retention settings.
