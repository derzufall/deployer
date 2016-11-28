package com.github.t1.deployer.container;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jboss.as.controller.client.*;
import org.jboss.dmr.ModelNode;
import org.jetbrains.annotations.TestOnly;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.jboss.as.controller.client.helpers.ClientConstants.*;
import static org.jboss.as.controller.client.helpers.Operations.*;
import static org.wildfly.plugin.core.ServerHelper.*;

@Slf4j
@TestOnly
public class CLI {
    private static final boolean DEBUG = Boolean.getBoolean(CLI.class.getName() + "#DEBUG");
    private static final int STARTUP_TIMEOUT = 30;

    private static final OperationMessageHandler LOGGING = (severity, message) -> {
        switch (severity) {
        case ERROR:
            log.error(message);
            break;
        case WARN:
            log.warn(message);
            break;
        case INFO:
            log.info(message);
            break;
        }
    };

    @Inject ModelControllerClient client;


    @SneakyThrows({ InterruptedException.class, TimeoutException.class })
    public void waitForBoot() {
        log.info("wait for boot");
        waitForStandalone(client, STARTUP_TIMEOUT);
        log.info("boot done");
    }


    public ModelNode writeAttribute(ModelNode address, String name, String value) {
        return execute(createWriteAttributeOperation(address, name, value));
    }

    public ModelNode writeAttribute(ModelNode address, String name, boolean value) {
        return execute(createWriteAttributeOperation(address, name, value));
    }

    public ModelNode writeAttribute(ModelNode address, String name, long value) {
        return execute(createWriteAttributeOperation(address, name, value));
    }


    public ModelNode mapPut(ModelNode address, String name, String key, String value) {
        ModelNode request = createOperation("map-put", address);
        request.get("name").set(name);
        request.get("key").set(key);
        request.get("value").set(value);

        return execute(request);
    }

    public ModelNode mapRemove(ModelNode address, String name, String key) {
        ModelNode request = createOperation("map-remove", address);
        request.get("name").set(name);
        request.get("key").set(key);

        return execute(request);
    }


    public ModelNode execute(ModelNode request) {
        ModelNode result = executeRaw(request);
        checkOutcome(result);
        return result.get("result");
    }

    @SneakyThrows(IOException.class)
    public ModelNode executeRaw(ModelNode command) {
        logCli("execute command {}", command);
        ModelNode result = client.execute(command, LOGGING);
        logCli("response {}", result);
        return result;
    }

    public ModelNode execute(Operation operation) {
        ModelNode result = executeRaw(operation);
        checkOutcome(result);
        return result.get("result");
    }

    @SneakyThrows(IOException.class)
    private ModelNode executeRaw(Operation operation) {
        logCli("execute operation {}", operation.getOperation());
        ModelNode result = client.execute(operation);
        logCli("response {}", result);
        return result;
    }

    public void checkOutcome(ModelNode result) {
        if (!isSuccessfulOutcome(result))
            fail(result);
    }

    public void fail(ModelNode result) {
        throw new RuntimeException("outcome " + result.get("outcome")
                + (result.hasDefined(FAILURE_DESCRIPTION) ? ": " + result.get(FAILURE_DESCRIPTION) : ""));
    }

    public static boolean isNotFoundMessage(ModelNode result) {
        String message = result.get("failure-description").toString();
        boolean jboss7start = message.startsWith("\"JBAS014807: Management resource");
        boolean jboss8start = message.startsWith("\"WFLYCTL0216: Management resource");
        boolean notFoundEnd = message.endsWith(" not found\"");
        boolean isNotFound = (jboss7start || jboss8start) && notFoundEnd;
        log.trace("is not found message: jboss7start:{} jboss8start:{} notFoundEnd:{} -> {}: [{}]",
                jboss7start, jboss8start, notFoundEnd, isNotFound, message);
        return isNotFound;
    }

    /** see debug-cli in reference.md */
    private void logCli(String format, Object arg) {
        if (DEBUG)
            log.debug(format, arg);
    }
}
