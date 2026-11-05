package org.wit.vitasense.reports

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.wit.vitasense.data.repository.HealthSnapshot
import org.wit.vitasense.domain.WeeklyReportGenerator
import org.wit.vitasense.ui.reports.WeeklyReportPdfExporter
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WeeklyPdfTest {
    @Test fun multiPagePdfAndSecureSharingUri() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val end=LocalDate.of(2026,11,5)
        val snapshot=HealthSnapshot(end.minusDays(6),end,emptyList(),emptyList(),emptyList(),emptyList())
        val report=WeeklyReportGenerator.generate(snapshot,snapshot)
        val file=File(context.cacheDir,"pdf-test.pdf")
        try {
            file.outputStream().use { WeeklyReportPdfExporter().write(report,"Long report line with several words. ".repeat(3000),it) }
            PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { assertTrue(it.pageCount>2) }
            file.copyTo(File(context.getExternalFilesDir(null),"VitaSense-multipage-test.pdf"),overwrite=true)
            val uri=WeeklyReportPdfExporter().shareFile(context,report,"Local summary")
            assertEquals("content",uri.scheme); assertEquals("${context.packageName}.reports",uri.authority)
            context.contentResolver.openInputStream(uri).use { assertEquals('%'.code,it!!.read()) }
        } finally { file.delete() }
    }
}
