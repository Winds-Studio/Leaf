package dev.tr7zw.entityculling.versionless.access;

public interface Cullable {

    void setTimeout();

    boolean isForcedVisible();

    void setCulled(boolean value);

    boolean isCulled();

}
