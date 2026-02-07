package com.bi1kbu.articleid.articleidmanagement.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PluginState {
    private RuleConfig ruleConfig = new RuleConfig();
    private List<LedgerEntry> ledger = new ArrayList<>();
    private List<OperationLog> logs = new ArrayList<>();
}
