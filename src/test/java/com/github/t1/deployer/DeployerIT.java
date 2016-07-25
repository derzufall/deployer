package com.github.t1.deployer;

import com.github.t1.deployer.app.*;
import com.github.t1.deployer.container.*;
import com.github.t1.deployer.model.Checksum;
import com.github.t1.deployer.repository.ArtifactoryMockLauncher;
import com.github.t1.rest.*;
import com.github.t1.testtools.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Condition;
import org.jboss.arquillian.junit.*;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.github.t1.deployer.model.LoggingHandlerType.*;
import static com.github.t1.log.LogLevel.*;
import static com.github.t1.rest.RestContext.*;
import static java.util.concurrent.TimeUnit.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

@Slf4j
@RunWith(Arquillian.class)
@SuppressWarnings("CdiInjectionPointsInspection")
public class DeployerIT {
    private static final DeploymentName DEPLOYER_IT = new DeploymentName("deployer-it");
    private static final String DEPLOYER_IT_WAR = DEPLOYER_IT + ".war";
    private static final Checksum POSTGRESQL_9_4_1207_CHECKSUM = Checksum.fromString(
            "f2ea471fbe4446057991e284a6b4b3263731f319");
    private static final Checksum JOLOKIA_1_3_2_CHECKSUM = Checksum.fromString(
            "9E29ADD9DF1FA9540654C452DCBF0A2E47CC5330");

    private static Condition<DeploymentResource> deployment(String name) {
        return deployment(new DeploymentName(name));
    }

    private static Condition<DeploymentResource> deployment(DeploymentName name) {
        return new Condition<>(name::matches, "deployment with name '" + name + "'");
    }

    private static Condition<DeploymentResource> checksum(Checksum checksum) {
        return new Condition<>(deployment -> deployment.checksum().equals(checksum),
                "deployment with checksum '" + checksum + "'");
    }

    @org.jboss.arquillian.container.test.api.Deployment
    public static WebArchive createDeployment() {
        return new WebArchiveBuilder(DEPLOYER_IT_WAR)
                .with(DeployerBoundary.class.getPackage())
                .with(TestLoggerRule.class, FileMemento.class, LoggerMemento.class)
                .library("org.assertj", "assertj-core")
                .print()
                .build();
    }

    static {
        if (runningOnClient())
            try {
                // TODO can we instead deploy this? or use DropwizardClientRule?
                new ArtifactoryMockLauncher().noConsole().run("server");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }

    private static boolean runningOnClient() { return System.getProperty("jboss.server.config.dir") == null; }


    @Rule
    public LoggerMemento loggerMemento = new LoggerMemento()
            .with("org.apache.http.wire", DEBUG)
            .with("com.github.t1.rest", DEBUG)
            .with("com.github.t1.rest.ResponseConverter", INFO)
            .with("com.github.t1.deployer", DEBUG);
    @Rule public TestLoggerRule logger = new TestLoggerRule();
    private static FileMemento jbossConfig;
    private static boolean first = true;

    @ArquillianResource URI baseUri;

    @Inject Container container;

    @Before
    public void setup() throws Exception {
        if (first && !runningOnClient()) {
            first = false;

            jbossConfig = new FileMemento(System.getProperty("jboss.server.config.dir") + "/standalone.xml").setup();
            jbossConfig.setOrig(jbossConfig.getOrig().replaceFirst(""
                    + "        <deployment name=\"" + DEPLOYER_IT_WAR + "\" runtime-name=\"" + DEPLOYER_IT_WAR + "\">\n"
                    + "            <content sha1=\"[0-9a-f]{40}\"/>\n"
                    + "        </deployment>\n", ""));
            // restore after JBoss is down
            jbossConfig.restoreOnShutdown().after(100, MILLISECONDS); // hell won't freeze over if this is too fast

            container.logHandler(console, new LogHandlerName("CONSOLE")).build().updateLevel(ALL);
            container.logger(LoggerCategory.of("com.github.t1.deployer")).level(DEBUG).build().add();

            log.info("artifacts: {}", container.allDeployments());
            assertNoOtherDeployments();
        }
    }

    public List<Audit> run(String plan) { return run(plan, OK).getBody().getAudits(); }

    @SneakyThrows(IOException.class)
    public EntityResponse<Audits> run(String plan, Status status) {
        try (FileMemento memento = new FileMemento(DeployerBoundary.getConfigPath()).setup()) {
            memento.write(plan);

            return REST
                    .createResource(UriTemplate.from(baseUri))
                    .accept(Audits.class)
                    .POST_Response()
                    .expecting(status);
        }
    }


    private void assertNoOtherDeployments() {
        assertThat(container.allDeployments().stream().filter(DEPLOYER_IT::matches).count()).isEqualTo(0);
    }

    @Test
    @InSequence(value = 100)
    public void shouldFailToDeployWebArchiveWithUnknownVersion() throws Exception {
        String plan = ""
                + "artifacts:\n"
                + "  jolokia-war:\n"
                + "    group-id: org.jolokia\n"
                + "    version: 9999\n";

        EntityResponse<?> response = run(plan, NOT_FOUND);

        assertThat(container.allDeployments())
                .hasSize(1)
                .haveExactly(1, deployment(DEPLOYER_IT_WAR));
        assertThat(response.getBody(String.class))
                .contains("artifact not in repository: org.jolokia:jolokia-war:9999:war");
    }

    @Test
    @InSequence(value = 150)
    public void shouldDeployWebArchive() throws Exception {
        String plan = ""
                + "artifacts:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.2\n";

        List<Audit> audits = run(plan);

        assertThat(container.allDeployments())
                .hasSize(2)
                .haveExactly(1, allOf(deployment("jolokia.war"), checksum(JOLOKIA_1_3_2_CHECKSUM)))
                .haveExactly(1, deployment(DEPLOYER_IT_WAR));
        if (plan.isEmpty()) // TODO this should run with Jackson 2+
            assertThat(audits).containsExactly(
                    Audit.ArtifactAudit.builder().name("jolokia")
                                       .change("group-id", null, "org.jolokia")
                                       .change("artifact-id", null, "jolokia-war")
                                       .change("version", null, "1.3.2")
                                       .change("type", null, "war")
                                       .added());
    }

    @Test
    @InSequence(value = 200)
    public void shouldUndeployWebArchive() throws Exception {
        String plan = ""
                + "artifacts:\n"
                + "  jolokia:\n"
                + "    group-id: org.jolokia\n"
                + "    artifact-id: jolokia-war\n"
                + "    version: 1.3.2\n"
                + "    state: undeployed\n";

        List<Audit> audits = run(plan);

        assertThat(container.allDeployments())
                .hasSize(1)
                .haveExactly(1, deployment(DEPLOYER_IT_WAR));
        if (plan.isEmpty()) // TODO this should run with Jackson 2+
            assertThat(audits).containsExactly(
                    Audit.ArtifactAudit.builder().name("jolokia")
                                       .change("group-id", "org.jolokia", null)
                                       .change("artifact-id", "jolokia-war", null)
                                       .change("version", "1.3.2", null)
                                       .change("type", "war", null)
                                       .added());
    }

    @Test
    @InSequence(value = 300)
    public void shouldDeployJdbcDriver() throws Exception {
        String plan = ""
                + "artifacts:\n"
                + "  postgresql:\n"
                + "    group-id: org.postgresql\n"
                + "    version: \"9.4.1207\"\n"
                + "    type: jar\n";

        List<Audit> audits = run(plan);

        assertThat(container.allDeployments())
                .hasSize(2)
                .haveExactly(1, allOf(deployment("postgresql"), checksum(POSTGRESQL_9_4_1207_CHECKSUM)))
                .haveExactly(1, deployment(DEPLOYER_IT_WAR));
        if (plan.isEmpty()) // TODO this should run with Jackson 2+
            assertThat(audits).containsExactly(
                    Audit.ArtifactAudit.builder().name("postgresql")
                                       .change("group-id", null, "org.postgresql")
                                       .change("artifact-id", null, "postgresql")
                                       .change("version", null, "9.4.1207")
                                       .change("type", null, "jar")
                                       .added());
    }

    // TODO shouldUpdateDeployer (WOW!)

    @Test
    @InSequence(value = Integer.MAX_VALUE)
    public void shouldUndeployEverything() throws Exception {
        // TODO pin DEPLOYER_IT_WAR & manage all configs
        String plan = "---\n";

        List<Audit> audits = run(plan);

        assertNoOtherDeployments();
        if (plan.isEmpty()) { // TODO make this run
            assertThat(jbossConfig.read()).isEqualTo(jbossConfig.getOrig());
            assertThat(audits).containsExactly(
                    Audit.ArtifactAudit.builder().name("postgresql")
                                       .change("group-id", "org.postgresql", null)
                                       .change("artifact-id", "postgresql", null)
                                       .change("version", "9.4.1207", null)
                                       .change("type", "jar", null)
                                       .added());
        }
    }

    // FIXME parking position: do we still need this?
    // public static final ContextRoot UNDEFINED_CONTEXT_ROOT = new ContextRoot("?");
    //
    // public List<Deployment> getAllArtifacts() {
    //     return execute(readDeployments())
    //             .asList().stream()
    //             .map(cliDeploymentMatch -> toDeployment(cliDeploymentMatch.get("result")))
    //             .collect(toList());
    // }
    //
    // private static ModelNode readDeployments() {
    //     ModelNode request = new ModelNode();
    //     request.get("address").add("deployment", "*");
    //     return readResource(request);
    // }
    //
    // private Deployment toDeployment(ModelNode cliDeployment) {
    //     DeploymentName name = new DeploymentName(cliDeployment.get("name").asString());
    //     ContextRoot contextRoot = getContextRoot(cliDeployment);
    //     Checksum hash = Checksum.of(hash(cliDeployment));
    //     log.debug("{} -> {} -> {}", name, contextRoot, hash);
    //     return new Deployment(name, contextRoot, hash);
    // }
    //
    // private byte[] hash(ModelNode cliDeployment) {
    //     try {
    //         return cliDeployment.get("content").get(0).get("hash").asBytes();
    //     } catch (RuntimeException e) {
    //         log.error("failed to get hash for {}", cliDeployment.get("name"));
    //         return new byte[0];
    //     }
    // }
    //
    // private ContextRoot getContextRoot(ModelNode cliDeployment) {
    //     ModelNode subsystems = cliDeployment.get("subsystem");
    //     // JBoss 8+ uses 'undertow' while JBoss 7 uses 'web'
    //     ModelNode web = (subsystems.has("web")) ? subsystems.get("web") : subsystems.get("undertow");
    //     ModelNode contextRoot = web.get("context-root");
    //     return toContextRoot(contextRoot);
    // }
    //
    // private ContextRoot toContextRoot(ModelNode contextRoot) {
    //     if (!contextRoot.isDefined())
    //         return UNDEFINED_CONTEXT_ROOT;
    //     return new ContextRoot(contextRoot.asString());
    // }
}
