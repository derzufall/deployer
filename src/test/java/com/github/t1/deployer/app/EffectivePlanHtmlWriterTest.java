package com.github.t1.deployer.app;

import com.github.t1.deployer.model.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.file.Paths;

import static com.github.t1.deployer.model.ArtifactType.*;
import static com.github.t1.deployer.model.Expressions.*;
import static com.github.t1.deployer.model.LogHandlerType.*;
import static com.github.t1.log.LogLevel.*;
import static javax.ws.rs.core.MediaType.*;
import static org.assertj.core.api.Assertions.*;

public class EffectivePlanHtmlWriterTest {
    private static final Plan PLAN = Plan
            .builder()
            .logHandler(LogHandlerPlan
                    .builder()
                    .name(new LogHandlerName("FOO"))
                    .level(DEBUG)
                    .file("foo.log")
                    .type(periodicRotatingFile)
                    .formatter("COLOR-PATTERN")
                    .build())
            .logger(LoggerPlan
                    .builder()
                    .category(LoggerCategory.of("org.foo"))
                    .handler("FOO")
                    .level(ALL)
                    .build())
            .logger(LoggerPlan
                    .builder()
                    .category(LoggerCategory.of("org.bar"))
                    .handler("FOO")
                    .handler("BAR")
                    .level(INFO)
                    .build())
            .dataSource(DataSourcePlan
                    .builder()
                    .name(new DataSourceName("my-ds"))
                    .uri(URI.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"))
                    .build())
            .deployable(DeployablePlan
                    .builder()
                    .name(new DeploymentName("foo"))
                    .groupId(new GroupId("org.foo"))
                    .artifactId(new ArtifactId("foo"))
                    .version(new Version("1.0"))
                    .type(war)
                    .build())
            .deployable(DeployablePlan
                    .builder()
                    .name(new DeploymentName("bar"))
                    .groupId(new GroupId("org.bar"))
                    .artifactId(new ArtifactId("bar"))
                    .version(new Version("2.0"))
                    .type(jar)
                    .build())
            .build();

    public String expected() throws MalformedURLException {
        URI expected = Paths.get("src/test/java/com/github/t1/deployer/app/expected-plan.html").toUri();
        return contentOf(expected.toURL()).replaceAll("###hostname###", hostName());
    }

    @Test
    public void shouldWrite() throws Exception {
        EffectivePlanHtmlWriter writer = new EffectivePlanHtmlWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writer.writeTo(PLAN, Plan.class, Plan.class, null, TEXT_HTML_TYPE, null, out);

        assertThat(out.toString()).isEqualTo(expected());
    }
}
