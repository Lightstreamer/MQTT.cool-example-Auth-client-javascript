/*
 * MQTT.Cool - http://www.lightstreamer.com Authentication and Authorization Demo Copyright (c)
 * Lightstreamer Srl Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
 * writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package cool.mqtt.examples.auth_hooks;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cool.mqtt.hooks.HookException;
import cool.mqtt.hooks.MQTTCoolHook;
import cool.mqtt.hooks.MqttBrokerConfig;
import cool.mqtt.hooks.MqttConnectOptions;
import cool.mqtt.hooks.MqttMessage;
import cool.mqtt.hooks.MqttSubscription;

/**
 * Hook class for authorization checks.
 */
public class AuthHook implements MQTTCoolHook {

  /** Map for sessionId-user pairs */
  private final ConcurrentHashMap<String, String> sessionIdToUsers = new ConcurrentHashMap<>();

  @Override
  public void init(File configDir) throws HookException {
    // No specific initialization tasks to perform.
  }

  @Override
  public MqttBrokerConfig resolveAlias(String alias) throws HookException {
    // Actually never invoked.
    return null;
  }

  @Override
  public boolean canOpenSession(String sessionId, String user, String password,
      @SuppressWarnings("rawtypes") Map clientContext, String clientPrincipal)
      throws HookException {

    /*
     * A user is connecting. We suppose the password works as an authentication token, generated by
     * the webserver in response to a user/password login made by the client. Thus, we have to ask
     * the same server (or a common backend like a memcached or a DB) if the received token is
     * (still) valid. This demo does not actually perform the request, user/token pairs are
     * hard-coded in the AuthorizationRequest class.
     */
    AuthorizationResult result = AuthorizationRequest.validateToken(user, password);
    if (!AuthorizationResult.OK.equals(result)) {
      throw new HookException(result.getCode(),
          "Unauthorized access: token invalid for user '" + user + "'");
    }

    /*
     * Since subsequent Hook calls will rely only on the sessionId, we store the user associated
     * with this sessionId on an internal map.
     */
    sessionIdToUsers.put(sessionId, user);
    return true;

    /*
     * NOTE: as the canOpenSession call is blocking, a further blocking call you may need to perform
     * the client lookup may require a proper configuration of the specific "SET" thread pool
     * mqtt_master_connector_conf.xml file for MQTT.Cool. We could also speed up things using a
     * local cache.
     */

    /*
     * NOTE 2: it is common practice for a webserver to place its session token inside a cookie; if
     * the cookie, the SDK for Web Client, and MQTT.Cool are properly configured, such cookie is
     * available in the HTTP headers map, which can be obtained from the clientContext map with the
     * "HTTP_HEADERS" key; you might be tempted to use it to authenticate the user: this approach is
     * discouraged, please check the MQTT.Cool configuration file for the <use_protected_js> and
     * <forward_cookies> documentation for further info about the topic.
     */
  }

  @Override
  public void onSessionClose(String sessionId) {
    /*
     * A user is disconnecting. We clear the internal map from the association between this session
     * Id and its user.
     */
    sessionIdToUsers.remove(sessionId);
  }

  @Override
  public boolean canConnect(String sessionId, String clientId, String brokerAddress,
      MqttConnectOptions connectOptions) throws HookException {

    /*
     * A user is trying to connect to the specified MQTT broker, we have to verify if he is
     * authorized to perform what it is asking for. To do this we first recover the user associated
     * with the session Id from our internal map. This task might be performed by checking an
     * external service or a local cache. If a service has to be queried, it is, in most cases,
     * better to query it beforehand in the canOpenSession method. This class assumes such info has
     * been cached somewhere else. On the other hand, the AuthHookWithAuthCache class (available in
     * this package) takes a step further and shows the cache-during-canOpenSession approach. In any
     * case this demo does not actually perform the request, as user authorizations are hard-coded
     * in the AuthorizationRequest class.
     */
    String user = sessionIdToUsers.get(sessionId);
    if (user == null) {
      return false; // Should never happen
    }

    AuthorizationResult result = AuthorizationRequest.authorizeMQTTConnection(user, brokerAddress);
    if (!AuthorizationResult.OK.equals(result)) {
      throw new HookException(result.getCode(), "Unauthorized access: user '" + user
          + "' can't connect to broker '" + brokerAddress + "'");
    }

    return true;
  }

  @Override
  public boolean canPublish(String sessionId, String clientId, String brokerAddress,
      MqttMessage message) throws HookException {

    /*
     * A user is trying to publish a message to a topic, we have to verify if he is authorized to
     * perform what it is asking for. To do this we first recover the user associated with the
     * session Id from our internal map. This task might be performed by checking an external
     * service or a local cache. If a service has to be queried, it is, in most cases, better to
     * query it beforehand in the canOpenSession method. This class assumes such info has been
     * cached somewhere else. On the other hand, the AuthHookWithAuthCache class (available in this
     * package) takes a step further and shows the cache-during-canOpenSession approach. In any case
     * this demo does not actually perform the request, as user authorizations are hard-coded in the
     * AuthorizationRequest class.
     */
    String user = sessionIdToUsers.get(sessionId);
    if (user == null) {
      return false; // Should never happen
    }

    AuthorizationResult result =
        AuthorizationRequest.authorizePublishTo(user, message.getTopicName());
    if (!AuthorizationResult.OK.equals(result)) {
      throw new HookException(result.getCode(),
          String.format("Unauthorized access: user '%s' can't publish messages to '%s'", user,
              message.getTopicName()));
    }

    return true;
  }

  @Override
  public boolean canSubscribe(String sessionId, String clientId, String brokerAddress,
      MqttSubscription subscription) throws HookException {

    /*
     * A user is trying to subscribe to a topic, we have to verify if he is authorized to perform
     * what he's asking for. To do this we first recover the user associated with the session id
     * from our internal map. This task might be performed by checking an external service or a
     * local cache. If a service has to be queried, it is, in most cases, better to query it
     * beforehand in the canOpenSession method. This class assumes such info has been cached
     * somewhere else. On the other hand, the AuthHookWithAuthCache class (available in this
     * package) takes a step further and shows the cache-during-canOpenSession approach. In any case
     * this demo does not actually perform the request, as user authorizations are hard-coded in the
     * AuthorizationRequest class.
     */
    String user = sessionIdToUsers.get(sessionId);
    if (user == null) {
      return false; // Should never happen
    }

    AuthorizationResult result =
        AuthorizationRequest.authorizeSubscribeTo(user, subscription.getTopicFilter());
    if (!AuthorizationResult.OK.equals(result)) {
      throw new HookException(result.getCode(),
          String.format("Unauthorized access: user '%s' can't receive messages from '%s'", user,
              subscription.getTopicFilter()));
    }

    return true;
  }

  @Override
  public void onDisconnection(String sessionId, String clientId, String brokerAddress) {
    // Nothing to do.
  }

  @Override
  public void onUnsubscribe(String sessionId, String clientId, String brokerAddress,
      String topicFilter) {

    // Nothing to do.
  }
}
