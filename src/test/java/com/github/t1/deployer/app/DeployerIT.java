package com.github.t1.deployer.app;

import com.github.t1.deployer.app.Audit.*;
import com.github.t1.deployer.model.*;
import com.github.t1.deployer.repository.ArtifactoryMockLauncher;
import com.github.t1.testtools.*;
import com.github.t1.xml.Xml;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.logging.LoggingFeature;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.nio.file.Path;
import java.util.*;

import static com.github.t1.deployer.app.ConfigProducer.*;
import static com.github.t1.deployer.app.DeployerBoundary.*;
import static com.github.t1.deployer.container.Container.*;
import static com.github.t1.deployer.container.DeploymentResource.*;
import static com.github.t1.deployer.container.LogHandlerResource.*;
import static com.github.t1.deployer.model.LogHandlerPlan.*;
import static com.github.t1.deployer.model.LogHandlerType.*;
import static com.github.t1.deployer.model.Password.*;
import static com.github.t1.deployer.model.ProcessState.*;
import static com.github.t1.deployer.testtools.ModelNodeTestTools.*;
import static com.github.t1.deployer.testtools.TestData.*;
import static com.github.t1.log.LogLevel.*;
import static com.github.t1.rest.fallback.YamlMessageBodyReader.*;
import static com.github.t1.testtools.FileMemento.*;
import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;
import static org.hibernate.validator.internal.util.CollectionHelper.*;
import static org.junit.Assume.*;

@Slf4j
@RunWith(OrderedJUnitRunner.class)
public class DeployerIT {
    private static final boolean USE_ARTIFACTORY_MOCK = true;

    private static final String DEPLOYER_WAR = "deployer.war";
    private static final Checksum UNKNOWN_CHECKSUM = Checksum.ofHexString("9999999999999999999999999999999999999999");

    private static final String PLAN_JOLOKIA_WITH_VERSION_VAR = ""
            + "deployables:\n"
            + "  jolokia:\n"
            + "    group-id: org.jolokia\n"
            + "    artifact-id: jolokia-war\n"
            + "    version: ${jolokia.version}\n";
    private static final String POSTGRESQL = ""
            + "deployables:\n"
            + "  postgresql:\n"
            + "    group-id: org.postgresql\n"
            + "    version: 9.4.1207\n"
            + "    type: jar\n";
    private static final String FOO_DATASOURCE = ""
            + "  foo:\n"
            + "    uri: jdbc:h2:mem:test\n"
            + "    jndi-name: java:/datasources/TestDS\n"
            + "    driver: h2\n"
            + "    user-name: joe\n"
            + "    password: secret\n"
            + "    pool:\n"
            + "      min: 0\n"
            + "      initial: 1\n"
            + "      max: 10\n";

    @ClassRule public static WildflyContainerTestRule container =
            new WildflyContainerTestRule("10.1.0.Final")
                    .withLogger("org.apache.http.headers", DEBUG)
                    // .withLogger("org.apache.http.wire", DEBUG)
                    // .withLogger("com.github.t1.rest", DEBUG)
                    // .withLogger("com.github.t1.rest.ResponseConverter", INFO)
                    .withLogger("com.github.t1.deployer", DEBUG)
                    // .withSystemProperty(IGNORE_SERVER_RELOAD, "true")
                    .withSystemProperty(CLI_DEBUG, "true");

    @BeforeClass
    public static void startup() {
        startArtifactoryMock();
        writeDeployerConfig();
        container.deploy(deployer_war());
        startConfig = container.readConfig(); // after startup & deploy, so the container did format and order the file
    }

    private static Xml startConfig;

    private static void startArtifactoryMock() {
        if (USE_ARTIFACTORY_MOCK)
            try {
                // TODO can we instead deploy this? or use DropwizardClientRule?
                ArtifactoryMockLauncher.main();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }

    private static void writeDeployerConfig() {
        writeFile(container.configDir().resolve(DEPLOYER_CONFIG_YAML), ""
                + "vars:\n"
                + "  config-var: 1.3.2\n"
                + "pin:\n"
                + "  deployables: [deployer]\n"
                + "  log-handlers: [CONSOLE, FILE]\n"
                + "  loggers: [org.jboss.as.config, sun.rmi, com.arjuna, com.github.t1.deployer, "
                + "org.apache.http.headers, org.apache.http.wire, "
                + "com.github.t1.rest, com.github.t1.rest.ResponseConverter]\n"
                + "  data-sources: [ExampleDS]\n"
                + "manage: [all]\n"
                + "triggers: [post]\n"
        );
    }

    private static WebArchive deployer_war() {
        return new WebArchiveBuilder(DEPLOYER_WAR)
                .with(DeployerBoundary.class.getPackage())
                .withBeansXml()
                .library("com.github.t1", "problem-detail")
                .print()
                .build();
    }


    private static final Client HTTP = ClientBuilder.newClient().register(LoggingFeature.class);

    @Rule public TestLoggerRule logger = new TestLoggerRule();
    @Rule public LoggerMemento loggerMemento = new LoggerMemento()
            .with("org.apache.http.headers", DEBUG)
            // .with("org.apache.http.wire", DEBUG)
            // .with("com.github.t1.rest", DEBUG)
            // .with("com.github.t1.rest.ResponseConverter", INFO)
            .with("com.github.t1.deployer", DEBUG)
            .with(LoggingFeature.DEFAULT_LOGGER_NAME, DEBUG);


    public List<Audit> post(String expectedStatus) {
        return post(expectedStatus, null, OK).readEntity(Audits.class).getAudits();
    }

    public Response post(String plan, Entity<?> entity, Status expectedStatus) {
        return container.retryConnect("post request", () -> {
            try (FileMemento memento = new FileMemento(this::rootBundlePath).setup()) {
                memento.write(plan);

                Response response = HTTP
                        .target(container.baseUri().resolve("deployer"))
                        .request(APPLICATION_JSON_TYPE)
                        .buildPost(entity)
                        .invoke();
                assertThat(response.getStatus())
                        .as("failed: %s", new Object() {
                            @Override public String toString() { return response.readEntity(String.class); }
                        })
                        .isIn(asSet(expectedStatus.getStatusCode(), NOT_FOUND.getStatusCode()));
                return response;
            }
        }, response -> response.getStatusInfo().equals(expectedStatus));
    }

    private Path rootBundlePath() { return container.configDir().resolve(ROOT_BUNDLE_CONFIG_FILE); }

    private static Map<String, Checksum> theDeployments() {
        ModelNode response = container.execute(readAllDeploymentsRequest());
        Map<String, Checksum> map = new LinkedHashMap<>();
        response.asList()
                .stream()
                .map(node -> node.get("result"))
                .filter(node -> !node.get("name").asString().equals("deployer.war"))
                .forEach(node -> map.put(node.get("name").asString(), Checksum.of(hash(node))));
        return map;
    }


    @Test
    public void shouldFailToDeployWebArchiveWithUnknownVersion() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia-war:\n"
                + "    group-id: org.jolokia\n"
                + "    version: 9999\n";

        Response response = post(plan, null, NOT_FOUND);

        assertThat(theDeployments()).isEmpty();
        assertThat(response.readEntity(String.class))
                .contains("not in repository")
                .contains("org.jolokia:jolokia-war:9999:war");
    }

    @Test
    public void shouldFailToDeployWebArchiveWithIncorrectChecksum() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.1\n"
                + "    checksum: " + UNKNOWN_CHECKSUM;

        String detail = post(plan, null, BAD_REQUEST).readEntity(String.class);

        assertThat(theDeployments()).isEmpty();
        assertThat(detail).contains("Repository checksum [52709cbc859e208dc8e540eb5c7047c316d9653f] "
                + "does not match planned checksum [" + UNKNOWN_CHECKSUM + "]");
    }

    @Test
    public void shouldDeployWebArchiveWithCorrectChecksum() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.1\n"
                + "    checksum: 52709cbc859e208dc8e540eb5c7047c316d9653f";

        Audits audits = post(plan, null, OK).readEntity(Audits.class);

        assertThat(audits.getProcessState()).isEqualTo(running);
        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_131_CHECKSUM));
        assertThat(audits.getAudits()).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", null, "org.jolokia")
                               .change("artifact-id", null, "jolokia-war")
                               .change("version", null, "1.3.1")
                               .change("type", null, "war")
                               .change("checksum", null, JOLOKIA_131_CHECKSUM)
                               .added());
    }

    @Test
    public void shouldNotUpdateWebArchiveWithSameVersion() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.1\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_131_CHECKSUM));
        assertThat(audits).isEmpty();
    }

    @Test
    public void shouldFailToUpdateWebArchiveWithWrongChecksum() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.2\n"
                + "    checksum: " + UNKNOWN_CHECKSUM;

        String detail = post(plan, null, BAD_REQUEST).readEntity(String.class);

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_131_CHECKSUM));
        assertThat(detail).contains("Repository checksum [" + JOLOKIA_132_CHECKSUM + "] "
                + "does not match planned checksum [" + UNKNOWN_CHECKSUM + "]");
    }

    @Test
    public void shouldUpdateWebArchiveWithConfiguredVariablePlusAddLogger() throws Exception {
        String plan = PLAN_JOLOKIA_WITH_VERSION_VAR
                .replace("${jolokia.version}", "${config-var}")
                + "loggers:\n"
                + "  foo:\n"
                + "    level: DEBUG\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_132_CHECKSUM));
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("checksum", JOLOKIA_131_CHECKSUM, JOLOKIA_132_CHECKSUM)
                               .change("version", "1.3.1", "1.3.2")
                               .changed(),
                LoggerAudit.builder().category(LoggerCategory.of("foo"))
                           .change("level", null, DEBUG)
                           .change("use-parent-handlers", null, true)
                           .added());
    }

    @Test
    public void shouldUpdateWebArchiveWithPostParameterAndRemoveLogger() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: ${jolokia.version}\n";

        Entity<String> entity = Entity.json("{\"jolokia.version\":\"1.3.3\"}");
        Audits audits = post(plan, entity, OK).readEntity(Audits.class);

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_133_CHECKSUM));
        assertThat(audits.getAudits()).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("checksum", JOLOKIA_132_CHECKSUM, JOLOKIA_133_CHECKSUM)
                               .change("version", "1.3.2", "1.3.3")
                               .changed(),
                LoggerAudit.builder().category(LoggerCategory.of("foo"))
                           .change("level", DEBUG, null)
                           .change("use-parent-handlers", true, null)
                           .removed());
    }

    @Test
    public void shouldFailToOverwriteVariableWithPostParameter() throws Exception {
        String plan = PLAN_JOLOKIA_WITH_VERSION_VAR.replace("${jolokia.version}", "${config-var}");

        Entity<String> entity = Entity.json("{\"config-var\":\"1.3.3\"}");
        String detail = post(plan, entity, BAD_REQUEST).readEntity(String.class);

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_133_CHECKSUM));
        assertThat(detail).contains("Variable named [config-var] already set. It's not allowed to overwrite.");
    }

    // @Test
    @Ignore("sending */* behaves different from sending no Content-Type header at all... but how should we do that?")
    public void shouldNotAcceptPostWildcardWithBody() throws Exception {
        Entity<String> entity = Entity.entity("non-empty", WILDCARD_TYPE);
        String detail = post(PLAN_JOLOKIA_WITH_VERSION_VAR, entity, BAD_REQUEST).readEntity(String.class);

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_133_CHECKSUM));
        assertThat(detail).contains("Please specify a `Content-Type` header when sending a body.");
    }

    @Test
    public void shouldAcceptJsonBody() throws Exception {
        Entity<String> entity = Entity.json("{\"jolokia.version\":\"1.3.3\"}");
        List<Audit> audits = post(PLAN_JOLOKIA_WITH_VERSION_VAR, entity, OK).readEntity(Audits.class).getAudits();

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_133_CHECKSUM));
        assertThat(audits).isEmpty();
    }

    @Test
    public void shouldAcceptYamlBody() throws Exception {
        Entity<String> entity = Entity.entity("jolokia.version: 1.3.3\n", APPLICATION_YAML_TYPE);
        List<Audit> audits = post(PLAN_JOLOKIA_WITH_VERSION_VAR, entity, OK).readEntity(Audits.class).getAudits();

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_133_CHECKSUM));
        assertThat(audits).isEmpty();
    }

    @Test
    public void shouldAcceptFormBody() throws Exception {
        Entity<Form> entity = Entity.form(new Form("jolokia.version", "1.3.3"));
        List<Audit> audits = post(PLAN_JOLOKIA_WITH_VERSION_VAR, entity, OK).readEntity(Audits.class).getAudits();

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_133_CHECKSUM));
        assertThat(audits).isEmpty();
    }

    @Test
    public void shouldUndeployWebArchive() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: xxx\n"
                + "    state: undeployed\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).isEmpty();
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", "org.jolokia", null)
                               .change("artifact-id", "jolokia-war", null)
                               .change("version", "1.3.3", null)
                               .change("type", "war", null)
                               .change("checksum", JOLOKIA_133_CHECKSUM, null)
                               .removed());
    }

    @Test
    public void shouldDeployTwoWebArchives() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.3\n"
                + "  mockserver:\n"
                + "    group-id: org.mock-server\n"
                + "    artifact-id: mockserver-war\n"
                + "    version: 3.10.3\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).containsOnly(
                entry("jolokia.war", JOLOKIA_133_CHECKSUM),
                entry("mockserver.war", MOCKSERVER_3_10_3_CHECKSUM));
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", null, "org.jolokia")
                               .change("artifact-id", null, "jolokia-war")
                               .change("version", null, "1.3.3")
                               .change("type", null, "war")
                               .change("checksum", null, JOLOKIA_133_CHECKSUM)
                               .added(),
                DeployableAudit.builder().name("mockserver")
                               .change("group-id", null, "org.mock-server")
                               .change("artifact-id", null, "mockserver-war")
                               .change("version", null, "3.10.3")
                               .change("type", null, "war")
                               .change("checksum", null, MOCKSERVER_3_10_3_CHECKSUM)
                               .added());
    }

    @Test
    public void shouldUpdateTwoWebArchives() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.4\n"
                + "  mockserver:\n"
                + "    group-id: org.mock-server\n"
                + "    artifact-id: mockserver-war\n"
                + "    version: 3.10.4\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).containsOnly(
                entry("jolokia.war", JOLOKIA_134_CHECKSUM),
                entry("mockserver.war", MOCKSERVER_3_10_4_CHECKSUM));
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("checksum", JOLOKIA_133_CHECKSUM, JOLOKIA_134_CHECKSUM)
                               .change("version", "1.3.3", "1.3.4")
                               .changed(),
                DeployableAudit.builder().name("mockserver")
                               .change("checksum", MOCKSERVER_3_10_3_CHECKSUM, MOCKSERVER_3_10_4_CHECKSUM)
                               .change("version", "3.10.3", "3.10.4")
                               .changed());
    }

    @Test
    public void shouldUndeployTwoWebArchives() throws Exception {
        List<Audit> audits = post("{}");

        assertThat(theDeployments()).isEmpty();
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", "org.jolokia", null)
                               .change("artifact-id", "jolokia-war", null)
                               .change("version", "1.3.4", null)
                               .change("type", "war", null)
                               .change("checksum", JOLOKIA_134_CHECKSUM, null)
                               .removed(),
                DeployableAudit.builder().name("mockserver")
                               .change("group-id", "org.mock-server", null)
                               .change("artifact-id", "mockserver-war", null)
                               .change("version", "3.10.4", null)
                               .change("type", "war", null)
                               .change("checksum", MOCKSERVER_3_10_4_CHECKSUM, null)
                               .removed());
    }

    @Test
    public void shouldDeploySnapshotWebArchive() throws Exception {
        assumeTrue(USE_ARTIFACTORY_MOCK);

        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.4-SNAPSHOT\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).containsOnly(entry("jolokia.war", JOLOKIA_134_SNAPSHOT_CHECKSUM));
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", null, "org.jolokia")
                               .change("artifact-id", null, "jolokia-war")
                               .change("version", null, "1.3.4-SNAPSHOT")
                               .change("type", null, "war")
                               .change("checksum", null, JOLOKIA_134_SNAPSHOT_CHECKSUM)
                               .added());
    }

    @Test
    public void shouldUndeploySnapshotWebArchive() throws Exception {
        assumeTrue(USE_ARTIFACTORY_MOCK);

        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: xxx\n"
                + "    state: undeployed\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).isEmpty();
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", "org.jolokia", null)
                               .change("artifact-id", "jolokia-war", null)
                               .change("version", "1.3.4-SNAPSHOT", null)
                               .change("type", "war", null)
                               .change("checksum", JOLOKIA_134_SNAPSHOT_CHECKSUM, null)
                               .removed());
    }

    @Test
    public void shouldDeployJdbcDriver() throws Exception {
        List<Audit> audits = post(POSTGRESQL);

        assertThat(theDeployments()).containsOnly(entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("postgresql")
                               .change("group-id", null, "org.postgresql")
                               .change("artifact-id", null, "postgresql")
                               .change("version", null, "9.4.1207")
                               .change("type", null, "jar")
                               .change("checksum", null, POSTGRESQL_9_4_1207_CHECKSUM)
                               .added());
    }

    @Test
    public void shouldDeployDataSource() throws Exception {
        String plan = ""
                + POSTGRESQL
                + "data-sources:\n"
                + FOO_DATASOURCE
                + "      max-age: 5 min\n";

        List<Audit> audits = post(plan);

        assertThat(audits).containsOnly(
                DataSourceAudit.builder().name(new DataSourceName("foo"))
                               .change("uri", null, "jdbc:h2:mem:test")
                               .change("jndi-name", null, "java:/datasources/TestDS")
                               .change("driver", null, "h2")
                               .change("user-name", null, CONCEALED)
                               .change("password", null, CONCEALED)
                               .change("pool:min", null, "0")
                               .change("pool:initial", null, "1")
                               .change("pool:max", null, "10")
                               .change("pool:max-age", null, "5 min")
                               .added());
        assertThat(definedPropertiesOf(container.execute(readDatasourceRequest("foo", false))))
                .has(property("connection-url", "jdbc:h2:mem:test"))
                .has(property("driver-name", "h2"))
                .has(property("enabled", "true"))
                .has(property("idle-timeout-minutes", "5"))
                .has(property("initial-pool-size", "1"))
                .has(property("jndi-name", "java:/datasources/TestDS"))
                .has(property("max-pool-size", "10"))
                .has(property("min-pool-size", "0"))
                .has(property("password", "secret"))
                .has(property("user-name", "joe"));
        assertThat(theDeployments()).containsOnly(entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
    }

    @Test
    public void shouldChangeDataSourceMaxAgeTo10() throws Exception {
        String plan = ""
                + POSTGRESQL
                + "data-sources:\n"
                + FOO_DATASOURCE
                + "      max-age: 600 seconds\n";
        // TODO + "    xa: true\n"

        Audits audits = post(plan, null, OK).readEntity(Audits.class);

        assertThat(audits.getProcessState()).isEqualTo(reloadRequired);
        assertThat(audits.getAudits()).containsOnly(
                DataSourceAudit.builder().name(new DataSourceName("foo"))
                               // TODO .change("xa", null, true)
                               .change("pool:max-age", "5 min", "10 min")
                               .changed());
        assertThat(definedPropertiesOf(container.execute(readDatasourceRequest("foo", false))))
                .has(property("connection-url", "jdbc:h2:mem:test"))
                .has(property("driver-name", "h2"))
                .has(property("enabled", "true"))
                .has(property("idle-timeout-minutes", "10"))
                .has(property("initial-pool-size", "1"))
                .has(property("jndi-name", "java:/datasources/TestDS"))
                .has(property("max-pool-size", "10"))
                .has(property("min-pool-size", "0"))
                .has(property("password", "secret"))
                .has(property("user-name", "joe"));
        assertThat(theDeployments()).containsOnly(entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
    }

    @Test
    public void shouldDeployXaDataSource() throws Exception {
        String plan = ""
                + POSTGRESQL
                + "data-sources:\n"
                + FOO_DATASOURCE
                + "      max-age: 10 min\n"
                // TODO + "    xa: true\n"
                + "  barDS:\n"
                + "    xa: true\n"
                + "    uri: jdbc:postgresql://my-db.server.lan:5432/bar\n"
                + "    user-name: joe\n"
                + "    password: secret\n"
                + "    pool:\n"
                + "      min: 0\n"
                + "      initial: 0\n"
                + "      max: 10\n"
                + "      max-age: 5 min\n";

        List<Audit> audits = post(plan);

        assertThat(audits).containsOnly(
                DataSourceAudit.builder().name(new DataSourceName("barDS"))
                               .change("uri", null, "jdbc:postgresql://my-db.server.lan:5432/bar")
                               .change("jndi-name", null, "java:/datasources/barDS")
                               .change("driver", null, "postgresql")
                               .change("xa", null, true)
                               .change("user-name", null, CONCEALED)
                               .change("password", null, CONCEALED)
                               .change("pool:min", null, "0")
                               .change("pool:initial", null, "0")
                               .change("pool:max", null, "10")
                               .change("pool:max-age", null, "5 min")
                               .added());
        assertThat(definedPropertiesOf(container.execute(readDatasourceRequest("barDS", true))))
                .has(property("driver-name", "postgresql"))
                .has(property("enabled", "true"))
                .has(property("idle-timeout-minutes", "5"))
                .has(property("initial-pool-size", "0"))
                .has(property("jndi-name", "java:/datasources/barDS"))
                .has(property("max-pool-size", "10"))
                .has(property("min-pool-size", "0"))
                .has(property("password", "secret"))
                .has(property("user-name", "joe"))
                .has(property("xa-datasource-properties", "{"
                        + "\"ServerName\" => {\"value\" => \"my-db.server.lan\"},"
                        + "\"PortNumber\" => {\"value\" => \"5432\"},"
                        + "\"DatabaseName\" => {\"value\" => \"bar\"}"
                        + "}"));
        assertThat(theDeployments()).containsOnly(entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
    }

    @Test
    public void shouldUndeployAllDataSources() throws Exception {
        List<Audit> audits = post(POSTGRESQL);

        assertThat(audits).containsOnly(
                DataSourceAudit.builder().name(new DataSourceName("barDS")) // sorted!
                               .change("uri", "jdbc:postgresql://my-db.server.lan:5432/bar", null)
                               .change("jndi-name", "java:/datasources/barDS", null)
                               .change("driver", "postgresql", null)
                               .change("xa", true, null)
                               .change("user-name", CONCEALED, null)
                               .change("password", CONCEALED, null)
                               .change("pool:min", "0", null)
                               .change("pool:initial", "0", null)
                               .change("pool:max", "10", null)
                               .change("pool:max-age", "5 min", null)
                               .removed(),
                DataSourceAudit.builder().name(new DataSourceName("foo"))
                               .change("uri", "jdbc:h2:mem:test", null)
                               .change("jndi-name", "java:/datasources/TestDS", null)
                               .change("driver", "h2", null)
                               // TODO .change("xa", true, null)
                               .change("user-name", CONCEALED, null)
                               .change("password", CONCEALED, null)
                               .change("pool:min", "0", null)
                               .change("pool:initial", "1", null)
                               .change("pool:max", "10", null)
                               .change("pool:max-age", "10 min", null)
                               .removed());
        assertThat(theDeployments()).containsOnly(entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
    }

    @Test
    public void shouldDeployLogHandlerAndLogger() throws Exception {
        String plan = ""
                + POSTGRESQL
                + "log-handlers:\n"
                + "  FOO:\n"
                + "    level: INFO\n"
                + "loggers:\n"
                + "  foo:\n"
                + "    handler: FOO\n";

        List<Audit> audits = post(plan);

        assertThat(audits).containsOnly(
                LogHandlerAudit.builder()
                               .type(periodicRotatingFile)
                               .name(new LogHandlerName("FOO"))
                               .change("level", null, INFO)
                               .change("file", null, "foo.log")
                               .change("suffix", null, DEFAULT_SUFFIX)
                               .added(),
                LoggerAudit.builder().category(LoggerCategory.of("foo"))
                           .change("level", null, DEBUG)
                           .change("use-parent-handlers", null, false)
                           .change("handlers", null, "[FOO]")
                           .added());
        assertThat(definedPropertiesOf(container.execute(readLogHandlerRequest(periodicRotatingFile, "FOO"))))
                .has(property("append", "true"))
                .has(property("autoflush", "true"))
                .has(property("enabled", "true"))
                .has(property("file", "{"
                        + "\"path\" => \"foo.log\","
                        + "\"relative-to\" => \"jboss.server.log.dir\"}"))
                .has(property("formatter", DEFAULT_LOG_FORMAT))
                .has(property("level", "INFO"))
                .has(property("name", "FOO"))
                .has(property("suffix", DEFAULT_SUFFIX));
        assertThat(definedPropertiesOf(container.execute(readLoggerRequest("foo"))))
                .has(property("category", "foo"))
                .has(property("handlers", "[\"FOO\"]"))
                .has(property("level", "DEBUG"))
                .has(property("use-parent-handlers", "false"));
        assertThat(theDeployments()).containsOnly(entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
    }

    @Test
    public void shouldUndeployLogHandler() throws Exception {
        String plan = ""
                + POSTGRESQL;

        List<Audit> audits = post(plan);

        assertThat(audits).containsOnly(
                LogHandlerAudit.builder()
                               .type(periodicRotatingFile)
                               .name(new LogHandlerName("FOO"))
                               .change("level", INFO, null)
                               .change("file", "foo.log", null)
                               .change("suffix", DEFAULT_SUFFIX, null)
                               .removed(),
                LoggerAudit.builder().category(LoggerCategory.of("foo"))
                           .change("level", DEBUG, null)
                           .change("use-parent-handlers", false, null)
                           .change("handlers", "[FOO]", null)
                           .removed());
        assertThat(theDeployments()).containsOnly(entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
    }

    @Test
    public void shouldDeploySecondDeployableWithOnlyOnePostParameter() throws Exception {
        String plan = ""
                + "deployables:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: ${jolokia.version}\n"
                + "  postgresql:\n"
                + "    group-id: org.postgresql\n"
                + "    version: ${postgresql.version}\n"
                + "    type: jar\n";

        Entity<String> entity = Entity.json("{\"jolokia.version\":\"1.3.3\"}");
        Audits audits = post(plan, entity, OK).readEntity(Audits.class);

        assertThat(theDeployments()).containsOnly(
                entry("jolokia.war", JOLOKIA_133_CHECKSUM),
                entry("postgresql", POSTGRESQL_9_4_1207_CHECKSUM));
        assertThat(audits.getAudits()).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", null, "org.jolokia")
                               .change("artifact-id", null, "jolokia-war")
                               .change("version", null, "1.3.3")
                               .change("type", null, "war")
                               .change("checksum", null, JOLOKIA_133_CHECKSUM)
                               .added());
    }

    @Test
    public void shouldCleanUp() throws Exception {
        String plan = "---\n";

        List<Audit> audits = post(plan);

        assertThat(theDeployments()).isEmpty();
        assertThat(audits).containsOnly(
                DeployableAudit.builder().name("jolokia")
                               .change("group-id", "org.jolokia", null)
                               .change("artifact-id", "jolokia-war", null)
                               .change("version", "1.3.3", null)
                               .change("type", "war", null)
                               .change("checksum", JOLOKIA_133_CHECKSUM, null)
                               .removed(),
                DeployableAudit.builder().name("postgresql")
                               .change("group-id", "org.postgresql", null)
                               .change("artifact-id", "postgresql", null)
                               .change("version", "9.4.1207", null)
                               .change("type", "jar", null)
                               .change("checksum", POSTGRESQL_9_4_1207_CHECKSUM, null)
                               .removed());
        assertThat(container.readConfig().toXmlString()).isEqualTo(startConfig.toXmlString());
    }
}
