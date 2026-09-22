package org.leavesmc.leaves.protocol.syncmatica;

public enum LocalLitematicState {
    NO_LOCAL_LITEMATIC(true, false),
    LOCAL_LITEMATIC_DESYNC(true, false),
    DOWNLOADING_LITEMATIC(false, false),
    LOCAL_LITEMATIC_PRESENT(false, true);

    private final boolean downloadReady;
    private final boolean fileReady;

    LocalLitematicState(final boolean downloadReady, final boolean fileReady) {
        this.downloadReady = downloadReady;
        this.fileReady = fileReady;
    }

    public boolean isReadyForDownload() {
        return downloadReady;
    }

    public boolean isLocalFileReady() {
        return fileReady;
    }
}
