package com.hedwig.sms.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedwig.sms.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_help)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.menu_settings))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.hedwig_banner),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth,
            )

            Text(
                stringResource(R.string.help_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HelpSection(R.string.help_s1_title, R.string.help_s1_body)
            // 环境变量 / 常见问题：默认折叠，点开显示 / collapsed by default, tap to expand
            CollapsibleSection(R.string.help_s2_title, R.string.help_s2_body)
            CollapsibleSection(R.string.help_s3_title, R.string.help_s3_body)
            HelpSection(R.string.help_s4_title, R.string.help_s4_body)

            // 关于（含统一版本号）/ About (with unified version)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.help_s5_title),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.help_s5_body),
                        fontSize = 14.sp, lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.help_version, com.hedwig.sms.BuildConfig.VERSION_NAME),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OutlinedButton(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.help_check_update))
            }
            OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.help_replay))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HelpSection(titleRes: Int, bodyRes: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(titleRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(bodyRes),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 可折叠分区：标题可点开/收起，正文按行结构化排版 / collapsible section with structured body. */
@Composable
private fun CollapsibleSection(titleRes: Int, bodyRes: Int) {
    var expanded by remember { mutableStateOf(false) }
    val body = stringResource(bodyRes)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(titleRes),
                    Modifier.weight(1f),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    body.split("\n").forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) {
                            Spacer(Modifier.height(6.dp))
                        } else {
                            BodyLine(line)
                        }
                    }
                }
            }
        }
    }
}

/** 单行：以「•」开头的渲染为带缩进的要点，其余为普通段落 / one line, bullet-aware. */
@Composable
private fun BodyLine(line: String) {
    if (line.startsWith("•")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text("•", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Text(
                line.removePrefix("•").trim(),
                Modifier.padding(start = 8.dp),
                fontSize = 14.sp, lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        Text(
            line,
            Modifier.padding(vertical = 3.dp),
            fontSize = 14.sp, lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
