package com.weishao.webdav.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.weishao.webdav.data.WebDavConfig

@Composable
fun LoginScreen(
    config: WebDavConfig,
    onLogin: (WebDavConfig) -> Unit
) {
    var url by remember { mutableStateOf(config.url) }
    var username by remember { mutableStateOf(config.username) }
    var password by remember { mutableStateOf(config.password) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WebDAV 登录", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        TextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("服务器地址 (https://...)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                onLogin(config.copy(url = url, username = username, password = password))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("登录")
        }
    }
}
