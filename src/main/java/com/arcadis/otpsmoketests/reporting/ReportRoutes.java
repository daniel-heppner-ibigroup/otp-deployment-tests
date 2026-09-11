package com.arcadis.otpsmoketests.reporting;

import io.javalin.Javalin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ReportRoutes {

  private ReportRoutes() {}

  public static void register(Javalin app, ReportStore store)
    throws IOException {
    String css;
    try (
      var resource = ReportRoutes.class.getResourceAsStream(
          "/reporting/report.css"
        )
    ) {
      if (resource == null) throw new IOException("Missing report stylesheet");
      css = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }
    app.before(
      "/reports*",
      ctx -> {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Cache-Control", "no-store");
        ctx.header(
          "Content-Security-Policy",
          "default-src 'none'; style-src 'self'; base-uri 'none'; frame-ancestors 'none'"
        );
      }
    );
    app.get("/reports", ctx -> ctx.html(ReportPages.index(store.list())));
    app.get(
      "/reports/assets/report.css",
      ctx -> ctx.contentType("text/css; charset=utf-8").result(css)
    );
    app.get(
      "/reports/{id}",
      ctx -> {
        var report = store.load(ctx.pathParam("id"));
        if (report.isEmpty()) {
          ctx
            .status(404)
            .html(
              "<!doctype html><html lang=\"en\"><title>Report not found</title><h1>Report not found</h1><p>It may have expired.</p><a href=\"/reports\">All reports</a></html>"
            );
        } else {
          ctx.html(ReportPages.report(report.get()));
        }
      }
    );
  }
}
