package com.github.t1.deployer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.*;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlValue;

import static lombok.AccessLevel.*;

@Value
@NoArgsConstructor(access = PRIVATE, force = true)
@JsonSerialize(using = ToStringSerializer.class)
public class DeploymentName implements Comparable<DeploymentName> {
    public static final DeploymentName ALL = new DeploymentName("*");

    @NonNull
    @XmlValue
    String value;

    @JsonCreator public DeploymentName(String value) { this.value = value; }

    @Override public String toString() { return value; }

    @Override public int compareTo(@NotNull DeploymentName that) { return this.value.compareTo(that.value); }
}
