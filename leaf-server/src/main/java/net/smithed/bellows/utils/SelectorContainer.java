package net.smithed.bellows.utils;

import java.util.HashSet;
import java.util.Set;

public class SelectorContainer {

    public final Set<String> selectorTags = new HashSet<>();
    public final Set<String> notSelectorTags = new HashSet<>();
    public Set<String> entityTypes = null;
    public String type = "";
    public boolean isTypeTag = false;
    public boolean isNotType = false;

    @Override
    public String toString() {
        return "SelectorContainer{" +
                "selectorTags=" + selectorTags +
                ", notSelectorTags=" + notSelectorTags +
                ", entityTypes=" + entityTypes +
                ", type='" + type + '\'' +
                ", isTypeTag=" + isTypeTag +
                ", isNotType=" + isNotType +
                '}';
    }
}
