package com.imoosh.hudoor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imoosh.hudoor.MainActivity
import com.imoosh.hudoor.data.Prefs
import com.imoosh.hudoor.data.Punch
import com.imoosh.hudoor.data.Workplace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF16A34A)
private val Red = Color(0xFFDC2626)
private val Amber = Color(0xFFD97706)
private val BgDark = Color(0xFF0F172A)
private val CardDark = Color(0xFF1E293B)

@Composable
fun AppRoot(act: MainActivity) {
    val prefs = remember { Prefs(act) }
    var configured by remember { mutableStateOf(com.imoosh.hudoor.data.AppConfig.isReady) }
    var loggedIn by remember { mutableStateOf(prefs.isLoggedIn) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green,
            background = BgDark,
            surface = CardDark
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = BgDark) {
            when {
                !configured -> SetupScreen(act) { configured = true }
                !loggedIn -> LoginScreen(act) { loggedIn = true }
                else -> HomeScreen(act) { loggedIn = false }
            }
        }
    }
}

// ================== شاشة ربط Firebase (أول تشغيل فقط) ==================

@Composable
private fun SetupScreen(act: MainActivity, onDone: () -> Unit) {
    var pid by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ربط النظام", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "أدخل البيانات التي أعطاك المسؤول. تُطلب مرة واحدة فقط.",
            fontSize = 13.sp, color = Color(0xFF94A3B8)
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pid, onValueChange = { pid = it.trim() },
            label = { Text("Project ID") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it.trim() },
            label = { Text("API Key") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (err.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(err, color = Red, fontSize = 13.sp)
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                if (pid.isBlank() || key.isBlank()) { err = "أدخل القيمتين"; return@Button }
                com.imoosh.hudoor.data.AppConfig.save(act, pid, key)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) { Text("متابعة", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

// ================== شاشة الدخول ==================

@Composable
private fun LoginScreen(act: MainActivity, onDone: () -> Unit) {
    val prefs = remember { Prefs(act) }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Green),
            contentAlignment = Alignment.Center
        ) { Text("ب", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White) }

        Spacer(Modifier.height(20.dp))
        Text("نظام الحضور والانصراف", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "سجّل دخولك بالبيانات التي أعطاك المسؤول",
            fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it.trim() },
            label = { Text("البريد الإلكتروني") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pw, onValueChange = { pw = it },
            label = { Text("كلمة المرور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (err.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(err, color = Red, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (email.isBlank() || pw.isBlank()) { err = "أدخل البريد وكلمة المرور"; return@Button }
                busy = true; err = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            val s = com.imoosh.hudoor.data.Fs.signIn(email, pw)
                            prefs.email = email; prefs.password = pw; prefs.uid = s.uid
                            true
                        }
                    }
                    busy = false
                    if (r.isSuccess) {
                        act.askBackgroundLocation()
                        onDone()
                    } else {
                        err = "بيانات الدخول غير صحيحة أو لا يوجد اتصال"
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White)
            else Text("دخول", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ================== الشاشة الرئيسية ==================

@Composable
private fun HomeScreen(act: MainActivity, onLogout: () -> Unit) {
    val prefs = remember { Prefs(act) }
    val punch = remember { Punch(act) }
    val scope = rememberCoroutineScope()

    var wp by remember { mutableStateOf<Workplace?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    var onShift by remember { mutableStateOf(prefs.isOnShift) }
    var askLeave by remember { mutableStateOf(false) }
    var leaveReason by remember { mutableStateOf("") }
    var loadErr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { runCatching { punch.load() } }
        r.onSuccess { wp = it.wp }.onFailure { loadErr = it.message ?: "تعذّر تحميل الإعدادات" }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    prefs.name.ifBlank { prefs.email },
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    if (onShift) "الدوام جاري" else "غير مسجّل حضور",
                    fontSize = 13.sp, color = if (onShift) Green else Color(0xFF94A3B8)
                )
            }
            TextButton(onClick = { prefs.logout(); onLogout() }) {
                Text("خروج", color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(18.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green)
            }
        } else if (loadErr.isNotBlank()) {
            InfoCard(loadErr, Red)
        } else {
            val w = wp!!

            InfoCard(
                "الدوام: ${w.fmt(w.shiftStart)} — ${w.fmt(w.shiftEnd)}\n" +
                    "نطاق البصمة: ${w.radius.toInt()} متر من المقر",
                Color(0xFF334155)
            )

            // تحذيرات الصلاحيات
            if (!act.hasLocation()) {
                Spacer(Modifier.height(12.dp))
                WarnCard("صلاحية الموقع غير مفعّلة", "تفعيل") { act.askPermissions() }
            } else if (!act.hasBackgroundLocation()) {
                Spacer(Modifier.height(12.dp))
                WarnCard(
                    "لازم تختار «السماح دائماً» للموقع حتى تستمر المتابعة أثناء الدوام",
                    "ضبط"
                ) { act.openAppSettings() }
            }

            Spacer(Modifier.height(24.dp))

            // الزر الرئيسي
            Button(
                onClick = {
                    busy = true; msg = ""
                    act.authenticate(
                        if (onShift) "تأكيد الانصراف" else "تأكيد الحضور",
                        onOk = {
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    if (onShift) punch.checkOut() else punch.checkIn()
                                }
                                busy = false
                                when (res) {
                                    is Punch.Result.Ok -> {
                                        msgOk = true; msg = res.message
                                        onShift = prefs.isOnShift
                                        if (onShift) act.askIgnoreBattery()
                                    }
                                    is Punch.Result.Blocked -> { msgOk = false; msg = res.reason }
                                    is Punch.Result.Error -> { msgOk = false; msg = res.reason }
                                }
                            }
                        },
                        onFail = { busy = false; msgOk = false; msg = "لم يتم التحقق من هويتك" }
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (onShift) Red else Green
                )
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
                else Text(
                    if (onShift) "تسجيل انصراف" else "تسجيل حضور",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
            }

            if (onShift) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { askLeave = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("طلب استئذان للمغادرة المبكرة") }
            }

            if (msg.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                InfoCard(msg, if (msgOk) Green else Red)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "بصمتك تُسجّل بوقت السيرفر وبإحداثية موقعك، ولا يمكن تعديلها أو حذفها بعد التسجيل.",
                fontSize = 11.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("طلب استئذان") },
            text = {
                Column {
                    Text("اكتب سبب المغادرة — يوصل المسؤول فوراً.", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = leaveReason, onValueChange = { leaveReason = it },
                        label = { Text("السبب") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = leaveReason
                    askLeave = false; leaveReason = ""
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { punch.requestEarlyLeave(r) }
                        msgOk = ok
                        msg = if (ok) "تم إرسال الطلب للمسؤول. انتظر الموافقة."
                        else "تعذّر إرسال الطلب."
                    }
                }) { Text("إرسال") }
            },
            dismissButton = {
                TextButton(onClick = { askLeave = false }) { Text("إلغاء") }
            }
        )
    }
}

// ================== مكوّنات ==================

@Composable
private fun InfoCard(text: String, accent: Color) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp, 6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.size(12.dp))
            Text(text, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun WarnCard(text: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3F2D12))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 13.sp, color = Amber, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(action, color = Amber) }
        }
    }
}
