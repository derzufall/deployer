package com.github.t1.deployer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.KebabCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import static com.github.t1.deployer.model.DeploymentState.*;
import static com.github.t1.deployer.model.Plan.*;
import static lombok.AccessLevel.*;

@Data
@Builder
@AllArgsConstructor(access = MODULE)
@JsonNaming(KebabCaseStrategy.class)
class AbstractArtifactPlan implements Plan.AbstractPlan {
    final DeploymentState state;
    final GroupId groupId;
    @NonNull final ArtifactId artifactId;
    @NonNull final Version version;
    final Classifier classifier;
    final Checksum checksum;

    /** can't be abstract, as the class can't be abstract, as the lombok builder would complain */
    @Override public String getId() { throw new UnsupportedOperationException("need to overload"); }

    @SuppressWarnings("unchecked")
    public static class AbstractArtifactPlanBuilder<T extends AbstractArtifactPlanBuilder> {
        public T state(DeploymentState state) {
            this.state = state;
            return (T) this;
        }

        public T groupId(GroupId groupId) {
            this.groupId = groupId;
            return (T) this;
        }

        public T artifactId(ArtifactId artifactId) {
            this.artifactId = artifactId;
            return (T) this;
        }

        public T version(Version version) {
            this.version = version;
            return (T) this;
        }

        public T classifier(Classifier classifier) {
            this.classifier = classifier;
            return (T) this;
        }

        public T checksum(Checksum checksum) {
            this.checksum = checksum;
            return (T) this;
        }
    }

    public static void fromJson(JsonNode node, AbstractArtifactPlanBuilder builder,
            String defaultArtifactId, String defaultVersion) {
        apply(node, "state", builder::state, DeploymentState::valueOf);
        apply(node, "group-id", builder::groupId, GroupId::of, "default.group-id");
        apply(node, "artifact-id", builder::artifactId, ArtifactId::new, "«" + defaultArtifactId + "»");
        apply(node, "version", builder::version, Version::new, defaultVersion);
        apply(node, "classifier", builder::classifier, Classifier::new);
        apply(node, "checksum", builder::checksum, Checksum::fromString);
        verify(builder);
    }

    private static void verify(AbstractArtifactPlanBuilder builder) {
        if (builder.groupId == null && builder.state != undeployed)
            throw new Plan.PlanLoadingException("the `group-id` can only be null when undeploying");
    }

    @JsonIgnore @Override public DeploymentState getState() { return (state == null) ? deployed : state; }

    @Override public String toString() {
        return getState() + ":" + groupId + ":" + artifactId + ":" + version
                + ((classifier == null) ? "" : ":" + classifier);
    }
}
