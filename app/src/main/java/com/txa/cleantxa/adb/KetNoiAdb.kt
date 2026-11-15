package com.txa.cleantxa.adb

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

class KetNoiAdb private constructor(
    private val khoa: PrivateKey,
    private val chungChi: Certificate,
) : AbsAdbConnectionManager() {

    init {
        setTimeout(20, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = khoa
    override fun getCertificate(): Certificate = chungChi
    override fun getDeviceName(): String = "CleanTxa"

    @Volatile
    var diemCuoi: Pair<String, Int>? = null
        private set

    override fun connect(host: String, port: Int): Boolean {
        val ok = super.connect(host, port)
        if (ok) {
            diemCuoi = host to port
            coHeadC = null
            coDd = null
        }
        return ok
    }

    fun ketNoiLai(): Boolean {
        val d = diemCuoi ?: return false
        runCatching { disconnect() }
        return runCatching { super.connect(d.first, d.second) }.getOrDefault(false)
    }

    fun shell(lenh: String): String {
        val dau = "__TXA_" + java.lang.Long.toHexString(System.nanoTime()) + "__"
        val chu = docToiKhi(openStream("shell:$lenh; echo $dau")) { it.contains(dau) }
        return chu.substringBefore(dau)
    }

    fun chupPng(): ByteArray {
        val b64 = shell("screencap -p | base64").filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    }

    private fun docToiKhi(stream: AdbStream, giay: Int = 120, xong: (String) -> Boolean): String {
        val out = ByteArrayOutputStream()
        val hetHan = System.currentTimeMillis() + giay * 1000L
        val luong = Thread {
            runCatching {
                val input = stream.openInputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    synchronized(out) { out.write(buf, 0, n) }
                    if (xong(synchronized(out) { out.toString("UTF-8") })) break
                }
            }
        }.apply { isDaemon = true; start() }
        while (luong.isAlive && System.currentTimeMillis() < hetHan) luong.join(200)
        runCatching { stream.close() }
        if (luong.isAlive) {
            luong.interrupt()
            throw IOException("TV did not respond after $giay s")
        }
        return synchronized(out) { out.toString("UTF-8") }.replace("\r", "")
    }

    fun caiApk(apk: ByteArray): String {
        val stream = openStream("exec:cmd package install -r -S ${apk.size}")
        val o = stream.openOutputStream()
        var i = 0
        while (i < apk.size) {
            val n = minOf(4000, apk.size - i)
            o.write(apk, i, n)
            i += n
        }
        o.flush()
        return docToiKhi(stream, 180) { it.contains("Success") || it.contains("Failure") || it.contains("Error") }
    }

    fun ghiFile(duongDan: String, noiDung: ByteArray) {
        if (coHeadC() && runCatching { ghiNhanh(duongDan, noiDung, "head -c ${noiDung.size}") }.getOrDefault(false)) return
        if (coDd() && runCatching { ghiNhanh(duongDan, noiDung, "dd bs=1 count=${noiDung.size} 2>/dev/null") }.getOrDefault(false)) return
        shell("rm -f $duongDan $duongDan.b64")
        val b64 = android.util.Base64.encodeToString(noiDung, android.util.Base64.NO_WRAP)
        b64.chunked(3000).forEach { manh -> shell("printf '%s' '$manh' >> $duongDan.b64") }
        shell("base64 -d $duongDan.b64 > $duongDan; rm -f $duongDan.b64")
        if (shell("wc -c < $duongDan").trim().toIntOrNull() != noiDung.size) {
            throw IOException("TV failed to receive ${duongDan.substringAfterLast('/')}")
        }
    }

    @Volatile
    private var coHeadC: Boolean? = null
    private fun coHeadC(): Boolean =
        coHeadC ?: (runCatching { shell("echo abc | head -c 2 2>/dev/null").trim() == "ab" }.getOrDefault(false)).also { coHeadC = it }

    @Volatile
    private var coDd: Boolean? = null
    private fun coDd(): Boolean =
        coDd ?: (runCatching { shell("echo abc | dd bs=1 count=2 2>/dev/null").trim() == "ab" }.getOrDefault(false)).also { coDd = it }

    private fun ghiNhanh(duongDan: String, noiDung: ByteArray, lenh: String): Boolean {
        val dau = "__TXA_" + java.lang.Long.toHexString(System.nanoTime()) + "__"
        val stream = openStream("exec:sh -c '$lenh > $duongDan; echo $dau'")
        val o = stream.openOutputStream()
        var i = 0
        while (i < noiDung.size) {
            val n = minOf(4000, noiDung.size - i)
            o.write(noiDung, i, n)
            i += n
        }
        o.flush()
        docToiKhi(stream, 120) { it.contains(dau) }
        return shell("wc -c < $duongDan").trim().toIntOrNull() == noiDung.size
    }

    companion object {
        @Volatile
        private var hienTai: KetNoiAdb? = null

        fun lay(context: Context): KetNoiAdb = hienTai ?: synchronized(this) {
            hienTai ?: taoMoi(context).also { hienTai = it }
        }

        private fun taoMoi(context: Context): KetNoiAdb {
            val thuMuc = File(context.filesDir, "adb").apply { mkdirs() }
            val fKhoa = File(thuMuc, "khoa.der")
            val fCert = File(thuMuc, "cert.der")
            if (fKhoa.exists() && fCert.exists()) {
                runCatching {
                    val khoa = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(fKhoa.readBytes()))
                    val cert = CertificateFactory.getInstance("X.509").generateCertificate(fCert.inputStream())
                    return KetNoiAdb(khoa, cert)
                }
            }
            val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val cap = gen.generateKeyPair()
            val ten = X500Name("CN=CleanTxa")
            val tu = Date()
            val den = Date(tu.time + 3650L * 24 * 3600 * 1000)
            val builder = JcaX509v3CertificateBuilder(ten, BigInteger.valueOf(tu.time), tu, den, ten, cap.public)
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(cap.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            fKhoa.writeBytes(cap.private.encoded)
            fCert.writeBytes(cert.encoded)
            return KetNoiAdb(cap.private, cert)
        }
    }
}

