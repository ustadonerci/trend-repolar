package io.github.halilozat.trendrepolar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private val PERIODS = listOf("Bugün" to 1, "Bu hafta" to 7, "Bu ay" to 30)
private val LANGUAGES = listOf(null, "Python", "TypeScript", "JavaScript", "Go", "Rust", "Java", "Kotlin", "C++", "C#", "Swift", "PHP")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    var selected by remember { mutableStateOf<Repo?>(null) }
    // Liste durumu detaya girip çıkınca kaybolmasın diye burada tutuluyor.
    var days by rememberSaveable { mutableIntStateOf(1) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var repos by remember { mutableStateOf<List<Repo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(days, language, query, reload) {
        loading = true
        error = null
        try {
            repos = withContext(Dispatchers.IO) { fetchTrending(days, language, query) }
        } catch (e: RateLimitException) {
            error = "GitHub istek sınırı doldu. Bir dakika sonra tekrar dene."
        } catch (e: IOException) {
            error = "Bağlantı hatası. İnternetini kontrol edip tekrar dene."
        }
        loading = false
    }

    val repo = selected
    if (repo != null) {
        BackHandler { selected = null }
        DetailScreen(repo) { selected = null }
    } else {
        ListScreen(
            days, { days = it }, language, { language = it }, query, { query = it },
            repos, loading, error, onRefresh = { reload++ }, onOpen = { selected = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    days: Int, onDays: (Int) -> Unit,
    language: String?, onLanguage: (String?) -> Unit,
    query: String, onQuery: (String) -> Unit,
    repos: List<Repo>, loading: Boolean, error: String?,
    onRefresh: () -> Unit, onOpen: (Repo) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(query) }
    val focus = LocalFocusManager.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Trend Repolar", fontWeight = FontWeight.Bold) },
            actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Yenile") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Repo, konu veya anahtar kelime ara…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onQuery(draft); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIODS.forEach { (label, d) ->
                    FilterChip(selected = days == d, onClick = { onDays(d) }, label = { Text(label) })
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LANGUAGES) { lang ->
                    FilterChip(selected = language == lang, onClick = { onLanguage(lang) }, label = { Text(lang ?: "Tüm diller") })
                }
            }
            Text(
                "Son ${if (days == 1) "24 saatte" else "$days günde"} oluşturulan, en çok yıldız alan repolar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                error != null -> Message(error, "Tekrar dene", onRefresh)
                repos.isEmpty() -> Message("Bu filtrelerle repo bulunamadı.", null, null)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(repos, key = { _, r -> r.fullName }) { i, r -> RepoCard(i + 1, r) { onOpen(r) } }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun RepoCard(rank: Int, repo: Repo, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$rank", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(repo.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (repo.description.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(repo.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Stats(repo)
        }
    }
}

@Composable
private fun Stats(repo: Repo) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val style = MaterialTheme.typography.labelMedium
        Text("★ ${compact(repo.stars)}", style = style)
        Text("⑂ ${compact(repo.forks)}", style = style)
        repo.language?.let { Text(it, style = style, color = MaterialTheme.colorScheme.primary) }
    }
}

/** 1234 -> "1,2 B", 1_500_000 -> "1,5 Mn" */
fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1f Mn".format(n / 1_000_000.0).replace('.', ',')
    n >= 1_000 -> "%.1f B".format(n / 1_000.0).replace('.', ',')
    else -> n.toString()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(repo: Repo, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(repo.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            actions = {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, repo.url)
                    context.startActivity(Intent.createChooser(send, "Paylaş"))
                }) { Icon(Icons.Default.Share, "Paylaş") }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(repo.description.ifEmpty { "Açıklama yok." }, style = MaterialTheme.typography.bodyLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Info("Yıldız", "%,d".format(repo.stars).replace(',', '.'))
                    Info("Fork", "%,d".format(repo.forks).replace(',', '.'))
                    Info("Açık sorun", repo.openIssues.toString())
                    Info("Dil", repo.language ?: "—")
                    Info("Lisans", repo.license ?: "—")
                    Info("Oluşturulma", repo.createdAt.split("-").reversed().joinToString("."))
                }
            }
            if (repo.topics.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repo.topics.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.url))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GitHub'da aç") }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
