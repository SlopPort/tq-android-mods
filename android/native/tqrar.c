/* Read-only RAR access for the importer through libarchive's own RAR4/RAR5 readers. */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "archive.h"
#include "archive_entry.h"

static void fail(JNIEnv *env, const char *message) {
    jclass io = (*env)->FindClass(env, "java/io/IOException");
    if (io) (*env)->ThrowNew(env, io, message ? message : "RAR archive is damaged");
}

JNIEXPORT jlong JNICALL Java_org_tqmods_Rar_open(JNIEnv *env, jclass c, jstring path) {
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    if (!p) return 0;
    struct archive *a = archive_read_new();
    archive_read_support_format_rar(a);
    archive_read_support_format_rar5(a);
    int r = archive_read_open_filename(a, p, 65536);
    (*env)->ReleaseStringUTFChars(env, path, p);
    if (r != ARCHIVE_OK) { fail(env, "Not a readable RAR archive"); archive_read_free(a); return 0; }
    return (jlong) (intptr_t) a;
}

/* Next entry's path, ending in "/" for folders, or null at the end. */
JNIEXPORT jstring JNICALL Java_org_tqmods_Rar_next(JNIEnv *env, jclass c, jlong h) {
    struct archive *a = (struct archive *) (intptr_t) h;
    struct archive_entry *e;
    int r = archive_read_next_header(a, &e);
    if (r == ARCHIVE_EOF) return NULL;
    if (r < ARCHIVE_WARN) { fail(env, archive_error_string(a)); return NULL; }
    if (archive_entry_is_encrypted(e)) { fail(env, "RAR archive is password-protected"); return NULL; }
    const char *name = archive_entry_pathname_utf8(e);
    if (!name) name = archive_entry_pathname(e);
    if (!name || !*name) { fail(env, "RAR entry without a name"); return NULL; }
    int dir = archive_entry_filetype(e) == AE_IFDIR;
    if (!dir && archive_entry_filetype(e) != AE_IFREG) { fail(env, "RAR archive contains links or special files"); return NULL; }
    size_t n = strlen(name);
    char *copy = malloc(n + 2);
    if (!copy) { fail(env, "Out of memory"); return NULL; }
    memcpy(copy, name, n);
    if (dir && (n == 0 || name[n - 1] != '/')) copy[n++] = '/';
    copy[n] = 0;
    jstring s = (*env)->NewStringUTF(env, copy);
    free(copy);
    return s;
}

JNIEXPORT jint JNICALL Java_org_tqmods_Rar_read(JNIEnv *env, jclass c, jlong h, jbyteArray buf, jint off, jint len) {
    struct archive *a = (struct archive *) (intptr_t) h;
    jbyte *b = (*env)->GetByteArrayElements(env, buf, NULL);
    if (!b) return -1;
    la_ssize_t n = archive_read_data(a, b + off, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buf, b, n > 0 ? 0 : JNI_ABORT);
    if (n < 0) { fail(env, archive_error_string(a)); return -1; }
    return n == 0 ? -1 : (jint) n;
}

JNIEXPORT void JNICALL Java_org_tqmods_Rar_close(JNIEnv *env, jclass c, jlong h) {
    if (h) archive_read_free((struct archive *) (intptr_t) h);
}
