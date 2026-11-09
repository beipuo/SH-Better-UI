package com.dishanhai.sh_better_ui.client.gui.collision;

/** 山海：尺寸约束与同优先级压缩的相对阻力。 */
public record CompressionModel(float minWidth, float minHeight, float maxWidth, float maxHeight, float resistance) {
    public static final CompressionModel UNCONSTRAINED =
            new CompressionModel(0, 0, CollisionShape.LIMIT, CollisionShape.LIMIT, 1);

    public CompressionModel {
        if (!(CollisionShape.validSize(minWidth) && CollisionShape.validSize(minHeight)
                && CollisionShape.validSize(maxWidth) && CollisionShape.validSize(maxHeight)
                && minWidth <= maxWidth && minHeight <= maxHeight
                && Float.isFinite(resistance) && resistance > 0)) {
            throw new IllegalArgumentException("Invalid compression limits or resistance");
        }
    }
}
