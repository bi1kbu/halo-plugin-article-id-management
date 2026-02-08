package com.bi1kbu.articleid.articleidmanagement.domain;

import java.util.List;

public final class DefaultRules {
    private DefaultRules() {
    }

    public static RuleConfig create() {
        return new RuleConfig(
            "MS",
            4,
            "{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}",
            true,
            "POST",
            "",
            List.of(
                new RuleOption("A", "大会"),
                new RuleOption("P", "主席团"),
                new RuleOption("YL", "支部"),
                new RuleOption("MC", "管委会"),
                new RuleOption("AC", "顾问委员会和指导教师委员会"),
                new RuleOption("TC", "技术培训部"),
                new RuleOption("SC", "行政管理中心"),
                new RuleOption("BU", "事业部"),
                new RuleOption("BD", "分社及分部")
            ),
            List.of(
                new RuleOption("Res", "决议"),
                new RuleOption("SR", "纪要"),
                new RuleOption("Ann", "通知公告"),
                new RuleOption("SOP", "操作手册"),
                new RuleOption("Reg", "规章制度"),
                new RuleOption("Doc", "工作文件")
            )
        );
    }
}
