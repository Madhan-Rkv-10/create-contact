package com.example.add_contact

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.net.Uri
import android.util.Base64

class MainActivity : FlutterActivity() {

    private val CHANNEL = "contact_launcher"
    private val REQUEST_CONTACT_INSERT = 1001
    private val REQUEST_CONTACT_PERMISSION = 2001

    private var pendingResult: MethodChannel.Result? = null
    private var pendingArgs: Map<String, Any>? = null
    private var existingContactIds: Set<String> = emptySet()

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "createContact") {
                if (pendingResult != null) {
                    result.error("IN_PROGRESS", "Another contact creation is in progress", null)
                    return@setMethodCallHandler
                }

                val args = call.arguments as? Map<String, Any>
                if (!hasContactPermissions()) {
                    pendingResult = result
                    pendingArgs = args
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            android.Manifest.permission.READ_CONTACTS,
                            android.Manifest.permission.WRITE_CONTACTS
                        ),
                        REQUEST_CONTACT_PERMISSION
                    )
                } else {
                    pendingResult = result
                    invokeCreateContact(args)
                }
            } else {
                result.notImplemented()
            }
        }
    }

    private fun hasContactPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun invokeCreateContact(args: Map<String, Any>?) {
        existingContactIds = fetchAllContactIds()

        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE

            putExtra(ContactsContract.Intents.Insert.NAME, args?.get("fullName") as? String)
            putExtra(ContactsContract.Intents.Insert.PHONE, args?.get("primaryPhone") as? String)
            putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, args?.get("secondaryPhone") as? String)
            putExtra(ContactsContract.Intents.Insert.EMAIL, args?.get("email") as? String)
            putExtra(ContactsContract.Intents.Insert.COMPANY, args?.get("company") as? String)
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, args?.get("jobTitle") as? String)

            val note = buildList {
                (args?.get("verificationCode") as? String)?.let { add("Code: $it") }
                (args?.get("pronunciation") as? String)?.let { add("Pronunciation: $it") }
            }.joinToString("\n")
            if (note.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.NOTES, note)

            val address = args?.get("address") as? Map<String, String>
            if (address != null) {
                val fullAddress = listOfNotNull(
                    address["street"],
                    address["city"],
                    address["state"],
                    address["postalCode"],
                    address["country"]
                ).joinToString(", ")
                putExtra(ContactsContract.Intents.Insert.POSTAL, fullAddress)
            }
        }

        startActivityForResult(intent, REQUEST_CONTACT_INSERT)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CONTACT_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                invokeCreateContact(pendingArgs)
            } else {
                pendingResult?.error("PERMISSION_DENIED", "Contacts permission not granted", null)
                pendingResult = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CONTACT_INSERT) {
            val result = pendingResult
            pendingResult = null

            val newId = fetchAllContactIds().subtract(existingContactIds).firstOrNull()
            val contactInfo = if (newId != null) getContactDetails(newId) else emptyMap()

            if (resultCode == Activity.RESULT_OK || contactInfo.isNotEmpty()) {
                result?.success(mapOf("status" to "CREATED") + contactInfo)
            } else {
                result?.success(mapOf("status" to "USER_CANCELLED"))
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun fetchAllContactIds(): Set<String> {
        val ids = mutableSetOf<String>()
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                ids.add(it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)))
            }
        }
        return ids
    }

    private fun getContactDetails(contactId: String): Map<String, String?> {
        val result = mutableMapOf<String, String?>()

        // // Display Name
        // contentResolver.query(
        //     ContactsContract.Contacts.CONTENT_URI,
        //     arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
        //     "${ContactsContract.Contacts._ID} = ?",
        //     arrayOf(contactId),
        //     null
        // )?.use { cursor ->
        //     if (cursor.moveToFirst()) {
        //         result["fullName"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
        //     }
        // }
 contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI // 👈 Add this
        ),
        "${ContactsContract.Contacts._ID} = ?",
        arrayOf(contactId),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            result["fullName"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
            result["photoUri"] = getContactPhotoBase64(cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)))
        }
    }
        // Phone
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                result["phone"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }

        // Email
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                result["email"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
            }
        }

        // Company and Job Title
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                result["company"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY))
                result["jobTitle"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.TITLE))
            }
        }

        // Notes
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                result["notes"] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Note.NOTE))
            }
        }

        return result
    }



    private fun getContactPhotoBase64(uriString: String?): String? {
    return try {
        if (uriString.isNullOrEmpty()) return null
        val uri = Uri.parse(uriString)
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
}