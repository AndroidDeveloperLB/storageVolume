package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.os.Build.*
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.lang.reflect.Array

inline fun <reified T : Any> Context.getSystemServiceCompat(): T =
    ContextCompat.getSystemService(applicationContext, T::class.java)!!

fun File.lengthEx(): Long {
    return try {
        this.length()
    } catch (e: Exception) {
        -1L
    }
}

object FileUtilEx {
    private const val PRIMARY_VOLUME_NAME = "primary"

    /** for each storageVolume, tells if we have access or not, via a HashMap (true for each iff we identified it has access*/
    fun getStorageVolumesAccessState(context: Context): HashMap<StorageVolume, Boolean> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
        val storagePathsWeHaveAccessTo = HashSet<String>()
        //            Log.d("AppLog", "got access to paths:")
        for (persistedUriPermission in persistedUriPermissions) {
            val path = getFullPathFromTreeUri(context, persistedUriPermission.uri)
                ?: continue
            //                Log.d("AppLog", "path: $path")
            storagePathsWeHaveAccessTo.add(path)
        }
        //            Log.d("AppLog", "storage volumes:")
        val result = HashMap<StorageVolume, Boolean>(storageVolumes.size)
        for (storageVolume in storageVolumes) {
            val volumePath = getVolumePath(storageVolume)
            val hasAccess =
                volumePath != null && storagePathsWeHaveAccessTo.contains(volumePath)
            result[storageVolume] = hasAccess
        }
        return result
    }

    fun getVolumePath(context: Context, uri: Uri): String? {
        val volumeIdFromTreeUri = getVolumeIdFromTreeUri(uri) ?: return null
        return getVolumePath(context, volumeIdFromTreeUri)
    }

    @RequiresApi(VERSION_CODES.N)
    fun getVolumePath(storageVolume: StorageVolume): String? {
        if (VERSION.SDK_INT >= VERSION_CODES.R)
            return storageVolume.directory?.absolutePath
        try {
            val storageVolumeClazz = StorageVolume::class.java
            val getPath = storageVolumeClazz.getMethod("getPath")
            return getPath.invoke(storageVolume) as String
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Get the path of a certain volume.
     *
     * @param volumeId The volume id.
     * @return The path.
     */
    private fun getVolumePath(context: Context, volumeId: String): String? {
        val storageManager: StorageManager = context.getSystemServiceCompat()
        if (VERSION.SDK_INT > VERSION_CODES.Q)
            try {
                for (storageVolume in storageManager.storageVolumes) {
                    val primary = storageVolume.isPrimary
                    // primary volume?
                    if (primary && PRIMARY_VOLUME_NAME == volumeId) {
                        return getVolumePath(storageVolume)
                    }
                    val uuid: String? = storageVolume.uuid
                    // other volumes?
                    if (uuid != null && uuid == volumeId) return getVolumePath(storageVolume)
                }
                return null
            } catch (_: Exception) {
            }
        try {
            if (VERSION.SDK_INT >= VERSION_CODES.N) {
                val storageVolumeClazz = StorageVolume::class.java
                val getPath = storageVolumeClazz.getMethod("getPath")
                val storageVolumes = storageManager.storageVolumes
                for (storageVolume in storageVolumes) {
                    val uuid = storageVolume.uuid
                    val primary = storageVolume.isPrimary
                    // primary volume?
                    if (primary && PRIMARY_VOLUME_NAME == volumeId) {
                        return getPath.invoke(storageVolume) as String
                    }
                    // other volumes?
                    if (uuid != null && uuid == volumeId)
                        return getPath.invoke(storageVolume) as String
                }
                return null
            }
            val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
            val getUuid = storageVolumeClazz.getMethod("getUuid")
            val getPath = storageVolumeClazz.getMethod("getPath")
            val isPrimary = storageVolumeClazz.getMethod("isPrimary")
            val result = getVolumeList.invoke(storageManager)
            val length = Array.getLength(result!!)
            for (i in 0 until length) {
                val storageVolumeElement = Array.get(result, i)
                val uuid = getUuid.invoke(storageVolumeElement) as String?
                val primary = isPrimary.invoke(storageVolumeElement) as Boolean
                // primary volume?
                if (primary && PRIMARY_VOLUME_NAME == volumeId) {
                    return getPath.invoke(storageVolumeElement) as String
                }
                // other volumes?
                if (uuid != null && uuid == volumeId)
                    return getPath.invoke(storageVolumeElement) as String
            }
            // not found.
            return null
        } catch (ex: Exception) {
            return null
        }
    }

    /**
     * Get the full path of a document from its tree URI.
     *
     * @param treeUri The tree RI.
     * @return The path (without trailing file separator).
     */
    fun getFullPathFromTreeUri(context: Context, treeUri: Uri?): String? {
        if (treeUri == null)
            return null
        //        noinspection InlinedApi
        val volumeIdFromTreeUri = getVolumeIdFromTreeUri(treeUri) ?: return null
        var volumePath: String? = getVolumePath(context, volumeIdFromTreeUri)
            ?: return null
        if (volumePath!!.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length - 1)
        //        noinspection InlinedApi
        var documentPath = getDocumentPathFromTreeUri(treeUri)
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length - 1)
        return if (documentPath.isNotEmpty())
            if (documentPath.startsWith(File.separator))
                volumePath + documentPath
            else
                volumePath + File.separator + documentPath
        else volumePath
    }

    /**
     * Get the volume ID from the tree URI.
     *
     * @param treeUri The tree URI.
     * @return The volume ID.
     */
    private fun getVolumeIdFromTreeUri(treeUri: Uri): String? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            //            e.printStackTrace()
            return null
        }
        val end = docId.indexOf(':')
        return if (end == -1) null else docId.substring(0, end)
    }

    /**
     * Get the document path (relative to volume name) for a tree URI (LOLLIPOP).
     *
     * @param treeUri The tree URI.
     * @return the document path.
     */
    private fun getDocumentPathFromTreeUri(treeUri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        //TODO avoid using spliting of a string (because it uses extra strings creation)
        //final int start = docId.indexOf(':');
        //int end = docId.indexOf(':', start+1);
        //if(end==-1)
        //    return File.separator;
        val split = docId.split(':').dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (split.size >= 2)
            split[1]
        else
            File.separator
    }
}
