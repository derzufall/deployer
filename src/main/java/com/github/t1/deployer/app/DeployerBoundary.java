package com.github.t1.deployer.app;

import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.nio.file.*;

@Path("/")
@Stateless
@Slf4j
public class DeployerBoundary {
    public static final String ROOT_DEPLOYER_CONFIG = "root.deployer.config";

    public static java.nio.file.Path getConfigPath() {
        return getConfigPath(ROOT_DEPLOYER_CONFIG);
    }

    public static java.nio.file.Path getConfigPath(String fileName) {
        return Paths.get(System.getProperty("jboss.server.config.dir"), fileName);
    }

    @Inject Deployer deployer;

    @POST
    public Response post() {
        java.nio.file.Path root = getConfigPath();

        if (!Files.isRegularFile(root))
            throw new RuntimeException("config file '" + root + "' not found");

        log.debug("load config plan from: {}", root);

        Audits audits = deployer.apply(root);

        if (log.isDebugEnabled())
            log.debug("audits:\n {}", audits.toYaml());

        return Response.ok(audits).build();
    }

    @GET
    public ConfigurationPlan get() {
        return deployer.effectivePlan();
    }
}
