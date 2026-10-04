package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudSyncState
import com.example.data.sync.CloudSyncDiagnostic
import com.example.ui.StockViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TajawalFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun UserProfileScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val syncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val syncDiagnostic by viewModel.syncDiagnostic.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()

    var nameStr by remember(currentUser) { mutableStateOf(currentUser.fullName) }
    var roleStr by remember(currentUser) { mutableStateOf(currentUser.role) }
    var emailStr by remember(currentUser) { mutableStateOf(currentUser.email) }
    var phoneStr by remember(currentUser) { mutableStateOf(currentUser.phone) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        AppHeader(
            title = "حساب المستخدم والمزامنة السحابية",
            subtitle = "إدارة هويتك وتسجيل عمليات الصرف والتسليم",
            showBackButton = true,
            onBackClick = { viewModel.navigateBack() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active User Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldPrimary,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = currentUser.fullName.take(1).ifBlank { "م" },
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = TajawalFontFamily
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser.fullName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontFamily = TajawalFontFamily
                                )
                                Text(
                                    text = currentUser.role,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = EmeraldPrimary,
                                    fontFamily = TajawalFontFamily
                                )
                                Text(
                                    text = currentUser.email,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    fontFamily = TajawalFontFamily
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldContainer,
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "نشط",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        fontFamily = TajawalFontFamily
                                    )
                                }
                            }
                        }

                        Text(
                            text = "💡 سيتم إدراج اسمك تلقائياً كمسؤول عن التسليم في سندات صرف وتوزيع المصاحف.",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            fontFamily = TajawalFontFamily,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Edit / Sign In Profile Form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    border = BorderStroke(1.dp, AppBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "تسجيل الدخول / تعديل بيانات الحساب",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = TajawalFontFamily
                            )
                        }

                        OutlinedTextField(
                            value = nameStr,
                            onValueChange = { nameStr = it },
                            label = { Text("الاسم الكامل للمستخدم *", fontFamily = TajawalFontFamily, fontSize = 12.sp) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TextMuted) },
                            modifier = Modifier.fillMaxWidth().testTag("user_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = TajawalFontFamily, fontSize = 13.5.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary, unfocusedBorderColor = AppBorder)
                        )

                        OutlinedTextField(
                            value = roleStr,
                            onValueChange = { roleStr = it },
                            label = { Text("الصفة / المسمى الوظيفي *", fontFamily = TajawalFontFamily, fontSize = 12.sp) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = TextMuted) },
                            modifier = Modifier.fillMaxWidth().testTag("user_role_input"),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = TajawalFontFamily, fontSize = 13.5.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary, unfocusedBorderColor = AppBorder)
                        )

                        OutlinedTextField(
                            value = emailStr,
                            onValueChange = { emailStr = it },
                            label = { Text("البريد الإلكتروني / الحساب", fontFamily = TajawalFontFamily, fontSize = 12.sp) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = TextMuted) },
                            modifier = Modifier.fillMaxWidth().testTag("user_email_input"),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = TajawalFontFamily, fontSize = 13.5.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary, unfocusedBorderColor = AppBorder)
                        )

                        OutlinedTextField(
                            value = phoneStr,
                            onValueChange = { phoneStr = it },
                            label = { Text("رقم الهاتف (اختياري)", fontFamily = TajawalFontFamily, fontSize = 12.sp) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = TextMuted) },
                            modifier = Modifier.fillMaxWidth().testTag("user_phone_input"),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = TajawalFontFamily, fontSize = 13.5.sp, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary, unfocusedBorderColor = AppBorder)
                        )

                        Button(
                            onClick = {
                                if (nameStr.isNotBlank()) {
                                    viewModel.updateActiveUserProfile(nameStr, roleStr, emailStr, phoneStr)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("save_user_profile_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = nameStr.isNotBlank()
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ وتحديث بيانات الحساب", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold)
                        }

                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.logoutOrSwitchUser() },
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("logout_switch_user_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC0392B)),
                            border = BorderStroke(1.dp, Color(0xFFE74C3C).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFC0392B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الخروج / تبديل المستخدم", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Cloud Sync Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    border = BorderStroke(1.dp, GoldBorder.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "حالة المزامنة السحابية للأجهزة المتعددة",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = TajawalFontFamily
                            )
                        }

                        Text(
                            text = "تتيح المزامنة مشاركة أعداد المخزون، السندات، وسجل الحركات لحظياً بين جميع هواتف المسؤولين عبر سحابة Google Firebase Firestore.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = TajawalFontFamily,
                            lineHeight = 17.sp
                        )

                        // Live Diagnostic State Indicator
                        when (val diag = syncDiagnostic) {
                            is CloudSyncDiagnostic.Connected -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = EmeraldContainer,
                                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "متصل بنجاح بسحابة Firestore (mushaf-stock)",
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Text(
                                            text = "قاعدة البيانات الافتراضية (default) مفعلة وجاهزة للمزامنة اللحظية بين الأجهزة.",
                                            fontFamily = TajawalFontFamily,
                                            color = TextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                            is CloudSyncDiagnostic.DatabaseNotFound -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFDEBD0).copy(alpha = 0.7f),
                                    border = BorderStroke(1.2.dp, Color(0xFFE67E22))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD35400), modifier = Modifier.size(20.dp))
                                            Text(
                                                text = "تنبيه في الاتصال بقاعدة البيانات",
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD35400),
                                                fontSize = 13.sp
                                            )
                                        }
                                        Text(
                                            text = diag.message,
                                            fontFamily = TajawalFontFamily,
                                            color = TextPrimary,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp
                                        )
                                        if (diag.technicalDetail.isNotBlank()) {
                                            Text(
                                                text = "التفاصيل: ${diag.technicalDetail}",
                                                fontFamily = TajawalFontFamily,
                                                color = TextSecondary,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                            is CloudSyncDiagnostic.GeneralError -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFDEDEC),
                                    border = BorderStroke(1.dp, Color(0xFFE74C3C).copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "تنبيه أثناء الاتصال السحابي",
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC0392B),
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = diag.message,
                                            fontFamily = TajawalFontFamily,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                        if (diag.technicalDetail.isNotBlank()) {
                                            Text(
                                                text = diag.technicalDetail,
                                                fontFamily = TajawalFontFamily,
                                                color = TextSecondary.copy(alpha = 0.8f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                            is CloudSyncDiagnostic.PermissionDenied -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFADBD8).copy(alpha = 0.7f),
                                    border = BorderStroke(1.2.dp, Color(0xFFC0392B))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(20.dp))
                                            Text(
                                                text = "قواعد الأمان في Firestore تمنع الوصول",
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC0392B),
                                                fontSize = 13.sp
                                            )
                                        }
                                        Text(
                                            text = "في منصة Firebase Console > Firestore Database > Rules، يرجى تفعيل القواعد بالسماح بالقراءة والكتابة: allow read, write: if true;",
                                            fontFamily = TajawalFontFamily,
                                            color = TextPrimary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                            is CloudSyncDiagnostic.NetworkError -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF5EEF8),
                                    border = BorderStroke(1.dp, Color(0xFFA569BD))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFA569BD), modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "تعذر الاتصال بالخادم، يرجى التحقق من اتصال الإنترنت بالجهاز.",
                                            fontFamily = TajawalFontFamily,
                                            color = TextPrimary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            is CloudSyncDiagnostic.Checking -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoldContainer,
                                    border = BorderStroke(1.dp, GoldBorder)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "جاري فحص الاتصال بقاعدة بيانات Firestore...",
                                            fontFamily = TajawalFontFamily,
                                            color = TextPrimary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            else -> {
                                if (syncError != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFDEDEC),
                                        border = BorderStroke(1.dp, Color(0xFFE74C3C).copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "تنبيه أثناء الاتصال السحابي:",
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC0392B),
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = syncError ?: "",
                                                fontFamily = TajawalFontFamily,
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.triggerSyncNow() },
                                modifier = Modifier.weight(1f).height(44.dp).testTag("btn_trigger_sync"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (syncState == CloudSyncState.SYNCING) "جاري المزامنة..." else "مزامنة البيانات",
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.5.sp
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.checkCloudConnection() },
                                modifier = Modifier.weight(1f).height(44.dp).testTag("btn_check_connection"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.7f))
                            ) {
                                Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "فحص الاتصال",
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldPrimary,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
