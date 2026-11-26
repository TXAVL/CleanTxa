package com.txa.cleantxa.ui

import com.txa.cleantxa.t
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.txa.cleantxa.adb.KetNoiAdb
import com.txa.cleantxa.adb.ThaoTacTv
import com.txa.cleantxa.dulieu.BanSaoLuu
import com.txa.cleantxa.dulieu.DuLieu
import com.txa.cleantxa.dulieu.KeHoach
import com.txa.cleantxa.dulieu.KhoSaoLuu
import com.txa.cleantxa.dulieu.THANH_PHO
import com.txa.cleantxa.dulieu.MauTv
import com.txa.cleantxa.dulieu.MucGoi
import com.txa.cleantxa.dulieu.ThongTinTv
import com.txa.cleantxa.dulieu.TrangThaiGoi
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

enum class Man { CHINH, KET_NOI, THONG_TIN, CHON_GOI, DANG_TAT, HOME_SACH, XONG, KHOI_PHUC, HUONG_DAN, GIAY_PHEP, KHOA_TRE, REMOTE, LOGS, TV_LOI_QR }
enum class BuocCaiDat { CHUA_BAT_DAU, DANG_TAI, SAN_SANG_CAI, CHO_PLAY_PROTECT, HOAN_TAT }

/** Số app tối đa trong dock: bằng số cột lớn nhất của Home sạch (columns 1–8); dock dài hơn số cột thì tràn xuống hàng sau (22/10/2026, góp ý muốn hàng đầu nhiều app hơn). */
const val DOCK_TOI_DA = 8

/**
 * Bảng phím chung cho TCL không có mẫu riêng: Netflix 4062, YouTube 4065, Prime Video 4099, bánh răng 83 / 4077 — cùng số
 * trên mọi mẫu TCL có bảng trong upstream (tcl-43c645, tcl-55c725, tcl-65c735, tcl-g10…).
 */
/** doiHang đặc biệt: hàng dock nằm giữa màn hình (rows_middle của bản vá launcher), góp ý 24/10/2026. */
const val HANG_GIUA = Int.MIN_VALUE
/** Số ô kênh tối đa (link_tiles): 12 lên 16 cho người gộp kênh VTVgo + FPT Play (góp ý tnhoang97 28/10/2026). */
const val KENH_TOI_DA = 16
/** KEY_UNKNOWN của Linux (input-event-codes.h): không phải mã riêng của một nút. */
const val SCAN_KHONG_RO = 240
/** Số ảnh thêm tối đa sau ảnh riêng chính trong bộ ảnh tự đổi (cả bộ 20 ảnh, gửi qua ADB vẫn nhanh). */
const val ANH_XOAY_TOI_DA = 19

/**
 * Ô kênh VTV trên Home sạch (góp ý khoald92vn, trananhht9 23/10/2026: bố mẹ bấm thẳng vào kênh). Mỗi ô mở thẳng kênh
 * trong VTVgo bằng vtvgo://play?mediaType=CHANNEL&contentId=<mã> (mã kênh từ API công khai display/v21.0/catalogs/
 * with-channels của VTVgo, thử trên VTVgo TV 12.10.23, 25/10/2026). Launcher: link_tiles + gói giả vgs.kenh.* trong dock_apps.
 */
const val GOI_VTVGO = "vn.vtv.vtvgotv"
val KENH_VTV = listOf(
    Triple("VTV1", 1, "#D71920"), Triple("VTV2", 2, "#2E7D32"), Triple("VTV3", 3, "#E8590C"),
    Triple("VTV4", 4, "#1565C0"), Triple("VTV5", 5, "#6A1B9A"), Triple("VTV6", 13, "#00897B"),
    Triple("VTV7", 27, "#F9A825"), Triple("VTV8", 36, "#AD1457"), Triple("VTV9", 39, "#283593"),
)
private const val GOI_KENH = "vgs.kenh."

/** Keycode lên/xuống/trái/phải, OK, Enter, Enter số, Back: không cho gán app (khớp RemoteKeys.navigationKey). */
private val PHIM_DIEU_HUONG = setOf(19, 20, 21, 22, 23, 66, 160, 4)
private const val PHIM_TCL_CHUNG ="""{"app_keys":["4062:com.netflix.ninja","4065:com.google.android.youtube.tv","4099:com.amazon.amazonvideo.livingroom"],"settings_key_keycodes":[83,4077]}"""

/**
 * Mẫu mà phím remote mặc định TẮT: TCL G09 Android 12 — bật dịch vụ lọc phím là giữ nút âm lượng chỉ nhảy một nấc
 * (upstream docs/devices/tcl-55p71b-g09-key-repeat.md). Người dùng vẫn tự bật được.
 */
private val PHIM_TAT_SAN = setOf("tcl-g09-google-tv")

/** Trình duyệt cho nút Web của remote TCL, theo thứ tự ưu tiên; tivi không có cái nào thì nút Web để nguyên. */
private val TRINH_DUYET_TV = listOf("com.android.chrome", "com.tcl.browser", "com.phlox.tvwebbrowser", "com.cloudmosa.puffinTV", "org.mozilla.tv.firefox")

class PhienLamViec(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val adb by lazy { KetNoiAdb.lay(ctx) }
    val kho by lazy { KhoSaoLuu(ctx) }
    private val tv by lazy { ThaoTacTv(adb, kho).also { it.chonTxaHome = prefs.getBoolean("home_txa", false) } }
    private val prefs by lazy { ctx.getSharedPreferences("cleantxa", Context.MODE_PRIVATE) }

    /** true: cài CleanTxa Home (launcher riêng của app); false: Home sạch của CleanYourTV. Lưu giữa các lần mở app. */
    var homeTxa by mutableStateOf(prefs.getBoolean("home_txa", false))
        private set
    fun chonHomeTxa(bat: Boolean) {
        homeTxa = bat
        prefs.edit().putBoolean("home_txa", bat).apply()
        tv.chonTxaHome = bat
    }

    // Công tắc riêng của CleanTxa Home (chỉ ghi vào cấu hình khi homeTxa bật).
    var txaNhanh by mutableStateOf(prefs.getBoolean("txa_nhanh", true))
    var txaGanDay by mutableStateOf(prefs.getBoolean("txa_gan_day", true))
    var txaNguon by mutableStateOf(prefs.getBoolean("txa_nguon", true))
    var txaNutTreEm by mutableStateOf(prefs.getBoolean("txa_nut_tre", true))
    // Đồng hồ: ẩn thì ẩn luôn dòng ngày; hiện thì có thêm giây, ngày, 12 / 24 giờ.
    var txaDongHo by mutableStateOf(prefs.getBoolean("txa_dong_ho", true))
    var txaGiay by mutableStateOf(prefs.getBoolean("txa_giay", false))
    var txaNgay by mutableStateOf(prefs.getBoolean("txa_ngay", true))
    var txa24h by mutableStateOf(prefs.getBoolean("txa_24h", true))
    /** Cỡ ô app: "small" | "medium" | "large". */
    var txaCoO by mutableStateOf(prefs.getString("txa_co_o", "medium") ?: "medium")
    /** Kiểu bo góc ô app: "rounded" | "pill" | "sharp". */
    var txaBoGoc by mutableStateOf(prefs.getString("txa_bo_goc", "rounded") ?: "rounded")
    /** Màu nhấn giao diện CleanTxa Home ("#RRGGBB"). */
    var txaMauNhan by mutableStateOf(prefs.getString("txa_mau_nhan", "#2EC4B6") ?: "#2EC4B6")
    var txaNutWifi by mutableStateOf(prefs.getBoolean("txa_nut_wifi", true))
    var txaNutCaiDat by mutableStateOf(prefs.getBoolean("txa_nut_cai_dat", true))
    var txaHienIp by mutableStateOf(prefs.getBoolean("txa_hien_ip", false))
    fun luuTuyChonTxa() {
        prefs.edit().putBoolean("txa_nhanh", txaNhanh).putBoolean("txa_gan_day", txaGanDay)
            .putBoolean("txa_nguon", txaNguon).putBoolean("txa_nut_tre", txaNutTreEm)
            .putBoolean("txa_dong_ho", txaDongHo).putBoolean("txa_giay", txaGiay).putBoolean("txa_ngay", txaNgay)
            .putBoolean("txa_24h", txa24h).putString("txa_co_o", txaCoO)
            .putString("txa_bo_goc", txaBoGoc).putString("txa_mau_nhan", txaMauNhan)
            .putBoolean("txa_nut_wifi", txaNutWifi).putBoolean("txa_nut_cai_dat", txaNutCaiDat).putBoolean("txa_hien_ip", txaHienIp)
            .apply()
    }

    var man by mutableStateOf(Man.CHINH)
    /** Màn để quay về sau khi xem Hướng dẫn / Giấy phép. */
    var manTruoc by mutableStateOf(Man.CHINH)

    fun moHuongDan() { if (man != Man.HUONG_DAN) manTruoc = man; man = Man.HUONG_DAN }
    fun moGiayPhep() { if (man != Man.GIAY_PHEP) manTruoc = man; man = Man.GIAY_PHEP }
    fun moLogs() { if (man != Man.LOGS && man != Man.TV_LOI_QR) manTruoc = man; man = Man.LOGS }
    fun moManLoiQr() { if (man != Man.TV_LOI_QR) manTruoc = man; man = Man.TV_LOI_QR }

    fun tvSummaryForLogs(): String? = thongTin?.let { tt ->
        buildString {
            appendLine("TV Model : ${tt.brand} ${tt.model} (${tt.tenHienThi})")
            appendLine("TV IP    : ${ip.ifBlank { "localhost" }}:$cong")
            appendLine("Android  : ${tt.android} (SDK ${tt.sdk})")
            appendLine("Home     : ${trangThai?.homeHienTai ?: "?"}")
        }.trim()
    }
    var dangBan by mutableStateOf(false)
    var thongBao by mutableStateOf("")
    var loi by mutableStateOf("")

    // Kết nối
    var ip by mutableStateOf("")
    var cong by mutableStateOf("5555")
    val timThay = mutableStateListOf<String>()
    var dangQuet by mutableStateOf(false)
    var ghepIpCong by mutableStateOf("")
    var ghepMa by mutableStateOf("")
    var dangTimGhep by mutableStateOf(false)
    val dsGhepTimThay = mutableStateListOf<String>()

    // Liên kết ngầm không cần ADB (CleanTxa Link cổng 2312)
    var linkClient by mutableStateOf<com.txa.cleantxa.link.TxaLinkClient?>(null)
        private set
    /** true khi đang giữ phiên ADB thật; false khi đang nối ngầm qua Wi-Fi Link (ADB trên TV có thể đã tắt hoàn toàn). */
    var dangAdb by mutableStateOf(false)
        private set
    val dangLienKetNgam: Boolean get() = linkClient != null
    val dsTvLink = mutableStateListOf<com.txa.cleantxa.link.TxaLinkClient.DiscoveredTv>()
    var dangQuetLink by mutableStateOf(false)
    /** Mã đối chiếu 4 số khi điện thoại đang chờ người dùng bấm "Tin tưởng" trên màn hình TV. */
    var maGhepLinkDangCho by mutableStateOf<String?>(null)
    var ramTv by mutableStateOf<Pair<Long, Long>?>(null)
    /** Trạng thái độc quyền của CleanTxa Link: âm lượng (hiện tại, tối đa), độ tối ban đêm (0..75%), đang chiếu ảnh, app đang mở. */
    var amLuongTv by mutableStateOf<Pair<Int, Int>?>(null)
    var doToiTv by mutableStateOf(0)
    var dangChieuAnhTv by mutableStateOf(false)
    var appDangMoTrenTv by mutableStateOf("")
    var linkCanBan by mutableStateOf("")
    var tinNhanTv by mutableStateOf("")
    var docToTinNhanTv by mutableStateOf(true)

    // Tivi
    var thongTin by mutableStateOf<ThongTinTv?>(null)
    /** Máy đích không khai là tivi (laTivi): chặn dọn / đổi Home cho tới khi người dùng xác nhận đúng là tivi. */
    /** Múi giờ tivi khác điện thoại (giờ trên Home sạch lệch), "" khi khớp. 3.0.1. */
    var muiGioLech by mutableStateOf("")
    /** Bộ nhớ tivi (byte trống, tổng byte), đọc lúc kết nối; null khi không đọc được. */
    var boNho by mutableStateOf<Pair<Long, Long>?>(null)
    var tiLeHieuUng by mutableStateOf<String?>(null)
    /** Lỗi đang hiện là do tivi hết bộ nhớ: thanh lỗi có nút "Dọn bộ nhớ đệm". */
    var canDonBoNho by mutableStateOf(false)
    val muiGioDienThoai: String get() = java.util.TimeZone.getDefault().id
    fun datGioNhuDienThoai() = chay(t("Đang đặt giờ cho tivi…", "Setting the TV's time zone…")) {
        val sau = io { tv.datMuiGio(muiGioDienThoai) }
        if (lechMuiGio(sau)) error(t("Tivi không nhận đổi múi giờ (đang $sau). Hãng có thể khoá phần này.", "The TV did not accept the time zone (still $sau). The maker may lock it."))
        muiGioLech = ""
        thongBao = t("Đã đặt giờ tivi theo $muiGioDienThoai.", "The TV now uses $muiGioDienThoai.")
    }
    /** Lệch thật khi giờ hiện tại của hai múi khác nhau (Asia/Saigon và Asia/Ho_Chi_Minh coi như khớp). */
    private fun lechMuiGio(tz: String): Boolean {
        if (tz.isBlank()) return false
        val now = System.currentTimeMillis()
        return java.util.TimeZone.getTimeZone(tz).getOffset(now) != java.util.TimeZone.getDefault().getOffset(now)
    }
    var khongPhaiTivi by mutableStateOf(false)
    var xacNhanLaTivi by mutableStateOf(false)
    val chanKhongPhaiTivi get() = khongPhaiTivi && !xacNhanLaTivi
    private var getprop = ""
    var trangThai by mutableStateOf<TrangThaiGoi?>(null)
    var ungVien by mutableStateOf<List<Pair<MauTv, Int>>>(emptyList())
    /** Mẫu cùng hãng tên máy gần giống (DuLieu.ganGiong): chỉ gợi ý, không tự chọn. */
    var ganGiong by mutableStateOf<List<MauTv>>(emptyList())
    /** Toàn bộ mẫu, cho hộp "Chọn mẫu khác". */
    var tatCaMau by mutableStateOf<List<MauTv>>(emptyList())
    var moChonMau by mutableStateOf(false)
    /** Màn Home sạch: thẻ đang mở (0 Giao diện, 1 Home, 2 Tuỳ chỉnh) và màn Nâng cao riêng. */
    var theHome by mutableStateOf(0)
    var moNangCao by mutableStateOf(false)
    var mauChon by mutableStateOf<MauTv?>(null)
    var saoLuu by mutableStateOf<BanSaoLuu?>(null)
    /** Bản sao lưu vừa lấy từ tivi (điện thoại này không có). */
    var saoLuuTuTv by mutableStateOf(false)

    // Chọn gói
    var muc by mutableStateOf<List<MucGoi>>(emptyList())
    val chon = mutableStateMapOf<String, Boolean>()
    var epSetupwraith by mutableStateOf(false)

    // Kết quả
    val nhatKy = mutableStateListOf<String>()
    var ketQua by mutableStateOf<ThaoTacTv.KetQuaTat?>(null)
    var anhTv by mutableStateOf<Bitmap?>(null)

    // Home sạch
    val dock = mutableStateListOf<String>()
    var tenChao by mutableStateOf("")
    var nutInput by mutableStateOf(false)
    var nutHome by mutableStateOf(false)
    var dnsChanQc by mutableStateOf(false)
    var nutManCho by mutableStateOf(true)
    /** Màn chờ động (3.5.0): màn chờ đồng hồ của Home sạch trên ảnh nền trôi chậm, đổi ảnh 20 giây (dream_motion). */
    var manChoDong by mutableStateOf(false)
    /** Đồng hồ động hiện sau bao nhiêu phút để yên ở Home sạch (home_idle_clock_minutes); 0 = chỉ màn chờ của tivi. */
    var phutManCho by mutableStateOf(10)
    var gonNut by mutableStateOf(true)
    /** Dời khối dock + hàng app lên (âm) / xuống (dương), dp — rows_offset_dp của bản vá launcher (góp ý 24/10/2026). */
    var doiHang by mutableStateOf(0)
    /** Nút "Đang phát" trên Home sạch (now_playing của bản vá launcher), mặc định bật. */
    var nutDangPhat by mutableStateOf(true)
    var dungTiengNen by mutableStateOf(true)
    /** Cỡ chữ cụm đồng hồ / ngày / âm lịch / thời tiết (header_scale của bản vá launcher, %), góp ý 24/10/2026. */
    var coChuDau by mutableStateOf(100)
    /** Mọi app trên một hàng cuộn ngang, không lưới bên dưới (single_row của launcher 2.8, cần dock_row). */
    var motHang by mutableStateOf(false)
    /** Ẩn lời chào trên đồng hồ (show_greeting=false). */
    var anLoiChao by mutableStateOf(false)
    /**
     * Giữ nút âm lượng để tăng/giảm liên tục (key_filter=false): dịch vụ bắt phím của Home sạch làm Android thôi tự lặp
     * phím giữ trên Sony, Xiaomi, Hisense (upstream #1680, #944, #1326; góp ý meorudo, Sony, 25/10/2026). Bật thì dịch vụ
     * vẫn chạy để mở đè Home cũ, chỉ thôi lọc phím: nút app / Input / Apps trên remote không còn do Home sạch lo.
     */
    var giuAmLuong by mutableStateOf(false)
    /** Ô icon đều nhau kiểu Apple TV: icon trên nền màu của chính nó, không dùng ảnh banner (uniform_tiles). */
    var oDeuNhau by mutableStateOf(false)
    /** Chọn theme trên màn: công tắc ô icon đều nhau theo theme mới, người dùng vẫn đổi lại được sau đó. */
    fun chonTheme(th: com.txa.cleantxa.dulieu.Theme) {
        themeChon = th
        oDeuNhau = th.cauHinh["uniform_tiles"] == true
        anLoiChao = th.cauHinh["show_greeting"] == false
        hieuUngKinh = th.cauHinh["dock_glass"] == true
        khungDau = th.cauHinh["header_panel"] == true
        mauChu = th.cauHinh["header_color"] as? String
        (th.cauHinh["accent_color"] as? String)?.let { txaMauNhan = it; luuTuyChonTxa() }
    }
    /** Số app mỗi hàng (columns của launcher, 0 = theo theme), góp ý 24/10/2026: Sony chỉ có 4 app một hàng. */
    var soCot by mutableStateOf(0)
    /** Cho chọn app tự mở khi bật tivi (open_on_start_choice của launcher; góp ý dmenh568, quan1sd1998 24/10/2026). */
    var moKhiBat by mutableStateOf(false)
    /** Mở app đã chọn cả khi bật tivi từ chế độ chờ, không chỉ khi khởi động lại (wake_open_app trong bản vá launcher, cần
     *  HomeKeyService). Góp ý dmenh568, quan1sd1998 25/10/2026. */
    var moKhiThuc by mutableStateOf(false)
    /** Chữ giờ / ngày trắng sáng hẳn (header_white của launcher). Góp ý tranhoangnt555 26/10/2026. */
    var chuTrang by mutableStateOf(false)
    /** Màu chữ đồng hồ / ngày / thời tiết "#RRGGBB" (header_color), null là mặc định. */
    var mauChu by mutableStateOf<String?>(null)
    /** Khung mờ sau đồng hồ, ngày, thời tiết (header_panel của bản vá), góp ý nhathoang5795 30/10/2026. */
    var khungDau by mutableStateOf(false)
    /** Bật tivi luôn về Home sạch (power_on_home_always của launcher, cần HomeKeyService). 3.0.0, góp ý caibangddd, huyenson91. */
    var luonVeHome by mutableStateOf(false)
    /** Nút hẹn giờ tắt tivi trên Home sạch (sleep_timer_button, cần HomeKeyService). 3.2.0, góp ý duongghoangg1103. */
    var nutHenTat by mutableStateOf(false)
    /** Nút Ngủ trên Home sạch (sleep_button, cần HomeKeyService, Android 9+). 4.1.2, góp ý hanhphucnoidau1999: máy chiếu remote
     *  chỉ có nút nguồn. Mặc định bật cho máy chiếu, tắt cho tivi; luôn ghi rõ vào config để lựa chọn không đổi theo máy. */
    var nutNgu by mutableStateOf(false)
    /** Nút Đa nhiệm trên Home sạch (recents_button, cần HomeKeyService). */
    var nutDaNhiem by mutableStateOf(false)
    /**
     * Nút remote người dùng tự gán (3.1.1, "Dò phím remote"; góp ý Hoàng Hữu: nút VTVgo của remote TCL 65C655 chết sau khi
     * tắt Home gốc). Mỗi dòng là một mục app_keys: "scan:<mã quét>:<gói>" hoặc "<keycode>:<gói>". Cần HomeKeyService lọc phím.
     */
    val phimGan = mutableStateListOf<String>()
    var dangDoPhim by mutableStateOf(false)
    /** Phím vừa dò được, chờ chọn app: (keycode, scancode). */
    var phimVuaDo by mutableStateOf<Pair<Int, Int>?>(null)
    fun doPhimRemote() = chay(t("Bấm nút muốn gán trên remote tivi (trong 20 giây)…", "Press the button you want on the TV remote (within 20 seconds)…")) {
        dangDoPhim = true
        try {
            io { tv.batBatPhimHome() }
            val p = io { tv.docPhimRemote(20) } ?: error(t("Không bắt được nút nào. Bấm lại nút trên remote ngay sau khi bấm Dò (remote phải đang điều khiển tivi này).",
                "No button was caught. Press the remote button right after tapping Detect (the remote must control this TV)."))
            // OK, mũi tên, Back không gán được: gán OK cho CH Play là kẹt luôn tivi (ngocdaikimoanh 31/10). Launcher cũng bỏ qua.
            if (p.first in PHIM_DIEU_HUONG) error(t("Nút OK, mũi tên và Back dùng để điều khiển tivi nên không gán cho app được. Bấm Dò rồi chọn nút khác.",
                "OK, the arrows and Back drive the TV, so they can't open an app. Tap Detect and pick another button."))
            // Scancode 240 là KEY_UNKNOWN của Linux: remote TCL gửi mọi nút app (YouTube, FPT Play…) cùng mã này, gán
            // theo nó thì một nút kéo theo cả loạt (hp4440s, TCL G08 01/11/2026). Khi đó chỉ dùng keycode.
            phimVuaDo = if (p.second == SCAN_KHONG_RO && p.first > 0) p.first to 0 else p
        } finally { dangDoPhim = false }
    }
    fun ganPhim(goi: String) {
        val (key, scan) = phimVuaDo ?: return
        val dau = if (scan > 0) "scan:$scan:" else "$key:"
        phimGan.removeAll { it.startsWith(dau) }
        phimGan.add(dau + goi)
        // "library" chỉ mở Thư viện app khi app_library bật (tắt thì phím chỉ đưa về Home): gán là bật luôn.
        if (goi == "library") thuVienApp = true
        phimVuaDo = null
    }
    /** App có biểu tượng trên tivi, để gán cho nút remote. */
    val appGanDuoc: List<String> get() = trangThai?.let { tt -> tt.coBieuTuong.filter { it in tt.caiDat && it !in tt.daTat }.sortedBy { com.txa.cleantxa.dulieu.tenDep(it).lowercase() } } ?: emptyList()

    /** HomeKeyService cần bật: phím Home lạ, phím app remote, hoặc mở app khi bật từ chế độ chờ. */
    val canDichVuPhim get() = nutHome || dungPhimRemote || (moKhiBat && moKhiThuc) || luonVeHome || phimGan.isNotEmpty() || nutHenTat || nutDaNhiem
    /**
     * Ô kênh đặt ở đầu dock, theo thứ tự chọn: VTVgo (3.0.0, mọi kênh), FPT Play và IPTV (3.1.0). Tối đa KENH_TOI_DA (link_tiles).
     */
    val kenhChon = mutableStateListOf<com.txa.cleantxa.dulieu.OKenh>()
    /** Danh sách kênh VTVgo (tải khi mở hộp chọn kênh); rỗng = chưa tải. */
    var dsKenhGo by mutableStateOf<List<com.txa.cleantxa.dulieu.KenhVtvGo.Kenh>>(emptyList())
    var moChonKenh by mutableStateOf(false)
    var dangTaiKenh by mutableStateOf(false)
    private fun uriVtvGo(ma: String) = "vtvgo://play?mediaType=CHANNEL&contentId=$ma"
    fun coKenh(ma: String) = kenhChon.any { it.goi == GOI_VTVGO && it.uri == uriVtvGo(ma) }
    fun batTatKenh(ten: String, ma: String) =
        batTatO(com.txa.cleantxa.dulieu.OKenh(ten, GOI_VTVGO, uriVtvGo(ma), KENH_VTV.firstOrNull { it.second.toString() == ma }?.third ?: "#C62828"))
    fun coO(uri: String) = kenhChon.any { it.uri == uri }
    /** Thêm / bỏ một ô (theo uri). Trùng tên ô (slug) thì không thêm, vì launcher đặt tên gói giả theo tên. */
    fun batTatO(o: com.txa.cleantxa.dulieu.OKenh): Boolean {
        val i = kenhChon.indexOfFirst { it.uri == o.uri }
        if (i >= 0) { kenhChon.removeAt(i); return true }
        if (o.slug.isEmpty() || kenhChon.any { it.slug == o.slug } || kenhChon.size >= KENH_TOI_DA) return false
        kenhChon.add(o)
        o.anh?.let { a -> ctx.getSharedPreferences("anh_o", android.content.Context.MODE_PRIVATE).edit().putString(o.uri, a).apply() }
        return true
    }
    private fun anhCuaO(o: com.txa.cleantxa.dulieu.OKenh) = o.anh ?: ctx.getSharedPreferences("anh_o", android.content.Context.MODE_PRIVATE).getString(o.uri, null)

    // ---- FPT Play (3.1.0) ----
    val coFpt get() = trangThai?.caiDat?.contains(com.txa.cleantxa.dulieu.FptPlay.GOI) == true
    var linkFpt by mutableStateOf("")
    fun batTatKenhFpt(ten: String, slug: String) = viewModelScope.launch {
        val url = com.txa.cleantxa.dulieu.FptPlay.linkKenh(slug)
        if (coO(url)) { kenhChon.removeAll { it.uri == url }; return@launch }
        val anh = io { com.txa.cleantxa.dulieu.FptPlay.docTrang(url) }?.second
        batTatO(com.txa.cleantxa.dulieu.OKenh(ten, com.txa.cleantxa.dulieu.FptPlay.GOI, url, "#E65100", anh))
    }
    fun themLinkFpt() = chay(t("Đang đọc link FPT Play…", "Reading the FPT Play link…")) {
        val url = com.txa.cleantxa.dulieu.FptPlay.chuanHoa(linkFpt)
            ?: error(t("Không phải link nội dung FPT Play. Mở kênh / trận đấu / phim trên app hoặc web FPT Play, bấm Chia sẻ → Sao chép link rồi dán vào.",
                "Not an FPT Play content link. Open a channel / match / film in FPT Play, tap Share → Copy link and paste it here."))
        val (ten, anh) = io { com.txa.cleantxa.dulieu.FptPlay.docTrang(url) }
            ?: error(t("FPT Play không có nội dung ở link này (đã kết thúc hoặc sai link).", "FPT Play has nothing at this link (ended or wrong)."))
        if (!batTatO(com.txa.cleantxa.dulieu.OKenh(ten, com.txa.cleantxa.dulieu.FptPlay.GOI, url, "#E65100", anh)))
            error(t("Không thêm được: đã đủ $KENH_TOI_DA ô hoặc trùng tên ô khác.", "Couldn't add: $KENH_TOI_DA tiles already, or the name clashes with another tile."))
        linkFpt = ""
    }

    // ---- TV360 (3.5.14) ----
    val coTv360 get() = trangThai?.caiDat?.contains(com.txa.cleantxa.dulieu.Tv360.GOI) == true
    fun batTatKenhTv360(k: com.txa.cleantxa.dulieu.Tv360.K) = viewModelScope.launch {
        val uri = com.txa.cleantxa.dulieu.Tv360.uri(k.id)
        if (coO(uri)) { kenhChon.removeAll { it.uri == uri }; return@launch }
        val anh = io { com.txa.cleantxa.dulieu.Tv360.anh(k) }
        if (!batTatO(com.txa.cleantxa.dulieu.OKenh(k.ten, com.txa.cleantxa.dulieu.Tv360.GOI, uri, "#D32F2F", anh)))
            loi = t("Không thêm được: đã đủ $KENH_TOI_DA ô hoặc trùng tên ô khác.", "Couldn't add: $KENH_TOI_DA tiles already, or the name clashes with another tile.")
    }

    // ---- IPTV (3.1.0) ----
    var nguonIptv by mutableStateOf("")
    var dsIptv by mutableStateOf<List<com.txa.cleantxa.dulieu.Iptv.Kenh>?>(null)
    /** Trình phát video có trên tivi, theo thứ tự ưu tiên của Iptv.TRINH_PHAT. */
    val trinhPhatCo get() = com.txa.cleantxa.dulieu.Iptv.TRINH_PHAT.filter { trangThai?.caiDat?.contains(it.first) == true }
    var trinhPhatChon by mutableStateOf<String?>(null)
    val trinhPhat get() = trinhPhatChon?.takeIf { p -> trinhPhatCo.any { it.first == p } } ?: trinhPhatCo.firstOrNull()?.first
    fun moIptv() = chay(t("Đang đọc danh sách IPTV…", "Reading the IPTV list…")) {
        val ds = io { runCatching { com.txa.cleantxa.dulieu.Iptv.doc(nguonIptv) }.getOrNull() }
            ?: error(t("Không tải được danh sách. Kiểm tra link (http/https) và mạng của điện thoại.", "Couldn't load the list. Check the link (http/https) and the phone's internet."))
        if (ds.isEmpty()) error(t("Không thấy kênh nào trong danh sách (cần định dạng m3u có #EXTINF và link luồng).", "No channels found (needs an m3u list with #EXTINF and stream links)."))
        dsIptv = ds
    }
    fun batTatIptv(k: com.txa.cleantxa.dulieu.Iptv.Kenh): Boolean {
        val p = trinhPhat ?: return false
        return batTatO(com.txa.cleantxa.dulieu.OKenh(com.txa.cleantxa.dulieu.OKenh.tenGon(k.ten), p, k.url, "#37474F", k.logo))
    }

    fun moHopKenh() {
        moChonKenh = true
        if (dsKenhGo.isNotEmpty() || dangTaiKenh) return
        dsKenhGo = com.txa.cleantxa.dulieu.KenhVtvGo.docNho(ctx)
        dangTaiKenh = true
        viewModelScope.launch {
            val moi = io { com.txa.cleantxa.dulieu.KenhVtvGo.tai(ctx) }
            if (moi.isNotEmpty()) dsKenhGo = moi
            dangTaiKenh = false
        }
    }
    val coVtvGo get() = trangThai?.caiDat?.contains(GOI_VTVGO) == true
    /**
     * Nút app / Cài đặt trên remote (24/10/2026, góp ý TCL G09): tắt Home gốc của TCL là nút YouTube, Netflix… chết vì
     * hãng chỉ gửi chúng cho launcherx. null = tự chọn: bật khi mẫu có bảng phím, trừ mẫu trong PHIM_TAT_SAN.
     */
    var nutAppRemoteChon by mutableStateOf<Boolean?>(null)
    val nutAppRemote get() = nutAppRemoteChon ?: (mauChon?.id !in PHIM_TAT_SAN && !laTclGoogleTv)

    /**
     * TCL Google TV (có Home Google launcherx): dịch vụ trợ năng nào lọc phím cũng làm giữ nút âm lượng chỉ nhảy một nấc
     * (upstream docs/devices/tcl-55p71b-g09-key-repeat.md; góp ý nda2192 24/10/2026). Mặc định ưu tiên âm lượng: tắt nút
     * app remote và ghi key_filter=false — dịch vụ vẫn bật để mở đè Home (theo cửa sổ, không cần lọc phím).
     */
    // Chỉ khi launcherx CÒN BẬT (đường mở đè): nút app remote tự chạy qua nó, thôi lọc phím không mất gì. Đã tắt
    // launcherx thì nút app chỉ chạy nhờ app_keys → giữ như 2.6.7 (heavensky95, TCL G10 đã tắt launcherx).
    val laTclGoogleTv get() = thongTin?.brand?.equals("TCL", ignoreCase = true) == true &&
        trangThai?.caiDat?.contains("com.google.android.apps.tv.launcherx") == true &&
        trangThai?.daTat?.contains("com.google.android.apps.tv.launcherx") != true

    /** Bảng phím remote dùng cho tivi này: của mẫu đang chọn, không có thì bảng chung của TCL; null nếu không có. */
    fun phimRemote(): JSONObject? = mauChon?.phim
        ?: if (thongTin?.brand?.equals("TCL", ignoreCase = true) == true) JSONObject(PHIM_TCL_CHUNG) else null

    /** Có ghép bảng phím vào cấu hình lần này không (cần HomeKeyService). */
    val dungPhimRemote get() = nutAppRemote && phimRemote() != null
    // Tuỳ chỉnh thêm (1.7, các "switch" của launcher — upstream themes/README.md).
    var amLich by mutableStateOf(true)
    /** Hàng nút Wi-Fi / Cài đặt sát mép trên thay vì canh theo đáy khối đồng hồ (top_buttons_top, 3.5.8, góp ý nguyenthanhsn88). */
    var nutTrenCung by mutableStateOf(false)
    var tenDuoiApp by mutableStateOf(false)
    /** Tên app to phía trên dock (dock_title, launcher mặc định bật); góp ý 22/10/2026 muốn tắt cho đỡ rối. */
    var tenToTrenDock by mutableStateOf(true)
    /** Ô app điện thoại (không có ảnh tivi) tô màu theo icon (launcher icon_tile_color, 23/10/2026). */
    var toMauOApp by mutableStateOf(true)
    /** Icon to trên ô app không có ảnh tivi (launcher icon_fill, góp ý duongle 28/10/2026). */
    var iconTo by mutableStateOf(false)
    var thuVienApp by mutableStateOf(false)
    /** Thư viện app mở từ menu Cài đặt của Home sạch thay vì nút trên thanh đầu (góp ý thangmath 28/10/2026). */
    var thuVienTrongMenu by mutableStateOf(false)
    /** Bấm nút Cài đặt mở Thư viện app, giữ vào cài đặt tivi, kiểu Projectivy (góp ý thangmath 02/11/2026). */
    var nutCaiDatThuVien by mutableStateOf(false)
    /** Home chỉ còn dock, app khác mở từ Thư viện (dock_only, góp ý ngocnd156 04/11/2026). */
    var chiHienDock by mutableStateOf(false)
    var dongHoKhiYen by mutableStateOf(0)
    var tiviYeu by mutableStateOf(false)
    var hieuUngKinh by mutableStateOf(false)
    var lamToiNen by mutableStateOf(0)
    var hienThongSo by mutableStateOf(false)
    /** Mục Nâng cao: danh sách tuỳ chọn + giá trị người dùng đặt (khoá → chữ; thiếu = mặc định). */
    val dsNangCao: List<com.txa.cleantxa.dulieu.TuyChon> by lazy { com.txa.cleantxa.dulieu.NangCao.nap(ctx) }
    val nangCao = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var homeXong by mutableStateOf(false)
    /** Lần thử nút Home thật gần nhất ra Home sạch (thuPhimHome). */
    var phimHomeOk by mutableStateOf(true)
    /** Kết quả bấm thử nút Home lần gần nhất, gửi kèm danh sách gói để chẩn đoán. */
    var phimHomeGhi = ""
    var themeChon by mutableStateOf(com.txa.cleantxa.dulieu.THEMES.first())

    // Máy chiếu: màn của hãng dò được + những màn đã ghim vào nút "Máy chiếu" trên Home sạch (comp → nhãn).
    var laMayChieu by mutableStateOf(false)
    /** Máy chiếu chưa có danh sách đúng mẫu: màn Chọn gói không tích sẵn gì và hiện lời cảnh báo (4.0.8). */
    var mayChieuKhongMau by mutableStateOf(false)
    var mayChieu by mutableStateOf<List<String>?>(null)
    val ghimMayChieu = mutableStateMapOf<String, String>()
    var dangDoMayChieu by mutableStateOf(false)

    fun doMayChieu() {
        if (dangDoMayChieu) return
        dangDoMayChieu = true; loi = ""
        viewModelScope.launch {
            try {
                val ds = io { tv.doMayChieu() }
                mayChieu = ds
                if (ds.isEmpty()) loi = t("Không thấy màn lấy nét / chỉnh hình nào của hãng. Mở Nhật ký (Logs) và gửi cho nhà phát triển xem giúp.",
                    "No focus / keystone screens found. Open the Logs and send them to the developer to take a look.")
            } catch (e: Throwable) { loi = moTaLoi(e) } finally { dangDoMayChieu = false }
        }
    }

    fun moThuMayChieu(comp: String) = chay(t("Đang mở trên máy chiếu…", "Opening on the projector…")) {
        io { tv.moThu(comp) }?.let { error(t("Máy chiếu không mở được màn này: $it", "The projector refused this screen: $it")) }
    }

    fun doiGhim(comp: String) {
        if (comp in ghimMayChieu) ghimMayChieu.remove(comp) else if (ghimMayChieu.size < 6) ghimMayChieu[comp] = nhanMayChieu(comp)
    }

    /** Đoán nhãn theo tên màn: focus → Lấy nét, keystone/trapez… → Chỉnh hình, zoom/scale → Thu phóng. */
    fun nhanMayChieu(comp: String): String {
        val c = comp.substringAfter('/').lowercase()
        return when {
            Regex("focus|duijiao").containsMatchIn(c) -> t("Lấy nét", "Focus")
            Regex("keystone|trapez|tixing|jiaozheng|correct|geometr|ladder").containsMatchIn(c) -> t("Chỉnh hình", "Keystone")
            Regex("zoom|scale|size").containsMatchIn(c) -> t("Thu phóng", "Zoom")
            Regex("calibr").containsMatchIn(c) -> t("Hiệu chỉnh", "Calibrate")
            else -> c.substringAfterLast('.').removeSuffix("activity").replaceFirstChar { it.uppercase() }
        }
    }

    /** Danh sách màn dò được, để dán vào hộp góp ý vgs.zone. */
    fun chuDanhSachMayChieu(): String = buildString {
        thongTin?.let { append("${it.tenHienThi} · ${it.device} · Android ${it.android}\n") }
        (mayChieu ?: emptyList()).forEach { append(it).append('\n') }
    }
    var thanhPho by mutableStateOf<com.txa.cleantxa.dulieu.ThanhPho?>(null)
    /** Ô tìm phường/xã cho thời tiết (góp ý 23/10/2026). */
    var timXa by mutableStateOf("")
        private set
    /**
     * Kết quả tìm phường/xã: lọc 3.321 xã sau khi ngừng gõ 0,35 giây, ở luồng phụ (25/10/2026, TCL chạy CleanTxa ngay
     * trên tivi: lọc lại mỗi lần vẽ màn hình làm ô gõ giật, không chọn được).
     */
    var ketQuaXa by mutableStateOf<List<com.txa.cleantxa.dulieu.XaPhuong.Xa>>(emptyList())
        private set
    private var viecTimXa: kotlinx.coroutines.Job? = null
    /** Dòng báo dưới ô tìm: "Đang tìm…", không thấy, hay lỗi mạng (3.5.3, trước đây lỗi chỉ để trống). Rỗng là không báo. */
    var baoTimXa by mutableStateOf("")
        private set
    /** Mở thẳng màn chọn app "Mở khi khởi động" trên tivi (25/10/2026: dmenh568 không tìm thấy chỗ chọn trên Xiaomi). */
    var baoChonApp by mutableStateOf("")
    fun moChonAppKhiBat() = chay(t("Đang mở trên tivi…", "Opening on the TV…")) {
        io { tv.moCaiDatLauncher() }
        baoChonApp = t("Đã mở Cài đặt launcher trên tivi: chọn \"Mở khi khởi động\" rồi chọn app.", "Launcher settings are open on the TV: pick \"Open on start\", then the app.")
    }

    fun doiTimXa(q: String) {
        timXa = q
        viecTimXa?.cancel()
        baoTimXa = ""
        if (q.trim().length < 2) { ketQuaXa = emptyList(); return }
        viecTimXa = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            val vn = withContext(Dispatchers.Default) { com.txa.cleantxa.dulieu.XaPhuong.timXa(com.txa.cleantxa.dulieu.XaPhuong.nap(ctx), q) }
            ketQuaXa = vn
            // Ít kết quả trong nước thì tìm thêm thành phố khắp thế giới (người dùng ở Bali, Osaka…).
            if (vn.size < 3 && q.trim().length >= 3) {
                baoTimXa = t("Đang tìm thêm thành phố trên thế giới…", "Searching cities worldwide…")
                val vi = com.txa.cleantxa.NgonNgu.tiengViet
                // Mạng chậm: thử lại một lần, chờ lâu hơn.
                val theGioi = withContext(Dispatchers.IO) {
                    com.txa.cleantxa.dulieu.XaPhuong.timTheGioi(q, vi) ?: com.txa.cleantxa.dulieu.XaPhuong.timTheGioi(q, vi, choMs = 12000)
                }
                if (timXa != q) return@launch
                ketQuaXa = vn + (theGioi ?: emptyList()).take(8 - vn.size)
                baoTimXa = when {
                    theGioi == null -> t("Không tìm được thành phố ngoài Việt Nam: điện thoại chưa vào được dịch vụ tìm địa danh (mạng, VPN hoặc chặn quảng cáo?). Thử đổi Wi-Fi/4G rồi gõ lại.",
                        "Couldn't search cities outside Vietnam: the phone can't reach the place search (network, VPN or ad blocker?). Try another Wi-Fi or mobile data, then type again.")
                    ketQuaXa.isEmpty() -> t("Không thấy nơi nào tên như vậy. Thử gõ tên tiếng Anh, không dấu.", "No place found with that name. Try the English spelling.")
                    else -> ""
                }
            } else if (vn.isEmpty()) baoTimXa = t("Không thấy phường/xã nào như vậy.", "No ward found with that name.")
        }
    }
    fun chonXa(x: com.txa.cleantxa.dulieu.XaPhuong.Xa) { thanhPho = com.txa.cleantxa.dulieu.ThanhPho(x.tenNgan, x.lat, x.lon); doiTimXa("") }

    /** Tivi đã có Home sạch: chỉ cần đẩy theme, không cài lại. */
    /** Tên gói Home sạch trên tivi đang nối (Xiaomi bản Trung Quốc: com.ts.launcher), dò lúc kết nối. */
    private var goiHome = ThaoTacTv.PKG_HOME
    val daCoHomeSach: Boolean get() = trangThai?.let { tt -> goiHome in tt.caiDat && goiHome !in tt.daTat } == true

    var dsSaoLuu by mutableStateOf<List<BanSaoLuu>>(emptyList())

    var banMoi by mutableStateOf<com.txa.cleantxa.CapNhat.BanMoi?>(null)
    var dangKiemTraCapNhat by mutableStateOf(false)
    var dangCapNhat by mutableStateOf(false)
    var hoiCapNhat by mutableStateOf(false)
    var tienTrinhCapNhat by mutableStateOf<com.txa.cleantxa.CapNhat.TaiTienTrinh?>(null)
    var apkDaTai by mutableStateOf<java.io.File?>(null)
    var loiCapNhat by mutableStateOf("")
    var buocCaiDat by mutableStateOf(BuocCaiDat.CHUA_BAT_DAU)

    init {
        com.txa.cleantxa.TxaLogger.init(ctx)
        ip = prefs.getString("ip", "") ?: ""
        cong = prefs.getString("cong", "5555") ?: "5555"
        taiLaiSaoLuu()
        if (com.txa.cleantxa.ManHinh.laTv && com.txa.cleantxa.TxaLogger.hasPendingTvError(ctx)) {
            man = Man.TV_LOI_QR
        }
        // Điện thoại mở app: tự động quét cùng mạng Wi-Fi và kết nối ngầm với TV đã tin cậy (kể cả khi TV đã tắt ADB).
        if (!com.txa.cleantxa.ManHinh.laTv) {
            quetLinkNgam(tuKetNoi = com.txa.cleantxa.link.TxaLinkStore.tuKetNoiNgam(ctx))
        }
        viewModelScope.launch {
            runCatching { io { com.txa.cleantxa.CapNhat.dongBoTuCleanYourTv(ctx) } }
        }
        viewModelScope.launch {
            val b = runCatching { io { com.txa.cleantxa.CapNhat.kiemTra(ctx) } }.getOrNull()
            banMoi = b
            if (b != null) {
                com.txa.cleantxa.CapNhat.fileApkDaTai(ctx, b)?.let { cached ->
                    apkDaTai = cached
                    buocCaiDat = BuocCaiDat.SAN_SANG_CAI
                }
                xuLyBanMoi(b)
            }
        }
        // Đang mở app thì cũng kiểm tra định kỳ, không đợi lần mở sau. Mỗi bản chỉ hỏi một lần (xuLyBanMoi nhớ da_hoi).
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(com.txa.cleantxa.CapNhat.CHU_KY_KIEM_TRA_PHUT * 60_000L)
                if (dangCapNhat || dangKiemTraCapNhat) continue
                val b = runCatching { io { com.txa.cleantxa.CapNhat.kiemTra(ctx) } }.getOrNull() ?: continue
                if (banMoi?.versionCode == b.versionCode) continue
                banMoi = b
                com.txa.cleantxa.CapNhat.fileApkDaTai(ctx, b)?.let { cached ->
                    apkDaTai = cached
                    buocCaiDat = BuocCaiDat.SAN_SANG_CAI
                }
                xuLyBanMoi(b)
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(400)
                if (!com.txa.cleantxa.CapNhat.moTuThongBao) continue
                val b = banMoi ?: runCatching { io { com.txa.cleantxa.CapNhat.kiemTraApp() } }.getOrNull()?.also { banMoi = it }
                if (b != null) xuLyBanMoi(b) else com.txa.cleantxa.CapNhat.moTuThongBao = false
            }
        }
        // Nhận nội dung "Chia sẻ → CleanTxa" từ YouTube / Trình duyệt / Bộ sưu tập ảnh trên điện thoại và bắn thẳng lên TV!
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(350)
                val sharedTxt = com.txa.cleantxa.link.TxaLinkStore.pendingSharedText
                val sharedImg = com.txa.cleantxa.link.TxaLinkStore.pendingSharedImageUri
                if (sharedTxt == null && sharedImg == null) continue
                com.txa.cleantxa.link.TxaLinkStore.pendingSharedText = null
                com.txa.cleantxa.link.TxaLinkStore.pendingSharedImageUri = null
                xuLyChiaSeTuAppKhac(sharedTxt, sharedImg)
            }
        }
    }

    private fun xuLyBanMoi(b: com.txa.cleantxa.CapNhat.BanMoi) {
        val p = ctx.getSharedPreferences("ota", Context.MODE_PRIVATE)
        if (com.txa.cleantxa.CapNhat.moTuThongBao) {
            com.txa.cleantxa.CapNhat.moTuThongBao = false
            hoiCapNhat = true
            capNhatApp()
        } else if (p.getInt("da_hoi", 0) < b.versionCode) {
            p.edit().putInt("da_hoi", b.versionCode).putInt("da_bao", maxOf(p.getInt("da_bao", 0), b.versionCode)).apply()
            hoiCapNhat = true
        }
    }

    fun kiemTraCapNhatThuCong() {
        if (dangKiemTraCapNhat || dangCapNhat) return
        dangKiemTraCapNhat = true
        viewModelScope.launch {
            try {
                val kq = runCatching { io { com.txa.cleantxa.CapNhat.kiemTraApp() } }
                val loiKiemTra = kq.exceptionOrNull()
                val b = kq.getOrNull()
                if (loiKiemTra != null) {
                    // Kiểm tra thất bại không có nghĩa là đang ở bản mới nhất: báo rõ, kèm nguyên nhân dễ hiểu.
                    com.txa.cleantxa.TxaLogger.w("CapNhat", "Manual update check failed", loiKiemTra)
                    val gioiHan = loiKiemTra.message?.contains(Regex("HTTP (403|429)")) == true
                    android.widget.Toast.makeText(
                        ctx,
                        if (gioiHan) t("GitHub tạm giới hạn số lần kiểm tra từ mạng của bạn. Thử lại sau vài phút hoặc đổi sang dữ liệu di động.",
                            "GitHub is temporarily limiting checks from your network. Try again in a few minutes or switch to mobile data.")
                        else t("Không kiểm tra được bản mới. Kiểm tra kết nối mạng rồi thử lại.", "Couldn't check for a new version. Check your connection and try again."),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                banMoi = b
                if (b != null) {
                    com.txa.cleantxa.CapNhat.fileApkDaTai(ctx, b)?.let { cached ->
                        apkDaTai = cached
                        buocCaiDat = BuocCaiDat.SAN_SANG_CAI
                    }
                    hoiCapNhat = true
                } else {
                    android.widget.Toast.makeText(
                        ctx,
                        t(
                            "Bạn đang dùng bản mới nhất (v${com.txa.cleantxa.BuildConfig.VERSION_NAME}).",
                            "You're on the latest version (v${com.txa.cleantxa.BuildConfig.VERSION_NAME}).",
                        ),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                dangKiemTraCapNhat = false
            }
        }
    }

    private suspend fun coQuyenCaiApk(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()) {
            return true
        }
        if (com.txa.cleantxa.ManHinh.laTv && thongTin != null) {
            runCatching { io { tv.choPhepCaiApp(ctx.packageName) } }
        }
        if (ctx.packageManager.canRequestPackageInstalls()) return true
        val moDuoc = runCatching {
            ctx.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + ctx.packageName),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        loiCapNhat = if (moDuoc) {
            t("Cho phép CleanTxa cài ứng dụng, rồi quay lại bấm Thử lại / Cập nhật.", "Allow CleanTxa to install apps, then come back and press Update.")
        } else {
            t("Tivi chưa cho CleanTxa cài bản mới. Vào Cài đặt → Ứng dụng → CleanTxa (hoặc Bảo mật → Nguồn không xác định), bật cho phép cài rồi thử lại.", "This device hasn't allowed CleanTxa to install updates yet. Enable Unknown sources for CleanTxa in Settings and retry.")
        }
        return false
    }

    fun capNhatApp() {
        val b = banMoi ?: return
        if (dangCapNhat) return
        hoiCapNhat = true
        loiCapNhat = ""
        val cached = com.txa.cleantxa.CapNhat.fileApkDaTai(ctx, b)
        if (cached != null) {
            apkDaTai = cached
            buocCaiDat = BuocCaiDat.SAN_SANG_CAI
            viewModelScope.launch {
                if (!coQuyenCaiApk()) return@launch
                buocCaiDat = BuocCaiDat.CHO_PLAY_PROTECT
                runCatching { com.txa.cleantxa.CapNhat.moCaiApk(ctx, cached) }
                    .onFailure {
                        buocCaiDat = BuocCaiDat.SAN_SANG_CAI
                        loiCapNhat = t("Không mở được trình cài đặt: ${it.message}", "Could not open installer: ${it.message}")
                    }
            }
            return
        }
        dangCapNhat = true
        buocCaiDat = BuocCaiDat.DANG_TAI
        tienTrinhCapNhat = com.txa.cleantxa.CapNhat.TaiTienTrinh(0L, b.apkSizeBytes, 0L)
        viewModelScope.launch {
            try {
                if (!coQuyenCaiApk()) {
                    buocCaiDat = BuocCaiDat.CHUA_BAT_DAU
                    return@launch
                }
                val file = io {
                    com.txa.cleantxa.CapNhat.taiApk(ctx, b) { prog ->
                        tienTrinhCapNhat = prog
                    }
                }
                apkDaTai = file
                buocCaiDat = BuocCaiDat.CHO_PLAY_PROTECT
                com.txa.cleantxa.CapNhat.moCaiApk(ctx, file)
            } catch (e: Throwable) {
                buocCaiDat = BuocCaiDat.CHUA_BAT_DAU
                loiCapNhat = t("Không tải được bản cập nhật: ${e.message}", "Update download failed: ${e.message}")
            } finally {
                dangCapNhat = false
            }
        }
    }

    fun caiApkDaTai() {
        val f = apkDaTai ?: return
        buocCaiDat = BuocCaiDat.CHO_PLAY_PROTECT
        runCatching { com.txa.cleantxa.CapNhat.moCaiApk(ctx, f) }
            .onFailure {
                buocCaiDat = BuocCaiDat.SAN_SANG_CAI
                loiCapNhat = t("Không mở được trình cài đặt: ${it.message}", "Could not open installer: ${it.message}")
            }
    }

    fun taiLaiSaoLuu() { dsSaoLuu = kho.tatCa() }

    private fun chay(viec: String, khoi: suspend () -> Unit) {
        if (dangBan) return
        dangBan = true; loi = ""; canDonBoNho = false; thongBao = viec
        viewModelScope.launch {
            com.txa.cleantxa.TxaLogger.i("Action", viec)
            try { khoi() } catch (e: Throwable) {
                com.txa.cleantxa.TxaLogger.e("Action", viec, e)
                val msg = moTaLoi(e)
                if (com.txa.cleantxa.ManHinh.laTv) {
                    com.txa.cleantxa.TxaLogger.markPendingTvError(ctx, "$viec: $msg")
                    loi = ""
                    man = Man.TV_LOI_QR
                } else {
                    loi = msg
                }
            } finally { dangBan = false; thongBao = "" }
        }
    }

    private fun laLoiTuChoi(e: Throwable) = listOfNotNull(e.message, e.cause?.message).any { it.contains("ECONNREFUSED") || it.contains("refused") }

    private fun moTaLoi(e: Throwable): String = when {
        e is ThaoTacTv.TiviKhoiDongLaiException ->
            t("Tivi vừa tự khởi động lại ngay sau khi tắt ${e.goi}. App đã dừng dọn để an toàn (${e.daTat} gói đã tắt, đều có trong sao lưu). Chờ tivi lên lại, kết nối lại: tivi chạy bình thường thì dọn tiếp và bỏ tích ${e.goi}; có gì lạ thì bấm Khôi phục tivi này. Gửi góp ý kèm model tivi cho nhà phát triển để ghi gói này vào danh sách giữ.",
                "The TV restarted right after ${e.goi} was disabled. The app stopped cleaning to be safe (${e.daTat} packages disabled, all in the backup). Wait for the TV, reconnect: if it works, clean again without ${e.goi}; if anything is wrong, press Restore this TV. Send feedback with the TV model so the developer can keep this package.")
        e is ThaoTacTv.MatKetNoiTvException ->
            t("Mất kết nối với tivi giữa lúc dọn và không nối lại được (${e.daTat} gói đã tắt, đều có trong sao lưu). Kiểm tra tivi còn bật và cùng Wi-Fi, rồi kết nối lại để dọn tiếp hoặc bấm Khôi phục tivi này. Không nối được: tắt tivi ở công tắc nguồn 10 giây rồi bật lại.",
                "Lost the connection to the TV while cleaning and couldn't reconnect (${e.daTat} packages disabled, all in the backup). Check the TV is on and on the same Wi-Fi, then reconnect to continue or press Restore this TV. Still nothing: switch the TV off at the wall for 10 seconds.")
        ThaoTacTv.laMatKetNoi(e) && !laLoiTuChoi(e) ->
            t("Mất kết nối với tivi. Những gì đã làm đều có trong sao lưu: kết nối lại để làm tiếp hoặc bấm Khôi phục tivi này.",
                "Lost the connection to the TV. Everything done so far is in the backup: reconnect to continue or press Restore this TV.")
        e is AdbPairingRequiredException -> t("Tivi đòi ghép đôi (Gỡ lỗi không dây). Dùng phần \"Ghép đôi bằng mã\" bên dưới.", "The TV needs pairing (Wireless debugging). Use \"Pair with code\" below.")
        // Chế độ chạy trên tivi: nhiều tivi (TCL…) bật Gỡ lỗi USB chỉ mở ADB qua dây, cổng 5555 chưa nghe (22/10/2026).
        laLoiTuChoi(e) && ip.trim() == "127.0.0.1" ->
            t("Tivi chưa mở ADB qua mạng (cổng 5555), nên app trên tivi chưa tự nối vào được. Cách sửa: trong Tuỳ chọn nhà phát triển bật thêm \"Gỡ lỗi qua mạng\" / \"ADB qua mạng\" (nếu có) rồi Kết nối lại. Tivi chỉ có \"Gỡ lỗi không dây\" thì dùng CleanTxa trên điện thoại, chọn Ghép bằng mã. Hoặc cắm tivi vào máy tính, chạy adb tcpip 5555 một lần.",
                "The TV hasn't opened ADB over the network (port 5555), so the app on the TV can't connect to itself. Fix: in Developer options also turn on \"Network debugging\" / \"ADB over network\" (if there) and Connect again. If the TV only has \"Wireless debugging\", use CleanTxa on a phone with Pair with code. Or plug the TV into a computer and run adb tcpip 5555 once.")
        laLoiTuChoi(e) ->
            t("Tivi từ chối kết nối ở cổng này. Kiểm tra đã bật Gỡ lỗi USB / Gỡ lỗi qua mạng chưa.", "The TV refused the connection on this port. Check USB debugging / Network debugging is on.")
        e.message?.contains("EHOSTUNREACH") == true || e.message?.contains("No route") == true ->
            t("Không tới được tivi. Điện thoại và tivi phải chung một mạng Wi-Fi.", "Cannot reach the TV. The phone and TV must be on the same Wi-Fi.")
        e is java.net.SocketTimeoutException || e.message?.contains("timed out") == true ->
            t("Hết thời gian chờ. Có thể tivi đang hiện hộp \"Cho phép gỡ lỗi?\", bấm Cho phép rồi thử lại.", "Timed out. The TV may be showing \"Allow debugging?\" — press Allow and try again.")
        // Dòng thô của Android như "cmd: Can't find service: package": tivi đang khởi động / dịch vụ gói chưa chạy.
        ThaoTacTv.laLoiDichVuGoi(listOfNotNull(e.message, e.cause?.message).joinToString(" ")) -> tv.baoGoiChuaChay()
        // Câu báo lỗi do chính app viết (error("…") và require của app, như "Mã PIN phải đúng 4 chữ số."): hiện nguyên văn,
        // không gói thành "lỗi bất ngờ". Chỉ khớp đúng IllegalStateException trơn để lỗi lạ của thư viện vẫn đi đường chung.
        e.javaClass == IllegalStateException::class.java && !e.message.isNullOrBlank() -> e.message!!
        // Dữ liệu mẫu tivi hỏng (vd. "No value for p"): lỗi của app chứ không phải của tivi hay mạng.
        e is org.json.JSONException ->
            t("Tivi đã kết nối, nhưng app không đọc được dữ liệu mẫu tivi (file dữ liệu bị lỗi). Đây không phải lỗi của bạn hay của tivi. App đã bỏ file lỗi, hãy bấm Kết nối lại. Vẫn báo thì mở Nhật ký và gửi cho nhà phát triển.${chiTietLoi(e)}",
                "The TV is connected, but the app couldn't read its TV model data (the data file is damaged). It isn't your fault or the TV's. The app dropped the bad file; press Connect again. If it still fails, open the Logs and send them to the developer.${chiTietLoi(e)}")
        // libadb báo "Connection failed" khi tivi không trả lời / không cho phép / chặn ADB.
        e.message?.contains("Connection failed") == true ->
            t("Không kết nối được tới tivi. Kiểm tra: tivi đang bật, đã bật Gỡ lỗi USB / Gỡ lỗi qua mạng, điện thoại cùng Wi-Fi với tivi, IP và cổng đúng. Nếu tivi hiện hộp \"Cho phép gỡ lỗi USB?\" thì chọn Cho phép rồi thử lại.${chiTietLoi(e)}",
                "Couldn't connect to the TV. Check that the TV is on, USB / Network debugging is enabled, the phone is on the same Wi-Fi, and the IP and port are right. If the TV shows \"Allow USB debugging?\", choose Allow and try again.${chiTietLoi(e)}")
        e is java.io.FileNotFoundException || e is java.io.IOException && e.message?.contains("No space") == true ->
            t("Điện thoại hoặc tivi không còn đủ chỗ trống hoặc không ghi được file. Giải phóng bộ nhớ rồi thử lại.${chiTietLoi(e)}",
                "The phone or TV is out of space or a file couldn't be written. Free up some storage and try again.${chiTietLoi(e)}")
        else ->
            t("Có lỗi bất ngờ khi làm việc với tivi. Thử lại một lần; vẫn lỗi thì mở Nhật ký và gửi cho nhà phát triển kèm hãng, model tivi.${chiTietLoi(e)}",
                "Something unexpected went wrong while working with the TV. Try once more; if it keeps happening, open the Logs and send them to the developer with the TV brand and model.${chiTietLoi(e)}")
    }

    /** Dòng chi tiết kỹ thuật ngắn đặt cuối thông báo lỗi, để người hỗ trợ còn đọc được nguyên nhân thật. */
    private fun chiTietLoi(e: Throwable): String {
        val goc = listOfNotNull(e.message, e.cause?.message).firstOrNull()?.lineSequence()?.firstOrNull()?.trim()?.take(120)
            ?: e.javaClass.simpleName
        return "\n(" + t("Chi tiết kỹ thuật", "Technical detail") + ": $goc)"
    }

    private suspend fun <T> io(khoi: () -> T): T = withContext(Dispatchers.IO) { khoi() }

    // ---------- Kết nối & Liên kết ngầm (CleanTxa Link) ----------

    /** Quét tìm các TV đang chạy ngầm CleanTxa trên cùng mạng Wi-Fi; tự kết nối ngầm nếu đã tin cậy đôi bên. */
    fun quetLinkNgam(tuKetNoi: Boolean = true) {
        if (dangQuetLink || com.txa.cleantxa.ManHinh.laTv) return
        dangQuetLink = true
        viewModelScope.launch {
            try {
                val ds = com.txa.cleantxa.link.TxaLinkClient.discoverLan(ctx, ipDienThoai())
                dsTvLink.clear()
                dsTvLink.addAll(ds)
                if (tuKetNoi && thongTin == null && !dangBan) {
                    val tinCay = ds.firstOrNull { it.isTrusted }
                    if (tinCay != null) {
                        runCatching { napQuaLink(tinCay.ip, tinCay.port, tinCay.trustedToken!!, tinCay.tvId, chuyenMan = false) }
                    }
                }
            } finally {
                dangQuetLink = false
            }
        }
    }

    /** Bấm kết nối tới một TV phát hiện qua CleanTxa Link: đã tin cậy thì vào thẳng, chưa thì gửi yêu cầu mã 4 số lên TV. */
    fun chonTvLink(d: com.txa.cleantxa.link.TxaLinkClient.DiscoveredTv, chuyenMan: Boolean = true) {
        val tok = d.trustedToken ?: com.txa.cleantxa.link.TxaLinkStore.timTvTinCay(ctx, d.tvId)?.token
        if (!tok.isNullOrBlank()) {
            chay(t("Đang kết nối ngầm tới ${d.name}…", "Linking to ${d.name}…")) {
                napQuaLink(d.ip, d.port, tok, d.tvId, chuyenMan = chuyenMan)
            }
            return
        }
        val pin = (1000..9999).random().toString()
        maGhepLinkDangCho = pin
        chay(t("Nhìn lên màn hình TV (${d.name}) và bấm \"Tin tưởng\" · Mã $pin…", "Look at the TV (${d.name}) and press \"Trust\" · Code $pin…")) {
            try {
                val trusted = com.txa.cleantxa.link.TxaLinkClient.pairWithTv(ctx, d, pin)
                val idx = dsTvLink.indexOfFirst { it.tvId == d.tvId }
                if (idx >= 0) dsTvLink[idx] = dsTvLink[idx].copy(trustedToken = trusted.token)
                napQuaLink(trusted.lastIp, trusted.port, trusted.token, trusted.tvId, chuyenMan = true)
            } finally {
                maGhepLinkDangCho = null
            }
        }
    }

    private suspend fun napQuaLink(host: String, port: Int, token: String, tvId: String, chuyenMan: Boolean) {
        val client = com.txa.cleantxa.link.TxaLinkClient(host, port, token, tvId)
        val st = io { client.docTrangThaiDayDu() }
        linkClient = client
        dangAdb = false
        daNapCu = false
        ip = host
        prefs.edit().putString("ip", host).apply()
        goiHome = st.pkgHome
        thongTin = st.info
        trangThai = st.trangThai
        boNho = st.boNho
        ramTv = st.ram
        amLuongTv = st.volCur to st.volMax
        doToiTv = st.dimPercent
        dangChieuAnhTv = st.photoShowing
        appDangMoTrenTv = st.activePkg
        henTatLuc = st.sleepTimerSec
        appTuCai = st.appTuCai
        goCoDau = st.hasA11y || daCoHomeSach
        khongPhaiTivi = false
        xacNhanLaTivi = true
        val du = io { DuLieu.nap(ctx) }
        ungVien = du.khopMau(st.info)
        mauChon = ungVien.firstOrNull()?.first
        ganGiong = du.ganGiong(st.info, ungVien.map { it.first.id }.toSet())
        tatCaMau = du.mau.sortedBy { it.ten.lowercase() }
        saoLuu = kho.doc(st.info.khoaLuu)
        laMayChieu = tv.coVeMayChieu(st.trangThai, st.info)
        nutNgu = laMayChieu
        chuanBiDock()
        if (chuyenMan) man = Man.THONG_TIN
    }

    /** Quét cả mạng /24 của điện thoại tìm cả TV chạy ngầm CleanTxa Link (2312) lẫn cổng ADB (5555). */
    fun quetMang() {
        if (dangQuet) return
        dangQuet = true; timThay.clear(); loi = ""
        quetLinkNgam(tuKetNoi = false)
        viewModelScope.launch {
            try {
                val goc = ipDienThoai() ?: run { loi = t("Điện thoại chưa vào Wi-Fi.", "The phone is not on Wi-Fi."); return@launch }
                val dau = goc.substringBeforeLast('.')
                val sem = Semaphore(48)
                (1..254).map { i ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val dc = "$dau.$i"
                            if (dc != goc && moCong(dc, 5555, 400)) withContext(Dispatchers.Main) { timThay += dc }
                        }
                    }
                }.awaitAll()
                if (timThay.isEmpty() && dsTvLink.isEmpty()) {
                    loi = t("Không thấy tivi nào mở cổng 5555 hay chạy ngầm CleanTxa. Google TV đời mới chỉ bật ADB thì dùng phần ghép đôi 6 số.", "No TV with port 5555 or CleanTxa Link found. For newer Google TVs, use 6-digit Wireless debugging pairing.")
                }
            } finally { dangQuet = false }
        }
    }

    /** Các cổng TCP đang mở trên [host] trong [dai], quét song song (256 luồng, chờ 300 ms mỗi cổng). */
    private suspend fun quetCongMo(host: String, dai: IntRange, boQua: Set<Int> = emptySet()): List<Int> = withContext(Dispatchers.IO) {
        val sem = Semaphore(256)
        dai.filter { it !in boQua }.map { c -> async { sem.withPermit { if (moCong(host, c, 300)) c else null } } }.awaitAll().filterNotNull()
    }

    private fun moCong(host: String, port: Int, ms: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), ms) }; true
    }.getOrDefault(false)

    private fun ipDienThoai(): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }

    fun ketNoi() = chay(t("Đang kết nối… Tivi hiện \"Cho phép gỡ lỗi USB?\" thì tích \"Luôn cho phép\" rồi bấm Cho phép.", "Connecting… If the TV shows \"Allow USB debugging?\" tick \"Always allow\" and press Allow.")) {
        val host = ip.trim(); val port = cong.trim().toIntOrNull() ?: 5555
        daTuTatAdb = false
        if (host.isEmpty()) error(t("Nhập địa chỉ IP của tivi.", "Enter the TV's IP address."))
        io {
            runCatching { adb.disconnect() }
            if (!adb.connect(host, port)) error(t("Kết nối không thành công. Thử lại và để ý hộp xác nhận trên tivi.", "Connection failed. Try again and watch for the prompt on the TV."))
        }
        prefs.edit().putString("ip", host).putString("cong", port.toString()).apply()
        sauKhiKetNoi(host)
    }

    /** Chế độ tivi: kết nối ADB vào chính máy này. */
    fun ketNoiTiviNay() { ip = "127.0.0.1"; cong = "5555"; ketNoi() }

    /** Chế độ tivi: các bước bấm Home / khởi động lại Home đẩy app xuống nền — mở lại app cho người dùng thấy kết quả. */
    private suspend fun veApp() {
        if (com.txa.cleantxa.ManHinh.laTv) io { runCatching { adb.shell("am start -n ${ctx.packageName}/com.txa.cleantxa.MainActivity") } }
    }

    /** Tự tìm IP & cổng ghép đôi Gỡ lỗi không dây (_adb-tls-pairing._tcp.) qua mDNS. */
    fun tuTimGhepDoi() {
        if (dangTimGhep) return
        dangTimGhep = true
        dsGhepTimThay.clear()
        loi = ""
        viewModelScope.launch {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = runCatching { wifi?.createMulticastLock("cleantxa_pair")?.apply { acquire() } }.getOrNull()
            val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsd == null) {
                runCatching { lock?.release() }
                dangTimGhep = false
                return@launch
            }
            var firstFoundAt = 0L
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    runCatching {
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                @Suppress("DEPRECATION")
                                val host = resolved.host?.hostAddress ?: return
                                val port = resolved.port
                                if (port <= 0) return
                                val addr = "$host:$port"
                                viewModelScope.launch(Dispatchers.Main) {
                                    if (addr !in dsGhepTimThay) dsGhepTimThay += addr
                                    if (ghepIpCong.isBlank()) ghepIpCong = addr
                                    if (firstFoundAt == 0L) firstFoundAt = System.currentTimeMillis()
                                }
                            }
                        })
                    }
                }
            }
            try {
                runCatching {
                    nsd.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                }
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 10_000L) {
                    kotlinx.coroutines.delay(250L)
                    if (firstFoundAt > 0L && System.currentTimeMillis() - firstFoundAt >= 800L) break
                }
            } finally {
                runCatching { nsd.stopServiceDiscovery(discoveryListener) }
                runCatching { lock?.release() }
                dangTimGhep = false
            }
        }
    }

    /** Ghép đôi Gỡ lỗi không dây (Android 11+), rồi tìm cổng kết nối qua mDNS. */
    fun ghepDoi() = chay(t("Đang ghép đôi…", "Pairing…")) {
        if (ghepIpCong.isBlank() && dsGhepTimThay.isNotEmpty()) ghepIpCong = dsGhepTimThay.first()
        val (h, p) = tachIpCong(ghepIpCong) ?: error(t("Nhập dạng 192.168.1.20:37123 (IP và cổng trong hộp ghép đôi).", "Enter it as 192.168.1.20:37123 (IP and port from the pairing dialog)."))
        val ma = ghepMa.trim()
        if (ma.length != 6) error(t("Mã ghép đôi có 6 số.", "The pairing code has 6 digits."))
        io { if (!adb.pair(h, p, ma)) error(t("Ghép đôi không thành công, mã có thể đã đổi. Mở lại hộp ghép đôi trên tivi.", "Pairing failed, the code may have changed. Reopen the pairing dialog on the TV.")) }
        thongBao = t("Đã ghép đôi. Đang tìm cổng kết nối của tivi…", "Paired. Looking for the TV's connect port…")
        val ok = io {
            runCatching { adb.disconnect() }
            runCatching { adb.connectTls(ctx, 15_000) }.getOrDefault(false)
        }
        // mDNS không thấy (mạng chặn multicast, Chromecast Android 14 — góp ý buivansaobg 24/10/2026): quét cổng
        // ngẫu nhiên của Gỡ lỗi không dây (dải cổng tạm 32768–60999 của Linux) rồi thử kết nối từng cổng mở.
        val ok2 = ok || run {
            thongBao = t("Chưa thấy cổng qua mạng, đang dò cổng của tivi (khoảng 30 giây)…", "Port not announced, probing the TV's ports (about 30 seconds)…")
            val mo = quetCongMo(h, 32768..60999, boQua = setOf(p))
            mo.any { c -> io { runCatching { adb.disconnect() }; runCatching { adb.connect(h, c) }.getOrDefault(false) } }
        }
        if (!ok2) {
            ip = h
            error(t("Đã ghép đôi nhưng chưa tự tìm được cổng. Nhập IP và cổng ở dòng \"Địa chỉ IP & cổng\" ", "Paired, but the port was not found automatically. Enter the IP and port from the \"IP address & Port\" line ") +
                t("trên màn Gỡ lỗi không dây của tivi (không phải cổng ghép đôi) rồi bấm Kết nối.", "on the TV's Wireless debugging screen (not the pairing port) and press Connect."))
        }
        ip = h
        prefs.edit().putString("ip", h).apply()
        sauKhiKetNoi(h)
    }

    private fun tachIpCong(s: String): Pair<String, Int>? {
        val t = s.trim(); val i = t.lastIndexOf(':')
        if (i <= 0) return null
        val p = t.substring(i + 1).toIntOrNull() ?: return null
        return t.substring(0, i) to p
    }

    private suspend fun sauKhiKetNoi(host: String) {
        dangAdb = true
        daNapCu = false
        tv.quenTivi()
        thongBao = t("Đang đọc thông tin tivi…", "Reading the TV…")
        val (info, gp) = io { tv.docThongTin() }
        goiHome = io { tv.pkgHome }
        thongTin = info; getprop = gp
        khongPhaiTivi = !io { tv.laTivi() }
        muiGioLech = io { tv.muiGioTv() }.takeIf { lechMuiGio(it) } ?: ""
        boNho = io { tv.boNhoTrong() }
        tiLeHieuUng = io { tv.docTiLeHieuUng() }
        xacNhanLaTivi = false
        val tt = io { tv.docTrangThai() }
        trangThai = tt
        // Nếu trên TV đã cài sẵn app CleanTxa, tự cấp quyền + trao khoá tin cậy để kể cả khi tắt ADB vẫn kết nối ngầm được!
        if (!com.txa.cleantxa.ManHinh.laTv && host != "127.0.0.1" && "com.txa.cleantxa" in tt.caiDat) {
            runCatching {
                val phoneId = com.txa.cleantxa.link.TxaLinkStore.deviceId(ctx)
                val phoneName = com.txa.cleantxa.link.TxaLinkStore.deviceName()
                val tok = com.txa.cleantxa.link.TxaLinkStore.taoTokenNgauNhien()
                io {
                    tv.kichHoatLienKetTrenTv(phoneId, phoneName, tok)
                    Thread.sleep(700)
                    com.txa.cleantxa.link.TxaLinkClient.pingHost(ctx, host, com.txa.cleantxa.link.TxaLinkStore.PORT, 1200)?.let { ping ->
                        val trusted = com.txa.cleantxa.link.TxaLinkStore.TrustedTv(
                            tvId = ping.tvId,
                            name = ping.name,
                            model = ping.model,
                            lastIp = host,
                            port = ping.port,
                            token = tok,
                            pairedAt = System.currentTimeMillis(),
                        )
                        com.txa.cleantxa.link.TxaLinkStore.luuTvTinCay(ctx, trusted)
                        linkClient = com.txa.cleantxa.link.TxaLinkClient(host, ping.port, tok, ping.tvId)
                    }
                }
            }
        }
        // Công tắc DNS AdGuard theo đúng tivi (24/10/2026, góp ý kimhuenguyen1964: kết nối lại thì công tắc luôn tắt).
        dnsChanQc = io { tv.dnsDangChan() }
        val du = io { DuLieu.nap(ctx) }
        ungVien = du.khopMau(info)
        mauChon = ungVien.firstOrNull()?.first
        ganGiong = du.ganGiong(info, ungVien.map { it.first.id }.toSet())
        tatCaMau = du.mau.sortedBy { it.ten.lowercase() }
        // Điện thoại không có bản sao lưu (gỡ app, đổi máy, người khác dọn) thì lấy bản trên tivi.
        val slMay = kho.doc(info.khoaLuu)
        saoLuu = (slMay ?: io { tv.docSaoLuuTrenTv(info.khoaLuu) })?.also { b ->
            b.ip = host; io { tv.luuSaoLuu(b) }
        }
        saoLuuTuTv = slMay == null && saoLuu != null
        // Xiaomi: sau khi đổi Home, nút INPUT của remote không mở bảng nguồn nữa (upstream #1368) — bật sẵn để Home sạch nhận.
        nutInput = tt.homeHienTai == "com.google.android.apps.tv.launcherx" || info.brand.equals("Xiaomi", ignoreCase = true)
        // Sony 2015 (bo SVP-DTV15): Cài đặt dễ mở ra trống mà muốn bật lại Gỡ lỗi USB lại phải qua Cài đặt — tự tắt ADB là
        // khoá chủ nhà ở ngoài, không sửa được nữa (25/10/2026, 2 máy phải reset cứng). Mặc định giữ ADB, vẫn tắt được tay.
        giuAdb = info.device == "SVP-DTV15"
        laMayChieu = tv.coVeMayChieu(tt, info)
        nutNgu = laMayChieu
        man = if (dangKhoiPhuc) Man.KHOI_PHUC else Man.THONG_TIN
    }

    var dangKhoiPhuc by mutableStateOf(false)
    var khoiPhucXong by mutableStateOf(false)

    // ---------- Lập kế hoạch ----------

    fun lapKeHoach() = chay(t("Đang so với ${tatCaMau.size.takeIf { it > 0 } ?: "các"} mẫu tivi…", "Comparing with ${tatCaMau.size.takeIf { it > 0 } ?: "the"} TV models…")) {
        val tt = trangThai ?: return@chay
        if (chanKhongPhaiTivi) return@chay
        val du = io { DuLieu.nap(ctx) }
        val ds = io { KeHoach.lap(du, mauChon, tt) }
        muc = ds
        // 4.0.8 (Movie Legend Q10 Pro, doankhapro09 07/11/2026: dọn theo luật chung xong khởi động lại treo logo): máy chiếu
        // chưa có danh sách đúng mẫu thì không tích sẵn gói nào — phần mềm máy chiếu khác tivi nhiều, chủ nhà tự chọn từng gói.
        mayChieuKhongMau = laMayChieu && mauChon == null
        chon.clear(); ds.forEach { chon[it.pkg] = it.chonSan && !mayChieuKhongMau }
        man = Man.CHON_GOI
    }

    fun soDaChon() = chon.count { it.value }

    // ---------- Tắt gói ----------

    fun batDauTat() = chay(t("Đang sao lưu…", "Backing up…")) {
        val info = thongTin ?: return@chay
        if (chanKhongPhaiTivi) return@chay
        nhatKy.clear(); ketQua = null; tienDo = 0f
        manTruocHome = Man.DANG_TAT
        man = Man.DANG_TAT
        val sl = io { tv.damBaoSaoLuu(info, getprop, ip) }
        saoLuu = sl
        ghi(t("Đã lưu bản sao lưu (${sl.goiTruoc.size} gói, ${sl.daTatTruoc.size} gói vốn đã tắt).", "Backup saved (${sl.goiTruoc.size} packages, ${sl.daTatTruoc.size} already disabled)."))
        val ds = muc.filter { chon[it.pkg] == true }.map { it.pkg }
        val kq = try {
            io { tv.tatGoi(ds, sl, epSetupwraith) { s -> ghiTuIo(s) } }
        } catch (e: ThaoTacTv.TiviKhoiDongLaiException) {
            saoLuu = kho.doc(info.khoaLuu); taiLaiSaoLuu(); throw e
        } catch (e: ThaoTacTv.MatKetNoiTvException) {
            saoLuu = kho.doc(info.khoaLuu); taiLaiSaoLuu(); throw e
        }
        ketQua = kq
        ghi(t("Xong: tắt ${kq.daTat.size}, giữ lại ${kq.boQua.size}, lỗi ${kq.loi.size}.", "Done: disabled ${kq.daTat.size}, kept ${kq.boQua.size}, failed ${kq.loi.size}."))
        trangThai = io { tv.docTrangThai() }
        saoLuu = kho.doc(info.khoaLuu)
        taiLaiSaoLuu()
        luuKichBanVaoTv()
        chuanBiDock()
    }

    /**
     * Bỏ qua bước dọn, chỉ cài / đổi Home sạch (góp ý github.com/vgscity/vgs-cleantv/issues/1, 22/10/2026). Sao lưu VẪN
     * chạy (vài giây): đó là đường lui duy nhất để Khôi phục.
     */
    fun boQuaDon() = chay(t("Đang sao lưu…", "Backing up…")) {
        val info = thongTin ?: return@chay
        saoLuu = io { tv.damBaoSaoLuu(info, getprop, ip) }
        taiLaiSaoLuu()
        chuanBiDock()
        manTruocHome = Man.CHON_GOI
        man = Man.HOME_SACH
        if (daCoHomeSach) napCauHinhCu()
    }

    // Đi qua hàng đợi Main như ghiTuIo, để dòng tổng kết không chen lên trước dòng tiến độ.
    /** Tên tivi gửi kèm số liệu ẩn danh: hãng + model + device + Android. */
    private fun tenTiviDem(): String = thongTin?.let { "${it.tenHienThi} (${it.device}) Android ${it.android}" } ?: ""

    private fun ghi(s: String) { viewModelScope.launch(Dispatchers.Main) { nhatKy += s } }
    /** 0..1 khi đang tắt/bật lại từng gói, -1 khi không đếm được. */
    var tienDo by mutableStateOf(-1f)

    private fun ghiTuIo(s: String) {
        viewModelScope.launch(Dispatchers.Main) {
            nhatKy += s; thongBao = s
            Regex("""(\d+)/(\d+)""").find(s)?.let { m ->
                val (a, b) = m.destructured
                if (b.toInt() > 0) tienDo = a.toFloat() / b.toFloat()
            }
        }
    }

    // ---------- Khoá trẻ em (23/10/2026) ----------

    /** Một khung giờ khoá: "HH:MM", ngay 1 = Thứ Hai … 7 = Chủ Nhật. */
    data class KhungKhoa(val tu: String, val den: String, val ngay: Set<Int>)
    var khoaTrenTv by mutableStateOf<org.json.JSONObject?>(null)
    var khoaBat by mutableStateOf(true)
    var khoaPin by mutableStateOf("")
    var khoaPin2 by mutableStateOf("")
    val khoaKhung = androidx.compose.runtime.mutableStateListOf<KhungKhoa>()
    /** Khoá từng app (15/11/2026): gói đã chọn, và các app trên tivi chọn được. */
    val khoaApps = androidx.compose.runtime.mutableStateListOf<String>()
    var khoaAppCo by mutableStateOf<List<String>>(emptyList())
    var khoaBao by mutableStateOf("")
    /** Đúng PIN trên tivi thì mở bao lâu (anh Alex chốt 60 phút). */
    val KHOA_MO_TAM = 60

    fun moKhoaTre() = chay(t("Đọc cài đặt khoá trên tivi…", "Reading the lock settings…")) {
        val j = io { tv.docKhoa() }
        khoaTrenTv = j; khoaBao = ""; khoaPin = ""; khoaPin2 = ""
        khoaKhung.clear(); khoaApps.clear()
        khoaAppCo = runCatching { io { tv.appKhoaDuoc() } }.getOrDefault(emptyList())
        if (j != null) {
            khoaBat = j.optBoolean("bat")
            j.optJSONArray("apps")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { khoaApps += it } }
            val ks = j.optJSONArray("khung") ?: org.json.JSONArray()
            for (i in 0 until ks.length()) {
                val k = ks.getJSONObject(i); val ng = k.optJSONArray("ngay") ?: org.json.JSONArray()
                khoaKhung += KhungKhoa(k.optString("tu"), k.optString("den"), (0 until ng.length()).map { ng.getInt(it) }.toSet())
            }
        }
        // Lần đầu gợi ý sẵn khung tối; đã lưu "chỉ khoá app" (không khung giờ) thì để trống.
        if (j == null && khoaKhung.isEmpty()) { khoaBat = true; khoaKhung += KhungKhoa("21:00", "06:00", (1..7).toSet()) }
        man = Man.KHOA_TRE
    }

    fun luuKhoaTre() = chay(t("Đang cài khoá lên tivi…", "Setting up the lock on the TV…")) {
        val coPin = khoaTrenTv?.optBoolean("coPin") == true
        if (!coPin || khoaPin.isNotEmpty()) {
            if (!Regex("^[0-9]{4}$").matches(khoaPin)) error(t("Mã PIN phải đúng 4 chữ số.", "The PIN must be exactly 4 digits."))
            // Màn khoá trẻ em chỉ có một ô nhập PIN (khoaPin2 không gắn vào ô nào): chỉ so khi có ô nhập lại.
            if (khoaPin2.isNotEmpty() && khoaPin != khoaPin2) error(t("Hai lần nhập PIN không giống nhau.", "The two PINs don't match."))
        }
        if (khoaKhung.isEmpty() && khoaApps.isEmpty()) error(t("Thêm một khung giờ khoá hoặc chọn app cần khoá.", "Add a lock period or pick apps to lock."))
        val gio = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
        khoaKhung.forEach { if (!gio.matches(it.tu) || !gio.matches(it.den) || it.ngay.isEmpty()) error(t("Khung giờ ${it.tu}–${it.den} chưa đúng (giờ dạng 21:00, chọn ít nhất một ngày).", "Period ${it.tu}–${it.den} is not valid.")) }
        val j = org.json.JSONObject().put("bat", khoaBat).put("moTamPhut", KHOA_MO_TAM)
            .put("khung", org.json.JSONArray(khoaKhung.map { org.json.JSONObject().put("tu", it.tu).put("den", it.den).put("ngay", org.json.JSONArray(it.ngay.sorted())) }))
            .put("apps", org.json.JSONArray(khoaApps.distinct().sorted()))
        if (khoaPin.isNotEmpty()) {
            val muoi = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val bam = java.security.MessageDigest.getInstance("SHA-256").digest((muoi + khoaPin).toByteArray()).joinToString("") { "%02x".format(it) }
            j.put("muoi", muoi).put("pinBam", bam)
        }
        val apk = io { com.txa.cleantxa.CapNhat.moAsset(ctx, "khoa.apk").readBytes() }
        io { tv.caiKhoa(apk, j.toString()) { thongBao = it } }
        kotlinx.coroutines.delay(2500)
        khoaTrenTv = io { tv.docKhoa() }
        khoaPin = ""; khoaPin2 = ""
        val chay = khoaTrenTv?.optBoolean("dichVuChay") == true
        val soApp = khoaApps.distinct().size
        val chuApp = if (soApp > 0) t(" $soApp app đã chọn hỏi PIN mỗi lần mở.", " $soApp chosen app(s) ask for the PIN when opened.") else ""
        khoaBao = if (!chay && (khoaBat || soApp > 0)) t("Đã lưu nhưng dịch vụ khoá chưa chạy. Tắt tivi bật lại rồi mở lại màn này kiểm tra.", "Saved, but the lock service isn't running yet. Restart the TV and check again.")
            else if (!khoaBat) t("Đã lưu. Khoá theo giờ đang tắt.", "Saved. The schedule is off.") + chuApp
            else if (chay) t("Đã bật khoá. Trong khung giờ, tivi hiện màn khoá; nhập đúng PIN thì mở $KHOA_MO_TAM phút.", "The lock is on. During the periods the TV shows the lock screen; the PIN unlocks it for $KHOA_MO_TAM minutes.")
            else t("Đã lưu nhưng dịch vụ khoá chưa chạy.", "Saved, but the lock service isn't running yet.")
        if (khoaBat && chay) khoaBao += chuApp
    }

    fun moTamKhoaTre(phut: Int) = chay(t("Đang gửi lệnh…", "Sending…")) {
        io { tv.moTamKhoa(phut) }
        kotlinx.coroutines.delay(800)
        khoaTrenTv = io { tv.docKhoa() }
        khoaBao = if (phut > 0) t("Đã mở tạm $phut phút.", "Unlocked for $phut minutes.") else t("Đã khoá lại (nếu đang trong khung giờ).", "Locked again (if within a period).")
    }

    fun tatKhoaTre() = chay(t("Đang tắt khoá…", "Turning the lock off…")) {
        io { tv.tatKhoa(go = true) }
        khoaTrenTv = null; khoaBao = t("Đã tắt và gỡ Khoá trẻ em khỏi tivi.", "The Kids lock is off and removed from the TV.")
    }

    private fun chiaSeVanBan(ten: String, noiDung: String, mime: String) {
        val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = java.io.File(dir, ten).apply { writeText(noiDung, Charsets.UTF_8) }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val gui = android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime)
            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(
            android.content.Intent.createChooser(gui, t("Chia sẻ báo cáo", "Share report"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Tạo báo cáo chẩn đoán cục bộ để chia sẻ hoặc lưu lại. */
    fun xuatBaoCaoChanDoan() = chay(t("Đang tạo báo cáo…", "Creating report…")) {
        val tt = io { tv.docTrangThai() }
        val tv0 = thongTin
        val du = org.json.JSONObject()
            .put("hang", tv0?.manufacturer?.ifBlank { tv0.brand } ?: "").put("model", tv0?.model ?: "").put("device", tv0?.device ?: "")
            .put("android", tv0?.android ?: "").put("mau", mauChon?.id ?: "")
            .put("goiTat", org.json.JSONArray(tt.daTat.sorted()))
            .put("giu", org.json.JSONArray((ketQua?.boQua ?: emptyList()).map { it.first }))
        val nutApp = io { tv.chanDoanNutApp() }
        du.put("chanDoan", io { tv.chanDoanHome(goiHome) } + "\n# phim-home " + phimHomeGhi + nutApp)
        chiaSeVanBan("cleantxa-report-${tv0?.khoaLuu ?: "tv"}.json", du.toString(2), "application/json")
    }

    fun chupManHinh() = chay(t("Đang chụp màn hình tivi…", "Taking a TV screenshot…")) {
        val lc = linkClient
        val bytes = if (!dangAdb && lc != null) {
            io { lc.chupManHinhQuaLink() }
                ?: error(
                    t(
                        "Không chụp được qua Liên kết ngầm. Kiểm tra TV đang chạy Android 11+ và đã bật quyền Trợ năng của CleanTxa trên TV (một số video bản quyền như Netflix cũng chặn chụp hình).",
                        "Could not capture over Wi-Fi Link. Ensure the TV runs Android 11+ with CleanTxa Accessibility enabled (DRM video also blocks screenshots).",
                    )
                )
        } else {
            io { tv.chupManHinh() }
        }
        anhTv = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error(t("Không chụp được (một số tivi cấm chụp khi đang xem HDMI, bấm Home trên remote rồi thử lại).", "Could not capture (some TVs block it on an HDMI input — press Home on the remote and retry)."))
    }

    fun chiaSeAnhChupTv() {
        val bmp = anhTv ?: return
        viewModelScope.launch {
            runCatching {
                val uri = io {
                    val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
                    val f = java.io.File(dir, "cleantxa-tv-${System.currentTimeMillis()}.jpg")
                    f.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                    androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                }
                val gui = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("image/jpeg")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(
                    android.content.Intent.createChooser(gui, t("Chia sẻ ảnh chụp TV", "Share TV screenshot"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // ---------- Đặc quyền độc quyền CleanTxa Link (Không cần ADB) ----------

    private suspend fun damBaoLinkClient(): com.txa.cleantxa.link.TxaLinkClient? {
        linkClient?.let { return it }
        val ds = com.txa.cleantxa.link.TxaLinkClient.discoverLan(ctx, ipDienThoai())
        dsTvLink.clear()
        dsTvLink.addAll(ds)
        val tinCay = ds.firstOrNull { it.isTrusted } ?: return null
        runCatching { napQuaLink(tinCay.ip, tinCay.port, tinCay.trustedToken!!, tinCay.tvId, chuyenMan = false) }
        return linkClient
    }

    private fun xuLyChiaSeTuAppKhac(sharedTxt: String?, sharedImg: android.net.Uri?) = chay(
        t("Đang bắn nội dung sang tivi…", "Beaming to your TV…")
    ) {
        val lc = damBaoLinkClient()
        if (lc == null) {
            if (!sharedTxt.isNullOrBlank()) linkCanBan = sharedTxt
            error(
                t(
                    "Chưa tìm thấy TV tin cậy nào đang chạy ngầm trên cùng mạng Wi-Fi. Hãy ghép tin cậy với TV trước rồi bấm gửi lại.",
                    "No trusted TV found online on this Wi-Fi. Pair with your TV first, then send again.",
                )
            )
        }
        if (sharedImg != null) {
            val bytes = io { nenAnhChieuTv(sharedImg) } ?: error(t("Không đọc được ảnh vừa chia sẻ.", "Could not read shared image."))
            val res = io { lc.chieuAnhLenTv(bytes, t("Chia sẻ từ điện thoại", "Shared from phone")) }
            if (!res.optBoolean("ok", false)) error(res.optString("error", t("Không chiếu được ảnh lên TV.", "Could not beam photo to TV.")))
            dangChieuAnhTv = res.optBoolean("overlay", false)
            man = Man.REMOTE
            remoteBao = t("Đã chiếu ảnh toàn màn hình lên tivi!", "Photo beamed full-screen onto the TV!")
            return@chay
        }
        if (!sharedTxt.isNullOrBlank()) {
            val hasUrl = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""").containsMatchIn(sharedTxt)
            if (hasUrl) {
                val res = io { lc.banLinkLenTv(sharedTxt) }
                man = Man.REMOTE
                if (res.optBoolean("ok", false)) {
                    remoteBao = t("Đã mở ngay đường dẫn vừa chia sẻ trên màn hình tivi!", "Opened shared link right on your TV!")
                } else {
                    linkCanBan = sharedTxt
                    remoteBao = t("Tivi chưa có ứng dụng nhận đường dẫn này.", "No app on the TV could open this link.")
                }
            } else {
                io { lc.ghiClipboardTv(sharedTxt, pasteNow = true) }
                chuGo = sharedTxt
                man = Man.REMOTE
                remoteBao = t("Đã chép nội dung sang Clipboard tivi (và điền vào ô nhập nếu đang mở).", "Copied text to TV clipboard.")
            }
        }
    }

    private fun nenAnhChieuTv(uri: android.net.Uri): ByteArray? = runCatching {
        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 1440) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = minOf(1920f / bmp.width.coerceAtLeast(1), 1080f / bmp.height.coerceAtLeast(1), 1f)
        val finalBmp = if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bmp) bmp.recycle()
            scaled
        } else {
            bmp
        }
        val bos = java.io.ByteArrayOutputStream()
        finalBmp.compress(Bitmap.CompressFormat.JPEG, 86, bos)
        finalBmp.recycle()
        bos.toByteArray()
    }.getOrNull()

    fun datAmLuongTv(muc: Int) {
        val lc = linkClient ?: return
        val max = amLuongTv?.second ?: 15
        amLuongTv = muc.coerceIn(0, max) to max
        guiRemote {
            val res = lc.datAmLuong(muc)
            viewModelScope.launch { amLuongTv = res }
        }
    }

    fun datDoToiManHinhTv(phanTram: Int) {
        val lc = linkClient ?: return
        val clamped = phanTram.coerceIn(0, 75)
        doToiTv = clamped
        guiRemote {
            val res = lc.datDoToiManHinh(clamped)
            if (!res.optBoolean("ok", false)) {
                viewModelScope.launch {
                    doToiTv = 0
                    remoteBao = res.optString("error", t("Cần cấp quyền Hiện đè (Overlay) trên TV để dùng Chế độ dịu mắt.", "Grant Overlay permission on TV once to use Night Dimmer."))
                }
            }
        }
    }

    fun banLinkLenTv(chuoi: String = linkCanBan) = chay(t("Đang bắn link sang tivi…", "Opening link on TV…")) {
        val lc = linkClient ?: error(t("Tính năng này cần kết nối qua CleanTxa Link.", "Requires CleanTxa Link connection."))
        val s = chuoi.trim()
        if (s.isEmpty()) error(t("Dán đường dẫn YouTube, web hoặc luồng phát trước.", "Paste a YouTube, web, or stream URL first."))
        val res = io { lc.banLinkLenTv(s) }
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error", t("Tivi chưa có trình duyệt hoặc ứng dụng mở được đường dẫn này.", "No app on the TV handled this URL.")))
        }
        linkCanBan = ""
        remoteBao = t("Đã mở ngay trên tivi: ${res.optString("url", s)}", "Opened on TV: ${res.optString("url", s)}")
    }

    fun chieuAnhLenTv(uri: android.net.Uri) = chay(t("Đang chiếu ảnh lên màn hình tivi…", "Beaming photo to TV…")) {
        val lc = linkClient ?: error(t("Tính năng này cần kết nối qua CleanTxa Link.", "Requires CleanTxa Link connection."))
        val bytes = io { nenAnhChieuTv(uri) } ?: error(t("Không đọc được file ảnh này.", "Could not read this photo."))
        val res = io { lc.chieuAnhLenTv(bytes) }
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error", t("Không hiển thị được ảnh trên tivi.", "Could not show photo on TV.")))
        }
        dangChieuAnhTv = res.optBoolean("overlay", false)
        remoteBao = t("Đang chiếu ảnh toàn màn hình trên tivi!", "Showing photo full-screen on TV!")
    }

    fun dongAnhTrenTv() {
        val lc = linkClient ?: return
        dangChieuAnhTv = false
        guiRemote { lc.dongAnhTrenTv() }
    }

    fun guiThongBaoLenTv(loiNhan: String = tinNhanTv, docTo: Boolean = docToTinNhanTv, giay: Int = 10) = chay(
        t("Đang gửi thông báo nổi lên tivi…", "Sending live message to TV…")
    ) {
        val lc = linkClient ?: error(t("Tính năng này cần kết nối qua CleanTxa Link.", "Requires CleanTxa Link connection."))
        val msg = loiNhan.trim()
        if (msg.isEmpty()) error(t("Nhập nội dung lời nhắn cần hiện trên màn hình tivi.", "Enter a message to show on the TV."))
        val res = io { lc.guiThongBaoNoi(msg, docTo, giay) }
        if (!res.optBoolean("ok", false)) error(res.optString("error", t("Không gửi được lời nhắn.", "Could not send message.")))
        if (loiNhan == tinNhanTv) tinNhanTv = ""
        remoteBao = if (docTo) {
            t("Đã hiện thông báo nổi & phát giọng nói trên tivi!", "Displayed banner & spoke message on TV!")
        } else {
            t("Đã hiện thông báo nổi trên màn hình tivi!", "Displayed live banner on TV!")
        }
    }

    fun dayClipboardLenTv(chuoi: String, danLuon: Boolean) = chay(t("Đang đồng bộ sang Clipboard tivi…", "Syncing to TV clipboard…")) {
        val lc = linkClient ?: error(t("Tính năng này cần kết nối qua CleanTxa Link.", "Requires CleanTxa Link connection."))
        if (chuoi.isBlank()) error(t("Clipboard điện thoại đang trống.", "Phone clipboard is empty."))
        val res = io { lc.ghiClipboardTv(chuoi, danLuon) }
        val pasted = res.optBoolean("pasted", false)
        remoteBao = if (pasted) {
            t("Đã chép sang TV và dán thẳng vào ô nhập đang mở!", "Copied to TV and pasted into focused input!")
        } else {
            t("Đã chép vào bộ nhớ tạm (Clipboard) của tivi!", "Copied to TV clipboard!")
        }
    }

    fun layClipboardTuTv(onCopied: (String) -> Unit) = chay(t("Đang lấy văn bản từ tivi…", "Reading text from TV…")) {
        val lc = linkClient ?: error(t("Tính năng này cần kết nối qua CleanTxa Link.", "Requires CleanTxa Link connection."))
        val txt = io { lc.docClipboardTv() }
        if (txt.isBlank()) {
            error(t("Chưa có chữ nào trong Clipboard hoặc ô đang trỏ trên tivi.", "TV clipboard and focused field are empty."))
        }
        onCopied(txt)
        remoteBao = t("Đã lấy từ TV về điện thoại: \"${txt.take(60)}\"", "Copied from TV: \"${txt.take(60)}\"")
    }

    // ---------- Remote trên điện thoại (3.5.0, góp ý build.dng 27/10/2026) ----------

    /** Phím gửi lần lượt từng cái, không chặn màn (không qua chay: bấm nhanh không bị "đang bận"). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val hangPhim = Dispatchers.IO.limitedParallelism(1)
    var remoteBao by mutableStateOf("")
    var chuGo by mutableStateOf("")
    var goCoDau by mutableStateOf(false)
    var henTatLuc by mutableStateOf<Long?>(null)
    var appTuCai by mutableStateOf<Set<String>>(emptySet())
    var hoiGo by mutableStateOf<String?>(null)
    val appTrenTv: List<String> get() = trangThai?.let { tt -> tt.coBieuTuong.filter { it in tt.caiDat && it !in tt.daTat && it !in com.txa.cleantxa.adb.ThaoTacTv.HOME_SACH &&
        !(com.txa.cleantxa.ManHinh.laTv && it == ctx.packageName) } }
        ?.sortedWith(compareBy({ it !in appTuCai }, { com.txa.cleantxa.dulieu.tenDep(it).lowercase() })) ?: emptyList()

    fun moRemote() = chay(t("Đang mở remote…", "Opening the remote…")) {
        remoteBao = ""
        val lc = linkClient
        if (!dangAdb && lc != null) {
            val st = io { lc.docTrangThaiDayDu() }
            trangThai = st.trangThai
            boNho = st.boNho
            ramTv = st.ram
            amLuongTv = st.volCur to st.volMax
            doToiTv = st.dimPercent
            dangChieuAnhTv = st.photoShowing
            appDangMoTrenTv = st.activePkg
            goCoDau = st.hasA11y || daCoHomeSach
            henTatLuc = st.sleepTimerSec
            appTuCai = st.appTuCai
            man = Man.REMOTE
            return@chay
        }
        goCoDau = daCoHomeSach && io { tv.coGoCoDau() }
        henTatLuc = io { tv.docHenTatTivi() }
        appTuCai = io { tv.appTuCai() }
        man = Man.REMOTE
    }

    private fun guiRemote(viec: () -> Unit) {
        viewModelScope.launch(hangPhim) {
            runCatching(viec).onFailure { e -> withContext(Dispatchers.Main) { remoteBao = moTaLoi(e) } }
        }
    }

    fun bamPhim(ma: Int) = guiRemote {
        val lc = linkClient
        if (!dangAdb && lc != null) {
            val res = lc.guiPhim(ma)
            if (!res.optBoolean("ok", false) && res.optBoolean("needA11y", false)) {
                viewModelScope.launch {
                    remoteBao = t(
                        "Phím này cần bật quyền Trợ năng của CleanTxa một lần trên tivi (mở app CleanTxa trên tivi để cấp).",
                        "This key needs CleanTxa's Accessibility permission enabled once on the TV."
                    )
                }
            }
        } else {
            tv.phim(ma)
        }
    }

    fun guiChu(xuongDong: Boolean = false) {
        val chu = chuGo
        if (chu.isEmpty() && !xuongDong) return
        chuGo = ""
        guiRemote {
            val lc = linkClient
            if (!dangAdb && lc != null) {
                val res = lc.goChu(chu, xuongDong)
                val ok = res.optBoolean("ok", false)
                viewModelScope.launch {
                    remoteBao = if (ok) "" else t("Chọn một ô nhập chữ trên tivi trước, và bật quyền Trợ năng của CleanTxa trên tivi.", "Select a text box on the TV first, and enable CleanTxa's Accessibility permission on the TV.")
                }
                return@guiRemote
            }
            val du = tv.goChu(chu)
            if (xuongDong) tv.phim(66)
            viewModelScope.launch {
                remoteBao = if (du) "" else t("Tivi chỉ nhận chữ không dấu. Bấm \"Bật gõ tiếng Việt có dấu\" bên dưới.", "The TV only took the plain letters. Turn on accented typing below.")
            }
        }
    }

    fun batGoCoDau() = chay(t("Đang bật gõ có dấu…", "Turning on accented typing…")) {
        if (!dangAdb && linkClient != null) {
            remoteBao = t("Mở CleanTxa trên tivi và bật mục \"Quyền Điều khiển & Gõ chữ\" một lần là gõ tiếng Việt có dấu luôn sẵn sàng.", "Open CleanTxa on the TV and enable \"Remote & Typing permission\" once.")
            return@chay
        }
        io { tv.batGoCoDau() }
        kotlinx.coroutines.delay(1500)
        goCoDau = io { tv.coGoCoDau() }
        remoteBao = if (goCoDau) t("Đã bật. Chọn ô chữ trên tivi rồi gõ ở đây.", "On. Pick a text field on the TV, then type here.")
        else t("Tivi không bật được dịch vụ này.", "The TV did not turn it on.")
    }

    fun henTat(phut: Int) = chay(if (phut > 0) t("Đang hẹn giờ tắt…", "Setting the sleep timer…") else t("Đang huỷ hẹn giờ…", "Cancelling the timer…")) {
        val lc = linkClient
        if (!dangAdb && lc != null) {
            henTatLuc = io { lc.henTat(phut) }
        } else {
            io { tv.henTatTivi(phut) }
            henTatLuc = io { tv.docHenTatTivi() }
        }
        remoteBao = if (phut > 0) t("Tivi sẽ tự tắt sau $phut phút, kể cả khi bạn đóng app.", "The TV will switch off in $phut minutes, even if you close the app.")
        else t("Đã huỷ hẹn giờ tắt.", "Sleep timer cancelled.")
    }

    fun moAppTv(pkg: String) = guiRemote {
        val lc = linkClient
        val ok = if (!dangAdb && lc != null) lc.moApp(pkg) else tv.moApp(pkg)
        if (!ok) viewModelScope.launch { remoteBao = t("App này không có màn mở được.", "This app has nothing to open.") }
    }

    fun goAppTv(pkg: String) = chay(t("Đang gỡ ${com.txa.cleantxa.dulieu.tenDep(pkg)}…", "Uninstalling ${com.txa.cleantxa.dulieu.tenDep(pkg)}…")) {
        val lc = linkClient
        if (!dangAdb && lc != null) {
            if (!io { lc.goApp(pkg) }) error(t("Không gửi được yêu cầu gỡ sang tivi.", "Couldn't open uninstall prompt on TV."))
            remoteBao = t("Đã mở hộp xác nhận gỡ trên màn hình tivi — bấm OK trên tivi để xác nhận.", "Opened uninstall prompt on the TV — confirm on the TV screen.")
            return@chay
        }
        if (!io { tv.goApp(pkg) }) error(t("Tivi không cho gỡ app này.", "The TV refused to uninstall this app."))
        trangThai = io { tv.docTrangThai() }
        appTuCai = io { tv.appTuCai() }
        remoteBao = t("Đã gỡ ${com.txa.cleantxa.dulieu.tenDep(pkg)}.", "Uninstalled ${com.txa.cleantxa.dulieu.tenDep(pkg)}.")
    }

    fun caiApkLenTv(uri: android.net.Uri) = chay(t("Đang gửi APK sang tivi…", "Sending the APK to the TV…")) {
        val du = io { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: error(t("Không đọc được file.", "Couldn't read the file."))
        caiApkByte(du)
    }

    private suspend fun caiApkByte(du: ByteArray) {
        if (du.size < 4 || du[0] != 'P'.code.toByte() || du[1] != 'K'.code.toByte()) error(t("File này không phải APK.", "This isn't an APK file."))
        val lc = linkClient
        if (!dangAdb && lc != null) {
            if (!io { lc.caiApk(du) }) error(t("Tivi chưa cấp quyền cài ứng dụng cho CleanTxa. Mở CleanTxa trên tivi để cấp quyền một lần.", "TV hasn't granted Install Unknown Apps to CleanTxa yet."))
            remoteBao = t("Đã gửi APK sang tivi và mở hộp cài đặt trên màn hình tivi.", "APK sent and installer opened on the TV screen.")
            return
        }
        val ra = io { tv.caiApkTuDienThoai(du) }
        if (!ra.contains("Success")) error(t("Tivi không cài được: $ra", "The TV couldn't install it: $ra"))
        trangThai = io { tv.docTrangThai() }
        appTuCai = io { tv.appTuCai() }
        remoteBao = t("Đã cài xong.", "Installed.")
    }


    // ---------- Home sạch ----------

    /** App có biểu tượng còn bật, để chọn vào dock. */
    fun appChoDock(): List<String> {
        val tt = trangThai ?: return emptyList()
        val khongDua = ThaoTacTv.HOME_SACH + setOf("com.android.tv.settings", "com.android.settings") + ThaoTacTv.HOME_GOC_BIET
        return tt.coBieuTuong.filter { it in tt.caiDat && it !in tt.daTat && it !in khongDua }
            .sortedWith(compareBy({ p -> UU_TIEN_DOCK.indexOf(p).let { if (it < 0) 99 else it } }, { it }))
    }

    /** Từ màn thông tin: đổi theme cho tivi đã có Home sạch. */
    /** Màn mà nút Back ở Home sạch quay về: vừa dọn → màn dọn; Đổi giao diện → thông tin tivi; Bỏ qua → chọn gói. */
    var manTruocHome by mutableStateOf(Man.DANG_TAT)

    fun moDoiTheme() { chuanBiDock(); manTruocHome = Man.THONG_TIN; man = Man.HOME_SACH; viewModelScope.launch { napCauHinhCu() } }

    /** "Đổi giao diện": mở ra đúng các lựa chọn đang có trên tivi thay vì mặc định. */
    /** Đã nạp config.json của tivi vào các lựa chọn chưa (mỗi lần kết nối một lần — quay lại màn không xoá chỉnh sửa). */
    private var daNapCu = false

    private suspend fun napCauHinhCu() {
        if (daNapCu) return
        daNapCu = true
        val lc = linkClient
        if (!dangAdb && lc != null) {
            val (j, wp) = runCatching { io { lc.docCauHinhHome() } }.getOrDefault(null to null)
            if (j != null) {
                napTuJson(j)
                if (anhNenRieng == null && wp != null && j.optBoolean("wallpaper")) {
                    val laAnhTheme = !j.optBoolean("vgs_anh_rieng") && io {
                        com.txa.cleantxa.dulieu.THEMES.mapNotNull { it.anhNen }.any { a -> runCatching { ctx.assets.open(a).readBytes().contentEquals(wp) }.getOrDefault(false) }
                    }
                    if (!laAnhTheme) anhNenRieng = wp
                }
            }
            return
        }
        val j = runCatching { io { tv.docCauHinhTv() } }.getOrNull() ?: return
        napTuJson(j)
        // Ảnh nền riêng đã đặt từ trước: đọc lại từ tivi để Áp dụng lần sau không thay bằng ảnh theme (23/10/2026).
        // Nhận ra bằng cờ vgs_anh_rieng (từ 2.5.6) hoặc ảnh trên tivi không trùng ảnh của theme nào (bản cũ hơn).
        if (anhNenRieng == null && j.optBoolean("wallpaper")) {
            val anh = runCatching { io { tv.docAnhNenTv() } }.getOrNull()
            if (anh != null) {
                val laAnhTheme = !j.optBoolean("vgs_anh_rieng") && io {
                    com.txa.cleantxa.dulieu.THEMES.mapNotNull { it.anhNen }.any { a -> runCatching { ctx.assets.open(a).readBytes().contentEquals(anh) }.getOrDefault(false) }
                }
                if (!laAnhTheme) anhNenRieng = anh
            }
            // Bộ ảnh riêng tự đổi (3.5.0): ảnh 2 trở đi nằm ở wallpapers/r02.jpg…, đọc lại để Áp dụng lần sau không mất.
            val soAnh = j.optInt("vgs_bo_rieng", 0)
            if (anhNenRieng != null && soAnh > 1) anhXoay = io { (2..minOf(soAnh, ANH_XOAY_TOI_DA + 1)).mapNotNull { tv.docFileTheme("wallpapers/r%02d.jpg".format(it)) } }
        }
    }

    /** Đưa một config.json (của tivi hoặc file cấu hình đã lưu) vào các lựa chọn trên màn Home sạch. */
    private fun napTuJson(j: JSONObject) {
        // Khoá do theme đang dùng ghi (vgs_theme, từ 2.9) không phải lựa chọn của người dùng: đọc như mặc định, không thì
        // đổi sang theme khác vẫn dính (Kiểu Apple TV bật uniform_tiles, dock_glass, ẩn lời chào, dời hàng app).
        val theme = com.txa.cleantxa.dulieu.THEMES.firstOrNull { it.id == j.optString("cleantxa_theme") }
        theme?.let { themeChon = it }
        fun cuaTheme(k: String) = theme?.cauHinh?.containsKey(k) == true
        amLich = j.optBoolean("lunar_date", amLich)
        nutTrenCung = j.optBoolean("top_buttons_top", false)
        tenDuoiApp = j.optBoolean("always_show_names", false)
        tenToTrenDock = j.optBoolean("dock_title", true)
        toMauOApp = j.optBoolean("icon_tile_color", true)
        iconTo = j.optBoolean("icon_fill", false)
        thuVienApp = j.optBoolean("app_library", false)
        thuVienTrongMenu = thuVienApp && !j.optBoolean("library_button", true)
        nutCaiDatThuVien = thuVienApp && j.optBoolean("settings_button_library", false)
        chiHienDock = thuVienApp && j.optBoolean("dock_only", false)
        dongHoKhiYen = j.optInt("home_idle_clock_minutes", 0)
        tiviYeu = j.optBoolean("reduced_motion", false)
        hieuUngKinh = !cuaTheme("dock_glass") && j.optBoolean("dock_glass", false)
        lamToiNen = j.optInt("wallpaper_dim", 0)
        hienThongSo = j.optBoolean("show_stats", false)
        doiHang = if (j.optBoolean("rows_middle", false)) HANG_GIUA else if (cuaTheme("rows_offset_dp")) 0 else j.optInt("rows_offset_dp", 0)
        nutDangPhat = j.optBoolean("now_playing", true)
        dungTiengNen = j.optBoolean("pause_behind_home", true)
        coChuDau = if (cuaTheme("header_scale")) 100 else j.optInt("header_scale", 100)
        motHang = !cuaTheme("single_row") && j.optBoolean("single_row", false)
        // 4.1.4 (góp ý nguyenthanhsn88 15/11/2026): như uniform_tiles — công tắc hiện đúng trạng thái thật (Kiểu Apple TV /
        // macOS ẩn lời chào) và bật lại được đè lên theme; trước đây công tắc báo "đang hiện" mà lời chào vẫn ẩn.
        anLoiChao = !j.optBoolean("show_greeting", true)
        giuAmLuong = !cuaTheme("key_filter") && !j.optBoolean("key_filter", true) && !laTclGoogleTv
        // 3.0.0 (góp ý contact.khanhtoan): công tắc hiện đúng trạng thái thật, kể cả khi theme bật, và tắt được đè lên theme.
        oDeuNhau = j.optBoolean("uniform_tiles", false)
        moKhiBat = j.optBoolean("open_on_start_choice", false)
        moKhiThuc = j.optBoolean("wake_open_app", false)
        luonVeHome = j.optBoolean("power_on_home_always", false)
        nutHenTat = j.optBoolean("sleep_timer_button", false)
        nutNgu = j.optBoolean("sleep_button", nutNgu)
        nutDaNhiem = j.optBoolean("recents_button", false)
        phimGan.clear(); j.optJSONArray("vgs_phim_gan")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.forEach { phimGan.add(it) } }
        chuTrang = !cuaTheme("header_white") && j.optBoolean("header_white", false)
        if (j.optInt("vgs_bo_rieng", 0) > 1) phutDoiAnh = j.optInt("wallpaper_rotation_minutes", 15)
        mauChu = j.optString("header_color").takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) }
        j.optString("accent_color").takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) }?.let { txaMauNhan = it }
        j.optString("tile_corner_style").takeIf { it in setOf("rounded", "pill", "sharp") }?.let { txaBoGoc = it }
        khungDau = j.optBoolean("header_panel", false)
        // Chỉ nạp số cột do người dùng chọn ở đây (vgs_so_cot), không lấy columns của theme.
        soCot = if (j.optBoolean("vgs_so_cot", false)) j.optInt("columns", 0) else 0
        if (j.has("handle_view_inputs")) nutInput = j.optBoolean("handle_view_inputs", nutInput)
        // Chỉ nạp lại khoá NGƯỜI DÙNG đặt ở Nâng cao (ghi trong vgs_nang_cao), không lấy khoá do theme ghi
        // (background_color, sky_intensity…) — nếu không, đổi theme sau đó sẽ bị kẹt giá trị của theme cũ.
        nangCao.clear()
        val daDat = j.optJSONArray("vgs_nang_cao")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        dsNangCao.filter { it.khoa in daDat }.forEach { tc -> com.txa.cleantxa.dulieu.NangCao.docChu(tc, j)?.let { nangCao[tc.khoa] = it } }
        nutManCho = j.optBoolean("dream_button", nutManCho)
        manChoDong = j.optBoolean("dream_motion", false)
        phutManCho = j.optInt("home_idle_clock_minutes", 10)
        gonNut = j.optBoolean("top_compact", gonNut)
        j.optJSONArray("dock_apps")?.let { a -> val ds = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() && !it.startsWith(GOI_KENH) }; if (ds.isNotEmpty()) { dock.clear(); dock.addAll(ds.take(DOCK_TOI_DA)) } }
        kenhChon.clear()
        j.optJSONArray("link_tiles")?.let { a -> (0 until a.length()).mapNotNull { com.txa.cleantxa.dulieu.OKenh.tuDong(a.optString(it)) }.forEach { kenhChon.add(it) } }
        // Trình phát IPTV đang dùng (ô IPTV đầu tiên) để thêm kênh mới cũng mở bằng nó.
        kenhChon.firstOrNull { o -> com.txa.cleantxa.dulieu.Iptv.TRINH_PHAT.any { it.first == o.goi } }?.let { trinhPhatChon = it.goi }
        j.optString("greeting_name").takeIf { it.isNotBlank() }?.let { tenChao = it }
        j.optString("weather_place").takeIf { it.isNotBlank() }?.let { ten ->
            thanhPho = THANH_PHO.firstOrNull { it.ten == ten }
                ?: com.txa.cleantxa.dulieu.ThanhPho(ten, j.optDouble("weather_lat"), j.optDouble("weather_lon")).takeIf { !it.lat.isNaN() && !it.lon.isNaN() }
        }
    }

    private fun chuanBiDock() {
        if (dock.isNotEmpty()) return
        dock.addAll(appChoDock().filter { it in UU_TIEN_DOCK }.take(4))
    }

    /** Dời app trong dock lên (-1) / xuống (+1). */
    fun dichDock(p: String, buoc: Int) {
        val i = dock.indexOf(p); val j = i + buoc
        if (i < 0 || j !in dock.indices) return
        dock[i] = dock[j]; dock[j] = p
    }

    fun doiDock(p: String) {
        if (p in dock) dock.remove(p) else if (dock.size < DOCK_TOI_DA) dock.add(p)
    }

    // Ẩn khỏi Home ngay ở danh sách app (góp ý khangnck.005 29/10/2026): ghi thẳng vào hidden_apps của Nâng cao.
    private fun dsAn() = nangCao["hidden_apps"].orEmpty().split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    fun daAn(p: String) = p in dsAn()
    fun doiAn(p: String) {
        val ds = dsAn().toMutableList()
        if (!ds.remove(p)) { ds.add(p); dock.remove(p) }
        if (ds.isEmpty()) nangCao.remove("hidden_apps") else nangCao["hidden_apps"] = ds.joinToString(", ")
    }

    fun caiHomeSach() = chay(t("Đang cài Home sạch…", "Installing the clean Home…")) { caiHomeSachBen() }

    /**
     * Xoá cache các app trên tivi, rồi báo kết quả. KHÔNG tự cài hay áp dụng Home sạch: Home sạch có màn riêng để chọn
     * giao diện rồi tự bấm cài (giống CleanTV). Đang ở màn Home sạch (vừa cài hỏng vì hết bộ nhớ) thì báo đủ chỗ chưa và
     * nhắc bấm nút cài.
     */
    fun donBoNhoDem() = chay(t("Đang dọn bộ nhớ đệm trên tivi…", "Clearing caches on the TV…")) {
        val truoc = boNho?.first ?: io { tv.boNhoTrong() }?.first
        val sau = io { tv.donBoNhoDem() }
        boNho = sau
        val trong = sau?.first
        val duoc = if (truoc != null && trong != null && trong > truoc) t(" (lấy lại ${ThaoTacTv.mb(trong - truoc)})", " (freed ${ThaoTacTv.mb(trong - truoc)})") else ""
        val trenManHome = man == Man.HOME_SACH
        val can = sau?.let { ThaoTacTv.canTrong(it.second) }
        if (trenManHome && trong != null && can != null && trong < can) {
            canDonBoNho = false
            error(t("Dọn xong vẫn chỉ còn ${ThaoTacTv.mb(trong)} trống, chưa đủ ${ThaoTacTv.mb(can)} để cài Home sạch. Vào Cài đặt > Ứng dụng trên tivi, gỡ bớt app không dùng rồi thử lại.",
                "Still only ${ThaoTacTv.mb(trong)} free after clearing, short of the ${ThaoTacTv.mb(can)} the clean Home needs. Uninstall unused apps in the TV's Settings > Apps, then try again."))
        }
        val con = trong?.let { t(" Còn trống ${ThaoTacTv.mb(it)}.", " ${ThaoTacTv.mb(it)} free now.") } ?: ""
        val goiY = if (trenManHome) t(" Giờ bạn có thể bấm nút cài Home sạch ở cuối màn.", " You can now press the install button at the bottom of the screen.") else ""
        android.widget.Toast.makeText(ctx, t("Đã dọn bộ nhớ đệm$duoc.$con$goiY", "Caches cleared$duoc.$con$goiY"), android.widget.Toast.LENGTH_LONG).show()
    }

    fun datTiLeHieuUng(scale: String) = chay(t("Đang chỉnh tốc độ hiệu ứng TV (${scale}x)…", "Setting TV animation scale (${scale}x)…")) {
        if (io { tv.datTiLeHieuUng(scale) }) {
            tiLeHieuUng = io { tv.docTiLeHieuUng() } ?: scale
            com.txa.cleantxa.TxaLogger.i("TvSpeed", "Set TV animation scale to ${scale}x")
            thongBao = t("Đã đặt tốc độ hiệu ứng TV: ${scale}x.", "TV animation scale set to ${scale}x.")
        } else {
            error(t("Không thể thay đổi tốc độ hiệu ứng trên TV này.", "Could not change animation scale on this TV."))
        }
    }

    fun giaiPhongRamNen() = chay(t("Đang giải phóng RAM nền trên TV…", "Trimming background RAM on TV…")) {
        val lc = linkClient
        if (!dangAdb && lc != null) {
            val (freed, storage) = io { lc.giaiPhongRam() }
            boNho = storage
            val freedMb = freed / (1024 * 1024)
            android.widget.Toast.makeText(
                ctx,
                t("Đã dọn tiến trình nền qua Liên kết Wi-Fi (trống thêm ~${freedMb} MB RAM).", "Trimmed background processes over Wi-Fi Link (~${freedMb} MB RAM freed)."),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return@chay
        }
        if (io { tv.giaiPhongRamNen() }) {
            com.txa.cleantxa.TxaLogger.i("TvSpeed", "Executed am kill-all on TV")
            thongBao = t("Đã dọn các tiến trình nền để giải phóng RAM.", "Background processes trimmed to free RAM.")
        } else {
            error(t("Không thể giải phóng RAM nền trên TV này.", "Could not trim background RAM on this TV."))
        }
    }

    private suspend fun caiHomeSachBen() {
        if (laAndroidCu) error(t("Home sạch cần Android 8 trở lên, tivi/hộp này là Android ${thongTin?.android}. CleanTxa chỉ dọn được quảng cáo và app rác trên máy này.", "The clean Home needs Android 8 or later; this TV/box runs Android ${thongTin?.android}. CleanTxa can only clean ads and junk apps here."))
        if (laFireTv) error(t("Fire TV không cho app nào thay màn Home (Amazon khoá). CleanTxa chỉ dọn được quảng cáo và app rác trên Fire TV.", "Fire TV does not let any app replace Home (Amazon locks it). On Fire TV CleanTxa can only clean ads and junk apps."))
        val sl = saoLuu ?: io { tv.damBaoSaoLuu(thongTin!!, getprop, ip) }.also { saoLuu = it }
        nhatKy.clear()
        val apk = io { com.txa.cleantxa.CapNhat.moAsset(ctx, tv.apkHome).readBytes() }
        val cauHinh = voiAppNgoai(cauHinhHome())
        val anh = anhNenTheme()
        val bo = boAnhTheme()
        napAnhKenh()
        val loiCai = io { tv.caiLauncher(apk, sl, cauHinh, anh, bo, nutInput, canDichVuPhim, { com.txa.cleantxa.CapNhat.moAsset(ctx, "tvhome-xiaomi.apk").readBytes() }) { s -> ghiTuIo(s) } }
        // caiLauncher báo lỗi là đã tự trả Home gốc: huỷ canh gác trên tivi.
        if (loiCai != null) { io { tv.boGac() }; canDonBoNho = tv.thieuBoNho; error(loiCai) }
        if (nutDangPhat || dungTiengNen) io { tv.choPhepDangPhat() }
        if (dnsChanQc) io { tv.batDnsChanQc(sl) }
        if (nutManCho) io { tv.chuanBiManCho(sl) }
        io { tv.datManChoDong(manChoDong, sl) }
        thuPhimHome(sl)
        homeXong = true
        luuKichBanVaoTv()
        ghi(t("Home sạch đã chạy. Bấm Home trên remote để xem.", "The clean Home is running. Press Home on the remote to see it."))
        taiLaiSaoLuu()
        man = Man.XONG
    }

    /**
     * Bấm Home thật trên tivi, tắt gói còn giành nút Home (ThaoTacTv.damBaoPhimHome). Không được thì báo rõ, không
     * báo "xong" suông; gửi tên gói giành Home về bộ đếm để biết hãng nào bị (22/10/2026).
     */
    private suspend fun thuPhimHome(sl: com.txa.cleantxa.dulieu.BanSaoLuu?) {
        val kq = io { tv.damBaoPhimHome(sl) { s -> ghiTuIo(s) } }
        // Đã thử xong (được, hoặc đã tự bật lại Home gốc): tivi thôi canh gác. Rớt kết nối trước dòng này thì
        // canh gác trên tivi tự trả Home gốc sau ThaoTacTv.GAC_GIAY giây.
        io { tv.boGac() }
        veApp()
        phimHomeOk = kq.ok
        phimHomeGhi = (if (kq.ok) "ok" else if (kq.gianh.isNotEmpty()) "lui" else "loi") + " gianh=" + kq.gianh.joinToString(",") + " conLai=" + kq.conLai + " lyDo=" + kq.lyDo
        if (kq.lyDo == "chuyen") { nutHome = true; ghi(t("Tivi không cho tắt Home gốc nên Home gốc vẫn bật, nhưng mỗi khi nó hiện ra Home sạch sẽ tự mở đè lên (bật \"Remote gửi phím Home lạ\"). Khôi phục là trả lại như cũ.", "The TV won't let the stock Home be disabled, so it stays on, but whenever it comes up the clean Home opens over it (\"Remote sends an odd Home key\" is on). Restore puts things back.")) }
        else if (kq.daBatBatPhim) { nutHome = true; ghi(t("Đã bật \"Remote gửi phím Home lạ\" vì nút Home của tivi bị app khác giành.", "Turned on \"Remote sends an odd Home key\" because another app held the Home button.")) }
        if (kq.ok && kq.gianh.isNotEmpty()) {
            ghi(t("Đã tắt ${kq.gianh.joinToString()} vì nó giành nút Home. Menu hình ảnh / âm thanh của hãng (nếu nằm trong Home cũ) vào bằng nút Cài đặt trên Home sạch; muốn trả lại thì bấm Khôi phục.",
                "Disabled ${kq.gianh.joinToString()} because it kept the Home button. Reach the maker's picture / sound menus with the Settings button on the clean Home; Restore brings it back."))
            taiLaiSaoLuu()
        }
        if (!kq.ok) {
            val goi = kq.conLai.ifBlank { t("không rõ", "unknown") }
            loi = if (kq.lyDo == "cvte")
                t("Nút Home trên tivi vẫn mở Home gốc ($goi). Tivi bo mạch CVTE: app không tắt Home gốc vì đã có tivi treo ở màn khởi động sau khi tắt. Tắt mở tivi một lần rồi bấm Home thử lại; vẫn về Home cũ thì Home sạch vẫn mở được từ danh sách ứng dụng.",
                    "The TV's Home button still opens the stock Home ($goi). CVTE board: the app never disables it, since one TV hung on its boot animation after that. Restart the TV once and press Home again; if it still opens the old Home, launch the clean Home from the app list.")
            else if (kq.lyDo == "setupwraith")
                t("Nút Home trên tivi vẫn mở Home Google ($goi). Trên Google TV phải tắt cả setupwraith thì mới thay được Home, mà mạng Wi-Fi lưu lúc cài tivi thuộc về nó nên app không tự tắt (tắt là rớt Wi-Fi). Tivi vẫn dùng bình thường như cũ. Muốn thay hẳn: trên tivi vào Cài đặt > Mạng, quên mạng Wi-Fi rồi kết nối lại (hoặc cắm dây LAN), sau đó kết nối lại app, ở bước chọn gói tích thêm setupwraith (nhóm Google Home) rồi dọn lại.",
                    "The TV's Home button still opens Google's Home ($goi). On Google TV setupwraith must be disabled too before Home can change, and the Wi-Fi saved during setup belongs to it, so the app leaves it (disabling it would drop the Wi-Fi). The TV works as before. To take over Home fully: on the TV, Settings > Network, forget the Wi-Fi and join it again (or plug in Ethernet), reconnect the app, tick setupwraith (Google Home group) in the package list and clean again.")
            else if (kq.lyDo == "khong-tat")
                t("Tivi không cho tắt Home gốc ($goi): tắt xong nó tự bật lại, và cách mở đè Home sạch cũng không ăn. App đã để tivi như cũ. Home sạch vẫn mở được từ danh sách ứng dụng. Gửi góp ý kèm hãng, model cho nhà phát triển để thêm hỗ trợ.",
                    "The TV won't keep the stock Home ($goi) disabled: it turns itself back on, and opening the clean Home over it didn't work either. The app left the TV as it was. Open the clean Home from the app list, and send feedback with the brand and model to the developer so they can add support.")
            else
                t("Nút Home trên tivi vẫn mở '$goi' thay vì Home sạch. App đã để tivi như cũ, không tắt thêm gì. Gửi góp ý kèm hãng, model cho nhà phát triển để thêm hỗ trợ.",
                    "The TV's Home button still opens '$goi' instead of the clean Home. The app left the TV as it was. Send feedback with the brand and model so the developer can add support.")
        }
    }

    private fun cauHinhHome(): String = JSONObject().apply {
        // Ô kênh (VTVgo, FPT Play, IPTV) đứng đầu dock, rồi tới app người dùng chọn. Chỉ ô có app trên tivi.
        val kenh = kenhChon.filter { trangThai?.caiDat?.contains(it.goi) == true }
        if (kenh.isNotEmpty()) put("link_tiles", JSONArray(kenh.map { it.dong() }))
        put("dock_apps", JSONArray(kenh.map { GOI_KENH + it.slug } + dock.toList()))
        if (tenChao.isNotBlank()) put("greeting_name", tenChao.trim())
        themeChon.ghiVao(this)
        if (anhNenRieng != null) { put("wallpaper", true); put("vgs_anh_rieng", true) }
        // Bộ ảnh riêng tự đổi (góp ý build.dng 27/10/2026): ảnh chính + ảnh thêm thành wallpapers/, đổi theo thứ tự.
        if (anhNenRieng != null && anhXoay.isNotEmpty()) {
            put("wallpaper_rotation_minutes", phutDoiAnh); put("wallpaper_order", "sequential"); put("vgs_bo_rieng", anhXoay.size + 1)
        }
        // Tivi Xiaomi bản Trung Quốc chỉ chuyển nguồn qua trình phát Xiaomi (upstream examples/xiaomi-mitv-astp0): Home sạch
        // (bản vá CleanTxa) đổi tên dòng HDMI1-3 / AV ra số cổng 23-25 / 2 khi input_action là EXTSRC_PLAY (23/10/2026).
        if (trangThai?.caiDat?.contains("com.xiaomi.mitv.tvplayer") == true) put("input_action", "com.xiaomi.mitv.tvplayer.EXTSRC_PLAY")
        put("dream_button", nutManCho)
        if (manChoDong) { put("dream_motion", true); put("clock_wallpaper", true); if (phutManCho > 0) put("home_idle_clock_minutes", phutManCho) }
        put("top_compact", gonNut)
        thanhPho?.let { put("weather_lat", it.lat); put("weather_lon", it.lon); put("weather_place", it.ten) }
        if (ghimMayChieu.isNotEmpty()) put("projector_actions", JSONArray(ghimMayChieu.map { (comp, nhan) -> "$nhan=$comp" }))
        put("lunar_date", amLich)
        if (nutTrenCung) put("top_buttons_top", true)
        // Âm lịch tính theo múi giờ: launcher tự chọn theo ngôn ngữ tivi, tivi để tiếng Anh ở VN thì lệch một ngày quanh
        // ngày sóc (Formovie L206, 22/10/2026: 12/9 thay vì 13/9). Ghi múi giờ của điện thoại khi là +7 / +8.
        if (amLich) (java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000).takeIf { it == 7 || it == 8 }?.let { put("lunar_utc_offset", it) }
        // Launcher 2.6: nút Input trên remote (VIEW_INPUTS) mở bảng chọn nguồn của Home sạch — đi cùng công tắc nút Input.
        // Ghi cả khi tắt: thiếu khoá thì lần kết nối sau công tắc lấy lại mặc định "bật" của Google TV (ngocnd156 29/10/2026).
        put("handle_view_inputs", nutInput)
        if (tenDuoiApp) put("always_show_names", true)
        if (!tenToTrenDock) put("dock_title", false)
        if (!toMauOApp) put("icon_tile_color", false)
        if (iconTo) put("icon_fill", true)
        if (thuVienApp) { put("app_library", true); put("library_button", !thuVienTrongMenu && !nutCaiDatThuVien) }
        // Giữ OK trên nút Cài đặt có dòng Thư viện ứng dụng khi nút trên thanh đầu tắt; viên cuối hàng cũng bỏ cho gọn.
        if (thuVienApp && (thuVienTrongMenu || nutCaiDatThuVien)) put("show_library_pill", false)
        // Nút Cài đặt thành lối vào Thư viện: bấm mở danh sách app, giữ vào cài đặt tivi, nút Menu trên nó ra menu cũ.
        if (thuVienApp && nutCaiDatThuVien) put("settings_button_library", true)
        if (thuVienApp && chiHienDock) put("dock_only", true)
        if (dongHoKhiYen > 0) put("home_idle_clock_minutes", dongHoKhiYen)
        if (tiviYeu) put("reduced_motion", true)
        if (hieuUngKinh) { put("dock_glass", true); put("app_glow", true) }
        if (lamToiNen > 0) put("wallpaper_dim", lamToiNen)
        if (hienThongSo) put("show_stats", true)
        // Giữ OK trên ô app: menu Di chuyển / Thêm vào dock / Bỏ khỏi Home / Thông tin (upstream #464, #746; tắt mặc định
        // ở upstream). CleanTxa bật sẵn vì người dùng xin đổi chỗ dock ngay trên tivi (22/10/2026); Nâng cao vẫn tắt được.
        put("tile_menu", true)
        // Giữa màn hình bỏ khoảng dời của theme (Kiểu Apple TV hạ dock 90dp) để hàng app nằm đúng giữa.
        if (doiHang == HANG_GIUA) { put("rows_middle", true); put("rows_offset_dp", 0) } else if (doiHang != 0) put("rows_offset_dp", doiHang)
        if (!nutDangPhat) put("now_playing", false)
        // Về Home là app cũ thôi phát tiếng, tắt màn cũng vậy (góp ý 01/11 + 03/11/2026); tắt cho ai muốn nhạc chạy tiếp.
        if (!dungTiengNen) put("pause_behind_home", false)
        if (coChuDau != 100) put("header_scale", coChuDau)
        if (motHang) { put("dock_row", true); put("single_row", true) }
        // Một hàng + thư viện app: viên Thư viện chen vào hàng app, đã có nút trên thanh trên cùng (góp ý Giun2911 25/10/2026).
        if (motHang && thuVienApp) put("show_library_pill", false)
        put("show_greeting", !anLoiChao)
        // CleanTxa Home đọc vài khoá theo tên riêng (khác Home sạch của CleanYourTV): ghi thêm để các công tắc có tác dụng.
        if (homeTxa) {
            put("show_quick_actions", txaNhanh)
            put("show_recent", txaGanDay)
            put("show_inputs", txaNguon)
            put("show_kids_button", txaNutTreEm)
            put("show_now_playing", nutDangPhat)
            put("show_input_button", nutInput)
            if (soCot > 0) put("columns", soCot)
            put("show_clock", txaDongHo)
            put("show_clock_seconds", txaGiay)
            put("show_date", txaNgay)
            put("clock_24h", txa24h)
            put("show_lunar_calendar", amLich)
            put("tile_scale", txaCoO)
            put("tile_corner_style", txaBoGoc)
            put("accent_color", txaMauNhan)
            put("show_wifi_button", txaNutWifi)
            put("show_settings_button", txaNutCaiDat)
            put("show_network_ip", txaHienIp)
            put("recents_button", nutDaNhiem)
        }
        put("uniform_tiles", oDeuNhau)
        if (moKhiBat) put("open_on_start_choice", true)
        if (moKhiBat && moKhiThuc) put("wake_open_app", true)
        if (luonVeHome) put("power_on_home_always", true)
        if (nutHenTat) put("sleep_timer_button", true)
        put("sleep_button", nutNgu)
        if (nutDaNhiem) put("recents_button", true)
        if (chuTrang) put("header_white", true)
        mauChu?.let { put("header_color", it) }
        if (khungDau) put("header_panel", true)
        if (soCot > 0) { put("columns", soCot); put("vgs_so_cot", true) }
        // Phím APPS (KEYCODE_ALL_APPS) chỉ là nút Home với que / tivi Xiaomi hoặc khi bật "Remote gửi phím Home lạ";
        // tivi khác (Sony VH2: nút Apps mở Home sạch như Back, abc999roses 24/10/2026) để phím cho tivi.
        put("all_apps_home", nutHome || thongTin?.brand?.equals("Xiaomi", ignoreCase = true) == true)
        // Ảnh riêng làm nền: tắt ánh sáng sau dock theo màu app (ambient_glow), không thì ảnh gia đình bị ám đỏ / vàng /
        // xanh mỗi lần đổi ô (góp ý dangngoctri2010, 23/10/2026). Nâng cao ghi sau nên người dùng vẫn bật lại được.
        if (anhNenRieng != null) put("ambient_glow", false)
        // Ảnh riêng hiện đúng như ảnh (25/10/2026, góp ý ảnh bị phủ mờ, loang màu): bỏ lớp phủ bầu trời theo giờ của theme,
        // giải mã màu 32-bit thay vì RGB_565. Nâng cao ghi sau nên vẫn đổi lại được.
        // Ảnh riêng sáng làm chìm chữ trắng (26/10/2026, Sony VH22): bóng mờ sau chữ đồng hồ, ngày, thời tiết.
        if (anhNenRieng != null) { put("sky_intensity", 0); put("wallpaper_true_color", true); put("header_shadow", true) }
        // Sony BRAVIA XR (Google TV): nút Khởi động lại trong hộp nguồn mở từ menu nút Cài đặt không chạy (góp ý moluna,
        // XR-65X90L 27/10/2026), nên ẩn dòng đó (power_menu_row của bản vá). Sony đời khác không hiện dòng này.
        if (thongTin?.brand?.equals("Sony", ignoreCase = true) == true && thongTin?.model?.startsWith("XR-", ignoreCase = true) == true) put("power_menu_row", false)
        // Tivi Xiaomi bản Trung Quốc không có tiếng Việt: Home sạch hiện chữ Trung (25/10/2026). Tivi đang để tiếng Trung mà
        // app dùng tiếng Việt thì ép Home sạch sang tiếng Việt (locale của bản vá launcher).
        if (com.txa.cleantxa.NgonNgu.tiengViet && Regex("""\[(persist\.sys\.locale|ro\.product\.locale)\]: \[zh""").containsMatchIn(getprop)) put("locale", "vi")
        // Phím remote của mẫu (app_keys, settings_key_keycodes…), HomeKeyService đọc. LUÔN ghi app_keys, kể cả khi bật
        // nút Input: 2.6.5 bỏ app_keys lúc đó (InputKeyService cũng đọc, sợ mở app hai lần — upstream tcl-55c725) mà nút
        // Input tự bật trên mọi Google TV, nên TCL Google TV mất hẳn nút YouTube (góp ý heavensky95, TCL G10, 24/10/2026).
        // Mở cùng một app hai lần chỉ là đưa nó lên trước.
        // TCL Google TV không dùng nút app remote: thôi lọc phím để giữ âm lượng lặp được (Nâng cao đặt key_filter thì đè sau).
        val tclGoogleTv = thongTin?.brand?.equals("TCL", ignoreCase = true) == true && trangThai?.caiDat?.contains("com.google.android.apps.tv.launcherx") == true
        if ((tclGoogleTv && !dungPhimRemote) || giuAmLuong) put("key_filter", false)
        if (dungPhimRemote) phimRemote()?.let { ph ->
            ph.keys().forEach { k -> if (!has(k)) put(k, ph.get(k)) }
            // Nút Web của remote TCL (4159, upstream tcl-g10-google-tv): mở trình duyệt nếu tivi có.
            if (thongTin?.brand?.equals("TCL", ignoreCase = true) == true) {
                val web = TRINH_DUYET_TV.firstOrNull { trangThai?.caiDat?.contains(it) == true }
                val ds = optJSONArray("app_keys")
                if (web != null && ds != null && (0 until ds.length()).none { ds.optString(it).startsWith("4159:") }) ds.put("4159:$web")
            }
        }
        // Nút remote người dùng tự gán: đứng đầu app_keys để đè bảng phím của mẫu.
        if (phimGan.isNotEmpty()) {
            val cu = optJSONArray("app_keys")
            put("app_keys", JSONArray(phimGan.toList() + (0 until (cu?.length() ?: 0)).map { cu!!.optString(it) }))
            put("vgs_phim_gan", JSONArray(phimGan.toList()))
        }
        // Nâng cao ghi sau cùng: người dùng đã chủ động đặt thì đè lên giá trị theme.
        val daDat = dsNangCao.mapNotNull { tc -> nangCao[tc.khoa]?.let { chu -> com.txa.cleantxa.dulieu.NangCao.giaTri(tc, chu)?.let { put(tc.khoa, it); tc.khoa } } }
        if (daDat.isNotEmpty()) put("vgs_nang_cao", JSONArray(daDat))
    }.toString()

    /** App tự cài hoặc chọn vào dock kiểu điện thoại (Phim4K, Appstore hãng…) vào library_apps để hiện trên Home sạch; dò lỗi thì gửi nguyên cấu hình. */
    private suspend fun voiAppNgoai(json: String): String =
        runCatching { com.txa.cleantxa.dulieu.AppNgoai.gop(json, io { tv.appNgoaiKhongLoiTv(dock.toSet()) }) }.getOrDefault(json)

    // ---------- Lưu / mở file cấu hình (góp ý 9000000, 23/10/2026) ----------
    var cauHinhBao by mutableStateOf("")

    /** Ghi mọi lựa chọn Home sạch (config.json + theme, nút Home, DNS, ảnh nền riêng) ra một file JSON người dùng chọn chỗ lưu. */
    fun xuatCauHinh(uri: android.net.Uri) = chay(t("Đang lưu file cấu hình…", "Saving the settings file…")) {
        val du = taoFileCauHinh()
        io { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(du) } ?: error("không mở được file") }
        cauHinhBao = t("Đã lưu file cấu hình.", "Settings file saved.")
    }

    val tenFileCauHinh get() = com.txa.cleantxa.dulieu.TenFile.cauHinh(thongTin?.model)

    private fun taoFileCauHinh(): ByteArray {
        val j = JSONObject(cauHinhHome())
        j.put("vgs_file", 1).put("cleantxa_theme", themeChon.id).put("vgs_nut_home", nutHome).put("vgs_dns", dnsChanQc)
        thongTin?.let { j.put("vgs_tivi", it.tenHienThi) }
        anhNenRieng?.let { j.put("vgs_anh_nen", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
        return j.toString(1).toByteArray()
    }

    /**
     * Xem trước lệnh (24/10/2026, học cleanyourtv-app "Customise shows every command"): các lệnh chính app sẽ gửi lên
     * tivi, theo thứ tự, trước khi bấm. Dòng "#" là chú thích. Gói lõi Android / Home đang dùng app vẫn tự bỏ qua lúc chạy.
     */
    fun lenhDon(): List<String> = buildList {
        add(t("# 1. Sao lưu (chỉ đọc, không đổi gì)", "# 1. Backup (read-only, changes nothing)"))
        add("pm list packages -d")
        add("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        add("settings get secure enabled_accessibility_services")
        add("settings get global private_dns_mode")
        val ds = muc.filter { chon[it.pkg] == true }.map { it.pkg }
        add(t("# 2. Tắt ${ds.size} gói (chỉ tắt, không gỡ; Khôi phục bật lại được)", "# 2. Disable ${ds.size} packages (disable only, never uninstall; Restore turns them back on)"))
        ds.forEach { add("pm disable-user --user 0 $it") }
    }

    fun lenhHome(): List<String> = buildList {
        val pk = tv.pkgHome
        val goc = trangThai?.homeHienTai?.substringBefore('/').orEmpty()
        if (!daCoHomeSach) {
            add(t("# Cài Home sạch (APK đi kèm app)", "# Install the clean Home (APK bundled in the app)"))
            add("pm install -r ${tv.apkHome}")
            add("cmd package compile -m speed -f $pk")
        }
        add(t("# Ghi giao diện", "# Write the look"))
        add("content write --uri content://$pk.theme/config.json")
        if (!daCoHomeSach && goc.isNotBlank() && !goc.startsWith(pk)) {
            add(t("# Tắt Home gốc, đặt Home sạch làm Home (tivi tự canh gác: rớt kết nối là tự bật lại Home gốc)", "# Disable the stock Home and make the clean Home default (the TV watches: if the connection drops it restores the stock Home)"))
            add("pm disable-user --user 0 $goc")
        }
        add("cmd package set-home-activity --user 0 ${tv.cmpHome}")
        if (canDichVuPhim) add("settings put secure enabled_accessibility_services $pk/com.gathin.tvhome.HomeKeyService")
        if (nutInput) add("settings put secure enabled_accessibility_services $pk/com.gathin.tvhome.InputKeyService")
        if (dnsChanQc) add("settings put global private_dns_specifier dns.adguard-dns.com")
        add(t("# Thử nút Home thật", "# Test the real Home button"))
        add("input keyevent KEYCODE_HOME")
    }

    /** Lưu kịch bản khôi phục cho máy tính (dulieu/KichBanKhoiPhuc) từ bản sao lưu của tivi đang mở. */
    fun xuatKichBan(uri: android.net.Uri) = chay(t("Đang lưu kịch bản khôi phục…", "Saving the restore script…")) {
        val chu = kichBan()
        io { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(chu.toByteArray()) } ?: error("không mở được file") }
        cauHinhBao = t("Đã lưu kịch bản khôi phục.", "Restore script saved.")
    }

    private suspend fun kichBan(): String {
        val sl = saoLuu ?: error(t("Chưa có bản sao lưu của tivi này.", "No backup for this TV yet."))
        // Chạy ngay trên tivi thì sao lưu ghi 127.0.0.1: kịch bản cho máy tính cần IP mạng thật của tivi.
        val ipLan = if (sl.ip == "127.0.0.1" || sl.ip == "localhost") runCatching { io { tv.ipMang() } }.getOrNull() else null
        return com.txa.cleantxa.dulieu.KichBanKhoiPhuc.tao(sl, com.txa.cleantxa.NgonNgu.tiengViet, ipLan)
    }

    val tenFileKichBan get() = com.txa.cleantxa.dulieu.TenFile.kichBanKhoiPhuc(saoLuu?.ten)

    // ---------- File ngay trên tivi (CleanTxa chạy trên tivi, 29/10/2026) ----------
    // Android TV thường không có trình chọn file (hộp Lưu / Mở báo "không hỗ trợ" hay không có gì): lưu thẳng vào
    // Download của tivi, mở file từ Download / USB qua ADB như ảnh nền.

    /** Hộp chọn file trên tivi: loại ("json" hoặc "apk") + đường dẫn; null = đóng. */
    var fileTrenTv by mutableStateOf<Pair<String, List<String>>?>(null)

    fun moFileTrenTv(loai: String) = chay(t("Đang tìm file trên tivi…", "Looking for files on the TV…")) {
        fileTrenTv = loai to io { tv.timFileTrenTv(listOf(loai)) }
    }

    fun chonFileTrenTv(duongDan: String) = chay(t("Đang đọc file từ tivi…", "Reading the file from the TV…")) {
        val loai = fileTrenTv?.first
        fileTrenTv = null
        val du = io { tv.docFileTv(duongDan) } ?: error(t("Không đọc được file này (quá 25 MB hoặc hỏng).", "Couldn't read this file (over 25 MB or broken)."))
        if (loai == "apk") caiApkByte(du) else napFileCauHinh(du.toString(Charsets.UTF_8))
    }

    fun xuatCauHinhTv() = chay(t("Đang lưu file cấu hình…", "Saving the settings file…")) {
        val du = taoFileCauHinh()
        val p = io { tv.ghiVaoDownload(tenFileCauHinh, du) }
        cauHinhBao = t("Đã lưu vào $p (thư mục Download của tivi).", "Saved to $p (the TV's Download folder).")
    }

    /**
     * Tự chép kịch bản khôi phục vào Download của tivi sau khi Dọn / Cài Home sạch (góp ý gy_mvy0wvet, 01/11/2026): lỡ mất
     * điện thoại hay xoá app vẫn còn đường khôi phục bằng máy tính. Không ghi được thì thôi, không chặn việc chính.
     */
    private suspend fun luuKichBanVaoTv() {
        if (saoLuu == null) return
        // Hai file vào Download của tivi mỗi lần cài / dọn: kịch bản khôi phục và file cấu hình Home vừa áp dụng.
        // Mỗi cái ghi riêng và báo rõ được hay không, không im lặng bỏ qua như trước.
        runCatching { val chu = kichBan(); io { tv.ghiVaoDownload(tenFileKichBan, chu.toByteArray()) } }
            .onSuccess { p -> ghi(t("Đã chép kịch bản khôi phục vào $p trên tivi.", "Copied the restore script to $p on the TV.")) }
            .onFailure { e -> ghi(t("Không chép được kịch bản khôi phục vào Download của tivi (${e.message ?: e.javaClass.simpleName}). Bản sao lưu trong app vẫn còn.", "Couldn't copy the restore script to the TV's Download folder (${e.message ?: e.javaClass.simpleName}). The backup in the app is still there.")) }
        runCatching { val du = taoFileCauHinh(); io { tv.ghiVaoDownload(tenFileCauHinh, du) } }
            .onSuccess { p -> ghi(t("Đã lưu file cấu hình Home vào $p trên tivi.", "Saved the Home settings file to $p on the TV.")) }
            .onFailure { e -> ghi(t("Không lưu được file cấu hình vào Download của tivi (${e.message ?: e.javaClass.simpleName}).", "Couldn't save the settings file to the TV's Download folder (${e.message ?: e.javaClass.simpleName}).")) }
    }

    fun xuatKichBanTv() = chay(t("Đang lưu kịch bản khôi phục…", "Saving the restore script…")) {
        val chu = kichBan()
        val p = io { tv.ghiVaoDownload(tenFileKichBan, chu.toByteArray()) }
        cauHinhBao = t("Đã lưu vào $p. Chép file này sang USB để chạy trên máy tính.", "Saved to $p. Copy it to a USB stick to run it on a computer.")
    }

    /** Mở file cấu hình đã lưu: điền lại mọi lựa chọn, bấm Áp dụng mới đưa lên tivi. */
    fun nhapCauHinh(uri: android.net.Uri) = chay(t("Đang mở file cấu hình…", "Opening the settings file…")) {
        val chu = io { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } ?: error(t("Không đọc được file.", "Couldn't read the file."))
        napFileCauHinh(chu)
    }

    private fun napFileCauHinh(chu: String) {
        val j = runCatching { JSONObject(chu) }.getOrNull()?.takeIf { it.has("vgs_file") || it.has("dock_apps") }
            ?: error(t("File này không phải file cấu hình CleanTxa.", "This isn't a CleanTxa settings file."))
        napTuJson(j)
        com.txa.cleantxa.dulieu.THEMES.firstOrNull { it.id == j.optString("cleantxa_theme") }?.let { themeChon = it }
        if (j.has("vgs_nut_home")) nutHome = j.optBoolean("vgs_nut_home")
        if (j.has("vgs_dns")) dnsChanQc = j.optBoolean("vgs_dns")
        anhNenRieng = j.optString("vgs_anh_nen").takeIf { it.isNotBlank() }?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() }
        cauHinhBao = t("Đã mở file cấu hình", "Settings file loaded") + (j.optString("vgs_tivi").takeIf { it.isNotBlank() }?.let { t(" (của $it)", " (from $it)") } ?: "") +
            t(". Bấm \"Áp dụng lên tivi\" để đưa lên tivi.", ". Press \"Apply to the TV\" to send it.")
    }

    /** Ảnh logo kênh (assets/kenh, ảnh nhỏ của VTVgo) cho các kênh đang chọn; launcher không có mạng nên gửi kèm. */
    private suspend fun napAnhKenh() {
        val ds = if (dsKenhGo.isEmpty()) io { com.txa.cleantxa.dulieu.KenhVtvGo.docNho(ctx) } else dsKenhGo
        tv.anhKenh = kenhChon.filter { trangThai?.caiDat?.contains(it.goi) == true }.mapNotNull { o ->
            val f = o.slug + ".jpg"
            // VTV1–9 của VTVgo có ảnh sẵn trong app; ô khác tải ảnh (thumbnail VTVgo, og:image FPT Play, tvg-logo IPTV).
            val sanCo = if (o.goi == GOI_VTVGO) runCatching { io { ctx.assets.open("kenh/$f").readBytes() } }.getOrNull() else null
            val url = anhCuaO(o) ?: if (o.goi == GOI_VTVGO) ds.firstOrNull { o.uri.endsWith("contentId=" + it.ma) }?.anh else null
            (sanCo ?: url?.let { u -> io { com.txa.cleantxa.dulieu.KenhVtvGo.taiAnh(u) } })?.let { f to it }
        }
    }

    private suspend fun anhNenTheme(): ByteArray? = anhNenRieng ?: themeChon.anhNen?.let { a -> io { ctx.assets.open(a).readBytes() } }

    /** Ảnh nền người dùng chọn từ điện thoại (góp ý 23/10/2026), đã thu về 1920×1080 JPEG; null = theo theme. */
    var anhNenRieng by mutableStateOf<ByteArray?>(null)
    /** Ảnh gốc vừa chọn (Uri trên điện thoại hoặc byte đọc từ tivi), để đổi cắt / vừa khung mà không phải chọn lại. */
    private var nguonAnh: Any? = null
    /** Ảnh riêng vừa khung, không cắt (viền mờ). 3.0.0, góp ý hieuinter. */
    var anhVuaKhung by mutableStateOf(false)
    fun doiVuaKhung(v: Boolean) = chay(t("Đang chuẩn bị ảnh…", "Preparing the picture…")) {
        anhVuaKhung = v
        when (val n = nguonAnh) {
            is android.net.Uri -> io { com.txa.cleantxa.dulieu.AnhNen.chuanBi(ctx, n, v) }?.let { anhNenRieng = it }
            is ByteArray -> io { com.txa.cleantxa.dulieu.AnhNen.chuanBiByte(n, v) }?.let { anhNenRieng = it }
        }
    }

    /** Ảnh thêm sau ảnh riêng chính, cùng xoay vòng trên tivi (wallpapers/); rỗng = một ảnh đứng yên. */
    var anhXoay by mutableStateOf<List<ByteArray>>(emptyList())
    /** Mấy phút đổi ảnh một lần khi có bộ ảnh riêng (wallpaper_rotation_minutes). */
    var phutDoiAnh by mutableStateOf(15)

    fun themAnhXoay(uris: List<android.net.Uri>) = chay(t("Đang chuẩn bị ${uris.size} ảnh…", "Preparing ${uris.size} pictures…")) {
        val moi = io { uris.mapNotNull { com.txa.cleantxa.dulieu.AnhNen.chuanBi(ctx, it, anhVuaKhung) } }
        anhXoay = (anhXoay + moi).take(ANH_XOAY_TOI_DA)
    }

    fun boAnhRieng() { anhNenRieng = null; anhXoay = emptyList() }

    /** Danh sách ảnh trên tivi / USB (null = chưa mở hộp chọn). */
    var anhTrenTv by mutableStateOf<List<String>?>(null)

    fun moAnhTrenTv() = chay(t("Đang tìm ảnh trên tivi…", "Looking for pictures on the TV…")) {
        anhTrenTv = io { tv.timAnhTrenTv() }
    }

    fun chonAnhTrenTv(duongDan: String) = chay(t("Đang đọc ảnh từ tivi…", "Reading the picture from the TV…")) {
        anhTrenTv = null
        val du = io { tv.docFileTv(duongDan) } ?: error(t("Không đọc được ảnh này (quá lớn hoặc hỏng), thử ảnh khác.", "Couldn't read this picture (too big or broken), try another one."))
        nguonAnh = du
        // Ảnh dọc thì bật sẵn vừa khung; muốn phủ kín thì tắt công tắc (30/10/2026).
        if (com.txa.cleantxa.dulieu.AnhNen.laAnhDocByte(du)) anhVuaKhung = true
        anhNenRieng = io { com.txa.cleantxa.dulieu.AnhNen.chuanBiByte(du, anhVuaKhung) } ?: error(t("Không mở được ảnh này (HEIC thì đổi sang JPG), thử ảnh khác.", "Couldn't open this picture (convert HEIC to JPG), try another one."))
    }

    fun chonAnhNen(uri: android.net.Uri) = chay(t("Đang chuẩn bị ảnh…", "Preparing the picture…")) {
        nguonAnh = uri
        if (io { com.txa.cleantxa.dulieu.AnhNen.laAnhDoc(ctx, uri) }) anhVuaKhung = true
        anhNenRieng = io { com.txa.cleantxa.dulieu.AnhNen.chuanBi(ctx, uri, anhVuaKhung) } ?: error(t("Không đọc được ảnh này, thử ảnh khác.", "Couldn't read this picture, try another one."))
    }

    /** Bộ ảnh xoay vòng của theme: mọi file ảnh trong thư mục assets boAnh. */
    private suspend fun boAnhTheme(): List<Pair<String, ByteArray>>? = anhNenRieng?.let { chinh ->
        if (anhXoay.isEmpty()) null else (listOf(chinh) + anhXoay).mapIndexed { i, b -> "r%02d.jpg".format(i + 1) to b }
    } ?: if (anhNenRieng != null) null else themeChon.boAnh?.let { thu ->
        io { (ctx.assets.list(thu) ?: emptyArray()).sorted().map { it to ctx.assets.open("$thu/$it").readBytes() } }
    }

    /** Đổi theme / dock cho tivi đã có Home sạch. */
    fun apDungTheme() = chay(t("Đang đổi giao diện…", "Applying the theme…")) { apDungThemeBen() }

    private suspend fun apDungThemeBen() {
        val lc = linkClient
        if (!dangAdb && lc != null) {
            // Đang nối ngầm không cần ADB: ghi thẳng cấu hình, ảnh nền, bộ ảnh và logo kênh qua ThemeProvider của Home sạch trên TV!
            val targetPkg = if (homeTxa) ThaoTacTv.PKG_HOME_TXA else goiHome
            val json = cauHinhHome()
            val anh = anhNenTheme()
            val bo = boAnhTheme()
            napAnhKenh()
            thongBao = t("Đang gửi giao diện sang tivi qua Liên kết ngầm…", "Sending look to TV over Wi-Fi Link…")
            val res = io { lc.apDungHome(targetPkg, json, anh, bo, tv.anhKenh) }
            if (!res.optBoolean("ok", false)) {
                error(res.optString("error", t("Chưa áp dụng được giao diện qua Liên kết ngầm.", "Couldn't apply look over Wi-Fi Link.")))
            }
            homeXong = true
            cauHinhBao = t("Đã áp dụng giao diện mới lên tivi (không cần ADB)!", "Applied new look to the TV (no ADB needed)!")
            return
        }
        val json = voiAppNgoai(cauHinhHome())
        val anh = anhNenTheme()
        napAnhKenh()
        // Cài đè launcher đi kèm app (bản mới có dòng thời tiết…), giữ dữ liệu và dịch vụ trợ năng.
        thongBao = t("Đang cập nhật Home sạch…", "Updating the clean Home…")
        if (!io { tv.capNhatLauncher(com.txa.cleantxa.CapNhat.moAsset(ctx, tv.apkHome).readBytes()) }) {
            if (tv.thieuBoNho) { canDonBoNho = true; loi = tv.baoThieuBoNho(io { tv.boNhoTrong() }); return }
            loi = t("Không cập nhật được Home sạch trên tivi, giao diện mới sẽ không hiện đủ. Thử lại hoặc gửi góp ý kèm hãng, model.", "Couldn't update the clean Home on the TV, so the new look won't fully show. Try again or send feedback with the brand and model.")
            return
        }
        if (anh != null) thongBao = t("Đang gửi ảnh nền (${anh.size / 1024} KB)…", "Sending the wallpaper (${anh.size / 1024} KB)…")
        val bo = boAnhTheme()
        if (bo != null) thongBao = t("Đang gửi ${bo.size} ảnh nền…", "Sending ${bo.size} wallpaper pictures…")
        io { tv.dayCauHinh(json, anh, bo) }
        io { tv.datNutInput(nutInput) }
        if (dungPhimRemote || (moKhiBat && moKhiThuc) || luonVeHome || phimGan.isNotEmpty() || nutHenTat || nutDaNhiem) io { tv.batBatPhimHome() }
        // Đổi giao diện cũng bật / tắt DNS AdGuard theo công tắc (trước 2.7.3 chỉ lúc cài Home sạch mới đặt).
        val dangChan = io { tv.dnsDangChan() }
        if (dnsChanQc && !dangChan) saoLuu?.let { sl -> io { tv.batDnsChanQc(sl) } }
        if (!dnsChanQc && dangChan) io { tv.tatDnsChanQc(saoLuu) }
        if (nutDangPhat || dungTiengNen) io { tv.choPhepDangPhat() }
        if (nutManCho) io { tv.chuanBiManCho(saoLuu) }
        io { tv.datManChoDong(manChoDong, saoLuu) }
        thongBao = t("Đang thử nút Home trên tivi…", "Testing the TV's Home button…")
        val sl = saoLuu ?: thongTin?.let { tt -> runCatching { io { tv.damBaoSaoLuu(tt, getprop, ip) } }.getOrNull() }?.also { saoLuu = it }
        thuPhimHome(sl)
        homeXong = true
        man = Man.XONG
        // 3.5.16: tivi không cho cấp quyền mà Luôn mở / Mở đè cần (Xiaomi MZTU0, famquanh) thì nói ra, đừng để người dùng tưởng đã xong.
        if (tv.thieuQuyen.isNotEmpty()) loi = t("Tivi không cho Home sạch quyền ${tv.thieuQuyen}, nên \"Luôn mở Home sạch khi bật tivi\" có thể không chạy. Bấm Áp dụng lên tivi lại; vẫn báo thì gửi góp ý kèm model tivi.",
            "The TV refused the clean Home permission to ${tv.thieuQuyen}, so \"Always open the clean Home at power-on\" may not work. Apply again; if this shows again, send feedback with the TV model.")
    }

    fun khoiDongLaiTv() = chay(t("Đang khởi động lại tivi…", "Rebooting the TV…")) {
        io { runCatching { adb.shell("reboot") } }
        runCatching { io { adb.disconnect() } }
        thongBao = ""
        loi = t("Tivi đang khởi động lại. Đợi tivi lên rồi bấm Kết nối lại để kiểm tra (gói nào tự bật lại sau khởi động sẽ hiện ra).", "The TV is rebooting. When it is back, press Connect again to check (packages that re-enable at boot will show up).")
        man = Man.KET_NOI
    }

    /**
     * Tự tắt ADB khi xong (24/10/2026, học cleanyourtv-app): đang ở màn Xong mà người dùng rời app thì hẹn TIVI tự tắt
     * ADB sau 60 giây (hẹn giờ phải nằm trên tivi: Android 14 đóng băng app nằm nền). Quay lại trước đó thì huỷ; quá
     * hạn thì về màn chính, báo đã tự tắt. Bật "Giữ ADB bật cho lần sau" thì không hẹn.
     */
    var giuAdb by mutableStateOf(false)
    var daTuTatAdb by mutableStateOf(false)
    private var daHenTatAdb = false

    /** Ngắt phiên Liên kết ngầm Wi-Fi hiện tại (nhưng vẫn giữ danh sách tin cậy cho lần sau). */
    fun ngatLienKetNgam() {
        if (!dangAdb) {
            datLai()
            loi = ""
            man = Man.CHINH
        } else {
            linkClient = null
        }
    }

    /** App quay lại từ nền khi đang nối tivi: kiểm kết nối, rớt thì nối lại ngầm (không đụng khi đang chạy việc). */
    fun kiemKetNoiKhiVe() {
        if (dangBan || daHenTatAdb) return
        if (thongTin == null) {
            if (!com.txa.cleantxa.ManHinh.laTv && com.txa.cleantxa.link.TxaLinkStore.tuKetNoiNgam(ctx)) {
                quetLinkNgam(tuKetNoi = true)
            }
            return
        }
        if (!dangAdb && linkClient != null) {
            val lc = linkClient ?: return
            viewModelScope.launch {
                runCatching {
                    val st = io { lc.docTrangThaiDayDu() }
                    trangThai = st.trangThai
                    boNho = st.boNho
                    ramTv = st.ram
                    amLuongTv = st.volCur to st.volMax
                    doToiTv = st.dimPercent
                    dangChieuAnhTv = st.photoShowing
                    appDangMoTrenTv = st.activePkg
                    henTatLuc = st.sleepTimerSec
                }.onFailure {
                    if (com.txa.cleantxa.link.TxaLinkStore.tuKetNoiNgam(ctx)) quetLinkNgam(tuKetNoi = true)
                }
            }
            return
        }
        viewModelScope.launch { runCatching { io { tv.giuKetNoi() } } }
    }

    /**
     * Bao lâu sau khi rời màn Xong thì tivi tự tắt ADB. Chạy ngay trên tivi thì 20 phút (issue #6, HoangTran0410,
     * 01/11/2026): màn Xong bảo bấm Home xem thử, thử YouTube/HDMI — đi thử là rời app, 60 giây là ADB tắt trước khi kịp
     * quay lại chọn Giữ / Tắt. Trên điện thoại rời app là thôi dùng, giữ 60 giây.
     */
    val phutTuTatAdb: Int get() = if (com.txa.cleantxa.ManHinh.laTv) 20 else 1

    fun henTuTatAdb() {
        if (giuAdb || man != Man.XONG || dangBan) return
        daHenTatAdb = true
        viewModelScope.launch { runCatching { io { tv.henTatAdbTrenTv(phutTuTatAdb * 60) } }.onFailure { daHenTatAdb = false } }
    }

    fun huyTuTatAdb() {
        if (!daHenTatAdb) return
        daHenTatAdb = false
        viewModelScope.launch {
            // Huỷ không được (tivi đã tắt ADB, kết nối mất): coi như đã tự tắt.
            if (!runCatching { io { tv.huyTatAdbTrenTv() } }.getOrDefault(false)) baoDaTuTatAdb()
        }
    }

    /** Danh sách app chiếm chỗ đang mở (null = đóng hộp). */
    var appChiemCho by mutableStateOf<List<com.txa.cleantxa.adb.AppChiemCho>?>(null)
    /** Việc chờ xác nhận trong hộp: gói + "cap-nhat" | "du-lieu". */
    var hoiDonApp by mutableStateOf<Pair<String, String>?>(null)

    fun moAppChiemCho() = chay(t("Đang xem app nào chiếm chỗ…", "Checking which apps use the space…")) {
        val ds = io { tv.appChiemCho() }
        if (ds.isEmpty()) error(t("Tivi không cho biết dung lượng từng app. Vào Cài đặt > Ứng dụng trên tivi, gỡ bản cập nhật hoặc xoá dữ liệu app lớn (YouTube, CH Play…).",
            "The TV doesn't report app sizes. In the TV's Settings > Apps, uninstall updates or clear data of big apps (YouTube, Play Store…)."))
        appChiemCho = ds
    }

    fun donApp(pkg: String, viec: String) = chay(t("Đang dọn ${com.txa.cleantxa.dulieu.tenDep(pkg)}…", "Cleaning up ${com.txa.cleantxa.dulieu.tenDep(pkg)}…")) {
        val truoc = io { tv.boNhoTrong() }?.first
        val ok = io { if (viec == "cap-nhat") tv.goCapNhat(pkg) else tv.xoaDuLieu(pkg) }
        val sau = io { tv.boNhoTrong() }
        boNho = sau
        appChiemCho = io { tv.appChiemCho() }
        if (!ok) error(t("Tivi không cho làm việc này với ${com.txa.cleantxa.dulieu.tenDep(pkg)}.", "The TV refused this for ${com.txa.cleantxa.dulieu.tenDep(pkg)}."))
        val duoc = if (truoc != null && sau != null && sau.first > truoc) ThaoTacTv.mb(sau.first - truoc) else "0 MB"
        thongBaoDon = t("Lấy lại $duoc. Tivi còn trống ${sau?.let { ThaoTacTv.mb(it.first) } ?: "?"}" +
            (sau?.let { if (it.first >= ThaoTacTv.canTrong(it.second)) ", đủ để cài Home sạch." else ", cần ${ThaoTacTv.mb(ThaoTacTv.canTrong(it.second))}." } ?: "."),
            "Freed $duoc. The TV now has ${sau?.let { ThaoTacTv.mb(it.first) } ?: "?"} free" +
            (sau?.let { if (it.first >= ThaoTacTv.canTrong(it.second)) ", enough for the clean Home." else ", it needs ${ThaoTacTv.mb(ThaoTacTv.canTrong(it.second))}." } ?: "."))
    }
    /** Kết quả lần dọn app gần nhất, hiện trong hộp app chiếm chỗ. */
    var thongBaoDon by mutableStateOf("")

    /** Bấm "Giữ ADB": thử lại kết nối trước, ADB đã tắt thì nói rõ thay vì giả như giữ được (issue #6). */
    fun chonGiuAdb(sau: () -> Unit = {}) {
        giuAdb = true
        viewModelScope.launch {
            if (runCatching { io { tv.giuKetNoi() } }.getOrDefault(false)) sau() else baoDaTuTatAdb()
        }
    }

    private suspend fun baoDaTuTatAdb() {
        runCatching { io { adb.disconnect() } }
        datLai()
        daTuTatAdb = true
        man = Man.CHINH
    }

    fun tatAdbVaKetThuc() = chay(t("Đang tắt ADB trên tivi…", "Turning ADB off on the TV…")) {
        io { tv.tatAdb() }
        runCatching { io { adb.disconnect() } }
        val host = ip
        val conMo = io { Thread.sleep(2500); moCong(host, 5555, 1500) }
        loi = if (conMo) t("Cổng 5555 vẫn mở. Vào Tuỳ chọn nhà phát triển trên tivi, tắt Gỡ lỗi USB / Gỡ lỗi qua mạng rồi khởi động lại tivi.", "Port 5555 is still open. In Developer options on the TV, turn off USB / Network debugging and reboot.")
        else ""
        thongBao = ""
        datLai()
        man = Man.CHINH
        // ADB đã tắt nhưng nếu TV có CleanTxa chạy ngầm và đã tin cậy, tự nối lại ngầm luôn!
        quetLinkNgam(tuKetNoi = true)
    }

    // ---------- Khôi phục ----------

    fun moKhoiPhuc(b: BanSaoLuu) {
        dangKhoiPhuc = true
        ip = b.ip
        man = Man.KET_NOI
    }

    /** batTatCa: bật lại cả gói đang tắt ngoài bản sao lưu — gói lần dọn trước tắt bị lần dọn sau coi là "vốn đã tắt" (05/11/2026, góp ý ngocnd156). */
    fun chayKhoiPhuc(goLauncher: Boolean, batTatCa: Boolean = false) = chay(t("Đang khôi phục…", "Restoring…")) {
        val info = thongTin ?: return@chay
        val sl = kho.doc(info.khoaLuu) ?: error(t("Tivi này chưa có bản sao lưu trong máy.", "This phone has no backup for this TV."))
        nhatKy.clear()
        val loiKp = io { tv.khoiPhuc(sl, goLauncher) { s -> ghiTuIo(s) } + (if (batTatCa) tv.batLaiMoiGoiTat { s -> ghiTuIo(s) } else emptyList()) }
        veApp()
        loiKp.forEach { ghi(t("Lỗi: $it", "Error: $it")) }
        if (loiKp.isEmpty() && sl.daTatBoiApp.isEmpty() && !sl.launcherDaCai && !sl.dnsDaDoi) {
            kho.xoa(sl.khoa)
            io { tv.xoaSaoLuuTrenTv() }
            khoiPhucXong = true
            ghi(t("Đã khôi phục xong, xoá bản sao lưu.", "Restore complete, backup removed."))
        } else ghi(t("Khôi phục xong một phần, bản sao lưu vẫn giữ để chạy lại.", "Partly restored; the backup is kept so you can run it again."))
        saoLuu = kho.doc(info.khoaLuu)
        taiLaiSaoLuu()
    }

    /**
     * Fire TV / Fire OS (23/10/2026, Fire TV Cube 2 AFTR): Amazon giữ Home cho launcher của họ — đặt Home bị bỏ qua,
     * launcher là gói được bảo vệ không tắt được, dịch vụ trợ năng không nhận phím Home (upstream TROUBLESHOOTING
     * "Fire TV"). Chỉ dọn được, không thay Home.
     */
    val laFireTv: Boolean get() = (thongTin?.model?.startsWith("AFT") == true) ||
        trangThai?.caiDat?.contains("com.amazon.tv.launcher") == true

    /** Android 7 trở xuống (SDK < 26): Home sạch cần Android 8+ (23/10/2026, hộp MXQ Pro Android 7.1). Chỉ dọn được. */
    val laAndroidCu: Boolean get() = thongTin?.sdk?.let { it in 1..25 } == true

    /** Không cài được Home sạch trên tivi này: chỉ dọn. */
    val khongCaiHome: Boolean get() = laFireTv || laAndroidCu

    /** Tivi có Home sạch (bản nào cũng được): đã từng dọn bằng CleanTxa / CleanYourTV. */
    val coDauCleanTv: Boolean get() = trangThai?.let { tt -> ThaoTacTv.HOME_SACH.any { it in tt.caiDat } } == true

    /** Gói đang tắt trên tivi khi không có bản sao lưu (null = chưa đọc). */
    var goiTatKhongSaoLuu by mutableStateOf<List<String>?>(null)

    fun docGoiTatKhongSaoLuu() = chay(t("Đang đọc gói đang tắt…", "Reading disabled packages…")) {
        goiTatKhongSaoLuu = io { tv.goiTatBoiNguoiDung() }
    }

    // ---------- Bật lại từng app (issue vgs-cleantv#3, 23/10/2026) ----------
    /** Gói đang tắt kiểu disable-user, đọc thẳng từ tivi (không cần bản sao lưu); null = chưa đọc. */
    var dsTatTv by mutableStateOf<List<String>?>(null)
    val chonBatLai = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    var batLaiBao by mutableStateOf("")

    /** Gói tắt kiểu disable-user trong dsTatTv; gói còn lại là tắt kiểu khác (hãng tắt sẵn hoặc pm disable). */
    var dsTatNguoiDung by mutableStateOf<Set<String>>(emptySet())

    private fun docLaiDsTat() { dsTatTv = tv.goiDangTat(); dsTatNguoiDung = tv.goiTatBoiNguoiDung().toSet() }

    fun docDsTat() = chay(t("Đang đọc app đang tắt trên tivi…", "Reading disabled apps on the TV…")) {
        io { docLaiDsTat() }; chonBatLai.clear(); batLaiBao = ""
    }

    fun batLaiDaChon() = chay(t("Đang bật lại…", "Re-enabling…")) {
        val ds = chonBatLai.filterValues { it }.keys.sorted()
        val sl = saoLuu
        var ok = 0
        ds.forEachIndexed { i, p ->
            thongBao = t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p")
            if (io { tv.batLaiGoi(p, sl) }) ok++
        }
        io { docLaiDsTat() }; chonBatLai.clear()
        batLaiBao = if (ok == ds.size) t("Đã bật lại $ok app.", "Re-enabled $ok apps.") else t("Bật lại được $ok/${ds.size} app; app còn lại tivi không cho bật.", "Re-enabled $ok/${ds.size}; the TV refused the rest.")
        if (sl != null) saoLuu = sl
    }

    fun chayKhoiPhucKhongSaoLuu(goLauncher: Boolean) = chay(t("Đang khôi phục…", "Restoring…")) {
        nhatKy.clear()
        val loiKp = io { tv.khoiPhucKhongSaoLuu(goLauncher) { s -> ghiTuIo(s) } }
        veApp()
        loiKp.forEach { ghi(t("Lỗi: $it", "Error: $it")) }
        if (loiKp.isEmpty()) khoiPhucXong = true
        else ghi(t("Khôi phục xong một phần, chạy lại để thử tiếp.", "Partly restored; run it again to retry."))
        goiTatKhongSaoLuu = null
    }

    fun datLai() {
        dangAdb = false; linkClient = null; ramTv = null
        amLuongTv = null; doToiTv = 0; dangChieuAnhTv = false; appDangMoTrenTv = ""
        thongTin = null; trangThai = null; muc = emptyList(); chon.clear(); ketQua = null
        dock.clear(); tenChao = ""; homeXong = false; dnsChanQc = false; dangKhoiPhuc = false; khoiPhucXong = false; anhTv = null
        laMayChieu = false; mayChieu = null; ghimMayChieu.clear()
        nhatKy.clear(); saoLuu = null; saoLuuTuTv = false; goiTatKhongSaoLuu = null; epSetupwraith = false
        dsTatTv = null; chonBatLai.clear(); batLaiBao = ""
        taiLaiSaoLuu()
    }

    fun veManChinh() {
        viewModelScope.launch { runCatching { io { adb.disconnect() } } }
        datLai(); loi = ""; man = Man.CHINH
    }

    companion object {
        val UU_TIEN_DOCK = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.fptplay.tv", "net.fptplay.ottbox",
            "com.vieon.tv", "vn.vieon.tv", "com.viettel.tv360", "com.vnpt.mytv", "com.galaxyplay.tv",
            "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.spotify.tv.android",
            "com.android.vending",
        )
    }
}
