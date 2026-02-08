package com.bi1kbu.articleid.articleidmanagement.service;

import com.bi1kbu.articleid.articleidmanagement.domain.LedgerEntry;
import com.bi1kbu.articleid.articleidmanagement.domain.LedgerStatus;
import com.bi1kbu.articleid.articleidmanagement.domain.OperationLog;
import com.bi1kbu.articleid.articleidmanagement.domain.OperationLogChange;
import com.bi1kbu.articleid.articleidmanagement.domain.RuleConfig;
import com.bi1kbu.articleid.articleidmanagement.web.dto.GenerateRequest;
import com.bi1kbu.articleid.articleidmanagement.web.dto.UpdateLedgerRequest;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ArticleIdService {
    private final StateStorage storage;

    public ArticleIdService(StateStorage storage) {
        this.storage = storage;
    }

    public RuleConfig getRuleConfig() {
        return storage.read().getRuleConfig();
    }

    public RuleConfig updateRuleConfig(RuleConfig config) {
        validateRuleConfig(config);
        var state = storage.read();
        state.setRuleConfig(config);
        appendLog(state, "RULE_UPDATED", "system", null, null, "更新编号规则配置", List.of());
        storage.write(state);
        return config;
    }

    public LedgerEntry preview(GenerateRequest request) {
        var state = storage.read();
        var config = state.getRuleConfig();
        int serial = resolveSerial(request, config, state.getLedger());
        String code = composeCode(config, request.getDeptCode(), request.getDocType(), serial, request.getSubSerial(),
            request.getYear(), request.getRev());
        return LedgerEntry.builder()
            .id("preview")
            .prefix(config.getPrefix())
            .deptCode(request.getDeptCode())
            .docType(request.getDocType())
            .serial(serial)
            .subSerial(request.getSubSerial())
            .year(request.getYear())
            .rev(request.getRev())
            .status(LedgerStatus.REGISTERED)
            .fullCode(code)
            .replacesCode(request.getReplacesCode())
            .replacedByCode(request.getReplacedByCode())
            .dependencyCodes(request.getDependencyCodes())
            .remark(request.getRemark())
            .articleName(request.getArticleName())
            .articleTitleSnapshot(request.getArticleTitle())
            .articleTitle(request.getArticleTitle())
            .articleLink(request.getArticleLink())
            .articlePublishedDate(request.getArticlePublishedDate())
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    public LedgerEntry register(GenerateRequest request, String operator) {
        var state = storage.read();
        var config = state.getRuleConfig();
        int serial = resolveSerial(request, config, state.getLedger());
        String code = composeCode(config, request.getDeptCode(), request.getDocType(), serial, request.getSubSerial(),
            request.getYear(), request.getRev());
        boolean exists = state.getLedger().stream().anyMatch(item -> code.equalsIgnoreCase(item.getFullCode()));
        if (exists) {
            throw new IllegalArgumentException("编号已存在: " + code);
        }

        var now = OffsetDateTime.now();
        var entry = LedgerEntry.builder()
            .id(UUID.randomUUID().toString())
            .prefix(config.getPrefix())
            .deptCode(request.getDeptCode())
            .docType(request.getDocType())
            .serial(serial)
            .subSerial(request.getSubSerial())
            .year(request.getYear())
            .rev(request.getRev())
            .status(LedgerStatus.REGISTERED)
            .fullCode(code)
            .replacesCode(request.getReplacesCode())
            .replacedByCode(request.getReplacedByCode())
            .dependencyCodes(request.getDependencyCodes())
            .createdBy(operator)
            .updatedBy(operator)
            .createdAt(now)
            .updatedAt(now)
            .remark(request.getRemark())
            .articleName(request.getArticleName())
            .articleTitleSnapshot(request.getArticleTitle())
            .articleTitle(request.getArticleTitle())
            .articleLink(request.getArticleLink())
            .articlePublishedDate(request.getArticlePublishedDate())
            .build();
        state.getLedger().add(entry);
        appendLog(state, "LEDGER_REGISTERED", operator, entry.getId(), entry.getFullCode(), "注册新编号", List.of());
        storage.write(state);
        return entry;
    }

    public List<LedgerEntry> list() {
        return storage.read().getLedger().stream()
            .sorted(Comparator.comparing(LedgerEntry::getCreatedAt).reversed())
            .toList();
    }

    public List<OperationLog> logs() {
        return storage.read().getLogs().stream()
            .sorted(Comparator.comparing(OperationLog::getCreatedAt).reversed())
            .toList();
    }

    public LedgerEntry updateLedger(String id, UpdateLedgerRequest request, String operator) {
        var state = storage.read();
        var target = state.getLedger().stream()
            .filter(item -> Objects.equals(item.getId(), id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("编号不存在: " + id));
        var changes = new ArrayList<OperationLogChange>();

        if (request.getStatus() != null) {
            appendFieldChange(changes, "状态", target.getStatus() != null ? target.getStatus().name() : null,
                request.getStatus().name());
            target.setStatus(request.getStatus());
        }
        if (request.getReplacesCode() != null) {
            appendFieldChange(changes, "替代", target.getReplacesCode(), request.getReplacesCode());
            target.setReplacesCode(request.getReplacesCode());
        }
        if (request.getReplacedByCode() != null) {
            appendFieldChange(changes, "被替代", target.getReplacedByCode(), request.getReplacedByCode());
            target.setReplacedByCode(request.getReplacedByCode());
        }
        if (request.getDependencyCodes() != null) {
            appendFieldChange(changes, "依赖", target.getDependencyCodes(), request.getDependencyCodes());
            target.setDependencyCodes(request.getDependencyCodes());
        }
        if (request.getRemark() != null) {
            appendFieldChange(changes, "备注", target.getRemark(), request.getRemark());
            target.setRemark(request.getRemark());
        }
        if (request.getArticleTitle() != null) {
            appendFieldChange(changes, "绑定文章标题", target.getArticleTitle(), request.getArticleTitle());
            target.setArticleTitle(request.getArticleTitle());
            target.setArticleTitleSnapshot(request.getArticleTitle());
        }
        if (request.getArticleName() != null) {
            appendFieldChange(changes, "绑定文章ID", target.getArticleName(), request.getArticleName());
            target.setArticleName(request.getArticleName());
        }
        if (request.getArticleLink() != null) {
            appendFieldChange(changes, "绑定文章链接", target.getArticleLink(), request.getArticleLink());
            target.setArticleLink(request.getArticleLink());
        }
        if (request.getArticlePublishedDate() != null) {
            appendFieldChange(changes, "绑定文章发布日期", target.getArticlePublishedDate(),
                request.getArticlePublishedDate());
            target.setArticlePublishedDate(request.getArticlePublishedDate());
        }
        if (request.getEffectiveDate() != null) {
            appendFieldChange(changes, "生效日期", target.getEffectiveDate(), request.getEffectiveDate());
            target.setEffectiveDate(request.getEffectiveDate());
        }
        if (request.getSupersededDate() != null) {
            appendFieldChange(changes, "替代日期", target.getSupersededDate(), request.getSupersededDate());
            target.setSupersededDate(request.getSupersededDate());
        }
        if (request.getVoidDate() != null) {
            appendFieldChange(changes, "作废日期", target.getVoidDate(), request.getVoidDate());
            target.setVoidDate(request.getVoidDate());
        }

        if (request.getStatus() == LedgerStatus.SUPERSEDED
            && (target.getSupersededDate() == null || target.getSupersededDate().isBlank())) {
            String today = OffsetDateTime.now().toLocalDate().toString();
            appendFieldChange(changes, "替代日期", target.getSupersededDate(), today);
            target.setSupersededDate(today);
        }
        if (request.getStatus() == LedgerStatus.VOID && (target.getVoidDate() == null || target.getVoidDate().isBlank())) {
            String today = OffsetDateTime.now().toLocalDate().toString();
            appendFieldChange(changes, "作废日期", target.getVoidDate(), today);
            target.setVoidDate(today);
        }
        target.setUpdatedBy(operator);
        target.setUpdatedAt(OffsetDateTime.now());
        if (changes.isEmpty()) {
            changes.add(OperationLogChange.builder()
                .field("无字段变化")
                .fromValue("-")
                .toValue("-")
                .build());
        }
        appendLog(state, "LEDGER_UPDATED", operator, target.getId(), target.getFullCode(), "更新编号信息", changes);
        storage.write(state);
        return target;
    }

    public LedgerEntry markDeleted(String id, String operator) {
        var state = storage.read();
        var target = state.getLedger().stream()
            .filter(item -> Objects.equals(item.getId(), id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("编号不存在: " + id));

        if (target.getStatus() == LedgerStatus.DELETED) {
            return target;
        }
        var beforeStatus = target.getStatus();
        target.setStatus(LedgerStatus.DELETED);
        target.setUpdatedBy(operator);
        target.setUpdatedAt(OffsetDateTime.now());
        appendLog(state, "LEDGER_MARKED_DELETED", operator, target.getId(), target.getFullCode(), "标记删除编号",
            List.of(OperationLogChange.builder()
                .field("状态")
                .fromValue(beforeStatus != null ? beforeStatus.name() : "-")
                .toValue(LedgerStatus.DELETED.name())
                .build()));
        storage.write(state);
        return target;
    }

    private int resolveSerial(GenerateRequest request, RuleConfig config, List<LedgerEntry> ledger) {
        validateRuleSelection(request, config);
        if (request.getSerial() != null) {
            return request.getSerial();
        }
        return ledger.stream()
            .filter(item -> Objects.equals(item.getDeptCode(), request.getDeptCode()))
            .filter(item -> !config.isResetPerYear() || item.getYear() == request.getYear())
            .map(LedgerEntry::getSerial)
            .max(Integer::compareTo)
            .orElse(0) + 1;
    }

    private String composeCode(RuleConfig config, String deptCode, String docType, int serial, Integer subSerial,
        int year, Integer rev) {
        String serialPart = String.format("%0" + config.getSerialWidth() + "d", serial);
        String subPart = subSerial != null ? "." + subSerial : "";
        String revPart = rev != null ? ".Rev" + rev : "";

        String pattern = config.getCodePattern();
        if (pattern == null || pattern.isBlank()) {
            pattern = "{prefix}-{dept}/{type}-{serial}{subPart}/{year}{revPart}";
        }

        Map<String, String> tokens = new HashMap<>();
        tokens.put("{prefix}", config.getPrefix());
        tokens.put("{dept}", deptCode);
        tokens.put("{type}", docType);
        tokens.put("{serial}", serialPart);
        tokens.put("{sub}", subSerial != null ? String.valueOf(subSerial) : "");
        tokens.put("{year}", String.valueOf(year));
        tokens.put("{rev}", rev != null ? "Rev" + rev : "");
        tokens.put("{subPart}", subPart);
        tokens.put("{revPart}", revPart);

        tokens.put("{前缀}", config.getPrefix());
        tokens.put("{部门编码}", deptCode);
        tokens.put("{文件类型}", docType);
        tokens.put("{流水号}", serialPart);
        tokens.put("{子文件号}", subSerial != null ? String.valueOf(subSerial) : "");
        tokens.put("{年份}", String.valueOf(year));
        tokens.put("{修订号}", rev != null ? "Rev" + rev : "");
        tokens.put("{子文件片段}", subPart);
        tokens.put("{修订片段}", revPart);

        String result = pattern;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private void validateRuleSelection(GenerateRequest request, RuleConfig config) {
        Set<String> deptCodes = config.getDepartments().stream()
            .map(item -> item.getCode().toUpperCase())
            .collect(Collectors.toSet());
        Set<String> typeCodes = config.getDocTypes().stream()
            .map(item -> item.getCode().toUpperCase())
            .collect(Collectors.toSet());
        if (!deptCodes.contains(request.getDeptCode().toUpperCase())) {
            throw new IllegalArgumentException("无效部门编码: " + request.getDeptCode());
        }
        if (!typeCodes.contains(request.getDocType().toUpperCase())) {
            throw new IllegalArgumentException("无效文件类型编码: " + request.getDocType());
        }
    }

    private void validateRuleConfig(RuleConfig config) {
        if (config.getPrefix() == null || config.getPrefix().isBlank()) {
            throw new IllegalArgumentException("prefix 不能为空");
        }
        if (config.getCodePattern() == null || config.getCodePattern().isBlank()) {
            throw new IllegalArgumentException("codePattern 不能为空");
        }
        if (config.getSerialWidth() < 2 || config.getSerialWidth() > 8) {
            throw new IllegalArgumentException("serialWidth 必须在 2-8");
        }
        if (config.getDepartments() == null || config.getDepartments().isEmpty()) {
            throw new IllegalArgumentException("departments 不能为空");
        }
        if (config.getDocTypes() == null || config.getDocTypes().isEmpty()) {
            throw new IllegalArgumentException("docTypes 不能为空");
        }
        ensureUniqueCodes(config.getDepartments().stream().map(item -> item.getCode()).toList(), "departments");
        ensureUniqueCodes(config.getDocTypes().stream().map(item -> item.getCode()).toList(), "docTypes");
    }

    private void ensureUniqueCodes(List<String> codes, String field) {
        long unique = codes.stream().map(item -> item.toUpperCase()).distinct().count();
        if (unique != codes.size()) {
            throw new IllegalArgumentException(field + " 包含重复 code");
        }
    }

    private void appendLog(com.bi1kbu.articleid.articleidmanagement.domain.PluginState state, String action,
        String operator, String targetId, String targetCode, String detail, List<OperationLogChange> changes) {
        state.getLogs().add(OperationLog.builder()
            .id(UUID.randomUUID().toString())
            .action(action)
            .operator(operator)
            .targetId(targetId)
            .targetCode(targetCode)
            .detail(detail)
            .changes(changes != null ? changes : List.of())
            .createdAt(OffsetDateTime.now())
            .build());
    }

    private void appendFieldChange(List<OperationLogChange> changes, String field, String fromValue, String toValue) {
        String normalizedFrom = normalizeEmpty(fromValue);
        String normalizedTo = normalizeEmpty(toValue);
        if (Objects.equals(normalizedFrom, normalizedTo)) {
            return;
        }
        changes.add(OperationLogChange.builder()
            .field(field)
            .fromValue(normalizedFrom)
            .toValue(normalizedTo)
            .build());
    }

    private String normalizeEmpty(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
