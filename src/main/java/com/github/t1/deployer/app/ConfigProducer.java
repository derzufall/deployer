package com.github.t1.deployer.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.KebabCaseStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.t1.deployer.container.Container;
import com.github.t1.deployer.model.*;
import com.github.t1.deployer.model.Expressions.VariableName;
import com.github.t1.deployer.repository.*;
import com.github.t1.deployer.tools.KeyStoreConfig;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Produces;
import javax.inject.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;
import static com.github.t1.deployer.tools.Tools.*;
import static java.util.Collections.*;
import static lombok.AccessLevel.*;

@Slf4j
@Singleton
public class ConfigProducer {
    private static final ObjectMapper YAML = new ObjectMapper(
            new YAMLFactory()
                    .enable(MINIMIZE_QUOTES)
                    .disable(WRITE_DOC_START_MARKER))
            .setSerializationInclusion(NON_EMPTY)
            .setPropertyNamingStrategy(new KebabCaseStrategy())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    public static final String DEPLOYER_CONFIG_YAML = "deployer.config.yaml";

    private static final DeployerConfig DEFAULT_CONFIG = DeployerConfig
            .builder()
            .repository(RepositoryConfig.builder().build())
            .build();

    @Value
    @Builder
    @NoArgsConstructor(access = PRIVATE, force = true)
    @AllArgsConstructor(access = PRIVATE)
    private static class DeployerConfig {
        private final RepositoryConfig repository;
        @JsonProperty("root-bundle") private final RootBundleConfig rootBundle;
        @JsonProperty("key-store") private final KeyStoreConfig keyStore;
        @Singular @JsonProperty("vars") private final Map<VariableName, String> variables;
        @Singular @JsonProperty("manage") private final List<String> managedResourceNames;
        @Singular("pin") @JsonProperty("pin") private final Map<String, List<String>> pinned;
        private final EnumSet<Trigger> triggers = EnumSet.allOf(Trigger.class);

        @Override public String toString() { return toYAML(); }

        @SneakyThrows(IOException.class) private String toYAML() { return YAML.writeValueAsString(this); }
    }


    private DeployerConfig config = DEFAULT_CONFIG;

    @Inject Container container;

    @PostConstruct
    public void initConfig() {
        Path path = Container.getConfigDir().resolve(DEPLOYER_CONFIG_YAML);
        if (Files.isRegularFile(path)) {
            log.info("load deployer config from '" + path + "'");
            try (Reader reader = Files.newBufferedReader(path)) {
                DeployerConfig newConfig = YAML.readValue(reader, DeployerConfig.class);
                if (newConfig != null)
                    this.config = newConfig;
            } catch (IOException e) {
                log.error("can't load config from '" + path + "'.\n"
                        + "--------- CONTINUE WITH DEFAULT CONFIG! ---------", e);
            }
        } else {
            log.info("no deployer config file at '" + path + "'; use default config");
        }
    }


    private RepositoryConfig getRepository() { return nvl(config.getRepository(), DEFAULT_CONFIG.getRepository()); }

    @Produces @Config("root-bundle")
    public RootBundleConfig rootBundle() { return config.getRootBundle(); }

    @Produces @Config("key-store")
    public KeyStoreConfig keyStore() { return config.getKeyStore(); }

    @Produces @Config("repository.type")
    public RepositoryType repositoryType() { return getRepository().getType(); }

    @Produces @Config("repository.uri")
    public URI repositoryUri() { return getRepository().getUri(); }

    @Produces @Config("repository.username")
    public String repositoryUsername() { return getRepository().getUsername(); }

    @Produces @Config("repository.password")
    public Password repositoryPassword() { return getRepository().getPassword(); }

    @Produces @Config("repository.snapshots")
    public String repositorySnapshots() { return getRepository().getRepositorySnapshots(); }

    @Produces @Config("repository.releases")
    public String repositoryReleases() { return getRepository().getRepositoryReleases(); }


    @Produces @Config("managed.resources")
    public List<String> managedResources() { return nvl(config.getManagedResourceNames(), emptyList()); }

    @Produces @Config("pinned.resources")
    public Map<String, List<String>> pinned() { return nvl(config.getPinned(), emptyMap()); }

    @Produces @Config("triggers")
    public Set<Trigger> triggers() { return config.getTriggers(); }


    @Produces @Config("variables")
    public Map<VariableName, String> variables() { return config.getVariables(); }

    @Produces @Config("use.default.config") public boolean useDefaultConfig() { return config == DEFAULT_CONFIG; }
}
