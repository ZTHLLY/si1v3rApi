package com.si1v3r.si1v3rApi.utils;

import java.util.Set;

/** SQL sorting uses exact column names from a per-query allowlist. */
public class SqlUtils {
    public static boolean validSortField(String sortField, Set<String> allowed) {
        return sortField != null && allowed.contains(sortField);
    }

    public static boolean validSortOrder(String sortOrder) {
        return "ascend".equals(sortOrder) || "descend".equals(sortOrder);
    }
}
