package dev.korbi.helgeland

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactHit(val name: String, val uri: Uri)

object Contacts {
    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Up to [limit] contacts whose name matches [query]; empty without permission. */
    suspend fun search(context: Context, query: String, limit: Int = 4): List<ContactHit> = withContext(Dispatchers.IO) {
        if (!hasAccess(context) || query.isBlank()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val columns = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        runCatching {
            context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val name = cursor.getString(2) ?: continue
                        add(ContactHit(name, ContactsContract.Contacts.getLookupUri(cursor.getLong(0), cursor.getString(1))))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
