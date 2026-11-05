package org.wit.vitasense.ui.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import org.wit.vitasense.domain.WeeklyReport
import org.wit.vitasense.data.repository.display
import java.io.File
import java.io.OutputStream

/** A4 points, wrapped text with automatic page breaks; only this private cache directory is shared. */
class WeeklyReportPdfExporter {
    fun write(report: WeeklyReport, ai: String, output: OutputStream) {
        val document=PdfDocument(); val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        var page: PdfDocument.Page?=null; var y=0f; var number=0
        fun newPage() {
            page?.let { document.finishPage(it) }
            number++
            page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create())
            paint.color=Color.DKGRAY; paint.textSize=10f; paint.typeface=Typeface.DEFAULT
            page!!.canvas.drawText("VitaSense  ·  Weekly Health Report",40f,30f,paint)
            page!!.canvas.drawText("Page $number  ·  Informational, not a medical diagnosis",40f,818f,paint)
            y=62f
        }
        fun line(value:String, heading:Boolean=false) {
            paint.textSize=if(heading) 16f else 11f
            paint.typeface=if(heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color=Color.BLACK
            val lineHeight=if(heading) 24f else 17f
            var remaining=value.ifEmpty { " " }
            while(remaining.isNotEmpty()) {
                if(page==null || y+lineHeight>785f) newPage()
                // newPage changes the paint; restore before measuring and drawing.
                paint.textSize=if(heading) 16f else 11f; paint.typeface=if(heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; paint.color=Color.BLACK
                var count=paint.breakText(remaining,true,515f,null).coerceAtLeast(1)
                if(count < remaining.length) { val space=remaining.lastIndexOf(' ', count-1); if(space>0) count=space }
                page!!.canvas.drawText(remaining.substring(0,count),40f,y,paint)
                y+=lineHeight; remaining=remaining.substring(count).trimStart()
            }
        }
        try {
            newPage(); line("VitaSense Weekly Health Report",true)
            line("${report.start} to ${report.end}"); line("Generated: ${report.generatedAt}")
            report.metrics.forEach { line(it.name,true); it.text.lines().forEach { text -> line(text) }; y+=8 }
            report.trends.forEach { (title,values) -> line(title,true); values.forEach { (date,value) -> line("$date    ${value.display()}") }; y+=8 }
            report.sections.forEach { (title,body) -> if(y>735) newPage(); line(title,true); body.lines().forEach { line(it) }; y+=10 }
            line("AI Weekly Insights",true); ai.lines().forEach { line(it) }
            page?.let { document.finishPage(it) }; page=null
            document.writeTo(output)
        } finally { document.close() }
    }
    fun shareFile(context: Context, report: WeeklyReport, ai: String): android.net.Uri {
        val folder=File(context.cacheDir,"weekly-reports").apply { mkdirs() }
        // Keep only a small rolling cache of exports.
        folder.listFiles()?.filter { it.lastModified() < System.currentTimeMillis()-86400000 }?.forEach { it.delete() }
        val file=File(folder,"VitaSense-${report.end}-${java.util.UUID.randomUUID()}.pdf")
        file.outputStream().use { write(report,ai,it) }
        return FileProvider.getUriForFile(context,"${context.packageName}.reports",file)
    }
}
