package com.txa.cleantxa.dulieu

/** Tên các file CleanTxa lưu cho người dùng (file cấu hình, kịch bản khôi phục). Đường dẫn nội bộ trên tivi giữ nguyên. */
object TenFile {
    private const val TIEN_TO = "cleantxa-"

    /** File cấu hình Home: cleantxa-<model>.json. */
    fun cauHinh(model: String?): String =
        TIEN_TO + (model ?: "tivi").replace(Regex("[^A-Za-z0-9]+"), "-") + ".json"

    /** Kịch bản khôi phục chạy trên máy tính: cleantxa-khoi-phuc-<tên tivi>.bat. */
    fun kichBanKhoiPhuc(tenTivi: String?): String =
        TIEN_TO + "khoi-phuc-" + (tenTivi ?: "tivi").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30) + ".bat"
}
