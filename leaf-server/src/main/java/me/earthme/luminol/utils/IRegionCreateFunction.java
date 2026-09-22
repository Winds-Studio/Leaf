package me.earthme.luminol.utils;

import java.io.IOException;

@FunctionalInterface
public interface IRegionCreateFunction {
    me.earthme.luminol.data.RegionFile create(RegionCreatorInfo info) throws IOException;
}
