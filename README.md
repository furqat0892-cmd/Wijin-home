# Qo'riqchi Pro

Android uchun mustaqil xavfsizlik ilovasi (Kotlin, tashqi kutubxonasiz). Tahlil telefonning o'zida bajariladi.

## Imkoniyatlari
- **O'rnatilgan ilovalarni tekshirish**: Accessibility xizmati, qurilma administratori, bildirishnomalarni o'qish, SMS o'qish/yuborish, boshqa ilovalar ustidan oyna chizish kabi bank troyanlariga xos belgilar bo'yicha 0–100 xavf bali. O'rnatish manbasi (Play Market yoki qo'lda), yashirin ikonka, taqlid qilingan nomlar ham tekshiriladi.
- **APK faylni o'rnatishdan oldin tekshirish**: ruxsatlar, imzo, SHA-256. Telefonda shu nomli ilova bo'lib, imzosi boshqa bo'lsa, soxta nusxa ogohlantirishi chiqadi.
- **"Ulashish" orqali tekshirish**: Telegramdagi APK'ni ochmasdan, Ulashish → Qo'riqchi Pro.
- **Havola tekshiruvi**: havola ochilmaydi. IP manzil, punycode, `@`, qisqartirilgan havola, shubhali domen zonalari, `.uz` bo'lmagan bank/to'lov brendlari, `.apk` ga olib borishi aniqlanadi.
- **VirusTotal (ixtiyoriy)**: o'z API kalitingizni kiritsangiz, faqat fayl xeshi yuboriladi, faylning o'zi emas.

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
