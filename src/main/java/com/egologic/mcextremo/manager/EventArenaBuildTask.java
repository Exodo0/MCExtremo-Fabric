package com.egologic.mcextremo.manager;

import net.minecraft.util.math.BlockPos;

class EventArenaBuildTask {
    final BlockPos center;
    final int radius;
    int stage;
    int dx;
    int dz;
    boolean structuresBuilt;

    EventArenaBuildTask(BlockPos center, int radius) {
        this.center = center;
        this.radius = radius;
        this.dx = -radius - 10;
        this.dz = -radius - 10;
    }
}
