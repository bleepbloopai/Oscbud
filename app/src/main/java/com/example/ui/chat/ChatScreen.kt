package com.example.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.face.CrtTheme
import com.example.ui.face.Emotion
import com.example.ui.face.OscilloscopeFace
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.messages.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val currentEmotion by viewModel.currentEmotion.collectAsState()
    val crtTheme by viewModel.crtTheme.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()

    val isListening by viewModel.voiceManager.isListening.collectAsState()
    val isSpeaking by viewModel.voiceManager.isSpeaking.collectAsState()
    val audioLevel by viewModel.voiceManager.audioLevel.collectAsState()
    val frequencyBins by viewModel.voiceManager.frequencyBins.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    val primaryColor = Color(crtTheme.hexColor)
    val bgColor = Color(crtTheme.bgHex)

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceInput()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission required for voice chat.")
            }
        }
    }

    LaunchedEffect(statusNotice) {
        statusNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("chat_scaffold"),
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OscilloTopBar(
                crtTheme = crtTheme,
                memoryCount = memories.size,
                autoSpeak = autoSpeak,
                onOpenMemories = { showMemoryDialog = true },
                onOpenExport = { showExportDialog = true },
                onToggleAutoSpeak = { viewModel.toggleAutoSpeak() },
                onToggleThemeMenu = { showThemeMenu = true },
                onClearChat = { viewModel.clearAllMessages() }
            )

            // Theme selector menu
            DropdownMenu(
                expanded = showThemeMenu,
                onDismissRequest = { showThemeMenu = false },
                modifier = Modifier
                    .background(Color(0xFF0F1A15))
                    .border(1.dp, primaryColor, RoundedCornerShape(8.dp))
            ) {
                CrtTheme.values().forEach { theme ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(Color(theme.hexColor), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = theme.title,
                                    color = if (theme == crtTheme) Color(theme.hexColor) else Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (theme == crtTheme) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            viewModel.setCrtTheme(theme)
                            showThemeMenu = false
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            // Oscilloscope Face Viewport (Hero CRT monitor)
            OscilloscopeFace(
                emotion = currentEmotion,
                audioLevel = audioLevel,
                frequencyBins = frequencyBins,
                isSpeaking = isSpeaking,
                isListening = isListening,
                crtTheme = crtTheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(vertical = 4.dp)
            )

            // Emotion test chips bar (allows user to preview and verify emotion morphing)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EMOTION MATRIX:",
                    color = primaryColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Emotion.HAPPY to "Happy",
                        Emotion.THINKING to "Think",
                        Emotion.CURIOUS to "Curious",
                        Emotion.EMPATHETIC to "Empath",
                        Emotion.WINK to "Wink"
                    ).forEach { (emo, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (currentEmotion == emo) primaryColor.copy(alpha = 0.25f)
                                    else Color.Black.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp,
                                    if (currentEmotion == emo) primaryColor else primaryColor.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    // Trigger quick speech to demonstrate audio waveform reaction too!
                                    viewModel.speakMessage("Emotion matrix updated to ${emo.name}")
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (currentEmotion == emo) primaryColor else Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Chat conversation log
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, primaryColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                if (messages.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting input signal...\nSpeak or type below to begin.",
                            color = primaryColor.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                crtTheme = crtTheme,
                                onReplayAudio = { viewModel.speakMessage(msg.text) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (isLoading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = primaryColor,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Oscillo synthesizing neural response...",
                                        color = primaryColor.copy(alpha = 0.8f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Listening status banner
            AnimatedVisibility(
                visible = isListening,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A0808))
                        .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recording",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Listening to audio frequencies... Speak now",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "TAP MIC TO STOP",
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.stopVoiceInput() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Terminal Input Row (Voice & Text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Input Button
                IconButton(
                    onClick = {
                        if (isListening) {
                            viewModel.stopVoiceInput()
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.startVoiceInput()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isListening) Color(0xFFFF5252).copy(alpha = 0.25f)
                            else primaryColor.copy(alpha = 0.15f)
                        )
                        .border(
                            1.5.dp,
                            if (isListening) Color(0xFFFF5252) else primaryColor.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Voice" else "Voice Input",
                        tint = if (isListening) Color(0xFFFF5252) else primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = "Transmit signal to Oscillo…",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_text_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendMessage(textInput)
                            textInput = ""
                        }
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = primaryColor.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = primaryColor
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendMessage(textInput)
                            textInput = ""
                        }
                    },
                    enabled = textInput.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (textInput.isNotBlank()) primaryColor.copy(alpha = 0.25f)
                            else Color.DarkGray.copy(alpha = 0.3f)
                        )
                        .border(
                            1.5.dp,
                            if (textInput.isNotBlank()) primaryColor else Color.DarkGray,
                            RoundedCornerShape(12.dp)
                        )
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if (textInput.isNotBlank()) primaryColor else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // Memory Core Dialog
    if (showMemoryDialog) {
        MemoryDialog(
            memories = memories,
            crtTheme = crtTheme,
            onDismiss = { showMemoryDialog = false },
            onAddMemory = { fact -> viewModel.addManualMemory(fact) },
            onDeleteMemory = { id -> viewModel.deleteMemory(id) },
            onClearAll = { viewModel.clearAllMemories() }
        )
    }

    // Export Chat Logs Dialog
    if (showExportDialog) {
        ExportDialog(
            messageCount = messages.size,
            memoryCount = memories.size,
            crtTheme = crtTheme,
            onDismiss = { showExportDialog = false },
            onExportShare = { asEncrypted ->
                viewModel.exportChatLogs(context, asEncryptedArchive = asEncrypted)
            }
        )
    }
}

@Composable
private fun OscilloTopBar(
    crtTheme: CrtTheme,
    memoryCount: Int,
    autoSpeak: Boolean,
    onOpenMemories: () -> Unit,
    onOpenExport: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onToggleThemeMenu: () -> Unit,
    onClearChat: () -> Unit
) {
    val primaryColor = Color(crtTheme.hexColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App title & encryption badge
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OSCILLO",
                    color = primaryColor,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(primaryColor.copy(alpha = 0.15f))
                        .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = primaryColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "AES-256",
                            color = primaryColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "NEURAL CRT PERSONA",
                color = primaryColor.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Auto TTS Toggle
        IconButton(
            onClick = onToggleAutoSpeak,
            modifier = Modifier.size(38.dp).testTag("auto_tts_toggle")
        ) {
            Icon(
                imageVector = if (autoSpeak) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                contentDescription = if (autoSpeak) "Mute Voice" else "Enable Voice",
                tint = if (autoSpeak) primaryColor else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }

        // Memory Bank Button with count badge
        IconButton(
            onClick = onOpenMemories,
            modifier = Modifier.size(38.dp).testTag("open_memory_core_button")
        ) {
            BadgedBox(
                badge = {
                    if (memoryCount > 0) {
                        Badge(containerColor = primaryColor, contentColor = Color.Black) {
                            Text(text = memoryCount.toString(), fontSize = 9.sp)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Memory Bank",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Export Logs Button
        IconButton(
            onClick = onOpenExport,
            modifier = Modifier.size(38.dp).testTag("open_export_dialog_button")
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Export Logs",
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // CRT Theme Picker
        IconButton(
            onClick = onToggleThemeMenu,
            modifier = Modifier.size(38.dp).testTag("theme_selector_button")
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Change CRT Theme",
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // Clear Chat History
        IconButton(
            onClick = onClearChat,
            modifier = Modifier.size(38.dp).testTag("clear_chat_button")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "Clear Chat",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageUi,
    crtTheme: CrtTheme,
    onReplayAudio: () -> Unit
) {
    val isUser = message.role == "user"
    val primaryColor = Color(crtTheme.hexColor)
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_${message.id}" else "oscillo_message_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender header line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = if (isUser) "USER TX" else "OSCILLO RX",
                color = if (isUser) Color.LightGray else primaryColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            if (!isUser) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(primaryColor.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = message.emotion.name,
                        color = primaryColor,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formattedTime,
                color = Color.Gray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Message bubble
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 12.dp
                    )
                )
                .background(
                    if (isUser) Color(0xFF162520)
                    else Color(0xFF091410)
                )
                .border(
                    1.dp,
                    if (isUser) primaryColor.copy(alpha = 0.35f)
                    else primaryColor.copy(alpha = 0.6f),
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 12.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onReplayAudio,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("replay_audio_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak Aloud",
                            tint = primaryColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
