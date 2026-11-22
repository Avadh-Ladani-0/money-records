package com.moneyrecords.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate

/**
 * The folder picked for automatic backups (a document tree the app was given lasting access to).
 * Writes "MoneyRecords-YYYY-MM-DD.json" and keeps only the newest [keep].
 */
object BackupFolder {
    const val PREFIX = "MoneyRecords-"
    const val KEEP = 5

    fun fileName(date: LocalDate) = "$PREFIX$date.json"

    /** Names to delete so only the newest [keep] backups remain (names sort by date). */
    fun toPrune(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(".json") }.sortedDescending().drop(keep)

    /** Writes [bytes] as today's backup in [treeUri]; replaces an earlier one from the same day. */
    fun write(context: Context, treeUri: Uri, date: LocalDate, bytes: ByteArray) {
        val resolver = context.contentResolver
        val folder = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val name = fileName(date)
        children(context, treeUri).filter { it.first == name }.forEach { DocumentsContract.deleteDocument(resolver, it.second) }
        val file = DocumentsContract.createDocument(resolver, folder, "application/json", name)
            ?: error("Couldn't create the backup file")
        resolver.openOutputStream(file, "w")?.use { it.write(bytes) } ?: error("Couldn't write the backup file")
        val existing = children(context, treeUri)
        val prune = toPrune(existing.map { it.first }).toSet()
        existing.filter { it.first in prune }.forEach { DocumentsContract.deleteDocument(resolver, it.second) }
    }

    /** A short folder name for display ("Download/MoneyRecords"). */
    fun displayName(treeUri: Uri): String =
        DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':').ifBlank { "Selected folder" }

    private fun children(context: Context, treeUri: Uri): List<Pair<String, Uri>> {
        val listUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val out = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(
            listUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += c.getString(1) to DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
            }
        }
        return out
    }
}
