// Gale - JettPack - reduce array allocations

package me.titaniumtown;

public final class ArrayConstants {

    private ArrayConstants() {}

    public static final Object[] emptyObjectArray = new Object[0];
    public static final short[] emptyShortArray = new short[0];
    public static final int[] emptyIntArray = new int[0];
    public static final int[] zeroSingletonIntArray = new int[]{0};
    public static final byte[] emptyByteArray = new byte[0];
    public static final String[] emptyStringArray = new String[0];
    public static final long[] emptyLongArray = new long[0];
    public static final org.bukkit.entity.Entity[] emptyBukkitEntityArray = new org.bukkit.entity.Entity[0];
    public static final net.minecraft.world.entity.Entity[] emptyEntityArray = new net.minecraft.world.entity.Entity[0];
    //public static final net.minecraft.server.level.ServerLevel[] emptyServerLevelArray = new net.minecraft.server.level.ServerLevel[0];
    public static final net.minecraft.tags.TagKey[] emptyTagKeyArray = new net.minecraft.tags.TagKey[0]; // Leaf - Optimize isEyeInFluid
}
