package org.leavesmc.leaves.protocol.chatimage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import org.leavesmc.leaves.LeavesLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ServerBlockCache {

    public static final ServerBlockCache SERVER_BLOCK_CACHE = new ServerBlockCache();

    public Cache<String, List<UUID>> userCache = CacheBuilder.newBuilder().expireAfterAccess(60, TimeUnit.SECONDS).build();
    public Cache<String, Map<Integer, String>> blockCache = CacheBuilder.newBuilder().expireAfterAccess(60, TimeUnit.SECONDS).build();
    public Cache<String, Integer> fileCount = CacheBuilder.newBuilder().expireAfterAccess(60, TimeUnit.SECONDS).build();

    public Map<Integer, String> createBlock(ChatImageProtocol.ChatImageIndex title, String imgBytes) {
        try {
            Map<Integer, String> blocks = this.blockCache.get(title.url(), HashMap::new);
            blocks.put(title.index(), imgBytes);
            this.blockCache.put(title.url(), blocks);
            this.fileCount.put(title.url(), title.total());
            return blocks;
        } catch (Exception e) {
            LeavesLogger.LOGGER.warning("Failed to create block for title " + title.url() + ": " + e);
            return null;
        }
    }

    public Map<Integer, String> getBlock(String url) {
        Map<Integer, String> list;
        Integer total;
        if ((list = this.blockCache.getIfPresent(url)) != null && (total = this.fileCount.getIfPresent(url)) != null) {
            if (total == list.size()) {
                return list;
            }
        }
        return null;
    }

    public void tryAddUser(String url, UUID uuid) {
        try {
            List<UUID> names = this.userCache.get(url, Lists::newArrayList);
            names.add(uuid);
            this.userCache.put(url, names);
        } catch (Exception e) {
            LeavesLogger.LOGGER.warning("Failed to add user " + uuid + ": " + e);
        }
    }

    public List<UUID> getUsers(String url) {
        List<UUID> names;
        if ((names = this.userCache.getIfPresent(url)) != null) {
            this.userCache.put(url, Lists.newArrayList());
            return names;
        } else {
            return null;
        }
    }
}