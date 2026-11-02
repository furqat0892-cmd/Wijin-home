# Qo'riqchi Pro

Android uchun mustaqil xavfsizlik ilovasi (Kotlin, tashqi kutubxonasiz). Tahlil telefonning o'zida bajariladi.

## Imkoniyatlari
- **O'rnatilgan ilovalarni tekshirish**: Accessibility xizmati, qurilma administratori, bildirishnomalarni o'qish, SMS o'qish/yuborish, boshqa ilovalar ustidan oyna chizish kabi bank troyanlariga xos belgilar bo'yicha 0–100 xavf bali. O'rnatish manbasi (Play Market yoki qo'lda), yashirin ikonka, taqlid qilingan nomlar ham tekshiriladi.
- **APK faylni o'rnatishdan oldin tekshirish**: ruxsatlar, imzo, SHA-256. Telefonda shu nomli ilova bo'lib, imzosi boshqa bo'lsa, soxta nusxa ogohlantirishi chiqadi.
- **"Ulashish" orqali tekshirish**: Telegramdagi APK'ni ochmasdan, Ulashish → Qo'riqchi Pro.
- **Havola tekshiruvi**: havola ochilmaydi. IP manzil, punycode, `@`, qisqartirilgan havola, shubhali domen zonalari, `.uz` bo'lmagan bank/to'lov brendlari, `.apk` ga olib borishi aniqlanadi.
- **VirusTotal (ixtiyoriy)**: o'z API kalitingizni kiritsangiz, faqat fayl xeshi yuboriladi, faylning o'zi emas.

## Real vaqt himoyasi (v2)
- Himoyani yoqsangiz, yangi o'rnatilgan ilova darrov tekshiriladi, xavfli bo'lsa ogohlantirish chiqadi va "Ilovani o'chirish" tugmasi paydo bo'ladi.
- "Fayllarga kirish" ruxsati berilsa, Download va Telegram papkalariga tushgan yangi APK fayllar avtomatik tekshiriladi, xavfli bo'lsa "Faylni o'chirish" tugmasi chiqadi.
- VirusTotal kaliti kiritilgan bo'lsa, har bir yangi ilova/APK xeshi ham tekshiriladi.
- Android ilovani jimgina o'chirishga ruxsat bermaydi: o'chirish har doim sizning tasdig'ingiz bilan bajariladi.
- Himoya foreground xizmat sifatida ishlaydi (doimiy bildirishnoma ko'rinadi).

## v3 qo'shimchalari
- **Chuqur APK tahlili**: APK ichidagi yashirin kod/APK fayllar, Telegram bot orqali ma'lumot yuborish kodi, tashqaridan kod yuklash, ekranda o'zi bosish, bank nomlari, kodni yashiruvchi kutubxonalar.
- **Taqlid aniqlash**: do'kondan tashqari o'rnatilgan, lekin Click/Payme/MyID/Telegram kabi nomni ishlatayotgan yoki aldov so'zlari ("Aksiya", "Yangilanish") bilan nomlangan ilovalar.
- **Kamroq noto'g'ri ogohlantirish**: rasmiy do'kondan o'rnatilgan ilovalarning xavf bali kamaytiriladi; "Ishonaman" tugmasi bilan ilovani ro'yxatdan chiqarish mumkin.
- **Havola**: nusxalangan havola ilova ochilganda avtomatik tekshiriladi; boshqa ilovadan matnni "Ulashish → Qo'riqchi Pro" qilib ham tekshirish mumkin.
- **Hisobotni ulashish** va **ilovani qulflash** (telefon ekran qulfi bilan).

## APK olish
**Telefonning o'zidan (kompyutersiz):**
1. github.com'da bepul hisob oching va yangi repozitoriy yarating.
2. Shu papkadagi barcha fayllarni (`.github` papkasi bilan) yuklang.
3. Actions → "Build APK" → Run workflow. 5–10 daqiqada tayyor bo'ladi.
4. Natija sahifasidagi Artifacts → `QoriqchiPro-apk` ni yuklab, ichidan `app-debug.apk` ni oching.

**Kompyuterda:** Android Studio'da papkani oching → Build → Build APK(s).

## Cheklovlar
- Bu Google Play Protect o'rnini bosmaydi, qo'shimcha qatlam. Ball evristikaga asoslangan: yuqori ball "albatta virus" degani emas, past ball "albatta toza" degani ham emas.
- Barcha ilovalar ro'yxati uchun `QUERY_ALL_PACKAGES` ruxsati kerak, shuning uchun Play Market'ga emas, APK sifatida tarqatish uchun mo'ljallangan.
- Real vaqtda papkalarni kuzatish yo'q (u keng fayl ruxsatini talab qiladi). O'rniga "Ulashish" va fayl tanlash ishlatiladi.
