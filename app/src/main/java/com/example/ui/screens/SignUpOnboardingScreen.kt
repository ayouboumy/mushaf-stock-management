package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudSyncState
import com.example.ui.StockViewModel
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TajawalFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignUpOnboardingScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val syncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

    var fullName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val roleSuggestions = listOf(
        "المكلف بالمستودع والتسليم",
        "مندوب الشؤون الإسلامية",
        "مسؤول التوزيع والإرساليات",
        "كاتب الضبط والمخزون"
    )

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Official Emblem & Header
            Surface(
                shape = CircleShape,
                color = EmeraldPrimary,
                modifier = Modifier.size(72.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "شعار التطبيق",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "المملكة المغربية",
                    fontFamily = TajawalFontFamily,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "وزارة الأوقاف والشؤون الإسلامية",
                    fontFamily = TajawalFontFamily,
                    fontSize = 13.5.sp,
                    color = EmeraldDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "منظومة تدبير وتوزيع المصاحف الشريفة",
                    fontFamily = CairoFontFamily,
                    fontSize = 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }

            // Welcome & Clean Profile Setup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "تسجيل مستخدم جديد وبدء العمل",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = TajawalFontFamily
                            )
                            Text(
                                text = "يرجى إدخال بياناتك الوظيفية لتوثيق سندات الصرف والمزامنة",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                fontFamily = TajawalFontFamily
                            )
                        }
                    }

                    // Full Name Input
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            errorMessage = null
                        },
                        label = { Text("الاسم الكامل للمستخدم *", fontFamily = TajawalFontFamily, fontSize = 12.5.sp) },
                        placeholder = { Text("مثال: عبد الحق المرابط", fontFamily = TajawalFontFamily, fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldPrimary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AppBorder
                        )
                    )

                    // Role / Position Input
                    OutlinedTextField(
                        value = role,
                        onValueChange = {
                            role = it
                            errorMessage = null
                        },
                        label = { Text("الصفة / المسمى الوظيفي *", fontFamily = TajawalFontFamily, fontSize = 12.5.sp) },
                        placeholder = { Text("مثال: المكلف بالمستودع والتسليم", fontFamily = TajawalFontFamily, fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = EmeraldPrimary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_role_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AppBorder
                        )
                    )

                    // Quick Role Chips
                    Text(
                        text = "أو اختر الصفة بنقرة واحدة:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = TajawalFontFamily
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        roleSuggestions.forEach { suggestion ->
                            val isSelected = role == suggestion
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) EmeraldPrimary else EmeraldContainer.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, if (isSelected) EmeraldPrimary else EmeraldPrimary.copy(alpha = 0.2f)),
                                modifier = Modifier.clickable { role = suggestion }
                            ) {
                                Text(
                                    text = suggestion,
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else EmeraldDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("البريد الإلكتروني المهني *", fontFamily = TajawalFontFamily, fontSize = 12.5.sp) },
                        placeholder = { Text("user@habous.gov.ma", fontFamily = TajawalFontFamily, fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = EmeraldPrimary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AppBorder
                        )
                    )

                    // Phone Input
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                        },
                        label = { Text("رقم الهاتف للتواصل (اختياري)", fontFamily = TajawalFontFamily, fontSize = 12.5.sp) },
                        placeholder = { Text("06XXXXXXXX", fontFamily = TajawalFontFamily, fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AppBorder
                        )
                    )

                    // Password Input (Firebase Auth)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور للحساب السحابي (اختياري)", fontFamily = TajawalFontFamily, fontSize = 12.5.sp) },
                        placeholder = { Text("6 أحرف على الأقل لحماية الحساب", fontFamily = TajawalFontFamily, fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AppBorder
                        )
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFC0392B),
                            fontSize = 12.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Cloud Auto-Sync Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoldContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, GoldBorder.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "مزامنة سحابية فورية: بمجرد الدخول، سيتم جلب ومزامنة أحدث أرقام المخزون وسندات التسليم من الأجهزة الأخرى تلقائياً.",
                                fontSize = 11.5.sp,
                                color = TextPrimary,
                                fontFamily = TajawalFontFamily,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Submit Button
                    Button(
                        onClick = {
                            val trimmedName = fullName.trim()
                            val trimmedRole = role.trim()
                            val trimmedEmail = email.trim()

                            if (trimmedName.isBlank()) {
                                errorMessage = "يرجى إدخال الاسم الكامل للمستخدم"
                                return@Button
                            }
                            if (trimmedRole.isBlank()) {
                                errorMessage = "يرجى تحديد أو إدخال الصفة الوظيفية"
                                return@Button
                            }
                            if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
                                errorMessage = "يرجى إدخال بريد إلكتروني صحيح"
                                return@Button
                            }

                            isSubmitting = true
                            viewModel.registerAndSignIn(
                                fullName = trimmedName,
                                role = trimmedRole,
                                email = trimmedEmail,
                                phone = phone.trim(),
                                password = password.trim()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("signup_submit_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        enabled = !isSubmitting && fullName.isNotBlank() && role.isNotBlank() && email.isNotBlank()
                    ) {
                        if (isSubmitting || syncState == CloudSyncState.SYNCING) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "جاري تفعيل الحساب ومزامنة المخزون...",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تأكيد التسجيل والدخول إلى المنظومة",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
