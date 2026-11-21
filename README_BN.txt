SenseBridge Android App — Build & Install Guide (বাংলা)

এই ZIP-এ Android Studio project আছে। এটি এখনো APK নয়।

যা লাগবে:
1) কম্পিউটারে Android Studio ইনস্টল করুন।
2) ZIP Extract করুন।
3) Android Studio > Open > extracted SenseBridgeAndroid folder নির্বাচন করুন।
4) Gradle Sync শেষ হওয়া পর্যন্ত অপেক্ষা করুন। Android SDK Platform 35 ইনস্টল করতে বললে Install করুন।
5) Build > Build Bundle(s) / APK(s) > Build APK(s) নির্বাচন করুন।
6) Build সফল হলে app/build/outputs/apk/debug/app-debug.apk পাওয়া যাবে।
7) APK ফোনে পাঠিয়ে Install করুন। ফোনে "Install unknown apps" অনুমতি দিতে হতে পারে।

ফোনে চালানোর নিয়ম:
1) ESP32 চালু করুন।
2) Android Settings > Bluetooth-এ যান।
3) SenseBridge_Device খুঁজে Pair করুন। PIN চাইলে 1234 বা 0000 চেষ্টা করুন (ESP32 core/ফার্মওয়্যারভেদে pairing prompt ভিন্ন হতে পারে)।
4) SenseBridge অ্যাপ খুলুন; Bluetooth Connect permission Allow করুন।
5) Refresh devices চাপুন, SenseBridge_Device নির্বাচন করুন, Connect চাপুন।
6) Water / Food / Help বাটন দিয়ে যথাক্রমে W / F / H পাঠান।

ESP32 firmware-এর সাথে সামঞ্জস্য:
- Bluetooth Classic SPP UUID ব্যবহার করা হয়েছে।
- ডিভাইস নাম: SenseBridge_Device
- ESP32 থেকে STATE|TEMP:...|BPM:...|MSG:...|CMD:...|BT:...|LAT:...|LNG:... লাইন পড়া হয়।
- ALERT:FALL_DETECTED, ALERT:SHOCK_DETECTED, PATIENT_MSG:HELP ইত্যাদি সিগন্যাল দেখায়।
- ফোন থেকে W, F, H অক্ষর পাঠানো হয়।

গুরুত্বপূর্ণ:
- ESP32-র BluetoothSerial কোডের সাথে অ্যাপটি তৈরি।
- অ্যাপটি আগে থেকে Pair করা Bluetooth device তালিকা থেকে ডিভাইস নেয়।
- GPS ম্যাপে দেখাতে ESP32 থেকে valid GPS LAT/LNG আসতে হবে।
- এই প্রজেক্ট medical-grade monitor নয়; সেন্সর/ডেমো রিডিং চিকিৎসা-নির্ণয়ের জন্য ব্যবহার করবেন না।
- Android Studio build-এর জন্য ইন্টারনেট লাগতে পারে (Gradle/SDK প্রথমবার ডাউনলোডের সময়)।
