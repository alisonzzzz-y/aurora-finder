package com.aurora.observation.service;

import java.util.Map;

/**
 * Chinese names the geocoder misses or sends to the wrong place, such as 伦敦 finding only Canada and the US.
 * The IDs are the geocoder's own, so each name leads to one exact place.
 */
final class ChineseLocationAliases {
    record Alias(long locationId, String country) {}

    private static final Alias REYKJAVIK = new Alias(3413829, "冰岛");
    private static final Alias YELLOWKNIFE = new Alias(6185377, "加拿大");
    private static final Alias WHITEHORSE = new Alias(6180550, "加拿大");
    private static final Alias TEKAPO = new Alias(2188526, "新西兰");
    private static final Alias TOKYO = new Alias(1850147, "日本");

    private static final Map<String, Alias> ALIASES = Map.ofEntries(
            // Aurora destinations
            Map.entry("雷克雅未克", REYKJAVIK),
            Map.entry("雷克雅维克", REYKJAVIK),
            Map.entry("耶洛奈夫", YELLOWKNIFE),
            Map.entry("黄刀", YELLOWKNIFE),
            Map.entry("黄刀镇", YELLOWKNIFE),
            Map.entry("摩尔曼斯克", new Alias(524305, "俄罗斯")),
            Map.entry("费尔班克斯", new Alias(5861897, "美国")),
            Map.entry("朗伊尔城", new Alias(2729907, "斯瓦尔巴")),
            Map.entry("丘吉尔", new Alias(5922111, "加拿大")),
            Map.entry("怀特霍斯", WHITEHORSE),
            Map.entry("白马市", WHITEHORSE),
            Map.entry("特卡波", TEKAPO),
            Map.entry("蒂卡波", TEKAPO),
            Map.entry("特卡波湖", TEKAPO),
            Map.entry("伊瓦洛", new Alias(656220, "芬兰")),
            Map.entry("萨里塞尔卡", new Alias(7116868, "芬兰")),
            Map.entry("阿比斯库", new Alias(2727664, "瑞典")),
            Map.entry("霍巴特", new Alias(2163355, "澳大利亚")),
            Map.entry("卑尔根", new Alias(3161732, "挪威")),
            // Major cities
            Map.entry("纽约", new Alias(5128581, "美国")),
            Map.entry("伦敦", new Alias(2643743, "英国")),
            Map.entry("东京", TOKYO),
            Map.entry("东京都", TOKYO),
            Map.entry("大阪", new Alias(1853909, "日本")),
            Map.entry("首尔", new Alias(1835848, "韩国")),
            Map.entry("台北", new Alias(1668341, "台湾")),
            Map.entry("罗马", new Alias(3169070, "意大利")),
            Map.entry("奥克兰", new Alias(2193733, "新西兰")),
            Map.entry("温哥华", new Alias(6173331, "加拿大")),
            Map.entry("蒙特利尔", new Alias(6077243, "加拿大")),
            Map.entry("渥太华", new Alias(6094817, "加拿大")),
            Map.entry("西雅图", new Alias(5809844, "美国")));

    private ChineseLocationAliases() {}

    static Alias find(String name) {
        return ALIASES.get(name);
    }
}
