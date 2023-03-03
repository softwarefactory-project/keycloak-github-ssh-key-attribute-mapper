#!/bin/sh

# Set up keycloak and a mariadb backend to test the github ssh key mapper manually.
# Set env variable KEYCLOAK_VERSION to any version you would like to
# test the mapper against (it must be Quarkus-based so > 17)

# TODO support docker and choose the runtime to use automatically

echo "Building event listener provider ..."
mvn clean install
echo "Done."
echo

echo "Building keycloak with github ssh key mapper ..."
podman build -t test_kc_github_ssh_key_mapper --build-arg KEYCLOAK_VERSION=${KEYCLOAK_VERSION:-21.0} -f testing/Dockerfile .
echo "Done."
echo

echo "Starting compose ..."
podman-compose -f testing/docker-compose.yaml up -d
echo
echo "Waiting for keycloak to start ..."
until curl -s -f -o /dev/null http://localhost:8082/health/ready
do
  echo "."
  sleep 5
done
echo "Ready."

KC_CFG="--no-config --user admin --realm master --server http://localhost:8082 --password kcadmin"

echo "Patching database ..."
podman exec -ti testing_db_1 mysql -u keycloak -pkeycloak keycloak -e "alter table USER_ATTRIBUTE drop index IDX_USER_ATTRIBUTE_NAME; alter table USER_ATTRIBUTE modify VALUE TEXT(100000) CHARACTER SET utf8 COLLATE utf8_general_ci; alter table USER_ATTRIBUTE ADD KEY IDX_USER_ATTRIBUTE_NAME (NAME, VALUE(400));"
echo "Done."
echo

echo "Creating test realm and adding github auth ..."
podman exec -ti testing_keycloak_1 /opt/keycloak/bin/kcadm.sh create realms \
        --set realm=test \
        --set enabled=true \
        $KC_CFG
podman exec -ti testing_keycloak_1 /opt/keycloak/bin/kcadm.sh create identity-provider/instances \
        --target-realm test \
        --set alias=github \
        --set providerId=github \
        --set enabled=true \
        --set 'config.useJwksUrl="true"' \
        --set config.clientId=$GH_CLIENT_ID \
        --set config.clientSecret=$GH_CLIENT_PASSWORD \
        $KC_CFG
podman exec -ti testing_keycloak_1 /opt/keycloak/bin/kcadm.sh create identity-provider/instances/github/mappers \
        --target-realm test \
        --set name=pubkey_mapper \
        --set identityProviderMapper=github-ssh-key-mapper \
        --set 'config={"keyAttribute":"publicKey"}' \
        --set identityProviderAlias=github \
        $KC_CFG
echo "Done."
echo

read -n 1 -s -r -p "Log in with Github at: http://localhost:8082/realms/test/account/#/, then press any key to display user info"

podman exec -ti testing_keycloak_1 /opt/keycloak/bin/kcadm.sh get users \
        -r test \
        --limit 1 \
        $KC_CFG