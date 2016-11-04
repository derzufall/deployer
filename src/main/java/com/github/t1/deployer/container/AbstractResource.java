package com.github.t1.deployer.container;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.dmr.ModelNode;

import static com.github.t1.deployer.container.CLI.*;
import static org.jboss.as.controller.client.helpers.ClientConstants.*;
import static org.jboss.as.controller.client.helpers.Operations.*;

/**
 * Resources represent the configured state of the JavaEE container. They are responsible to {@link #add()},
 * {@link #remove()}, or update (using overloaded `updateSomething` methods) the current state in the container;
 * and to provide information about an existing resource with {@link #isDeployed()} and various fluent getters.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractResource<T extends AbstractResource<T>> {
    @NonNull private final CLI cli;

    protected Boolean deployed = null;

    protected void checkDeployed() {
        if (!isDeployed())
            throw new RuntimeException("not deployed '" + this + "'");
    }

    public boolean isDeployed() {
        if (deployed == null) {
            ModelNode readResource = createReadResourceOperation(address(), true);
            ModelNode response = cli.executeRaw(readResource);
            if (response == null)
                throw new RuntimeException("read-resource not properly mocked: " + this);
            if (isSuccessfulOutcome(response)) {
                this.deployed = true;
                readFrom(response.get(RESULT));
            } else if (isNotFoundMessage(response)) {
                this.deployed = false;
            } else {
                log.error("failed: {}", response);
                throw new RuntimeException("outcome " + response.get("outcome").asString()
                        + ": " + getFailureDescription(response));
            }
        }
        return deployed;
    }

    protected abstract ModelNode address();

    protected abstract void readFrom(ModelNode result);

    protected void execute(ModelNode request) { cli.execute(request); }

    public ServerDeploymentManager openServerDeploymentManager() { return cli.openServerDeploymentManager(); }

    public void writeAttribute(String name, String value) { cli.writeAttribute(address(), name, value); }

    public void writeAttribute(String name, boolean value) { cli.writeAttribute(address(), name, value); }

    public void mapPut(String name, String key, String value) { cli.mapPut(address(), name, key, value); }

    protected void mapRemove(String name, String key) { cli.mapRemove(address(), name, key); }

    public abstract void add();

    public void remove() { execute(createRemoveOperation(address())); }

    public abstract String getId();

    public boolean matchesId(T that) { return this.getId().equals(that.getId()); }
}
