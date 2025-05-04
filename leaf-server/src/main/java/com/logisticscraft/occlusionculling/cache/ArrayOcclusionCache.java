package com.logisticscraft.occlusionculling.cache;

import java.util.Arrays;

public class ArrayOcclusionCache implements OcclusionCache {

    private final int reachX2;
    private final byte[] cache;
    private int positionKey;
    private int entry;
    private int offset;

    public ArrayOcclusionCache(int reach) {
        this.reachX2 = reach * 2;
        this.cache = new byte[(reachX2 * reachX2 * reachX2) / 4];
    }

    @Override
    public void resetCache() {
        Arrays.fill(cache, (byte) 0);
    }

    @Override
    public void setVisible(int x, int y, int z) {
        positionKey = x + y * reachX2 + z * reachX2 * reachX2;
        entry = positionKey / 4;
        offset = (positionKey % 4) * 2;
        cache[entry] |= 1 << offset;
    }

    @Override
    public void setHidden(int x, int y, int z) {
        positionKey = x + y * reachX2 + z * reachX2 * reachX2;
        entry = positionKey / 4;
        offset = (positionKey % 4) * 2;
        cache[entry] |= 1 << offset + 1;
    }

    @Override
    public int getState(int x, int y, int z) {
        positionKey = x + y * reachX2 + z * reachX2 * reachX2;
        entry = positionKey / 4;
        offset = (positionKey % 4) * 2;
        return cache[entry] >> offset & 3;
    }

    @Override
    public void setLastVisible() {
        cache[entry] |= 1 << offset;
    }

    @Override
    public void setLastHidden() {
        cache[entry] |= 1 << offset + 1;
    }

}
