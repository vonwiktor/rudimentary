package hr.yeti.rudimentary.http.session;

import com.sun.net.httpserver.HttpExchange;
import hr.yeti.rudimentary.events.EventPublisher;
import hr.yeti.rudimentary.security.Identity;
import java.util.Map;
import java.util.Optional;

/**
 * An abstraction of HTTP session. Mostly used in MVC.
 *
 * @see <b>rudimentary/rudimentary-server</b> module for details about how RSID session cookie is generated.
 *
 * @author vedransmid@yeti-it.hr
 */
public interface Session {

    /**
     * Http cookie name used to identify user's session.
     */
    public static final String COOKIE = "RSID";

    /**
     * Name of the URI to which application will go after successful authentication. The value of URI is stored in
     * user's Session.
     */
    public static final String DEEP_LINK_URI = "deepLinkURI";

    /**
     * Get unique session id value which uniquely identifies session.
     *
     * @return Unique session id value.
     */
    String getRsid();

    /**
     * Get session creation time.
     *
     * @return Time of session creation.
     */
    long getCreationTime();

    /**
     * Get map of session attributes. Here you can store values to be used during HTTP session.
     *
     * @return Map of session attributes.
     */
    Map<String, Object> getAttributes();

    /**
     * Get session attribute by name.
     *
     * @param name Name of the session attribute.
     * @return Session attribute.
     */
    Optional<Object> getAttribute(String name);

    /**
     * Get time of last session access.
     *
     * @return Time of last session access.
     */
    long getLastAccessedTime();

    /**
     * Destroy current session.
     *
     * @param httpExchange {@link HttpExchange}
     */
    void invalidate(HttpExchange httpExchange);

    /**
     *
     * @return Whether or not user request has been successfully authenticated.
     */
    boolean isAuthenticated();

    /**
     *
     * @return Authenticated user.
     */
    Identity getIdentity();

    /**
     * Gets user details with custom details.
     *
     * @param <D> Type of user details.
     * @param details User details class.
     * @return User identity with custom details.
     */
    public <D> Identity<D> getIdentity(Class<D> details);

    /**
     * Static method to get session.If session does not yet exist it is created.
     *
     * @param exchange {@link HttpExchange}
     * @param createIfAbsent Set to true to create new session otherwise false.
     * @return
     */
    static Session acquire(HttpExchange exchange, boolean createIfAbsent) {
        AcquireSessionEvent acquireSessionEvent = new AcquireSessionEvent(exchange, createIfAbsent);
        acquireSessionEvent.publish(EventPublisher.Type.SYNC);
        return acquireSessionEvent.getSession();
    }
}
