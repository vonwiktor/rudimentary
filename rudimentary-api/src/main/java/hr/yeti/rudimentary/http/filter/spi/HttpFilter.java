package hr.yeti.rudimentary.http.filter.spi;

import com.sun.net.httpserver.Filter;
import hr.yeti.rudimentary.context.spi.Instance;

/**
 * Class used to register custom http filter. Use this to enrich incoming request or outgoing
 * response or to prevent request from executing depending on some condition.
 *
 * Since this abstract class implements {@link Instance} it means it is loaded automatically via
 * {@link ServiceLoader} on application startup.
 *
 * You can have as many different HttpFilter implementations as you want and you can register them
 * in <i>src/main/resources/META-INF/services/hr.yeti.rudimentary.http.filter.spi.HttpFilter</i>
 * file.
 *
 * @author vedransmid@yeti-it.hr
 */
public abstract class HttpFilter extends Filter implements Instance {

  /**
   * Condition on which this filter addition depends on. If the condition is true, filter will be
   * added to the executing context, otherwise not. To be more exact, if condition value is true,
   * filter will be used, otherwise it will not be used.
   *
   * @return true if condition is true, otherwise false.
   */
  public abstract boolean activatingCondition();

}
