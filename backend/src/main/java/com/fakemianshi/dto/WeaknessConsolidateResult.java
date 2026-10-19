package com.fakemianshi.dto;

import lombok.Data;

/**
 * 弱点标签归并结果（同一个知识点的多种写法合并成一条）。
 */
@Data
public class WeaknessConsolidateResult {

    /** 归并前的标签条数 */
    private int before;

    /** 归并后的标签条数 */
    private int after;

    /** 被合并掉的条数 = before - after */
    private int merged;

    public static WeaknessConsolidateResult of(int before, int after) {
        WeaknessConsolidateResult result = new WeaknessConsolidateResult();
        result.setBefore(before);
        result.setAfter(after);
        result.setMerged(before - after);
        return result;
    }
}
