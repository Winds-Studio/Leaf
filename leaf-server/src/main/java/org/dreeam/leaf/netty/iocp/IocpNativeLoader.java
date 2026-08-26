package org.dreeam.leaf.netty.iocp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class IocpNativeLoader {
    static final int ABI_VERSION = 3;
    private static final String NATIVE_LIBRARY_RESOURCE = "/native/windows-x86_64-iocp-transport-abi-" + ABI_VERSION + ".dll";
    private static boolean loaded;

    private IocpNativeLoader() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }
        Path extracted = null;
        try (InputStream input = IocpNativeLoader.class.getResourceAsStream(NATIVE_LIBRARY_RESOURCE)) {
            if (input == null) {
                throw new UnsatisfiedLinkError("Missing " + NATIVE_LIBRARY_RESOURCE);
            }
            extracted = Files.createTempFile("iocp-transport-", ".dll").toAbsolutePath();
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            System.load(extracted.toString());
            loaded = true;
        } catch (IOException exception) {
            if (extracted != null) {
                try {
                    Files.deleteIfExists(extracted);
                } catch (IOException suppressed) {
                    exception.addSuppressed(suppressed);
                }
            }
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Unable to extract the IOCP native library");
            error.initCause(exception);
            throw error;
        }
    }
}
