package dev.korbi.fjell

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactHit(val name: String, val uri: Uri, val number: String?)

object Contacts {
    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Up to [limit] contacts whose name matches [query]; empty without permission. */
    suspend fun search(context: Context, query: String, limit: Int = 4): List<ContactHit> = withContext(Dispatchers.IO) {
        if (!hasAccess(context) || query.isBlank()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val columns = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        runCatching {
            context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val name = cursor.getString(2) ?: continue
                        val id = cursor.getLong(0)
                        val number = if (cursor.getInt(3) > 0) phoneNumber(context, id) else null
                        add(ContactHit(name, ContactsContract.Contacts.getLookupUri(id, cursor.getString(1)), number))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /** The contact's primary number, else its first one. */
    private fun phoneNumber(context: Context, contactId: Long): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}
