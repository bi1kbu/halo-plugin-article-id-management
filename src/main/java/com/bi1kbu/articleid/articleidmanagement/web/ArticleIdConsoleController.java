package com.bi1kbu.articleid.articleidmanagement.web;

import com.bi1kbu.articleid.articleidmanagement.domain.RuleConfig;
import com.bi1kbu.articleid.articleidmanagement.service.ArticleIdService;
import com.bi1kbu.articleid.articleidmanagement.web.dto.GenerateRequest;
import com.bi1kbu.articleid.articleidmanagement.web.dto.UpdateLedgerRequest;
import com.bi1kbu.articleid.articleidmanagement.web.dto.UpdateRuleConfigRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/apis/api.article-id-management.console/v1")
public class ArticleIdConsoleController {
    private final ArticleIdService articleIdService;

    public ArticleIdConsoleController(ArticleIdService articleIdService) {
        this.articleIdService = articleIdService;
    }

    @GetMapping("/rules")
    public RuleConfig getRules() {
        return articleIdService.getRuleConfig();
    }

    @PutMapping("/rules")
    public RuleConfig updateRules(@Valid @RequestBody UpdateRuleConfigRequest request, Principal principal) {
        String operator = principal != null ? principal.getName() : "system";
        return articleIdService.updateRuleConfig(new RuleConfig(
            request.getPrefix(),
            request.getSerialWidth(),
            request.getCodePattern(),
            request.isResetPerYear(),
            request.getDepartments(),
            request.getDocTypes()
        ), operator);
    }

    @PostMapping("/ledger/preview")
    public Object preview(@Valid @RequestBody GenerateRequest request) {
        return articleIdService.preview(request);
    }

    @PostMapping("/ledger/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Object register(@Valid @RequestBody GenerateRequest request, Principal principal) {
        String operator = principal != null ? principal.getName() : "system";
        return articleIdService.register(request, operator);
    }

    @GetMapping("/ledger")
    public Object list() {
        return articleIdService.list();
    }

    @PatchMapping("/ledger/{id}")
    public Object updateLedger(@PathVariable("id") String id, @RequestBody UpdateLedgerRequest request,
        Principal principal) {
        String operator = principal != null ? principal.getName() : "system";
        return articleIdService.updateLedger(id, request, operator);
    }

    @PostMapping("/ledger/{id}/mark-delete")
    public Object markDelete(@PathVariable("id") String id, Principal principal) {
        String operator = principal != null ? principal.getName() : "system";
        return articleIdService.markDeleted(id, operator);
    }

    @GetMapping("/logs")
    public Object logs() {
        return articleIdService.logs();
    }
}
