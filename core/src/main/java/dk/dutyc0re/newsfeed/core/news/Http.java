package dk.dutyc0re.newsfeed.core.news;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Shared HTTP client for all fetchers. Always sends our User-Agent. Fetcher thread only. */
public class Http {

  private final HttpClient client;
  private final String userAgent;

  public Http(String userAgent) {
    this.userAgent = userAgent;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  /** GETs the URL and returns the raw body; throws on anything but HTTP 200. */
  public byte[] get(String url, String accept) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .header("User-Agent", this.userAgent)
        .header("Accept", accept)
        .GET()
        .build();

    HttpResponse<byte[]> response = this.client.send(
        request,
        HttpResponse.BodyHandlers.ofByteArray()
    );
    if (response.statusCode() != 200) {
      throw new IOException(url + " answered with HTTP " + response.statusCode());
    }
    return response.body();
  }
}
