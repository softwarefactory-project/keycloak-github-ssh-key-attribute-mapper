# keycloak-github-ssh-key-attribute-mapper

This custom attribute mapper can be used to fetch a user's public SSH key whenever they log in with Github, and store it into a custom attribute.

# Build

```
mvn clean install
```

To build the SPI for use with a version of Keycloak prior to 22.X, you need to use openjdk 11 and patch pom.xml to target java 11:

```
<source>11</source>
<target>11</target>
```

# Prerequisites

A SQL database backend is required. The user attributes table must be manually modified to allow larger attributes like public keys.

Example for mariadb/mysql:

`alter table USER_ATTRIBUTE drop index IDX_USER_ATTRIBUTE_NAME; alter table USER_ATTRIBUTE modify VALUE TEXT(100000) CHARACTER SET utf8 COLLATE utf8_general_ci; alter table USER_ATTRIBUTE ADD KEY IDX_USER_ATTRIBUTE_NAME (NAME, VALUE(400));`

# Deploy (Wildfly)

Copy the built jar into {KEYCLOAK_HOME}/standalone/deployments

# Deploy (Quarkus)

Copy the built jar into /opt/keycloak/providers

# Testing it out

The `testing` directory contains a Dockerfile that can be used to generate an optimized keycloak image with the mapper preinstalled.

There is also a compose spinning up keycloak, the mapper, and a mariadb instance - but the database needs to be modified manually (a Github oauth provider as well).

The `demo.sh` script automates everything, but requires the following preparation:

* Create a Github OAuth app for your test deployment, the callback URL will be http://localhost:8082/realms/test/broker/github/endpoint
* Set the environment variables GH_CLIENT_ID and GH_CLIENT_PASSWORD to the generated client ID and password, respectively
* run `demo.sh`
* Log in with Github when prompted and press any key to display the user's attributes, your SSH key should appear in "pubKey".