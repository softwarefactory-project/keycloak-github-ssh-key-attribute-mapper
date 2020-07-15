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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.IdentityProviderMapper;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

@JBossLog
@AutoService(IdentityProviderMapper.class)
public class GithubSSHKeyMapper extends AbstractIdentityProviderMapper {

    private static final String[] COMPATIBLE_PROVIDERS = {"github"};

    public static final String SSH_KEYS_URL = "https://api.github.com/users/%s/keys";

    public static final String CONF_KEY_ATTRIBUTE = "keyAttribute";

    private static final List<ProviderConfigProperty> configProperties;

    static {
        ProviderConfigProperty keysAttributeProperty = new ProviderConfigProperty();
        keysAttributeProperty.setName(CONF_KEY_ATTRIBUTE);
        keysAttributeProperty.setLabel("User Attribute Name");
        keysAttributeProperty.setHelpText("User attribute name to store the SSH public keys into.");
        keysAttributeProperty.setType("String");
        configProperties = Collections.singletonList(keysAttributeProperty);
    }

    public String[] getCompatibleProviders() {
        return COMPATIBLE_PROVIDERS;
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

    @SuppressWarnings("unchecked")
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

        if (!IdentityProviderMapperSyncMode.FORCE.equals(mapperModel.getSyncMode())) {
            return;
        }

        fetchKeysAndUpdateAttributes(session, mapperModel, context, (keyAttribute, keys) -> {
            if (keys instanceof List) {
                user.setAttribute(keyAttribute, (List<String>) keys);
            } else {
                user.setSingleAttribute(keyAttribute, keys.toString());
            }
        });
    }

    @SuppressWarnings("unchecked")
    public void preprocessFederatedIdentity(KeycloakSession session, RealmModel realm, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

        fetchKeysAndUpdateAttributes(session, mapperModel, context, (keyAttribute, keys) -> {

            if (keys instanceof List) {
                context.setUserAttribute(keyAttribute, (List<String>) keys);
            } else {
                context.setUserAttribute(keyAttribute, keys.toString());
            }
        });
    }

    private void fetchKeysAndUpdateAttributes(KeycloakSession session, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context, BiConsumer<String, Object> updater) {

        String attribute = getKeyAttribute(mapperModel);
        if (attribute == null) {
            return;
        }

        Object value = getKeys(context, session);

        if (value == null) {
            return;
        }

        updater.accept(attribute, value);
    }

    private String getKeyAttribute(IdentityProviderMapperModel mapperModel) {

        String attribute = mapperModel.getConfig() == null ? null : mapperModel.getConfig().get(CONF_KEY_ATTRIBUTE);
        if (attribute == null || attribute.trim().isEmpty()) {
            log.warnf("Attribute is not configured for mapper %s", mapperModel.getName());
            return null;
        }
        return attribute.trim();
    }

    protected static Object getKeys(BrokeredIdentityContext context, KeycloakSession session) {

        String keysUrl = String.format(SSH_KEYS_URL, context.getUsername());

        List<String> keys = new ArrayList<>();
        try {
            ArrayNode keyList = (ArrayNode) SimpleHttp.doGet(keysUrl, session).asJson();
            Iterator<JsonNode> loop = keyList.elements();
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
