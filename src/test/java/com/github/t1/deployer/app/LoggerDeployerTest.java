package com.github.t1.deployer.app;

import com.github.t1.deployer.app.Audit.LoggerAudit;
import com.github.t1.problem.WebApplicationApplicationException;
import org.junit.Test;

import static com.github.t1.deployer.container.LogHandlerType.*;
import static com.github.t1.deployer.container.LoggerCategory.*;
import static com.github.t1.log.LogLevel.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LoggerDeployerTest extends AbstractDeployerTest {
    @Test
    public void shouldAddEmptyLoggers() {
        deploy(""
                + "loggers:\n");
    }

    @Test
    public void shouldFailToAddLoggerWithoutItem() {
        Throwable thrown = catchThrowable(() -> deploy(""
                + "loggers:\n"
                + "  foo:\n"));

        assertThat(thrown).hasStackTraceContaining("incomplete plan for logger 'foo'");
    }

    @Test
    public void shouldAddLogger() {
        LoggerFixture fixture = givenLogger("com.github.t1.deployer.app").level(DEBUG);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n");

        fixture.verifyAdded(audits);
    }


    @Test
    public void shouldNotAddExistingLogger() {
        givenLogger("com.github.t1.deployer.app").level(DEBUG).deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n");

        // #after(): no add nor update
        assertThat(audits.getAudits()).isEmpty();
    }


    @Test
    public void shouldUpdateLogLevel() {
        LoggerFixture fixture = givenLogger("com.github.t1.deployer.app").level(DEBUG).deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: INFO\n");

        fixture.level(INFO).verifyUpdatedFrom(DEBUG, audits);
    }


    @Test
    public void shouldNotSetUseParentHandlersOfRootLogger() {
        Audits audits = deploy(""
                + "loggers:\n"
                + "  ROOT:\n"
                + "    level: DEBUG\n"
                + "    handlers: [CONSOLE, FILE]\n");

        verify(cli).writeAttribute(rootLoggerNode(), "level", "DEBUG");
        assertThat(audits.getAudits()).containsExactly(LoggerAudit.of(ROOT).change("level", "INFO", "DEBUG").changed());
    }

    @Test
    public void shouldAddLoggerWithExplicitState() {
        LoggerFixture fixture = givenLogger("com.github.t1.deployer.app").level(DEBUG);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    state: deployed\n");

        fixture.verifyAdded(audits);
    }


    @Test
    public void shouldAddLoggerWithDefaultLevel() {
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app").level(DEBUG);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    state: deployed\n");

        logger.verifyAdded(audits);
    }


    @Test
    public void shouldAddLoggerWithDefaultVariableLevel() {
        givenConfiguredVariable("default.log-level", "WARN");
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app").level(WARN);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    state: deployed\n");

        logger.verifyAdded(audits);
    }


    @Test
    public void shouldAddLoggerWithOneHandler() {
        givenLogHandler(periodicRotatingFile, "FOO").level(DEBUG).deployed();
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .useParentHandlers(false);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handler: FOO\n");

        logger.verifyAdded(audits);
    }


    @Test
    public void shouldAddLoggerWithTwoHandlers() {
        givenLogHandler(periodicRotatingFile, "FOO").level(DEBUG).deployed();
        givenLogHandler(periodicRotatingFile, "BAR").level(DEBUG).deployed();
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .handler("BAR")
                .useParentHandlers(false);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handlers: [FOO,BAR]\n");

        logger.verifyAdded(audits);
    }


    @Test
    public void shouldAddLoggerWithExplicitUseParentHandlersTrue() {
        givenLogHandler(periodicRotatingFile, "FOO").level(DEBUG).deployed();
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .useParentHandlers(true);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handler: FOO\n"
                + "    use-parent-handlers: true");

        logger.verifyAdded(audits);
    }


    @Test
    public void shouldAddLoggerWithExplicitUseParentHandlersFalse() {
        givenLogHandler(periodicRotatingFile, "FOO").level(DEBUG).deployed();
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .useParentHandlers(false);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handler: FOO\n"
                + "    use-parent-handlers: false");

        logger.verifyAdded(audits);
    }


    @Test
    public void shouldAddLoggerWithoutLogHandlersButExplicitUseParentHandlersTrue() {
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .useParentHandlers(true);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    use-parent-handlers: true");

        logger.verifyAdded(audits);
    }


    @Test
    public void shouldFailToAddLoggerWithoutLogHandlersButExplicitUseParentHandlersFalse() {
        givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .useParentHandlers(false)
                .deployed();

        Throwable thrown = catchThrowable(() -> deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer:\n"
                + "    level: DEBUG\n"
                + "    use-parent-handlers: false"));

        assertThat(thrown).hasStackTraceContaining("Can't set use-parent-handlers of [com.github.t1.deployer]"
                + " to false when there are no handlers");
    }


    @Test
    public void shouldNotUpdateLoggerWithHandlerAndUseParentHandlersFalseToFalse() {
        givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .useParentHandlers(false)
                .deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handler: FOO\n"
                + "    use-parent-handlers: false\n");

        assertThat(audits.getAudits()).isEmpty();
    }


    @Test
    public void shouldUpdateLoggerWithHandlerAndUseParentHandlersFalseToTrue() {
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .useParentHandlers(false)
                .deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handler: FOO\n"
                + "    use-parent-handlers: true\n");

        logger.useParentHandlers(true).verifyUpdatedUseParentHandlers(false, audits);
    }


    @Test
    public void shouldUpdateLoggerWithHandlerAndUseParentHandlersTrueToFalse() {
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .useParentHandlers(true)
                .deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handler: FOO\n"
                + "    use-parent-handlers: false\n");

        logger.useParentHandlers(false).verifyUpdatedUseParentHandlers(true, audits);
    }


    @Test
    public void shouldUpdateLoggerWithoutHandlerAndWithUseParentHandlersFalseToTrue() {
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .useParentHandlers(false)
                .deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    use-parent-handlers: true\n");

        logger.useParentHandlers(true).verifyUpdatedUseParentHandlers(false, audits);
    }


    @Test
    public void shouldFailToUpdateLoggerWithoutHandlerAndWithUseParentHandlersTrueToFalse() {
        givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .useParentHandlers(true)
                .deployed();

        Throwable thrown = catchThrowable(() -> deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer:\n"
                + "    level: DEBUG\n"
                + "    use-parent-handlers: false\n"));

        assertThat(thrown).hasStackTraceContaining("Can't set use-parent-handlers of [com.github.t1.deployer]"
                + " to false when there are no handlers");
    }


    @Test
    public void shouldAddLoggerHandler() {
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .useParentHandlers(false)
                .deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handlers: [FOO,BAR]\n");

        logger.verifyAddedHandler(audits, "BAR");
    }


    @Test
    public void shouldRemoveLoggerHandler() {
        LoggerFixture logger = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .handler("FOO")
                .handler("BAR")
                .useParentHandlers(false)
                .deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    handlers: [FOO]\n");

        logger.verifyRemovedHandler(audits, "BAR");
    }


    @Test
    public void shouldRemoveExistingLoggerWhenStateIsUndeployed() {
        LoggerFixture fixture = givenLogger("com.github.t1.deployer.app")
                .level(DEBUG)
                .useParentHandlers(true)
                .handler("FOO")
                .handler("BAR")
                .deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    state: undeployed\n");

        fixture.verifyRemoved(audits);
    }


    @Test
    public void shouldRemoveNonExistingLoggerWhenStateIsUndeployed() {
        givenLogger("com.github.t1.deployer.app").level(DEBUG);

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app:\n"
                + "    level: DEBUG\n"
                + "    state: undeployed\n");

        // #after(): not undeployed
        assertThat(audits.getAudits()).isEmpty();
    }

    @Test
    public void shouldRemoveLoggerWhenManaged() {
        givenLogger("com.github.t1.deployer.app1").level(DEBUG).deployed();
        LoggerFixture app2 = givenLogger("com.github.t1.deployer.app2").level(DEBUG).deployed();
        givenManaged("loggers");

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app1:\n"
                + "    level: DEBUG\n");

        // #after(): app1 not undeployed
        app2.verifyRemoved(audits);
    }

    @Test
    public void shouldRemoveLoggerWhenAllManaged() {
        givenLogger("com.github.t1.deployer.app1").level(DEBUG).deployed();
        LoggerFixture app2 = givenLogger("com.github.t1.deployer.app2").level(DEBUG).deployed();
        givenManaged("all");

        Audits audits = deploy(""
                + "loggers:\n"
                + "  com.github.t1.deployer.app1:\n"
                + "    level: DEBUG\n");

        // #after(): app1 not undeployed
        app2.verifyRemoved(audits);
    }

    @Test
    public void shouldIgnorePinnedLoggerWhenManaged() {
        givenManaged("all");
        givenLogger("FOO").level(DEBUG).deployed();
        givenLogger("BAR").deployed().pinned();
        LoggerFixture baz = givenLogger("BAZ").deployed();

        Audits audits = deploy(""
                + "loggers:\n"
                + "  FOO:\n"
                + "    level: DEBUG\n");

        baz.verifyRemoved(audits);
    }

    @Test
    public void shouldFailToDeployPinnedLogger() {
        givenLogger("FOO").deployed().pinned();

        Throwable thrown = catchThrowable(() ->
                deploy(""
                        + "loggers:\n"
                        + "  FOO:\n"
                        + "    level: DEBUG\n"));

        assertThat(thrown)
                .isInstanceOf(WebApplicationApplicationException.class)
                .hasMessageContaining("resource is pinned: logger:deployed:FOO:");
    }
}
