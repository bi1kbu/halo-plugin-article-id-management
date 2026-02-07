package com.bi1kbu.articleid.articleidmanagement.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleOption {
    @NotBlank
    private String code;
    @NotBlank
    @JsonAlias("name")
    private String label;
}
