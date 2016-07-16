package com.github.t1.deployer.testtools;

import com.github.t1.deployer.app.*;
import com.github.t1.deployer.app.Audit.ArtifactAudit;
import com.github.t1.rest.*;
import com.github.t1.testtools.FileMemento;
import org.junit.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static com.github.t1.deployer.app.DeployerBoundary.*;
import static com.github.t1.rest.RestContext.*;
import static org.assertj.core.api.Assertions.*;

@Ignore("this is not a automated test, but only a test client")
public class TestClient {
    private static final Path JBOSS_CONFIG = Paths.get(System.getProperty("user.home"),
            "Tools/JBoss/current/standalone/configuration");
    private static final ArtifactAudit.ArtifactAuditBuilder JOLOKIA =
            ArtifactAudit.of("org.jolokia", "jolokia-war", "1.3.2").name("jolokia");

    public List<Audit> run(String plan) throws IOException {
        try (FileMemento memento = new FileMemento(JBOSS_CONFIG.resolve(ROOT_DEPLOYER_CONFIG)).setup()) {
            memento.write(plan);
            return postUpdate().getAudits();
        }
    }

    private static Audits postUpdate() {
        return deployer().accept(Audits.class).POST();
    }


    public static RestResource deployer() {
        return REST.createResource(UriTemplate.fromString("http://localhost:8080/deployer"));
    }

    @Test
    public void shouldDeployJolokia() throws Exception {
        String plan = ""
                + "org.jolokia:\n"
                + "  jolokia-war:\n"
                + "    name: jolokia\n"
                + "    version: 1.3.2\n";

        List<Audit> audits = run(plan);

        assertThat(audits).containsExactly(JOLOKIA.added());
    }


    @Test
    public void shouldNotDeployJolokiaAgain() throws Exception {
        String plan = ""
                + "org.jolokia:\n"
                + "  jolokia-war:\n"
                + "    name: jolokia\n"
                + "    version: 1.3.2\n";

        List<Audit> audits = run(plan);

        assertThat(audits).isEmpty();
    }


    @Test
    public void shouldUndeployJolokia() throws Exception {
        String plan = ""
                + "org.jolokia:\n"
                + "  jolokia-war:\n"
                + "    version: 1.3.2\n"
                + "    name: jolokia\n"
                + "    state: undeployed";

        List<Audit> audits = run(plan);

        assertThat(audits).containsExactly(JOLOKIA.removed());
    }

    @Test
    public void shouldPostUpdate() throws Exception {
        Audits audits = postUpdate();

        System.out.println(audits.toYaml());
    }

    @Test
    public void shouldGetEffectivePlan() throws Exception {
        ConfigurationPlan plan = deployer().GET(ConfigurationPlan.class);

        System.out.println("plan:\n" + plan);
    }
}