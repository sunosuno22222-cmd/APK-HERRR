package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.service.FloatingAssistantService
import com.example.viewmodel.FloatingViewModel
import com.example.utils.ScreenCaptureManager
import com.example.model.ChatMessage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.CapturePermissionActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingAssistantUI(
    service: FloatingAssistantService,
    onMove: (Int, Int) -> Unit,
    onCaptureResult: (Int, Intent) -> Unit,
    viewModel: FloatingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val screenCaptureManager = remember { ScreenCaptureManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Update flag focusable based on expanded state
    LaunchedEffect(uiState.isChatExpanded) {
        service.updateLayoutParams { params ->
            if (uiState.isChatExpanded) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
        }
    }

    MyApplicationTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!uiState.isChatExpanded) {
                FloatingBubble(
                    onMove = onMove,
                    onTap = { viewModel.toggleChat() }
                )
            } else {
                ChatWindow(
                    uiState = uiState,
                    onClose = { viewModel.toggleChat() },
                    onSendMessage = { viewModel.sendMessage(it) },
                    onCapture = {
                        if (FloatingAssistantService.screenCaptureIntent == null) {
                            val intent = Intent(context, CapturePermissionActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } else {
                            coroutineScope.launch {
                                service.updateLayoutParams { it.alpha = 0f }
                                kotlinx.coroutines.delay(100)
                                screenCaptureManager.onActivityResult(
                                    FloatingAssistantService.screenCaptureResultCode,
                                    FloatingAssistantService.screenCaptureIntent!!
                                )
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    service.updateToCaptureForeground()
                                }
                                screenCaptureManager.captureContent { bitmap ->
                                    service.updateLayoutParams { it.alpha = 1f }
                                    if (bitmap != null) {
                                        viewModel.analyzeScreen(bitmap)
                                    }
                                }
                            }
                        }
                    },
                    onSummarize = { viewModel.summarizeCurrentChat() },
                    onExplain = { viewModel.explainBetter() },
                    onGenerateAnother = { viewModel.generateAnother() },
                    onCopyAll = { /* Handle elsewhere */ },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun FloatingBubble(
    onMove: (Int, Int) -> Unit,
    onTap: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                    }
                )
            }
            .clip(CircleShape)
            .shadow(8.dp, CircleShape),
        color = MaterialTheme.colorScheme.primary,
        onClick = onTap
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Assistant",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun ChatWindow(
    uiState: com.example.viewmodel.FloatingUiState,
    onClose: () -> Unit,
    onSendMessage: (String) -> Unit,
    onCapture: () -> Unit,
    onSummarize: () -> Unit,
    onExplain: () -> Unit,
    onGenerateAnother: () -> Unit,
    onCopyAll: () -> Unit,
    viewModel: FloatingViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)) // Dim background
            .pointerInput(Unit) { /* Intercept taps to not close if tapped outside (unless desired) */ },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
                .shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages) { message ->
                        MessageItem(message)
                    }
                    if (uiState.isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                
                // Quick Actions Scrollable Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip(Icons.Default.Screenshot, "Capturar", onCapture)
                    QuickActionChip(Icons.Default.Summarize, "Resumir", onSummarize)
                    QuickActionChip(Icons.Default.QuestionMark, "Explicar", onExplain)
                    QuickActionChip(Icons.Default.Refresh, "Gerar Outra", onGenerateAnother)
                }

                // Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Pergunte algo...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !uiState.isLoading,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                    }
                }
                
                // Bottom tools
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        val allText = uiState.messages.joinToString("\n\n") { "${it.role}: ${it.content}" }
                        clipboardManager.setText(AnnotatedString(allText))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar Tudo")
                    }
                    
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    val clipboardManager = LocalClipboardManager.current
    val isUser = message.role == "user"
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.content,
            color = if (isUser) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = if (isUser) TextAlign.End else TextAlign.Start,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth(0.85f)
        )
        if (!isUser) {
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuickActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) }
    )
}
