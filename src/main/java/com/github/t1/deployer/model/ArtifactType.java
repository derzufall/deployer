package com.github.t1.deployer.model;

public enum ArtifactType {
    war, jar, bundle, unknown;

    public String extension() { return name(); }

    public String type() { return name(); }
}
