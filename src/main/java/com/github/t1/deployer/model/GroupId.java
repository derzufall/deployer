package com.github.t1.deployer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.*;

import java.nio.file.*;

import static lombok.AccessLevel.*;

@Value
@NoArgsConstructor(access = PRIVATE, force = true)
@JsonSerialize(using = ToStringSerializer.class)
public class GroupId {
    @NonNull String value;

    public static GroupId of(String value) { return (value == null) ? null : new GroupId(value);}

    @JsonCreator public GroupId(@NonNull String value) { this.value = value; }

    @Override public String toString() { return value; }

    public Path asPath() { return Paths.get(value.replace('.', '/')); }
}
