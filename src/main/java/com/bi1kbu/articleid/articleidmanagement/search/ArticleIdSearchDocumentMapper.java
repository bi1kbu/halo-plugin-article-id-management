package com.bi1kbu.articleid.articleidmanagement.search;

import com.bi1kbu.articleid.articleidmanagement.domain.LedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.util.StringUtils;
import run.halo.app.search.HaloDocument;

public final class ArticleIdSearchDocumentMapper {

    public static final String DOC_TYPE = "article-id.ledger.halo.run";

    private ArticleIdSearchDocumentMapper() {
    }

    public static HaloDocument toDocument(LedgerEntry entry) {
        var ledgerId = safe(entry.getId(), "unknown");
        var fullCode = safe(entry.getFullCode(), "未命名编号");
        var articleTitle = safe(entry.getArticleTitle(), "");
        var title = StringUtils.hasText(articleTitle) ? (fullCode + " - " + articleTitle) : fullCode;
        var statusDisplay = safe(entry.getStatusDisplay(), safe(entry.getStatus() != null ? entry.getStatus().name() : null, "-"));
        var permalink = safe(entry.getArticleLink(), "/console/article-id-management/query");

        var doc = new HaloDocument();
        doc.setId("article-id-ledger-" + ledgerId);
        doc.setType(DOC_TYPE);
        doc.setMetadataName(ledgerId);
        doc.setAnnotations(Map.of(
            "ledgerId", ledgerId,
            "fullCode", fullCode
        ));
        doc.setTitle(title);
        doc.setDescription(buildDescription(entry, statusDisplay));
        doc.setContent(buildContent(entry, statusDisplay));
        doc.setPermalink(permalink);
        doc.setOwnerName(safe(entry.getCreatedBy(), "system"));
        doc.setPublished(true);
        doc.setExposed(true);
        doc.setRecycled(false);
        doc.setCreationTimestamp(toInstant(entry.getCreatedAt() != null ? entry.getCreatedAt().toInstant() : null));
        doc.setUpdateTimestamp(toInstant(entry.getUpdatedAt() != null ? entry.getUpdatedAt().toInstant() : null));
        doc.setCategories(List.of());
        doc.setTags(List.of());
        return doc;
    }

    private static String buildDescription(LedgerEntry entry, String statusDisplay) {
        return new StringJoiner(" | ")
            .add("编号: " + safe(entry.getFullCode(), "-"))
            .add("部门: " + safe(entry.getDeptCode(), "-"))
            .add("类型: " + safe(entry.getDocType(), "-"))
            .add("状态: " + statusDisplay)
            .add("链接: " + safe(entry.getArticleLink(), "-"))
            .toString();
    }

    private static String buildContent(LedgerEntry entry, String statusDisplay) {
        var joiner = new StringJoiner(" ");
        joiner.add(safe(entry.getFullCode(), ""));
        joiner.add(safe(entry.getArticleTitle(), ""));
        joiner.add(safe(entry.getArticleLink(), ""));
        joiner.add(safe(entry.getDeptCode(), ""));
        joiner.add(safe(entry.getDocType(), ""));
        joiner.add(statusDisplay);
        joiner.add(safe(entry.getStatusDisplayKey(), ""));
        if (entry.getStatus() != null) {
            joiner.add(entry.getStatus().name());
        }
        joiner.add(safe(entry.getReplacesCode(), ""));
        joiner.add(safe(entry.getReplacedByCode(), ""));
        joiner.add(safe(entry.getDependencyCodes(), ""));
        joiner.add(safe(entry.getRemark(), ""));
        return joiner.toString().trim();
    }

    private static Instant toInstant(Instant value) {
        return value != null ? value : Instant.now();
    }

    private static String safe(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
