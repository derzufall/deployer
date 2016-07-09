package com.github.t1.deployer.repository;

import com.github.t1.deployer.model.*;
import com.github.t1.rest.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.HttpHostConnectException;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.net.URI;

import static com.github.t1.deployer.model.Tools.*;
import static com.github.t1.deployer.repository.RepositoryType.*;
import static com.github.t1.rest.RestContext.*;

@Slf4j
public class RepositoryProducer {
    static final URI DEFAULT_ARTIFACTORY_URI = URI.create("http://localhost:8081/artifactory");
    static final URI DEFAULT_MAVEN_CENTRAL_URI = URI.create("https://search.maven.org");
    static final String REST_ALIAS = "repository";

    @Inject @Config("repository.type") RepositoryType type;
    @Inject @Config("repository.uri") URI uri;
    @Inject @Config("repository.username") String username;
    @Inject @Config("repository.password") Password password;

    @Produces Repository repository() {
        if (type == null)
            type = lookupType();
        switch (type) {
        case mavenCentral:
            return new MavenCentralRepository(mavenCentralContext());
        case artifactory:
            return new ArtifactoryRepository(artifactoryContext());
        }
        throw new UnsupportedOperationException("unknown repository type " + type);
    }

    private RepositoryType lookupType() {
        if (replies(artifactoryContext()))
            return artifactory;
        return mavenCentral;
    }

    private boolean replies(RestContext context) {
        try {
            context.resource(REST_ALIAS).GET_Response();
            return true;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof HttpHostConnectException
                    && e.getCause().getMessage().contains("Connection refused"))
                return false;
            throw e;
        }
    }

    private RestContext mavenCentralContext() { return rest(nvl(uri, DEFAULT_MAVEN_CENTRAL_URI)); }

    private RestContext artifactoryContext() { return rest(nvl(uri, DEFAULT_ARTIFACTORY_URI)); }

    private RestContext rest(URI baseUri) {
        RestContext rest = REST;
        if (baseUri != null)
            rest = rest.register(REST_ALIAS, baseUri);
        if (username != null && password != null) {
            log.debug("put {} credentials for {}", username, baseUri);
            Credentials credentials = new Credentials(username, password.getValue());
            rest = rest.register(baseUri, credentials);
        }
        return rest;
    }
}
