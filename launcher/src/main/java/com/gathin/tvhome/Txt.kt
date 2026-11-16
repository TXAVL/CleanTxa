package com.gathin.tvhome

import java.util.Locale

/** Chữ theo ngôn ngữ tivi: tiếng Việt khi tivi để tiếng Việt, còn lại tiếng Anh. */
fun tr(vi: String, en: String): String = if (Locale.getDefault().language == "vi") vi else en
