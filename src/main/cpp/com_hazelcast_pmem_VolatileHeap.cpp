#include "com_hazelcast_pmem_VolatileHeap.h"

const char *volatile_layout_name = "libpmem_volatile_heap";

void throw_OOM(JNIEnv *env)
{
    char className[50] = "java/lang/OutOfMemoryError";
    jclass exClass = env->FindClass(className);

    char errmsg[250];
    strcpy(errmsg, pmemobj_errormsg());
    env->ThrowNew(exClass, errmsg);
}

void throw_IOException(JNIEnv *env)
{
    char className[50] = "java/io/IOException";
    jclass exClass = env->FindClass(className);

    char errmsg[250];
    strcpy(errmsg, pmemobj_errormsg());
    env->ThrowNew(exClass, errmsg);
}

JNIEXPORT jlong JNICALL Java_com_hazelcast_pmem_VolatileHeap_nativeCreateHeap
  (JNIEnv *env, jobject obj, jstring path, jlong size)
{
    const char* native_string = env->GetStringUTFChars(path, 0);

    PMEMobjpool *pool = pmemobj_create(native_string, volatile_layout_name, (size_t) size, S_IRUSR | S_IWUSR);

    env->ReleaseStringUTFChars(path, native_string);

    if (pool == NULL) {
        throw_IOException(env);
    }

    return (long) pool;
}

JNIEXPORT jlong JNICALL Java_com_hazelcast_pmem_VolatileHeap_nativeOpenHeap
  (JNIEnv *env, jobject obj, jstring path, jlong size)
{
    const char* native_string = env->GetStringUTFChars(path, 0);

    PMEMobjpool *pool = pmemobj_open(native_string, volatile_layout_name);

    env->ReleaseStringUTFChars(path, native_string);

    if (pool == NULL) {
        throw_IOException(env);
    }

    return (long) pool;
}

JNIEXPORT void JNICALL Java_com_hazelcast_pmem_VolatileHeap_nativeCloseHeap
  (JNIEnv *env, jobject obj, jlong poolHandle)
{
    PMEMobjpool *pool = (PMEMobjpool*)poolHandle;
    pmemobj_close(pool);
}

JNIEXPORT jlong JNICALL Java_com_hazelcast_pmem_VolatileHeap_nativeAlloc
  (JNIEnv *env, jobject obj, jlong poolHandle, jlong size)
{
    PMEMobjpool *pool = (PMEMobjpool*)poolHandle;
    PMEMoid bytes = OID_NULL;

    int rc = pmemobj_alloc(pool, &bytes, (size_t)size, 0, NULL, NULL);
    if (rc == -1) {
        throw_OOM(env);
    }

    return bytes.off;
}

JNIEXPORT jlong JNICALL Java_com_hazelcast_pmem_VolatileHeap_nativeRealloc
  (JNIEnv *env, jobject obj, jlong poolHandle, jlong address, jlong size)
{
    PMEMobjpool *pool = (PMEMobjpool*)poolHandle;
    PMEMoid bytes = pmemobj_oid((const void*)address);

    int rc = pmemobj_realloc(pool, &bytes, (size_t)size, 0);
    if (rc == -1) {
        throw_OOM(env);
    }

    return bytes.off;
}

JNIEXPORT void JNICALL Java_com_hazelcast_pmem_VolatileHeap_nativeFree
  (JNIEnv *env, jobject obj, jlong address)
{
    PMEMoid oid = pmemobj_oid((const void*)address);
    TOID(char) bytes;

    TOID_ASSIGN(bytes, oid);
    POBJ_FREE(&bytes);
}

