# قانون‌های R8 برای نسخه‌ی انتشار جیبیتو.
# Room، WorkManager، Compose و کوروتین‌ها قانون‌های لازم خودشان را همراه کتابخانه دارند.

# شماره‌ی خط در گزارش خطا (با فایل mapping.txt که CI کنار APK نگه می‌دارد، اسم‌های واقعی برمی‌گردند)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# گیرنده‌های پیامک و دکمه‌های نوتیفیکیشن در Manifest ثبت شده‌اند و خود ابزار ساخت نگهشان می‌دارد؛
# این‌جا فقط برای اطمینان، چون بدون آن‌ها هسته‌ی اپ (ثبت خودکار خرج) کار نمی‌کند.
-keep class ir.jibito.app.data.sms.SmsReceivedReceiver { <init>(); }
-keep class ir.jibito.app.notify.CategoryActionReceiver { <init>(); }
-keep class ir.jibito.app.data.sms.SmsSyncWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
