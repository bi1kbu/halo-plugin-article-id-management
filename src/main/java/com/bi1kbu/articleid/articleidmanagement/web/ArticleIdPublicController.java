package com.bi1kbu.articleid.articleidmanagement.web;

import com.bi1kbu.articleid.articleidmanagement.service.ArticleIdService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/apis/api.article-id-management.halo.run/v1alpha1")
public class ArticleIdPublicController {
    private final ArticleIdService articleIdService;

    public ArticleIdPublicController(ArticleIdService articleIdService) {
        this.articleIdService = articleIdService;
    }

    @GetMapping("/lookup")
    public Object lookup(@RequestParam("link") String link) {
        return articleIdService.lookupByLink(link)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到绑定编号"));
    }

    @GetMapping("/health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void health() {
        // for theme side quick connectivity check
    }
}
