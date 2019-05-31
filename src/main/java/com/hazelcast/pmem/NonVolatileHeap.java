package com.hazelcast.pmem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NonVolatileHeap {
    static {
        NativeLibrary.load();
    }

    //This is approximation to overhead of libmemobj, no details are known
    //just relies on observation.
    private static final int FILE_SIZE_OVERHEAD = 10 * 1024 * 1024;

    private static final Object lock = new Object();
    private final Object closeLock = new Object();

    private final Path path;
    private final long size;
    private final long poolHandle;
    private boolean open;

    private NonVolatileHeap(Path path, long poolHandle) throws IOException {
        this.path = path;
        this.size = Files.size(path);
        this.poolHandle = poolHandle;
        this.open = true;
    }

    public static NonVolatileHeap openHeap(String path) throws IOException {
        synchronized (lock) {
            long poolHandle = nativeOpenHeap(path);
            if (poolHandle == 0) {
                throw new IOException("Failed to open heap at : " + path);
            }

            return new NonVolatileHeap(Paths.get(path), poolHandle);

        }
    }

    public static NonVolatileHeap createHeap(String path, long size) throws IOException {
        synchronized (lock) {
            long poolHandle = nativeCreateHeap(path, size + FILE_SIZE_OVERHEAD);
            if (poolHandle == 0) {
                throw new IOException("Cannot create heap at " + path);
            }

            return new NonVolatileHeap(Paths.get(path), poolHandle);
        }
    }
    public void close() {
        synchronized (closeLock) {
            if (open) {
                nativeCloseHeap(poolHandle);
                open = false;
            }
        }
    }

    public long toAddress(long handle) {
        if (handle == 0) {
            return 0;
        }

        return poolHandle + handle;
    }

    public long toHandle(long addr) {
        if (addr == 0) {
            return 0;
        }

        long handle = addr - poolHandle;
        if (handle < 0) {
            throw new IllegalArgumentException("addr is " + addr + " poolHandle is " + poolHandle);
        }
        return addr - poolHandle;
    }

    public long allocate(long size) {
        return toAddress(nativeAlloc(poolHandle, size));
    }

    public long realloc(long addr, long size) {
        return toAddress(nativeRealloc(poolHandle, toHandle(addr), size));
    }

    public void free(long addr) {
        nativeFree(toHandle(addr));
    }

    public int setRoot(long addr) {
        return nativeSetRoot(poolHandle, addr);
    }

    public long getRoot() {
        return nativeGetRoot(poolHandle);
    }

    public int startTransaction() {
        int ret = nativeStartTransaction(poolHandle);
        if (ret == -1) {
            throw new IllegalStateException("Starting transaction failed");
        }

        return ret;
    }

    public int addToTransaction(long address, long size) {
        int ret = nativeAddToTransaction(poolHandle, address, size);
        if (ret != 2) {
            throw new IllegalStateException("Transaction failed return value : " + ret);
        }

        return ret;
    }

    public int commitTransaction() {
        int ret = nativeCommitTransaction();
        if (ret != 2) {
            throw new IllegalStateException("Transaction failed return value " + ret);
        }

        return ret;
    }

    private static native long nativeCreateHeap(String path, long size);
    private static native long nativeOpenHeap(String path);
    private static native void nativeCloseHeap(long poolHandle);

    private static native int nativeSetRoot(long poolHandle, long address);
    private static native long nativeGetRoot(long poolHandle);

    private static native long nativeAlloc(long poolHandle, long size);
    private static native long nativeRealloc(long poolHandle, long addr, long size);
    private static native int nativeFree(long addr);

    private static native int nativeAddToTransaction(long poolHandle, long address, long size);
    private static native int nativeStartTransaction(long poolHandle);
    private static native int nativeCommitTransaction();
    private static native void nativeEndTransaction();
    private static native void nativeAbortTransaction();
    private static native int nativeTransactionState();

}
