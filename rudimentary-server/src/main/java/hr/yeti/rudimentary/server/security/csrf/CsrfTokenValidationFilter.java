package hr.yeti.rudimentary.server.security.csrf;

import com.sun.net.httpserver.HttpExchange;
import hr.yeti.rudimentary.config.ConfigProperty;
import hr.yeti.rudimentary.config.spi.Config;
import hr.yeti.rudimentary.context.spi.Instance;
import hr.yeti.rudimentary.http.HttpMethod;
import hr.yeti.rudimentary.http.filter.spi.HttpFilter;
import hr.yeti.rudimentary.http.session.Session;
import hr.yeti.rudimentary.server.http.Cookie;
import hr.yeti.rudimentary.server.http.HttpRequestUtils;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CsrfTokenValidationFilter extends HttpFilter {

  private ConfigProperty csrfEnabled = new ConfigProperty("security.csrf.enabled");
  private ConfigProperty csrfStateless = new ConfigProperty("security.csrf.stateless");
  private ConfigProperty csrfTokenHttpHeaderName = new ConfigProperty("security.csrf.tokenHttpHeaderName");
  private ConfigProperty csrfTokenCookieName = new ConfigProperty("security.csrf.tokenCookieName");

  @Override
  public boolean conditional() {
    return csrfEnabled.asBoolean();
  }

  @Override
  public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
    if (csrfEnabled.asBoolean()) {

      // If session.create=true and security.csrf.stateless=false, 
      // create CSRF token in the same request when RSID is generated and continue.
      // If session is created for the first time, it should be set in a 
      // response cookie with name RSID by the HttpSessionCreatingFilter.
      List<String> rsidResponseCookieList = exchange.getResponseHeaders().get("Set-Cookie");
      if (!csrfStateless.asBoolean() && Objects.nonNull(rsidResponseCookieList) && rsidResponseCookieList.get(0).contains(Session.COOKIE)) {
        CsrfToken csrfToken = Instance.of(CsrfTokenStore.class).create();

        HttpCookie cookie = new HttpCookie(
            Config.provider().property("security.csrf.tokenCookieName").value(),
            csrfToken.getValue()
        );
        cookie.setHttpOnly(false);
        cookie.setMaxAge(-1);

        exchange.getResponseHeaders().add("Set-Cookie", new Cookie(cookie).toString());
        chain.doFilter(exchange);
        return;
      }

      boolean csrfValid = false;
      Map<String, HttpCookie> cookies = HttpRequestUtils.parseCookies(exchange.getRequestHeaders());

      // If CSRF token cookie is absent from the request then we provide one 
      // in response but we stop the process at this stage.
      // Using this way we implement the first providing of token.
      if (!cookies.containsKey(csrfTokenCookieName.value())) {
        CsrfToken csrfToken = Instance.of(CsrfTokenStore.class).create();

        HttpCookie cookie = new HttpCookie(csrfTokenCookieName.value(), csrfToken.getValue());
        cookie.setHttpOnly(false);
        cookie.setMaxAge(-1);

        exchange.getResponseHeaders().add("Set-Cookie", new Cookie(cookie).toString());
        //clearly identify an initial response providing the initial CSRF token.
        exchange.sendResponseHeaders(204, 0);
        return;
      }

      // Skip for non state changing requests.
      if (EnumSet.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE)
          .contains(HttpMethod.valueOf(exchange.getRequestMethod()))) {
        chain.doFilter(exchange);
        return;
      }

      if (csrfStateless.asBoolean()) {
        List<String> csrfHttpHeader = exchange.getRequestHeaders().get(csrfTokenHttpHeaderName.value());

        if (Objects.nonNull(csrfHttpHeader) || !csrfHttpHeader.isEmpty()) {
          csrfValid = csrfHttpHeader.get(0).equals(cookies.get(csrfTokenCookieName.value()).getValue());
        }
      } else {
        if (!cookies.containsKey(Session.COOKIE)) {
          exchange.sendResponseHeaders(403, 0);
          return;
        } else {
          String rsid = cookies.get(Session.COOKIE).getValue();
          Session session = (Session) exchange.getAttribute(rsid);
          csrfValid = cookies.get(csrfTokenCookieName.value()).getValue().equals(session.getCsrfToken());
        }
      }

      if (!csrfValid) {
        exchange.sendResponseHeaders(403, 0);
        return;
      }
    }
    chain.doFilter(exchange);
  }

  @Override
  public String description() {
    return "Filter which validates CSRF token.";
  }

  @Override
  public Class[] dependsOn() {
    return new Class[]{ CsrfTokenStore.class };
  }

}
