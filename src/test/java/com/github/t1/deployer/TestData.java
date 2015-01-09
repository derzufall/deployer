package com.github.t1.deployer;

import static com.github.t1.deployer.ArtifactoryMock.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;

import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;

import lombok.SneakyThrows;

import org.jboss.as.controller.client.*;
import org.jboss.dmr.ModelNode;

public class TestData {
    public static Deployment deploymentFor(String contextRoot) {
        return deploymentFor(contextRoot, versionFor(contextRoot));
    }

    public static Deployment deploymentFor(String contextRoot, Version version) {
        Deployment deployment = new Deployment(nameFor(contextRoot), contextRoot, checksumFor(contextRoot));
        deployment.setVersion(version);
        return deployment;
    }

    public static void givenDeployments(Repository repository, String... deploymentNames) {
        for (String name : deploymentNames) {
            when(repository.getVersionByChecksum(checksumFor(name))).thenReturn(versionFor(name));
            for (Version version : availableVersionsFor(name)) {
                when(repository.getArtifactInputStream(checksumFor(name, version))) //
                        .thenReturn(inputStreamFor(name, version));
            }
        }
    }

    public static void givenDeployments(Container container, String... contextRoots) {
        List<Deployment> deployments = new ArrayList<>();
        for (String contextRoot : contextRoots) {
            Deployment deployment = deploymentFor(contextRoot);
            deployments.add(deployment);
            when(container.getDeploymentByContextRoot(contextRoot)).thenReturn(deployment);
        }
        when(container.getAllDeployments()).thenReturn(deployments);
    }

    @SneakyThrows(IOException.class)
    public static void givenDeployments(ModelControllerClient client, String... deploymentNames) {
        when(client.execute(eq(readAllDeploymentsCli()), any(OperationMessageHandler.class))) //
                .thenReturn(ModelNode.fromString(successCli(readDeploymentsCliResult(deploymentNames))));
    }

    public static ModelNode readAllDeploymentsCli() {
        ModelNode node = new ModelNode();
        node.get("address").add("deployment", "*");
        node.get("operation").set("read-resource");
        node.get("recursive").set(true);
        return node;
    }

    private static String readDeploymentsCliResult(String... deploymentNames) {
        StringBuilder out = new StringBuilder();
        for (String contextRoot : deploymentNames) {
            if (out.length() == 0)
                out.append("[");
            else
                out.append(",");
            out.append("{\n" //
                    + "\"address\" => [(\"deployment\" => \"" + nameFor(contextRoot) + "\")],\n" //
                    + "\"outcome\" => \"success\",\n" //
                    + "\"result\" => " + deploymentCli(contextRoot) + "\n" //
                    + "}\n");
        }
        out.append("]");
        return out.toString();
    }

    public static String nameFor(String contextRoot) {
        return contextRoot + ".war";
    }

    public static String failedCli(String message) {
        return "{\"outcome\" => \"failed\",\n" //
                + "\"failure-description\" => \"" + message + "\n" //
                + "}\n";
    }

    public static String successCli(String result) {
        return "{\n" //
                + "\"outcome\" => \"success\",\n" //
                + "\"result\" => " + result + "\n" //
                + "}\n";
    }

    public static String[] deploymentsCli(String... deployments) {
        String[] result = new String[deployments.length];
        for (int i = 0; i < deployments.length; i++) {
            result[i] = deploymentCli(deployments[i]);
        }
        return result;
    }

    public static String deploymentCli(String contextRoot) {
        return "{\n" //
                + "\"content\" => [{\"hash\" => bytes {\n" //
                + checksumFor(contextRoot).hexByteArray() //
                + "}}],\n" //
                + "\"enabled\" => true,\n" //
                + ("\"name\" => \"" + nameFor(contextRoot) + "\",\n") //
                + "\"persistent\" => true,\n" //
                + ("\"runtime-name\" => \"" + nameFor(contextRoot) + "\",\n") //
                + "\"subdeployment\" => undefined,\n" //
                + "\"subsystem\" => {\"web\" => {\n" //
                + ("\"context-root\" => \"/" + contextRoot + "\",\n") //
                + "\"virtual-host\" => \"default-host\",\n" //
                + "\"servlet\" => {\"javax.ws.rs.core.Application\" => {\n" //
                + "\"servlet-class\" => \"org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher\",\n" //
                + "\"servlet-name\" => \"javax.ws.rs.core.Application\"\n" //
                + "}}\n" //
                + "}}\n" //
                + "}";
    }

    public static String deploymentJson(String contextRoot) {
        return deploymentJson(contextRoot, versionFor(contextRoot));
    }

    public static String deploymentJson(String contextRoot, Version version) {
        return "{" //
                + "\"name\":\"" + nameFor(contextRoot) + "\"," //
                + "\"contextRoot\":\"" + contextRoot + "\"," //
                + "\"checkSum\":\"" + checksumFor(contextRoot, version) + "\"," //
                + "\"version\":\"" + version + "\"" //
                + "}";
    }

    public static void assertStatus(Status status, Response response) {
        if (status.getStatusCode() != response.getStatus()) {
            StringBuilder message = new StringBuilder();
            message.append("expected ").append(status.getStatusCode()).append(" ");
            message.append(status.getReasonPhrase().toUpperCase());
            message.append(" but got ").append(response.getStatus()).append(" ");
            message.append(Status.fromStatusCode(response.getStatus()).getReasonPhrase().toUpperCase());

            String entity = response.readEntity(String.class);
            if (entity != null)
                message.append(":\n" + entity);

            fail(message.toString());
        }
    }

    public static void assertDeployment(String contextRoot, Deployment deployment) {
        assertDeployment(contextRoot, versionFor(contextRoot), deployment);
    }

    public static void assertDeployment(String contextRoot, Version expectedVersion, Deployment deployment) {
        assertEquals(contextRoot, deployment.getContextRoot());
        assertEquals(nameFor(contextRoot), deployment.getName());
        assertEquals(expectedVersion, deployment.getVersion());
    }
}
