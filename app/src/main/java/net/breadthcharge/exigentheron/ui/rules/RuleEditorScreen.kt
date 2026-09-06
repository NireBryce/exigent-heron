package net.breadthcharge.exigentheron.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.breadthcharge.exigentheron.data.RuleRepository
import net.breadthcharge.exigentheron.domain.Rule
import net.breadthcharge.exigentheron.domain.RuleAction
import net.breadthcharge.exigentheron.domain.RuleFormValidator
import java.util.UUID

/**
 * Form state for one [Rule], new or existing. Deliberately not backed
 * by DataStore directly — [save] is the only write, and only fires when
 * [titleError]/[bodyError] are both null, which is the actual mechanism
 * behind BUILD_PLAN.md Phase 3's "invalid regex shows an error at save
 * time rather than crashing later."
 */
class RuleEditorViewModel(
    private val ruleRepository: RuleRepository,
    private val existingRule: Rule?,
) : ViewModel() {

    var enabled by mutableStateOf(existingRule?.enabled ?: true)
    var matchAllApps by mutableStateOf(existingRule?.packageNames?.isEmpty() ?: true)
    var selectedPackages by mutableStateOf(existingRule?.packageNames ?: emptySet())
    var titlePattern by mutableStateOf(existingRule?.titlePattern ?: "")
    var bodyPattern by mutableStateOf(existingRule?.bodyPattern ?: "")
    var action by mutableStateOf(existingRule?.action ?: RuleAction.SPEAK)
    var template by mutableStateOf(existingRule?.template ?: "")
    var priorityText by mutableStateOf((existingRule?.priority ?: 0).toString())

    var titleError by mutableStateOf<String?>(null)
        private set
    var bodyError by mutableStateOf<String?>(null)
        private set
    var appsError by mutableStateOf<String?>(null)
        private set
    var priorityError by mutableStateOf<String?>(null)
        private set

    /** Returns true (and persists) only once every field validates. */
    fun save(onSaved: () -> Unit) {
        val result = RuleFormValidator.validate(
            matchAllApps = matchAllApps,
            selectedPackages = selectedPackages,
            titlePattern = titlePattern,
            bodyPattern = bodyPattern,
            priorityText = priorityText,
        )
        titleError = result.errors.titleError
        bodyError = result.errors.bodyError
        appsError = result.errors.appsError
        priorityError = result.errors.priorityError

        if (!result.errors.isValid) return

        val rule = Rule(
            id = existingRule?.id ?: UUID.randomUUID().toString(),
            enabled = enabled,
            packageNames = if (matchAllApps) emptySet() else selectedPackages,
            titlePattern = result.titlePattern,
            bodyPattern = result.bodyPattern,
            action = action,
            template = template.trim().ifBlank { null },
            priority = result.priority!!,
        )
        viewModelScope.launch {
            ruleRepository.upsert(rule)
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    ruleRepository: RuleRepository,
    existingRule: Rule?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RuleEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { RuleEditorViewModel(ruleRepository, existingRule) } },
    )
    var showAppPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(if (existingRule == null) "New rule" else "Edit rule") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = viewModel.enabled, onCheckedChange = { viewModel.enabled = it })
                Text("Enabled", modifier = Modifier.padding(start = 8.dp))
            }

            Text("Match", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = viewModel.matchAllApps, onClick = { viewModel.matchAllApps = true })
                Text("All apps")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !viewModel.matchAllApps, onClick = { viewModel.matchAllApps = false })
                Text("Selected apps")
            }
            if (!viewModel.matchAllApps) {
                Text(
                    text = if (viewModel.selectedPackages.isEmpty()) {
                        "No apps chosen"
                    } else {
                        viewModel.selectedPackages.joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { showAppPicker = true }) { Text("Choose apps") }
            }
            viewModel.appsError?.let { ErrorText(it) }

            OutlinedTextField(
                value = viewModel.titlePattern,
                onValueChange = { viewModel.titlePattern = it },
                label = { Text("Title pattern (regex, optional)") },
                isError = viewModel.titleError != null,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            viewModel.titleError?.let { ErrorText(it) }

            OutlinedTextField(
                value = viewModel.bodyPattern,
                onValueChange = { viewModel.bodyPattern = it },
                label = { Text("Body pattern (regex, optional)") },
                isError = viewModel.bodyError != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            viewModel.bodyError?.let { ErrorText(it) }

            Text("Action", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            RuleAction.entries.forEach { candidate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = viewModel.action == candidate, onClick = { viewModel.action = candidate })
                    Text(candidate.name)
                }
            }

            OutlinedTextField(
                value = viewModel.template,
                onValueChange = { viewModel.template = it },
                label = { Text("Template, e.g. \"Message from {title}\" (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            OutlinedTextField(
                value = viewModel.priorityText,
                onValueChange = { viewModel.priorityText = it },
                label = { Text("Priority (higher wins ties)") },
                isError = viewModel.priorityError != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            viewModel.priorityError?.let { ErrorText(it) }

            TextButton(
                onClick = { viewModel.save(onSaved = onDone) },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Save") }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            initialSelection = viewModel.selectedPackages,
            onDone = { selected ->
                viewModel.selectedPackages = selected
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AppPickerDialog(
    initialSelection: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selection by remember { mutableStateOf(initialSelection) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { loadInstalledApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose apps") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(app.label)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(
                            checked = app.packageName in selection,
                            onCheckedChange = { checked ->
                                selection = if (checked) selection + app.packageName else selection - app.packageName
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(selection) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
