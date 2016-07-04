package com.github.t1.deployer.container;

import com.github.t1.log.LogLevel;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jboss.dmr.ModelNode;

import java.util.*;

import static com.github.t1.deployer.container.CLI.*;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.*;

@Slf4j
@Builder(toBuilder = true)
@RequiredArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@Accessors(fluent = true, chain = true)
public class LoggerResource {
    public static final String ROOT = "ROOT";

    @NonNull private final String category;
    @NonNull private final CLI cli;

    private Boolean deployed = null;

    @Singular
    private List<LogHandlerName> handlers = new ArrayList<>();
    private Boolean useParentHandlers;
    private LogLevel level;

    public String category() {
        return isRoot() ? ROOT : category;
    }

    public boolean isRoot() { return ROOT.equals(category) || category.isEmpty(); }

    public List<LogHandlerName> handlers() {
        assertDeployed();
        return handlers;
    }

    public boolean useParentHandlers() {
        assertDeployed();
        return (useParentHandlers == null) ? true : useParentHandlers;
    }

    public LogLevel level() {
        assertDeployed();
        return level;
    }

    public LoggerResource correctLevel(LogLevel newLevel) {
        assertDeployed();
        if (level.equals(newLevel))
            return this;
        return writeAttribute("level", newLevel.name());
    }


    public LoggerResource correctUseParentHandler(boolean newUseParentHandlers) {
        assertDeployed();
        if (Objects.equals(useParentHandlers(), newUseParentHandlers))
            return this;
        return writeAttribute("use-parent-handlers", newUseParentHandlers);
    }

    private void assertDeployed() {
        if (!isDeployed())
            throw new RuntimeException("no logger '" + category + "'");
    }

    public boolean isDeployed() {
        if (deployed == null) {
            ModelNode response = cli.executeRaw(readResource(createRequestWithAddress()));
            String outcome = response.get("outcome").asString();
            if ("success".equals(outcome)) {
                this.deployed = true;
                readFrom(response.get("result"));
            } else if (cli.isNotFoundMessage(response)) {
                this.deployed = false;
            } else {
                log.error("failed: {}", response);
                throw new RuntimeException("outcome " + outcome + ": " + response.get("failure-description"));
            }
        }
        return deployed;
    }

    private void readFrom(ModelNode response) {
        this.level = LogLevel.valueOf(response.get("level").asString());
        ModelNode useParentHandlersNode = response.get("use-parent-handlers");
        this.useParentHandlers = (useParentHandlersNode.isDefined()) ? useParentHandlersNode.asBoolean() : null;
    }

    private LoggerResource writeAttribute(String name, String value) {
        cli.writeAttribute(createRequestWithAddress(), name, value);
        return this;
    }

    private LoggerResource writeAttribute(String name, boolean value) {
        cli.writeAttribute(createRequestWithAddress(), name, value);
        return this;
    }

    private ModelNode createRequestWithAddress() {
        ModelNode request = new ModelNode();
        ModelNode logging = request.get("address").add("subsystem", "logging");
        if (ROOT.equals(category) || category.isEmpty())
            logging.add("root-logger", "ROOT");
        else
            logging.add("logger", category);
        return request;
    }

    public void remove() {
        if (isRoot())
            throw new RuntimeException("can't remove root logger");
        cli.execute(removeLogger());
    }

    private ModelNode removeLogger() {
        ModelNode request = createRequestWithAddress();
        request.get("operation").set("remove");
        return request;
    }

    public void add() {
        if (isRoot())
            throw new RuntimeException("can't add root logger");

        ModelNode request = createRequestWithAddress();
        request.get("operation").set("add");
        if (level != null)
            request.get("level").set(level.name());
        for (LogHandlerName handler : handlers)
            request.get("handlers").add(handler.getValue());
        if (useParentHandlers != null)
            request.get("use-parent-handlers").set(useParentHandlers);

        cli.execute(request);

        this.deployed = true;
    }

    public static List<LoggerResource> all(CLI cli) {
        ModelNode request = readResource(new LoggerResource("*", cli).createRequestWithAddress());
        List<LoggerResource> loggers =
                cli.execute(request)
                   .asList().stream()
                   .map(node -> toLoggerResource(cli, node.get("result"), category(node.get("address"))))
                   .collect(toList());
        Collections.sort(loggers, Comparator.comparing(LoggerResource::category));
        loggers.add(0, readRootLogger(cli));
        return loggers;
    }

    private static String category(ModelNode address) { return address.get(1).get("logger").asString(); }

    public static LoggerResource toLoggerResource(CLI cli, ModelNode node, String category) {
        LoggerResource logger = new LoggerResource(category, cli);
        logger.deployed = true;
        logger.readFrom(node);
        return logger;
    }

    private static LoggerResource readRootLogger(CLI cli) {
        ModelNode root = cli.execute(readResource(new LoggerResource(ROOT, cli).createRequestWithAddress()));
        return toLoggerResource(cli, root, ROOT);
    }
}