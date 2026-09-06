package net.breadthcharge.exigentheron.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.breadthcharge.exigentheron.data.RuleRepository
import net.breadthcharge.exigentheron.domain.Rule

class RuleListViewModel(private val ruleRepository: RuleRepository) : ViewModel() {

    val rules: StateFlow<List<Rule>> = ruleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(rule: Rule, enabled: Boolean) {
        viewModelScope.launch { ruleRepository.upsert(rule.copy(enabled = enabled)) }
    }

    fun delete(id: String) {
        viewModelScope.launch { ruleRepository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    ruleRepository: RuleRepository,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RuleListViewModel = viewModel(
        factory = viewModelFactory { initializer { RuleListViewModel(ruleRepository) } },
    )
    val rules by viewModel.rules.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Rules") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { innerPadding ->
        if (rules.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No rules yet — apps stay silent until you add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { enabled -> viewModel.setEnabled(rule, enabled) },
                        onClick = { onEditRule(rule.id) },
                        onDelete = { viewModel.delete(rule.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: Rule, onToggle: (Boolean) -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = ruleSummary(rule), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${rule.action} · priority ${rule.priority}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

private fun ruleSummary(rule: Rule): String =
    if (rule.packageNames.isEmpty()) "All apps" else rule.packageNames.joinToString(", ")
