/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softwarefactory.keycloak.social.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;

public class GithubSSHKeyMapper extends AbstractIdentityProviderMapper {
  private static final String[] cp = new String[] { "github" };

  protected static final Logger logger = Logger.getLogger(GithubSSHKeyMapper.class);

  protected static final Logger LOGGER_DUMP_USER_PROFILE = Logger.getLogger("org.keycloak.social.user_profile_dump");

  public static final String SSH_URL = "https://api.github.com/users/%s/keys";

  public static final String CONF_KEY_ATTRIBUTE = "keyAttribute";

  private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

  static {
    ProviderConfigProperty property = new ProviderConfigProperty();
    property.setName("keyAttribute");
    property.setLabel("User Attribute Name");
    property.setHelpText("User attribute name to store the SSH public keys into.");
    property.setType("String");
    configProperties.add(property);
  }

  public String[] getCompatibleProviders() {
    return cp;
  }

  public String getId() {
    return "github-ssh-key-mapper";
  }

  public List<ProviderConfigProperty> getConfigProperties() {
    return configProperties;
  }

  public String getDisplayCategory() {
    return "SSH Keys Importer";
  }

  public String getDisplayType() {
    return "SSH Keys Importer";
  }

  public String getHelpText() {
    return "Import user SSH keys into the specified user attribute.";
  }

  public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {}

  public void preprocessFederatedIdentity(KeycloakSession session, RealmModel realm, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
    String attribute = (String)mapperModel.getConfig().get("keyAttribute");
    if (attribute == null || attribute.trim().isEmpty()) {
      logger.warnf("Attribute is not configured for mapper %s", mapperModel.getName());
      return;
    }
    attribute = attribute.trim();
    Object value = getKeys(context, session);
    if (value != null)
      if (value instanceof List) {
        context.setUserAttribute(attribute, (List)value);
      } else {
        context.setUserAttribute(attribute, value.toString());
      }
  }

  protected static Object getKeys(BrokeredIdentityContext context, KeycloakSession session) {
    String username = context.getUsername();
    String keys_url = String.format("https://api.github.com/users/%s/keys", new Object[] { username });
    List<String> keys = new ArrayList<>();
    try {
      ObjectNode keys_list = (ObjectNode)SimpleHttp.doGet(keys_url, session).asJson();
      Iterator<JsonNode> loop = keys_list.elements();
      while (loop.hasNext()) {
        JsonNode key = loop.next();
        keys.add(key.get("key").textValue());
      }
      return keys;
    } catch (Exception e) {
      throw new IdentityBrokerException("Could not obtain user public keys from github.", e);
    }
  }
}
