package com.agentmesh.core.tool.marketplace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import java.io.Serializable;

/**
 * 语义化版本号（不可变对象）。
 * 格式：MAJOR.MINOR.PATCH，所有字段必须 >= 0。
 */
@Getter
@ToString
@EqualsAndHashCode
@Schema(description = "语义化版本号", example = "{\"major\":1,\"minor\":0,\"patch\":3}")
public class ToolVersion implements Comparable<ToolVersion>, Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主版本号", example = "1")
    private final int major;
    @Schema(description = "次版本号", example = "0")
    private final int minor;
    @Schema(description = "修订号", example = "3")
    private final int patch;

    @JsonCreator
    public ToolVersion(@JsonProperty("major") int major,
                       @JsonProperty("minor") int minor,
                       @JsonProperty("patch") int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    private static final String VERSION_REGEX = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$";

    /**
     * 从字符串解析版本号。
     * @param version 版本字符串，如 "1.2.3"
     * @throws IllegalArgumentException 版本号格式无效或包含负数
     */
    public static ToolVersion parse(String version) {
        if (version == null || !version.matches(VERSION_REGEX)) {
            throw new IllegalArgumentException(
                    "版本号格式无效: " + version + "，期望格式: MAJOR.MINOR.PATCH（各段为非负整数，如 1.2.3）");
        }
        String[] parts = version.split("\\.");
        return new ToolVersion(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    @Override
    public int compareTo(ToolVersion other) {
        if (other == null) {
            return 1;
        }
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.patch, other.patch);
    }
}
