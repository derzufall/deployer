# Deployer

Lists the applications deployed in a JBoss 7 (for now) container with the version found in Artifactory (for now), and allows to deploy any version found in the repository.

Also supports a REST API, just look at the URIs and request them with content type, e.g., `application/json`.

# TODO

* SSO
* cluster support
* Puppet support

# Configuration

## Security

The Artifactory instance is by default expected to run on `http://localhost:8081/artifactory`. You can configure another instance with the system property `deployer.artifactory.uri`, or by creating a file `%{jboss.server.base.dir}/security/deployer.war/credentials.properties` with this property. If your Artifactory is configured to require authentication for read access, you'll also have to configure `deployer.artifactory.username` and `deployer.artifactory.password`.

The JBoss is by default expected to run on `http-remoting://localhost:9999`. You can configure another instance with the system property `deployer.container.uri` (note that the scheme should be `http-remoting`), or by adding a property to the config file (see above).